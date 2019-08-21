package com.jackharrhy.storefront

import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.block.Sign
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.block.Chest

import java.io.File
import java.util.logging.Level

class Storefront : JavaPlugin() {
    private var storage: Storage? = null
    private var pluginFolder = dataFolder.absolutePath

    override fun onEnable() {
        logger.info(description.name + " has been enabled")

        val folder = File(pluginFolder)
        if (!folder.exists() && !folder.mkdirs()) {
            logger.log(Level.SEVERE, "Wasn't able to make the following folder: $pluginFolder")
            return
        }

        this.storage = Storage(logger, pluginFolder + File.separator + "storefront.db")

        SignListener(this, storage!!)
        WebServer(this, storage!!)
        UpdateStorefronts(this, storage!!).runTaskTimer(this, 20, 20)
    }

    override fun onDisable() {
        logger.info(description.name + " has been enabled")
    }

    fun removeStorefront(player: Player, chestLocation: Location) {
        if (storage!!.removeStorefront(player, chestLocation)!!) {
            player.sendMessage(ChatColor.AQUA.toString() + "Storefront removed")
        } else {
            player.sendMessage(ChatColor.RED.toString() + "Failed to remove storefront")
        }
    }

    fun updateStorefront(player: Player, chest: Chest, sign: Sign) {
        val chestInventory = chest.inventory
        val chestLocation = chest.location
        val contents = inventoryToJsonString(chestInventory)

        if (storage!!.updateStorefront(player, chestLocation, contents, sign.lines)!!) {
            player.sendMessage(ChatColor.AQUA.toString() + "Storefront updated")
        } else {
            player.sendMessage(ChatColor.RED.toString() + "Failed to update storefront")
        }
    }
}
