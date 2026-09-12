package com.jackharrhy.storefront

import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer
import org.bukkit.configuration.serialization.ConfigurationSerializable
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.block.Chest
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

internal val inventoryGson = GsonBuilder().registerTypeHierarchyAdapter(
    ConfigurationSerializable::class.java,
    JsonSerializer<ConfigurationSerializable> { value, _, context -> context.serialize(value.serialize()) }
).create()

fun inventoryToJsonString(inventory: Inventory): String {
    check(Bukkit.isPrimaryThread()) { "Inventory snapshots must be captured on the server thread" }
    val items = inventory.map { stack ->
        if (stack == null || stack.type.isAir) null else mapOf(
            "name" to PlainTextComponentSerializer.plainText().serialize(stack.effectiveName()),
            "key" to stack.type.key.toString(),
            "amount" to stack.amount,
            "meta" to inventoryGson.toJsonTree(stack.itemMeta?.serialize()),
            "isBlock" to stack.type.isBlock,
            "maxDurability" to stack.type.maxDurability
        )
    }
    return inventoryGson.toJson(items)
}

internal fun <T> captureBatch(iterator: Iterator<T>, limit: Int, budgetNanos: Long, clock: () -> Long = System::nanoTime, capture: (T) -> Unit): Int {
    val started = clock()
    var count = 0
    while (iterator.hasNext() && count < limit) {
        capture(iterator.next())
        count++
        if (clock() - started >= budgetNanos) break
    }
    return count
}

data class RefreshStatus(
    val running: Boolean,
    val completed: Int,
    val targets: Int = 0,
    val changed: Int = 0,
    val skippedUnloaded: Int = 0,
    val maxCaptureMs: Double = 0.0,
    val elapsedMs: Double = 0.0,
    val error: String? = null
)

class UpdateStorefronts(private val plugin: JavaPlugin, private val storage: Storage) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "Storefront-database").apply { isDaemon = true } }
    private var captureTask: BukkitTask? = null
    private var closed = false
    var status = RefreshStatus(false, 0)
        private set

    fun request(): Boolean {
        check(Bukkit.isPrimaryThread())
        if (closed || status.running) return false
        status = RefreshStatus(running = true, completed = status.completed)
        val started = System.nanoTime()
        CompletableFuture.supplyAsync({ storage.refreshTargets() }, worker).whenComplete { targets, error ->
            onMain {
                if (error != null) { finish(started, error); return@onMain }
                capture(targets, started)
            }
        }
        return true
    }

    private fun capture(targets: List<RefreshTarget>, started: Long) {
        status = status.copy(targets = targets.size)
        val iterator = targets.iterator()
        val limit = plugin.config.getInt("refresh.chests-per-tick", 8).coerceIn(1, 100)
        val budget = (plugin.config.getDouble("refresh.budget-ms", 2.0).coerceIn(0.1, 20.0) * 1_000_000).toLong()
        var skipped = 0
        var maxCapture = 0L
        val writes = mutableListOf<CompletableFuture<Int>>()
        captureTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            try {
                val tickStart = System.nanoTime()
                val changes = mutableListOf<RefreshChange>()
                captureBatch(iterator, limit, budget) { target ->
                    val location = deserializeLocation(target.location)
                    val world = location.world
                    if (world == null || !world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
                        skipped++
                    } else {
                        val chest = world.getBlockAt(location).state as? Chest
                        val contents = chest?.let { inventoryToJsonString(it.inventory) }
                        if (contents != target.contents) changes.add(RefreshChange(target, contents))
                    }
                }
                maxCapture = maxOf(maxCapture, System.nanoTime() - tickStart)
                if (changes.isNotEmpty()) writes.add(CompletableFuture.supplyAsync({ storage.applyRefresh(changes) }, worker))
                if (!iterator.hasNext()) {
                    captureTask?.cancel()
                    captureTask = null
                    CompletableFuture.allOf(*writes.toTypedArray()).whenComplete { _, error ->
                        onMain {
                            status = status.copy(targets = targets.size, skippedUnloaded = skipped, maxCaptureMs = maxCapture / 1_000_000.0,
                                changed = if (error == null) writes.sumOf { it.join() } else 0)
                            finish(started, error)
                        }
                    }
                }
            } catch (error: Exception) {
                captureTask?.cancel()
                captureTask = null
                // Drain submitted batches before permitting another sweep.
                CompletableFuture.allOf(*writes.toTypedArray()).whenComplete { _, _ -> onMain { finish(started, error) } }
            }
        }, 1L, 1L)
    }

    private fun finish(started: Long, error: Throwable?) {
        status = status.copy(running = false, completed = status.completed + 1, elapsedMs = (System.nanoTime() - started) / 1_000_000.0, error = error?.message)
        if (error != null) plugin.logger.log(Level.SEVERE, "Storefront refresh failed", error)
    }

    private fun onMain(action: () -> Unit) {
        if (!plugin.isEnabled) return
        try {
            plugin.server.scheduler.runTask(plugin, Runnable { if (!closed) action() })
        } catch (error: IllegalPluginAccessException) {
            // The plugin can be disabled between the check and the scheduling call.
            if (plugin.isEnabled) throw error
        }
    }

    override fun close() {
        closed = true
        captureTask?.cancel()
        worker.shutdown()
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow()
        } catch (_: InterruptedException) {
            worker.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
