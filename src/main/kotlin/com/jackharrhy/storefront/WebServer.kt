package com.jackharrhy.storefront

import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import org.bukkit.block.Chest
import org.bukkit.inventory.meta.MapMeta
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal fun createWebApp(storage: Storage, mapContents: (Int, Int) -> CompletableFuture<String>): Javalin {
    val gson = Gson()
    return Javalin.create { config ->
        config.routes.before { ctx -> ctx.contentType("application/json") }
        config.routes.get("/storefronts/") { ctx ->
            ctx.result(gson.toJson(storage.allContents))
        }
        config.routes.get("/storefronts/{id}") { ctx ->
            val id = ctx.pathParam("id").toIntOrNull() ?: throw BadRequestResponse("Invalid storefront ID")
            val contents = storage.storefrontContentsById(id) ?: throw NotFoundResponse("Storefront not found")
            ctx.result(gson.toJson(contents))
        }
        config.routes.get("/storefronts/{id}/item/{position}/map") { ctx ->
            val id = ctx.pathParam("id").toIntOrNull() ?: throw BadRequestResponse("Invalid storefront ID")
            val position = ctx.pathParam("position").toIntOrNull() ?: throw BadRequestResponse("Invalid item position")
            if (position < 0) throw BadRequestResponse("Invalid item position")
            ctx.future { mapContents(id, position).orTimeout(5, TimeUnit.SECONDS).thenAccept { ctx.result(it) } }
        }
    }
}

class WebServer(private val plugin: Storefront, private val storage: Storage) {
    private val app: Javalin

    init {
        val thread = Thread.currentThread()
        val classLoader = thread.contextClassLoader
        try {
            thread.contextClassLoader = Storefront::class.java.classLoader
            app = createWebApp(storage, ::mapContents)
                .start(plugin.config.getString("web.host", "127.0.0.1")!!, plugin.config.getInt("web.port", 7000))
        } finally {
            thread.contextClassLoader = classLoader
        }
    }

    private fun mapContents(id: Int, position: Int): CompletableFuture<String> {
        val location = storage.storefrontLocation(id) ?: throw NotFoundResponse("Storefront not found")
        val result = CompletableFuture<String>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            try {
                val chest = location.world?.getBlockAt(location)?.state as? Chest
                    ?: throw NotFoundResponse("Chest not found")
                if (position !in 0 until chest.inventory.size) throw BadRequestResponse("Invalid item position")
                val meta = chest.inventory.getItem(position)?.itemMeta as? MapMeta
                val map = meta?.mapView ?: throw NotFoundResponse("Map not found")
                result.complete(Gson().toJson(mapOf(
                    "world" to map.world?.name,
                    "centerX" to map.centerX,
                    "centerZ" to map.centerZ,
                    "scale" to mapOf("name" to map.scale.name, "ordinal" to map.scale.ordinal)
                )))
            } catch (exception: Exception) {
                result.completeExceptionally(exception)
            }
        })
        return result
    }

    fun stop() = app.stop()
}
