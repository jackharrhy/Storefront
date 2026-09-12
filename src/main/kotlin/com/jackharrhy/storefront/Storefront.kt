package com.jackharrhy.storefront

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

fun signDescription(sign: Sign): Array<String> = sign.getSide(Side.FRONT).lines()
    .map { PlainTextComponentSerializer.plainText().serialize(it) }.toTypedArray()

class Storefront : JavaPlugin() {
    private lateinit var storage: Storage
    private var app: WebServer? = null
    private var refresher: UpdateStorefronts? = null

    override fun onEnable() {
        saveDefaultConfig()
        check(dataFolder.isDirectory || dataFolder.mkdirs()) { "Could not create $dataFolder" }
        storage = Storage(logger, dataFolder.resolve("storefront.db").absolutePath)
        app = WebServer(this, storage)
        SignListener(this, storage)
        val updater = UpdateStorefronts(this, storage)
        refresher = updater
        server.scheduler.runTaskTimer(this, Runnable { updater.request() }, 2400L, 2400L)
        getCommand("storefrontcatalog")!!.setExecutor(this)
        getCommand("storefrontrefreshstatus")!!.setExecutor(this)
        getCommand("storefrontforceupdate")!!.setExecutor(this)
    }

    override fun onDisable() {
        refresher?.close()
        app?.stop()
        app = null
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("storefront.admin")) return true
        if (cmd.name == "storefrontcatalog") {
            fixtureCatalog(this, sender, args)
            return true
        }
        val updater = refresher ?: return true
        if (cmd.name == "storefrontrefreshstatus") {
            sender.sendMessage(com.google.gson.Gson().toJson(updater.status))
        } else {
            val accepted = updater.request()
            sender.sendMessage(Component.text(if (accepted) "Storefront refresh queued" else "Storefront refresh already running", NamedTextColor.GREEN))
        }
        return true
    }

    fun removeStorefront(player: Player, chest: Chest): Boolean {
        val owner = storage.ownerUUID(chest.location).orElse(null) ?: return true
        if (owner != player.uniqueId.toString()) {
            player.sendMessage(Component.text("This isn't your storefront!", NamedTextColor.RED))
            return false
        }
        if (storage.removeStorefront(player, chest.location) != true) {
            player.sendMessage(Component.text("Failed to remove storefront", NamedTextColor.RED))
            return false
        }
        player.playSound(chest.location, Sound.ENTITY_PIG_DEATH, 2f, 0.5f)
        player.sendMessage(Component.text("Storefront removed", NamedTextColor.YELLOW))
        return true
    }

    fun newStorefront(player: Player, chest: Chest, sign: Sign): Boolean {
        if (storage.storefrontExists(chest.location)) return updateStorefront(player, chest, sign)
        val created = storage.newStorefront(player, chest.location, inventoryToJsonString(chest.inventory), signDescription(sign)) == true
        player.sendMessage(Component.text(
            if (created) "Storefront created" else "Failed to create storefront",
            if (created) NamedTextColor.BLUE else NamedTextColor.RED
        ))
        if (created) player.playSound(chest.location, Sound.ENTITY_PIG_AMBIENT, 1f, 1f)
        return created
    }

    fun updateStorefront(player: Player, chest: Chest, sign: Sign): Boolean {
        if (storage.ownerUUID(chest.location).orElse(null) != player.uniqueId.toString()) {
            player.sendMessage(Component.text("This isn't your storefront!", NamedTextColor.RED))
            return false
        }
        val updated = storage.updateStorefront(chest.location, inventoryToJsonString(chest.inventory), signDescription(sign)) == true
        player.sendMessage(Component.text(
            if (updated) "Storefront updated" else "Failed to update storefront",
            if (updated) NamedTextColor.AQUA else NamedTextColor.RED
        ))
        if (updated) player.playSound(chest.location, Sound.ENTITY_PIG_AMBIENT, 1f, 1f)
        return updated
    }
}
