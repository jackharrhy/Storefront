package com.jackharrhy.storefront

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class SignListener(private val plugin: Storefront, private val storage: Storage) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun getChestFromSign(sign: Sign): Chest? {
        val wallSign = sign.blockData as? WallSign ?: return null
        return sign.block.getRelative(wallSign.facing.oppositeFace).state as? Chest
    }

    private fun getStorefrontSign(block: Block): Sign? {
        val sign = block.state as? Sign ?: return null
        val firstLine = PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(0))
        return sign.takeIf { firstLine.equals("[storefront]", ignoreCase = true) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onSignChange(event: SignChangeEvent) {
        if (event.side != Side.FRONT) return
        val firstLine = event.line(0)?.let { PlainTextComponentSerializer.plainText().serialize(it) }
        if (!firstLine.equals("[storefront]", ignoreCase = true)) return
        val sign = event.block.state as? Sign ?: return
        val chest = getChestFromSign(sign) ?: return
        val playerId = event.player.uniqueId.toString()
        if (storage.ownerUUID(chest.location).orElse(playerId) != playerId) {
            event.isCancelled = true
            return
        }
        // SignChangeEvent fires before the edited text is applied to the block.
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (event.isCancelled) return@Runnable
            val currentSign = getStorefrontSign(event.block) ?: return@Runnable
            val currentChest = getChestFromSign(currentSign) ?: return@Runnable
            plugin.newStorefront(event.player, currentChest, currentSign)
        })
    }

    @EventHandler(ignoreCancelled = true)
    fun onSignBreak(event: BlockBreakEvent) {
        val sign = getStorefrontSign(event.block) ?: return
        val chest = getChestFromSign(sign) ?: return
        event.isCancelled = !plugin.removeStorefront(event.player, chest)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSignInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val sign = getStorefrontSign(event.clickedBlock ?: return) ?: return
        val chest = getChestFromSign(sign) ?: return
        plugin.newStorefront(event.player, chest, sign)
    }
}
