package com.jackharrhy.storefront;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateStorefronts extends BukkitRunnable {
    private final JavaPlugin plugin;
    private Storage storage;

    static public String inventoryToJsonString(Inventory inventory) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack stack : inventory) {
            if (stack != null) {
                Map<String, Object> newStack = new HashMap<>();

                newStack.put("name", stack.getI18NDisplayName());
                newStack.put("key", stack.getType().getKey().toString());
                newStack.put("amount", stack.getAmount());

                Gson gson = new GsonBuilder().create();
                newStack.put("meta", gson.toJsonTree(stack.getItemMeta().serialize()));

                items.add(newStack);
            } else {
                items.add(null);
            }
        }
        return new GsonBuilder().create().toJson(items);
    }

    public UpdateStorefronts(JavaPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void run() {
        for (Location loc : storage.getAllLocations()) {
            BlockState blockStateFromDb = loc.getWorld().getBlockAt(loc).getState();

            plugin.getLogger().info("ran");

            if (blockStateFromDb instanceof Chest) {
                Chest chest = (Chest) blockStateFromDb;
                storage.updateStorefront(loc, inventoryToJsonString(chest.getInventory()));
            } else {
                storage.removeStorefront(loc);
                plugin.getLogger().info("remove" + loc.toString());
            }
        }
    }
}