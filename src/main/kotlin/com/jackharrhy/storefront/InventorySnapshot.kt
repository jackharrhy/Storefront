package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer
import org.bukkit.configuration.serialization.ConfigurationSerializable
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.bukkit.block.data.type.Chest
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory

internal val inventoryGson = GsonBuilder().registerTypeHierarchyAdapter(
    ConfigurationSerializable::class.java,
    JsonSerializer<ConfigurationSerializable> { value, _, context -> context.serialize(value.serialize()) }
).create()

fun inventoryToJsonString(inventory: Inventory): String {
    check(Bukkit.isPrimaryThread()) { "Inventory snapshots must be captured on the server thread" }
    val items = inventory.map { stack ->
        if (stack == null || stack.type.isAir) null else ItemSnapshot(
            PlainTextComponentSerializer.plainText().serialize(stack.effectiveName()),
            stack.type.key.toString(), stack.amount, inventoryGson.toJsonTree(stack.itemMeta?.serialize()),
            stack.type.isBlock, stack.type.maxDurability.toInt()
        )
    }
    return inventoryGson.toJson(items)
}

// Inspect chunk availability before Bukkit is allowed to combine a double chest.
internal fun chestChunksLoaded(location: Location): Boolean {
    val world = location.world ?: return false
    val x = location.blockX
    val z = location.blockZ
    if (!world.isChunkLoaded(x shr 4, z shr 4)) return false
    val data = world.getBlockAt(x, location.blockY, z).blockData as? Chest ?: return true
    if (data.type == Chest.Type.SINGLE) return true
    val turn = if (data.type == Chest.Type.LEFT) 1 else -1
    return world.isChunkLoaded((x - data.facing.modZ * turn) shr 4, (z + data.facing.modX * turn) shr 4)
}
