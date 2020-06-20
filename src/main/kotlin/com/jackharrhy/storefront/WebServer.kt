package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.javalin.Javalin
import org.bukkit.Location
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import java.util.concurrent.CompletableFuture


class WebServer(plugin: Storefront, storage: Storage) {
	private val storage : Storage = storage

	init {
		val classLoader = Thread.currentThread().contextClassLoader
		Thread.currentThread().contextClassLoader = Storefront::class.java.classLoader
		val app = Javalin.create().start(7000)
		Thread.currentThread().contextClassLoader = classLoader

		app.before { ctx ->
			ctx.res.contentType = "application/json"
		}

		app.get("/storefronts/") { ctx ->
			ctx.result(getAllContents())
		}

		app.get("/storefronts/:id") { ctx ->
			val storefrontId = ctx.pathParam<Int>("id").get()
			val contents = storage.storefrontContentsById(storefrontId)

			ctx.result(GsonBuilder().create().toJson(contents))
		}

		app.get("/storefronts/:id/item/:position/map") { ctx ->
			val storefrontId = ctx.pathParam<Int>("id").get()
			val itemPosition = ctx.pathParam<Int>("position").get()
			val loc = storage.storefrontLocation(storefrontId)

			val completableFuture = CompletableFuture<String>()

			if (loc is Location) {
				plugin.server.scheduler.runTask(plugin) { _ ->
					val blockState = loc.world.getBlockAt(loc).state

					if (blockState is Chest) {
						val blockStateInventory = blockState.inventory
						val item = blockStateInventory.getItem(itemPosition)
						if (item is ItemStack) {
							val meta = item.itemMeta

							if (meta is MapMeta) {
								val mapView = meta.mapView

								if (mapView is MapView) {
									val jsonObject = JsonObject()
									jsonObject.addProperty("world", mapView.world!!.name)
									jsonObject.addProperty("centerX", mapView.centerX)
									jsonObject.addProperty("centerZ", mapView.centerZ)

									val scale = JsonObject()
									scale.addProperty("name", mapView.scale.name)
									scale.addProperty("ordinal", mapView.scale.ordinal)
									jsonObject.add("scale", scale)

									completableFuture.complete(GsonBuilder().create().toJson(jsonObject))
								}
							}
						}
					}
				}
			}

			ctx.result(completableFuture)
		}

		plugin.logger.info("WebServer now running")
	}

	private fun getAllContents() : String {
		val allContents = storage.allContents
		return GsonBuilder().create().toJson(allContents)
	}
}
