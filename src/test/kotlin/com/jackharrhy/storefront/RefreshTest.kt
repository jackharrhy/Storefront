package com.jackharrhy.storefront

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.logging.Logger

class RefreshTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `capture yields at the count or time budget and resumes without losing targets`() {
        val remaining = (1..7).iterator()
        val captured = mutableListOf<Int>()
        var time = 0L
        val capture: (Int) -> Unit = { captured.add(it); time += 3 }
        assertEquals(2, captureBatch(remaining, 2, 100, { time }, capture))
        assertEquals(2, captureBatch(remaining, 8, 5, { time }, capture))
        assertEquals(1, captureBatch(remaining, 8, 1, { time }, capture))
        assertEquals(2, captureBatch(remaining, 8, 100, { time }, capture))
        assertEquals(0, captureBatch(remaining, 8, 100, { time }, capture))
        assertEquals((1..7).toList(), captured)
    }

    @Test
    fun `refresh changes are conditional so player edits and replacement rows survive stale captures`() {
        val database = directory.resolve("refresh.db").toString()
        val storage = Storage(Logger.getAnonymousLogger(), database)
        fun sql(statement: String) = DriverManager.getConnection("jdbc:sqlite:$database").use {
            it.createStatement().use { query -> query.executeUpdate(statement) }
        }
        sql("""INSERT INTO chest VALUES (1, '{"uuid":"alice","name":"Alice"}', 'world:1.0:64.0:2.0', '[]', 1, '[]')""")
        val original = storage.refreshTargets().single()
        assertEquals(1, storage.applyRefresh(listOf(RefreshChange(original, "[null]"))))
        val refreshed = storage.refreshTargets().single()
        assertTrue(refreshed.modified > original.modified)
        assertEquals("[null]", refreshed.contents)
        assertEquals(0, storage.applyRefresh(listOf(RefreshChange(original, null))))
        sql("UPDATE chest SET contents = '[null,null]', modified = ${refreshed.modified + 1} WHERE id = 1")
        assertEquals(0, storage.applyRefresh(listOf(RefreshChange(refreshed, "[]"))))
        assertEquals("[null,null]", storage.refreshTargets().single().contents)
        val deleted = storage.refreshTargets().single()
        sql("DELETE FROM chest WHERE id = 1")
        sql("""INSERT INTO chest VALUES (1, '{"uuid":"bob","name":"Bob"}', 'world:2.0:64.0:2.0', '[]', ${deleted.modified + 1}, '[]')""")
        assertEquals(0, storage.applyRefresh(listOf(RefreshChange(deleted, null))))
        assertEquals(1, storage.applyRefresh(listOf(RefreshChange(storage.refreshTargets().single(), null))))
        assertTrue(storage.refreshTargets().isEmpty())
    }
}
