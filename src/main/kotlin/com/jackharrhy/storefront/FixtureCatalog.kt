package com.jackharrhy.storefront

import com.google.gson.Gson
import org.bukkit.Registry
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

fun fixtureCatalog(plugin: JavaPlugin, sender: CommandSender, args: Array<out String>) {
    if (!plugin.config.getBoolean("development.fixtures", false)) {
        sender.sendMessage("Fixture catalog is disabled")
        return
    }
    val items = Registry.ITEM.filter { it.key.toString() != "minecraft:air" }.sortedBy { it.key.toString() }
    val result: Any = when (args.firstOrNull()) {
        "items" -> {
            val page = args.getOrNull(1)?.toIntOrNull()
            if (page == null || page < 0 || page >= (items.size + 19) / 20) {
                sender.sendMessage("Invalid catalog page")
                return
            }
            items.drop(page * 20).take(20).map { mapOf(
                "key" to it.key.toString(), "durability" to it.maxDurability,
                "stackSize" to it.maxStackSize, "enabled" to it.isEnabledByFeature(plugin.server.worlds.first())
            ) }
        }
        "potions" -> Registry.POTION.map { it.key.toString() }.sorted()
        "enchantments" -> Registry.ENCHANTMENT.map { mapOf("key" to it.key.toString(), "maxLevel" to it.maxLevel) }
        "trim-patterns" -> Registry.TRIM_PATTERN.map { it.key.toString() }.sorted()
        "trim-materials" -> Registry.TRIM_MATERIAL.map { it.key.toString() }.sorted()
        "instruments" -> Registry.INSTRUMENT.map { it.key.toString() }.sorted()
        else -> mapOf("minecraft" to plugin.server.minecraftVersion, "items" to items.size, "pages" to (items.size + 19) / 20)
    }
    sender.sendMessage(Gson().toJson(result))
}
