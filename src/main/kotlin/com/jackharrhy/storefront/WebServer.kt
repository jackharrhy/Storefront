package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import io.javalin.Javalin


class WebServer(plugin: Storefront, storage: Storage) {
    private val storage : Storage = storage

    init {
        val classLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = Storefront::class.java.classLoader
        val app = Javalin.create().start(7000)
        Thread.currentThread().contextClassLoader = classLoader

        app.get("/") { ctx ->
            ctx.res.contentType = "application/json"
            ctx.result(getAllContents())
        }

        plugin.logger.info("WebServer now running")
    }

    private fun getAllContents() : String {
        val allContents = storage.allContents
        return GsonBuilder().create().toJson(allContents)
    }
}
