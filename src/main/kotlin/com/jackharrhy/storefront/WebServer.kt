package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import io.javalin.Javalin

class WebServer(plugin: Storefront, storage: Storage) {
    init {
        val classLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = Storefront::class.java.classLoader
        val app = Javalin.create().start(7000)
        Thread.currentThread().contextClassLoader = classLoader

        app.get("/") { ctx ->
            ctx.res.contentType = "application/json"
            val allContents = storage.allContents
            ctx.result(GsonBuilder().create().toJson(allContents))
        }

        plugin.logger.info("WebServer now running")
    }
}
