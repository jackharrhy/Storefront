package com.jackharrhy.storefront

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
