package com.jackharrhy.storefront

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StorageTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reads legacy rows with stable IDs and preserves data across restarts`() {
        val database = directory.resolve("storefront.db").toString()
        val storage = Storage(database)
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.prepareStatement("INSERT INTO chest (id, owner, location, contents, modified, description) VALUES (?, ?, ?, ?, ?, ?)").use { statement ->
                statement.setInt(1, 42)
                statement.setString(2, """{"uuid":"550e8400-e29b-41d4-a716-446655440000","name":"Alice"}""")
                statement.setString(3, "world:1.0:64.0:2.0")
                statement.setString(4, """[null,{"key":"minecraft:diamond","amount":2,"meta":{}}]""")
                statement.setLong(5, 123456789)
                statement.setString(6, """["[storefront]","Diamonds","For sale",""]""")
                statement.executeUpdate()
            }
        }
        val storefront = storage.allContents.single()
        assertEquals(42, storefront.id)
        assertEquals("world:1.0:64.0:2.0", storage.storefrontLocationString(42))
        assertEquals("Alice", storefront.owner.asJsonObject["name"].asString)
        assertTrue(storefront.contents.asJsonArray[0].isJsonNull)
        assertEquals(2, storefront.contents.asJsonArray[1].asJsonObject["amount"].asInt)
        assertEquals(storefront, Storage(database).storefrontContentsById(42))
        assertNull(storage.storefrontContentsById(999))
    }

    @Test
    fun `saving an existing listing cannot replace another owner's data`() {
        val storage = Storage(directory.resolve("protected.db").toString())
        val location = "world:1.0:64.0:2.0"
        assertTrue(storage.newStorefront(Owner("alice", "Alice"), location, "[null]", arrayOf("Original")))
        val original = storage.allContents.single()
        assertFalse(storage.newStorefront(Owner("bob", "Bob"), location, "[]", arrayOf("Stolen")))
        assertEquals(original, storage.allContents.single())
        assertTrue(storage.newStorefront(Owner("alice", "Alice"), location, "[]", arrayOf("Updated")))
        assertEquals(original.id, storage.allContents.single().id)
        assertEquals("Updated", storage.allContents.single().description.asJsonArray[0].asString)
    }

    @Test
    fun `competing first saves cannot claim the same location or replace its winning ID`() {
        val storage = Storage(directory.resolve("competing.db").toString())
        val location = "world:1.0:64.0:2.0"
        val start = CountDownLatch(1)
        val owners = listOf(Owner("alice", "Alice"), Owner("bob", "Bob"))
        Executors.newFixedThreadPool(2).use { worker ->
            val saves = owners.map { owner -> worker.submit<Boolean> {
                check(start.await(5, TimeUnit.SECONDS))
                storage.newStorefront(owner, location, "[]", arrayOf(owner.name))
            } }
            start.countDown()
            val accepted = saves.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, accepted.count { it })
            val winner = owners[accepted.indexOf(true)]
            val original = storage.allContents.single()
            assertEquals(winner.uuid, storage.ownerUUID(location))
            assertTrue(storage.newStorefront(winner, location, "[null]", arrayOf("Updated")))
            assertEquals(original.id, storage.allContents.single().id)
        }
    }

    @Test
    fun `updates enforce ownership atomically and preserve the original owner and listing ID`() {
        val storage = Storage(directory.resolve("update.db").toString())
        val location = "world:1.0:64.0:2.0"
        assertTrue(storage.mayEdit("alice", location))
        assertFalse(storage.updateStorefront("alice", location, "[]", arrayOf("Missing")))
        storage.newStorefront(Owner("alice", "Alice"), location, "[]", arrayOf("Original"))
        val original = storage.allContents.single()
        assertTrue(storage.mayEdit("alice", location))
        assertFalse(storage.mayEdit("bob", location))
        assertFalse(storage.updateStorefront("bob", location, "[null]", arrayOf("Stolen")))
        assertEquals(original, storage.allContents.single())
        assertTrue(storage.updateStorefront("alice", location, "[null]", arrayOf("Updated")))
        val updated = storage.allContents.single()
        assertEquals(original.id, updated.id)
        assertEquals(original.owner, updated.owner)
        assertEquals("[null]", updated.contents.toString())
        assertEquals("Updated", updated.description.asJsonArray[0].asString)
    }

    @Test
    fun `ownership checks the UUID stored with the listing`() {
        val database = directory.resolve("owners.db").toString()
        val storage = Storage(database)
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val location = "world:1.0:64.0:2.0"
        assertTrue(storage.newStorefront(Owner(uuid, "Alice"), location, "[]", arrayOf("[storefront]")))
        assertEquals(uuid, storage.ownerUUID(location))
        assertFalse(storage.removeStorefront("550e8400-e29b-41d4-a716-446655440001", location))
        assertTrue(storage.removeStorefront(uuid, location))
        assertTrue(storage.allContents.isEmpty())
    }
}
