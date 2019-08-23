package com.jackharrhy.storefront

import org.bukkit.ChatColor
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
        UpdateStorefronts(this, storage!!).runTaskTimer(this, 2400L, 2400L)
    }

    override fun onDisable() {
        logger.info(description.name + " has been enabled")
    }

    fun removeStorefront(player: Player, chest: Chest) : Boolean {
        val chestLocation = chest.location
        val playerUuid = player.uniqueId.toString()

        val playerUuidFromDb = storage!!.ownerUUID(chestLocation).get().removeSurrounding("\"")
        if (playerUuidFromDb == playerUuid) {
            if (storage!!.removeStorefront(player, chest.location)!!) {
                player.sendMessage(ChatColor.YELLOW.toString() + "Storefront removed")
                return true
            }
            player.sendMessage(ChatColor.RED.toString() + "Failed to remove storefront")
            return false
        }
        player.sendMessage(ChatColor.RED.toString() + "This isn't your storefront!")
        return false
    }

    fun newStorefront(player: Player, chest: Chest, sign: Sign) : Boolean {
        if (storage!!.newStorefront(player, chest.location, inventoryToJsonString(chest.inventory), sign.lines)!!) {
            player.sendMessage(ChatColor.BLUE.toString() + "Storefront created")
            return true
        }
        player.sendMessage(ChatColor.RED.toString() + "Failed to create storefront")
        return false
    }

    fun updateStorefront(player: Player, chest: Chest, sign: Sign) : Boolean {
        if (storage!!.updateStorefront(chest.location, inventoryToJsonString(chest.inventory), sign.lines)!!) {
            player.sendMessage(ChatColor.AQUA.toString() + "Storefront updated")
            return true
        }
        player.sendMessage(ChatColor.RED.toString() + "Failed to update storefront")
        return false
    }
}
