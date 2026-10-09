package com.jackharrhy.storefront

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ListingTest {
    @Test
    fun `legacy and namespaced world locations round trip without changing storage keys`() {
        for (world in listOf("world", "world_nether", "custom:dimension")) {
            val value = "$world:-17.0:64.0:0.5"
            assertEquals(ListingLocation(world, -17.0, 64.0, 0.5), ListingLocation.parse(value))
            assertEquals(value, ListingLocation.parse(value).serialize())
        }
        for (value in listOf("world", "world:1:2", "world:x:2:3")) {
            assertThrows(IllegalArgumentException::class.java) { ListingLocation.parse(value) }
        }
    }

    @Test
    fun `snapshot JSON retains frontend fields null slots and opaque nested components`() {
        val gson = Gson()
        val meta = JsonParser.parseString("""{"damage":3,"components":{"minecraft:container":[{"slot":0,"item":{"id":"minecraft:diamond","count":4}}]},"internal":"compressed-base64"}""")
        val actual = gson.toJsonTree(listOf(null, ItemSnapshot("Box", "minecraft:shulker_box", 1, meta, true, 0)))
        val expected = JsonParser.parseString("""[null,{"name":"Box","key":"minecraft:shulker_box","amount":1,"meta":$meta,"isBlock":true,"maxDurability":0}]""")
        assertEquals(expected, actual)
        assertEquals(JsonParser.parseString("""{"world":"world","centerX":-10,"centerZ":20,"scale":{"name":"NORMAL","ordinal":2}}"""),
            gson.toJsonTree(MapSnapshot("world", -10, 20, MapScale("NORMAL", 2))))
        assertTrue(isStorefrontSign("[StoReFront]"))
        assertFalse(isStorefrontSign(" [storefront]"))
        assertFalse(isStorefrontSign(null))
    }
}
