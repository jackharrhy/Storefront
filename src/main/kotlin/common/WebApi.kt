package com.jackharrhy.storefront

import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun createWebApp(storage: Storage, mapContents: (Int, Int) -> CompletableFuture<String>): Javalin {
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
