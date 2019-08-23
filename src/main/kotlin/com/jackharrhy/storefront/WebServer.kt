package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import io.javalin.Javalin
import org.bukkit.Bukkit
import org.bukkit.Material

class WebServer(plugin: Storefront, storage: Storage) {
    private var scheduler = Bukkit.getScheduler()
    init {
        val classLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = Storefront::class.java.classLoader
        val app = Javalin.create().start(7000)
        Thread.currentThread().contextClassLoader = classLoader

        scheduler.schedule(plugin, SynchronizationContext.ASYNC) {
            app.get("/") { ctx ->
                ctx.res.contentType = "application/json"
                val allContents = storage.allContents
                ctx.result(GsonBuilder().create().toJson(allContents))
            }

            app.get("/all-materials") { ctx ->
                ctx.res.contentType = "application/json"
                val allMats: HashMap<String, JsonElement> = HashMap()
                for (mat in Material.values()) {
                    allMats[mat.key.key] = GsonBuilder().create().toJsonTree(mat.getData())
                }
                ctx.result(GsonBuilder().create().toJson(allMats))
            }
        }
        plugin.logger.info("WebServer now running")
    }
}
