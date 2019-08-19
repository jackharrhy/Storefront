package com.jackharrhy.storefront;

import io.javalin.Javalin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;

public final class Storefront extends JavaPlugin {
    private Storage storage;
    public String pluginFolder = getDataFolder().getAbsolutePath();

    @Override
    public void onEnable() {
        getLogger().info(getDescription().getName() + " has been enabled");

        this.storage = new Storage(getLogger(),  pluginFolder + File.separator + "storefront.db");

        new SignListener(this, storage);

        startWebServer();
    }

    private static void addSoftwareLibrary(File file) throws Exception {
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
    }

    public void startWebServer() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try {
            addSoftwareLibrary(
                    new File(pluginFolder + File.separator + "lib" + File.separator + "websocket-server-9.4.20.v20190813.jar"
                    ));
        } catch (Exception e) {
            e.printStackTrace();
        }

        URL[] urls = ((URLClassLoader)cl).getURLs();

        for(URL url: urls){
            System.out.println(url.getFile());
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Storefront.class.getClassLoader());
        Javalin app = Javalin.create().start(7000);
        app.get("/", ctx -> ctx.result("Hello World"));
        Thread.currentThread().setContextClassLoader(classLoader);
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

        UUID playerUUID = player.getUniqueId();

        String playerFolderPath = pluginFolder + File.separator + playerUUID;

        File folder = new File(playerFolderPath);
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().log(Level.SEVERE, "Wasn't able to make the following folder: " + playerFolderPath);
            return;
        }

        Location chestLocation = chest.getLocation();

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack stack : chestInventory) {
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

        Gson gson = new GsonBuilder().create();
        String contents = gson.toJson(items);

        if (storage.updateStorefront(player, chestLocation, contents)) {
            player.sendMessage(ChatColor.AQUA + "Storefront updated");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update storefront");
        }
    }
}
