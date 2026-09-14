package com.jackharrhy.storefront

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.jdbi.v3.core.Jdbi
import org.sqlite.SQLiteDataSource
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicLong

data class RefreshTarget(val id: Int, val location: String, val contents: String, val modified: Long)
data class RefreshChange(val target: RefreshTarget, val contents: String?)
data class Owner(val uuid: String, val name: String)
data class Content(val id: Int, val owner: JsonElement, val contents: JsonElement, val description: JsonElement)

class Storage(fileName: String) {
    private val gson = Gson()
    private val modifiedClock = AtomicLong(System.currentTimeMillis())
    private val jdbi = Jdbi.create(SQLiteDataSource().apply {
        url = "jdbc:sqlite:$fileName"
        config.setBusyTimeout(5000)
    })

    init {
        jdbi.useHandle<RuntimeException> { handle ->
            handle.execute("PRAGMA journal_mode=WAL")
            handle.execute("""
                CREATE TABLE IF NOT EXISTS chest (
                    id INTEGER PRIMARY KEY,
                    owner TEXT NOT NULL,
                    location TEXT NOT NULL,
                    contents TEXT NOT NULL,
                    modified INTEGER NOT NULL,
                    description TEXT NOT NULL
                )
            """.trimIndent())
            handle.execute("CREATE INDEX IF NOT EXISTS chest_location ON chest(location)")
            val latest = handle.createQuery("SELECT COALESCE(MAX(modified), 0) FROM chest")
                .mapTo(Long::class.java).one()
            modifiedClock.updateAndGet { maxOf(it, latest) }
        }
    }

    private fun nextModified(): Long = modifiedClock.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

    private fun readContent(row: ResultSet) = Content(
        row.getInt("id"),
        JsonParser.parseString(row.getString("owner")),
        JsonParser.parseString(row.getString("contents")),
        JsonParser.parseString(row.getString("description"))
    )

    val allContents: List<Content>
        get() = jdbi.withHandle<List<Content>, RuntimeException> { handle ->
            handle.createQuery("SELECT id, owner, contents, description FROM chest")
                .map { row, _ -> readContent(row) }.list()
        }

    fun storefrontContentsById(id: Int): Content? = jdbi.withHandle<Content?, RuntimeException> { handle ->
        handle.select("SELECT id, owner, contents, description FROM chest WHERE id = ?", id)
            .map { row, _ -> readContent(row) }.findOne().orElse(null)
    }

    fun storefrontLocationString(id: Int): String? = jdbi.withHandle<String?, RuntimeException> { handle ->
        handle.select("SELECT location FROM chest WHERE id = ?", id)
            .mapTo(String::class.java).findOne().orElse(null)
    }

    fun ownerUUID(location: String): String? = jdbi.withHandle<String?, RuntimeException> { handle ->
        handle.select("SELECT json_extract(owner, '$.uuid') FROM chest WHERE location = ?", location)
            .mapTo(String::class.java).findFirst().orElse(null)
    }

    fun newStorefront(owner: Owner, location: String, contents: String, description: Array<String>): Boolean =
        jdbi.withHandle<Boolean, RuntimeException> { handle ->
            handle.createUpdate("""
                INSERT OR REPLACE INTO chest (id, owner, location, contents, modified, description)
                VALUES ((SELECT id FROM chest WHERE location = :location),
                    :owner, :location, :contents, :modified, :description)
            """.trimIndent())
                .bind("owner", gson.toJson(owner))
                .bind("location", location)
                .bind("contents", contents)
                .bind("modified", nextModified())
                .bind("description", gson.toJson(description))
                .execute() == 1
        }

    fun updateStorefront(location: String, contents: String, description: Array<String>): Boolean =
        jdbi.withHandle<Boolean, RuntimeException> { handle ->
            handle.createUpdate("""
                UPDATE chest SET contents = :contents, modified = :modified, description = :description
                WHERE id = (SELECT id FROM chest WHERE location = :location)
            """.trimIndent())
                .bind("location", location)
                .bind("contents", contents)
                .bind("modified", nextModified())
                .bind("description", gson.toJson(description))
                .execute() == 1
        }

    fun removeStorefront(ownerUuid: String, location: String): Boolean =
        jdbi.withHandle<Boolean, RuntimeException> { handle ->
            handle.execute("DELETE FROM chest WHERE location = ? AND json_extract(owner, '$.uuid') = ?", location, ownerUuid) == 1
        }

    fun refreshTargets(): List<RefreshTarget> = jdbi.withHandle<List<RefreshTarget>, RuntimeException> { handle ->
        handle.createQuery("SELECT id, location, contents, modified FROM chest ORDER BY id")
            .map { row, _ -> RefreshTarget(row.getInt("id"), row.getString("location"), row.getString("contents"), row.getLong("modified")) }
            .list()
    }

    fun applyRefresh(changes: List<RefreshChange>): Int {
        if (changes.isEmpty()) return 0
        return jdbi.inTransaction<Int, RuntimeException> { handle ->
            changes.sumOf { (target, contents) ->
                val statement = if (contents == null) {
                    handle.createUpdate("DELETE FROM chest WHERE id = :id AND modified = :previous")
                } else {
                    handle.createUpdate("UPDATE chest SET contents = :contents, modified = :modified WHERE id = :id AND modified = :previous")
                        .bind("contents", contents).bind("modified", nextModified())
                }
                statement.bind("id", target.id).bind("previous", target.modified).execute()
            }
        }
    }
}
