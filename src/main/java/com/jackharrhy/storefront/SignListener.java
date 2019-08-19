package com.jackharrhy.storefront;

import org.bukkit.ChatColor;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {
    private Storefront plugin;
    private Storage storage;

    public SignListener(Storefront plugin, Storage storage) {
        this.storage = storage;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean isWallSign(Sign sign) {
        BlockData blockData = sign.getBlockData();
        return blockData instanceof WallSign;
    }

    private Block getBlockBehindWallSign(Sign sign) {
        BlockFace signInverseFace = ((Directional) sign.getBlockData()).getFacing().getOppositeFace();
        Block blockBehindSign = sign.getBlock().getRelative(signInverseFace);
        return blockBehindSign;
    }

    private Chest getChestFromSign(Sign sign) {
        if (!isWallSign(sign)) {
            return null;
        }

        BlockState blockBehindSignState = getBlockBehindWallSign(sign).getState();
        if (!(blockBehindSignState instanceof Chest)) {
            return null;
        }

        return (Chest) blockBehindSignState;
    }

    private void handleNewStorefront(SignChangeEvent event) {
        Block block = event.getBlock();
        BlockState blockState = block.getState();

        if (!(blockState instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) blockState;
        Chest chest = getChestFromSign(sign);

        if (chest != null) {
            this.plugin.updateStorefront(event.getPlayer(), chest, sign);
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (event.getLine(0).toLowerCase().equals("[storefront]")) {
            handleNewStorefront(event);
        }
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        Sign sign = getStorefrontSign(event.getBlock());
        if (sign == null) {
            return;
        }

        Chest chest = getChestFromSign(sign);
        if (chest == null) {
            return;
        }

        if (storage.owner(chest.getLocation()).equals(player.getUniqueId().toString())) {
            this.plugin.removeStorefront(player, chest.getLocation());
        } else {
            player.sendMessage(ChatColor.RED + "This isn't your storefront!");
            event.setCancelled(true);
        }
    }

    private Sign getStorefrontSign(Block block) {
        BlockState blockState = block.getState();
        if (!(blockState instanceof Sign)) {
            return null;
        }

        Sign sign = (Sign) blockState;
        if(!sign.getLine(0).equalsIgnoreCase("[storefront]")) {
            return null;
        }

        return sign;
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Sign sign = getStorefrontSign(event.getClickedBlock());
        if (sign == null) {
            return;
        }

        Chest chest = getChestFromSign(sign);

        if (chest != null) {
            this.plugin.updateStorefront(player, chest, sign);
        }
    }
}
