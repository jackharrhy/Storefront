package com.jackharrhy.storefront

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function

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

sealed interface ChestSnapshot {
    data object Unloaded : ChestSnapshot
    // A loaded location with no chest removes the listing; an unloaded one retains it.
    data class Loaded(val contents: String?) : ChestSnapshot
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

/** request, tick and close belong to the server thread; only database work runs on the worker. */
class Refresh @JvmOverloads constructor(
    private val storage: Storage,
    private val main: Executor,
    private val snapshot: Function<String, ChestSnapshot>,
    private val reportError: Consumer<Throwable>,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Storefront-database").apply { isDaemon = true }
    }
) : AutoCloseable {
    private var closed = false
    private var remaining: Iterator<RefreshTarget>? = null
    private val writes = mutableListOf<CompletableFuture<Int>>()
    private var started = 0L
    private var limit = 0
    private var budget = 0L
    var status = RefreshStatus(false, 0)
        private set

    fun request(chestsPerTick: Int, budgetNanos: Long): Boolean {
        if (closed || status.running) return false
        require(chestsPerTick > 0 && budgetNanos > 0)
        limit = chestsPerTick
        budget = budgetNanos
        status = RefreshStatus(true, status.completed)
        started = System.nanoTime()
        writes.clear()
        CompletableFuture.supplyAsync({ storage.refreshTargets() }, worker).whenComplete { targets, error ->
            onMain {
                if (error != null) finish(error) else {
                    status = status.copy(targets = targets.size)
                    remaining = targets.iterator()
                }
            }
        }
        return true
    }

    fun tick() {
        val iterator = remaining ?: return
        try {
            val tickStart = System.nanoTime()
            val changes = mutableListOf<RefreshChange>()
            captureBatch(iterator, limit, budget) { target ->
                when (val captured = snapshot.apply(target.location)) {
                    ChestSnapshot.Unloaded -> status = status.copy(skippedUnloaded = status.skippedUnloaded + 1)
                    is ChestSnapshot.Loaded -> if (captured.contents != target.contents) changes.add(RefreshChange(target, captured.contents))
                }
            }
            status = status.copy(maxCaptureMs = maxOf(status.maxCaptureMs, (System.nanoTime() - tickStart) / 1_000_000.0))
            if (changes.isNotEmpty()) writes.add(CompletableFuture.supplyAsync({ storage.applyRefresh(changes) }, worker))
            if (!iterator.hasNext()) drain(null)
        } catch (error: Exception) {
            drain(error)
        }
    }

    private fun drain(captureError: Throwable?) {
        remaining = null
        // Keep the sweep running until every submitted write finishes, even after capture fails.
        CompletableFuture.allOf(*writes.toTypedArray()).whenComplete { _, writeError ->
            onMain {
                val error = captureError ?: writeError
                if (error == null) status = status.copy(changed = writes.sumOf { it.join() })
                finish(error)
            }
        }
    }

    private fun finish(error: Throwable?) {
        status = status.copy(running = false, completed = status.completed + 1,
            elapsedMs = (System.nanoTime() - started) / 1_000_000.0, error = error?.message)
        if (error != null) reportError.accept(error)
    }

    private fun onMain(action: () -> Unit) = main.execute { if (!closed) action() }

    override fun close() {
        closed = true
        remaining = null
        worker.shutdown()
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow()
        } catch (_: InterruptedException) {
            worker.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
