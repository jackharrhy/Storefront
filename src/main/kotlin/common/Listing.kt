package com.jackharrhy.storefront

import com.google.gson.JsonElement

/** Coordinates keep their legacy double representation; world names may contain colons. */
data class ListingLocation(val world: String, val x: Double, val y: Double, val z: Double) {
    fun serialize(): String = "$world:$x:$y:$z"

    companion object {
        @JvmStatic fun parse(value: String): ListingLocation {
            val z = value.lastIndexOf(':')
            val y = value.lastIndexOf(':', z - 1)
            val x = value.lastIndexOf(':', y - 1)
            require(x >= 0) { "Invalid storefront location: $value" }
            return ListingLocation(value.substring(0, x), value.substring(x + 1, y).toDouble(),
                value.substring(y + 1, z).toDouble(), value.substring(z + 1).toDouble())
        }
    }
}

fun isStorefrontSign(firstLine: String?): Boolean = firstLine.equals("[storefront]", ignoreCase = true)

// Platform serializers translate item components; these fields are the frontend contract.
data class ItemSnapshot(val name: String, val key: String, val amount: Int, val meta: JsonElement?, val isBlock: Boolean, val maxDurability: Int)
data class MapSnapshot(val world: String?, val centerX: Int, val centerZ: Int, val scale: MapScale)
data class MapScale(val name: String, val ordinal: Int)
