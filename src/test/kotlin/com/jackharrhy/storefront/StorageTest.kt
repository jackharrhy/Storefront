package com.jackharrhy.storefront

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.logging.Logger

class StorageTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reads legacy rows with stable IDs and preserves data across restarts`() {
        val database = directory.resolve("storefront.db").toString()
        val storage = Storage(Logger.getAnonymousLogger(), database)
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
        assertEquals(storefront, Storage(Logger.getAnonymousLogger(), database).storefrontContentsById(42))
        assertNull(storage.storefrontContentsById(999))
        assertEquals(Owner("550e8400-e29b-41d4-a716-446655440000", "Alice"), deserializeOwner(storefront.owner.toString()))
    }
    @Test
    fun `ownership uses UUID so renamed owners can remove their listing`() {
        val database = directory.resolve("owners.db").toString()
        val storage = Storage(Logger.getAnonymousLogger(), database)
        val world = java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.World::class.java.classLoader, arrayOf(org.bukkit.World::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> "world"
                else -> error("Unexpected World call: ${method.name}")
            }
        } as org.bukkit.World
        fun player(name: String, uuid: String) = java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.entity.Player::class.java.classLoader, arrayOf(org.bukkit.entity.Player::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "getUniqueId" -> java.util.UUID.fromString(uuid)
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as org.bukkit.entity.Player
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val location = org.bukkit.Location(world, 1.0, 64.0, 2.0)
        assertEquals(true, storage.newStorefront(player("Alice", uuid), location, "[]", arrayOf("[storefront]")))
        assertEquals(uuid, storage.ownerUUID(location).orElseThrow())
        assertEquals(false, storage.removeStorefront(player("Bob", "550e8400-e29b-41d4-a716-446655440001"), location))
        assertEquals(true, storage.removeStorefront(player("RenamedAlice", uuid), location))
        assertTrue(storage.allContents.isEmpty())
    }

}
