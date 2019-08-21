package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import org.bukkit.block.Chest
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

import java.util.ArrayList
import java.util.HashMap

fun inventoryToJsonString(inventory: Inventory): String {
    val items = ArrayList<Map<String, Any>?>()
    for (stack in inventory) {
        if (stack != null) {
            val newStack = HashMap<String, Any>()

            newStack["name"] = stack.i18NDisplayName.toString()
            newStack["key"] = stack.type.key.toString()
            newStack["amount"] = stack.amount

            val gson = GsonBuilder().create()
            newStack["meta"] = gson.toJsonTree(stack.itemMeta.serialize())

            items.add(newStack)
        } else {
            items.add(null)
        }
    }
    return GsonBuilder().create().toJson(items)
}

class UpdateStorefronts(private val plugin: JavaPlugin, private val storage: Storage) : BukkitRunnable() {
    override fun run() {
        for (loc in storage.allLocations) {
            val blockStateFromDb = loc.world.getBlockAt(loc).state

            if (blockStateFromDb is Chest) {
                storage.updateStorefront(loc, inventoryToJsonString(blockStateFromDb.inventory))
            } else {
                storage.removeStorefront(loc)
                plugin.logger.info("Removed storefront since the block was no longer found")
            }
        }
    }
}