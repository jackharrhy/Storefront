package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.jdbi.v3.core.Jdbi

import java.io.File
import java.time.Instant
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors

fun serializeLocation(location: Location): String {
    return ("" + location.world.name
            + ":" + location.x
            + ":" + location.y
            + ":" + location.z)
}

fun deserializeLocation(serializedLocation: String): Location {
    val splitLocation = serializedLocation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return Location(
            Bukkit.getWorld(splitLocation[0]),
            java.lang.Double.parseDouble(splitLocation[1]),
            java.lang.Double.parseDouble(splitLocation[2]),
            java.lang.Double.parseDouble(splitLocation[3])
    )
}

fun serializeOwner(player: Player): String {
    val newPlayer = JsonObject()
    newPlayer.addProperty("uuid", player.uniqueId.toString())
    newPlayer.addProperty("name", player.name)
    return GsonBuilder().create().toJson(newPlayer)
}

fun deserializeOwner(serializedOwner: String): Owner {
    val owner = JsonParser().parse(serializedOwner).asJsonObject
    return Owner(
            owner.getAsJsonPrimitive("uuid").toString(),
            owner.getAsJsonPrimitive("name").toString()
    )
}

fun flattenDescription(desc: Array<String>): String {
    return GsonBuilder().create().toJson(desc)
}

data class Owner(val uuid: String , val name: String)
data class Content(val owner: JsonElement, val contents: JsonElement, val description: JsonElement)

class Storage(private val logger: Logger, fileName: String) {
    private val jdbi: Jdbi

    val allContents: List<Content>
        get() = jdbi.withHandle<List<Content>, RuntimeException> { handle ->
            handle.createQuery("SELECT owner, contents, description FROM chest")
                    .map {
                        rs, _ -> Content(
                            JsonParser().parse(rs.getString("owner")),
                            JsonParser().parse(rs.getString("contents")),
                            JsonParser().parse(rs.getString("description"))
                        )
                    }
                    .list()
        }

    val allLocations: List<Location>
        get() = jdbi.withHandle<List<Location>, RuntimeException> { handle ->
            handle.createQuery("SELECT location FROM chest")
                    .mapTo(String::class.java)
                    .list()
                    .stream()
                    .map { string -> deserializeLocation(string) }
                    .collect(Collectors.toList())
        }

    init {
        val dbFile = File(fileName)

        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, e.message)
                e.printStackTrace()
            }
        }

        val connectionString = "jdbc:sqlite:$fileName"
        this.jdbi = Jdbi.create(connectionString)
        this.initialize()
    }

    private fun initialize() {
        val createChestTableSql = ("CREATE TABLE IF NOT EXISTS chest ( "
                + "id INTEGER PRIMARY KEY, "
                + "owner TEXT NOT NULL, "
                + "location TEXT NOT NULL, "
                + "contents TEXT NOT NULL, "
                + "modified INTEGER NOT NULL, "
                + "description TEXT NOT NULL "
                + ")")
        jdbi.useHandle<RuntimeException> { handle -> handle.execute(createChestTableSql) }
    }

    fun newStorefront(owner: Player, location: Location, contents: String, description: Array<String>): Boolean? {
        val ownerSerialized = serializeOwner(owner)
        val locationSerialized = serializeLocation(location)
        val flatDescription = flattenDescription(description)

        val insertOrUpdateStorefrontSql = "INSERT OR REPLACE INTO chest " +
                "(id, owner, location, contents, modified, description) " +
                "VALUES ((SELECT id FROM chest WHERE location = :location), " +
                ":owner, :location, :contents, :modified, :description)"

        val updated = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.createUpdate(insertOrUpdateStorefrontSql)
                    .bind("location", locationSerialized)
                    .bind("owner", ownerSerialized)
                    .bind("contents", contents)
                    .bind("modified", Instant.now().epochSecond)
                    .bind("description", flatDescription)
                    .execute()
        }

        when (updated) {
            1 -> return true
            0 -> return false
            else -> {
                logger.log(Level.SEVERE, "Updated more than one storefront on a single call")
            }
        }
        return null
    }

    fun updateStorefront(location: Location, contents: String): Boolean? {
        val locationSerialized = serializeLocation(location)

        val updateStorefrontSql = "UPDATE chest SET contents = :contents, modified = :modified " +
                "WHERE id = (SELECT id FROM chest WHERE location = :location)"

        val updated = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.createUpdate(updateStorefrontSql)
                    .bind("location", locationSerialized)
                    .bind("contents", contents)
                    .bind("modified", Instant.now().epochSecond)
                    .execute()
        }

        when (updated) {
            1 -> return true
            0 -> return false
            else -> {
                logger.log(Level.SEVERE, "Updated more than one storefront on a single call")
            }
        }
        return null
    }

    fun updateStorefront(location: Location, contents: String, description: Array<String>): Boolean? {
        val locationSerialized = serializeLocation(location)
        val flatDescription = flattenDescription(description)

        val updateStorefrontSql = "UPDATE chest SET " +
                "contents = :contents, modified = :modified, description = :description " +
                "WHERE id = (SELECT id FROM chest WHERE location = :location)"

        val updated = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.createUpdate(updateStorefrontSql)
                    .bind("location", locationSerialized)
                    .bind("contents", contents)
                    .bind("modified", Instant.now().epochSecond)
                    .bind("description", flatDescription)
                    .execute()
        }

        when (updated) {
            1 -> return true
            0 -> return false
            else -> {
                logger.log(Level.SEVERE, "Updated more than one storefront on a single call")
            }
        }
        return null
    }

    fun removeStorefront(owner: Player, location: Location): Boolean? {
        val removeStorefrontSql = "DELETE FROM chest WHERE location = ? AND owner = ?"

        val removed = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.execute(removeStorefrontSql, serializeLocation(location), serializeOwner(owner))
        }

        when (removed) {
            1 -> return true
            0 -> return false
            else -> logger.log(Level.SEVERE, "Removed an unexpected number of storefronts ($removed)")
        }
        return null
    }

    fun storefrontExists(location: Location): Boolean {
        val storefrontCountSql = "SELECT COUNT(*) FROM chest WHERE location = ?"

        val count = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.select(storefrontCountSql, serializeLocation(location)).mapTo(Int::class.java).findOnly()
        }

        when (count) {
            1 -> return true
            0 -> return false
            else -> logger.log(Level.SEVERE, "Found an unexpected number of storefronts on a single call ($count)")
        }
        return true
    }

    fun removeStorefront(location: Location): Boolean? {
        val removeStorefrontSql = "DELETE FROM chest WHERE location = ?"

        val removed = jdbi.withHandle<Int, RuntimeException> { handle ->
            handle.execute(removeStorefrontSql, serializeLocation(location))
        }

        when (removed) {
            1 -> return true
            0 -> return false
            else -> logger.log(Level.SEVERE, "Removed more than one storefront on a single call")
        }
        return null
    }

    fun ownerUUID(location: Location): Optional<String> {
        val getOwnerFromChestSql = "SELECT owner FROM chest WHERE location = ?"
        return jdbi.withHandle<Optional<String>, RuntimeException> { handle ->
            handle.select(getOwnerFromChestSql, serializeLocation(location))
                    .mapTo(String::class.java)
                    .findFirst()
                    .map { serializedOwner -> deserializeOwner(serializedOwner).uuid }
        }
    }
}
