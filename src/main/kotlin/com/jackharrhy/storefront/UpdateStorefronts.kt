package com.jackharrhy.storefront

import org.bukkit.Bukkit
import org.bukkit.block.Chest
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class UpdateStorefronts(private val plugin: JavaPlugin, storage: Storage) : AutoCloseable {
    private val refresh = Refresh(storage, { action -> onMain(action) }, { serialized ->
        val location = deserializeLocation(serialized)
        val world = location.world
        if (world == null || !chestChunksLoaded(location)) {
            ChestSnapshot.Unloaded
        } else {
            val chest = world.getBlockAt(location).state as? Chest
            ChestSnapshot.Loaded(chest?.let { inventoryToJsonString(it.inventory) })
        }
    }, { error -> plugin.logger.log(Level.SEVERE, "Storefront refresh failed", error) })
    private val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { refresh.tick() }, 1L, 1L)
    val status get() = refresh.status

    fun request(): Boolean {
        check(Bukkit.isPrimaryThread())
        return refresh.request(plugin.config.getInt("refresh.chests-per-tick", 8).coerceIn(1, 100),
            (plugin.config.getDouble("refresh.budget-ms", 2.0).coerceIn(0.1, 20.0) * 1_000_000).toLong())
    }

    private fun onMain(action: Runnable) {
        if (!plugin.isEnabled) return
        try {
            plugin.server.scheduler.runTask(plugin, action)
        } catch (error: IllegalPluginAccessException) {
            // The plugin can be disabled between the check and the scheduling call.
            if (plugin.isEnabled) throw error
        }
    }

    override fun close() {
        task.cancel()
        refresh.close()
    }
}
