package com.jackharrhy.storefront;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;

public final class Storefront extends JavaPlugin {
    private Storage storage;
    public String pluginFolder = getDataFolder().getAbsolutePath();

    @Override
    public void onEnable() {
        getLogger().info(getDescription().getName() + " has been enabled");

        File folder = new File(pluginFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().log(Level.SEVERE, "Wasn't able to make the following folder: " + pluginFolder);
            return;
        }

        this.storage = new Storage(getLogger(),  pluginFolder + File.separator + "storefront.db");

        new SignListener(this, storage);
        new WebServer(this, storage);
        new UpdateStorefronts(this, storage).runTaskTimer(this, 20, 20);
    }

    private static void addSoftwareLibrary(File file) throws Exception {
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
    }

    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " has been enabled");
    }

    public void removeStorefront(Player player, Location chestLocation) {
        if (storage.removeStorefront(player, chestLocation)) {
            player.sendMessage(ChatColor.AQUA + "Storefront removed");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to remove storefront");
        }
    }

    public void updateStorefront(Player player, Chest chest, Sign sign) {
        Inventory chestInventory = chest.getInventory();
        Location chestLocation = chest.getLocation();
        String contents = UpdateStorefronts.inventoryToJsonString(chestInventory);

        if (storage.updateStorefront(player, chestLocation, contents)) {
            player.sendMessage(ChatColor.AQUA + "Storefront updated");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update storefront");
        }
    }
}
