package com.jackharrhy.storefront

import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional

class ItemSerializationTest {
    private class NestedItem(private val inaccessibleState: Optional<String>) : ConfigurationSerializable {
        override fun serialize(): Map<String, Any> = mapOf("type" to inaccessibleState.orElseThrow(), "amount" to 3)
    }

    private class ContainerMeta : ConfigurationSerializable {
        override fun serialize(): Map<String, Any> = mapOf("items" to listOf(NestedItem(Optional.of("DIAMOND"))))
    }

    @Test
    fun `nested item metadata uses Bukkit serialization without reflecting into Java internals`() {
        val json = inventoryGson.toJsonTree(mapOf("meta" to ContainerMeta()))
        val nested = json.asJsonObject["meta"].asJsonObject["items"].asJsonArray.single().asJsonObject
        assertEquals("DIAMOND", nested["type"].asString)
        assertEquals(3, nested["amount"].asInt)
        assertFalse(nested.has("inaccessibleState"))
    }
}
