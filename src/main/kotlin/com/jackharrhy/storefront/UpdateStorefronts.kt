package com.jackharrhy.storefront

import com.google.gson.Gson
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Chest
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

fun inventoryToJsonString(inventory: Inventory): String {
    val gson = Gson()
    val items = inventory.map { stack ->
        if (stack == null || stack.type.isAir) null else mapOf(
            "name" to PlainTextComponentSerializer.plainText().serialize(stack.effectiveName()),
            "key" to stack.type.key.toString(),
            "amount" to stack.amount,
            "meta" to gson.toJsonTree(stack.itemMeta?.serialize()),
            "isBlock" to stack.type.isBlock,
            "maxDurability" to stack.type.maxDurability
        )
    }
    return gson.toJson(items)
}

class UpdateStorefronts(private val plugin: JavaPlugin, private val storage: Storage) : BukkitRunnable() {
    override fun run() {
        for (location in storage.allLocations) {
            val world = location.world ?: continue
            val chest = world.getBlockAt(location).state as? Chest
            if (chest != null) {
                // Bukkit inventory access and serialization must stay on the server thread.
                storage.updateStorefront(location, inventoryToJsonString(chest.inventory))
            } else {
                storage.removeStorefront(location)
                plugin.logger.info("Removed storefront since the block was no longer found")
            }
        }
    }
}
