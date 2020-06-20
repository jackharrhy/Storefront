package com.jackharrhy.storefront

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.block.Sign
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.block.Chest

import java.io.File
import java.util.logging.Level
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

class Storefront : JavaPlugin(), CommandExecutor {
	var storage: Storage? = null
	var pluginFolder = dataFolder.absolutePath

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

		getCommand("storefrontforceupdate")?.setExecutor(this)
	}

	override fun onDisable() {
		logger.info(description.name + " has been disabled")
	}


	override fun onCommand(sender: CommandSender, cmd: Command, lbl: String, args: Array<out String>): Boolean {
		val isOp = sender is Player && sender.isOp()
		val isConsole = sender is ConsoleCommandSender

		if (isOp || isConsole) {
			UpdateStorefronts(this, storage!!).runTask(this)
			sender.sendMessage(ChatColor.GREEN.toString() + "Updated storefront")
		} else {
			sender.sendMessage(ChatColor.RED.toString() + "Operators / console command only!")
		}

		return true
	}

	fun removeStorefront(player: Player, chest: Chest) : Boolean {
		player.playSound(chest.location, Sound.ENTITY_PIG_DEATH, 2f, 0.5f)

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
		player.playSound(chest.location, Sound.ENTITY_PIG_AMBIENT, 1f, 1f)

		if (storage!!.newStorefront(player, chest.location, inventoryToJsonString(chest.inventory), sign.lines)!!) {
			player.sendMessage(ChatColor.BLUE.toString() + "Storefront created")
			return true
		}
		player.sendMessage(ChatColor.RED.toString() + "Failed to create storefront")
		return false
	}

	fun updateStorefront(player: Player, chest: Chest, sign: Sign) : Boolean {
		player.playSound(chest.location, Sound.ENTITY_PIG_AMBIENT, 1f, 1f)

		if (storage!!.updateStorefront(chest.location, inventoryToJsonString(chest.inventory), sign.lines)!!) {
			player.sendMessage(ChatColor.AQUA.toString() + "Storefront updated")
			return true
		}
		player.sendMessage(ChatColor.RED.toString() + "Failed to update storefront")
		return false
	}
}
