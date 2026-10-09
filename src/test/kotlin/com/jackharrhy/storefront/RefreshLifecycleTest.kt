package com.jackharrhy.storefront

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class RefreshLifecycleTest {
    @TempDir lateinit var directory: Path

    private class Queue : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(task: Runnable) { check(!stopped); tasks.add(task) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun shutdown() { stopped = true; drain() }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return tasks.toMutableList().also { tasks.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private class Sweep(directory: Path, snapshot: (String) -> ChestSnapshot) : AutoCloseable {
        val file = directory.resolve("refresh.db").toString()
        val storage = Storage(file)
        val main = Queue()
        val worker = Queue()
        val errors = mutableListOf<Throwable>()
        val refresh = Refresh(storage, Executor(main::execute), snapshot, { errors.add(it) }, worker)
        fun add(location: String) { storage.newStorefront(Owner("alice", "Alice"), location, "[]", arrayOf("Shop")) }
        fun load() {
            assertTrue(refresh.request(2, Long.MAX_VALUE))
            worker.drain()
            main.drain()
        }
        fun finish() { worker.drain(); main.drain() }
        fun dropTable() = DriverManager.getConnection("jdbc:sqlite:$file").use {
            it.createStatement().use { statement -> statement.execute("DROP TABLE chest") }
        }
        override fun close() { refresh.close() }
    }

    @Test
    fun `sweep budgets captures and counts only committed changes while retaining unloaded listings`() {
        val captured = mutableListOf<String>()
        Sweep(directory) { location ->
            captured.add(location)
            when (location) {
                "unloaded" -> ChestSnapshot.Unloaded
                "missing" -> ChestSnapshot.Loaded(null)
                "changed" -> ChestSnapshot.Loaded("[null]")
                else -> ChestSnapshot.Loaded("[]")
            }
        }.use { s ->
            listOf("changed", "unchanged", "unloaded", "missing").forEach(s::add)
            s.load()
            assertFalse(s.refresh.request(2, 1))
            s.refresh.tick()
            assertEquals(listOf("changed", "unchanged"), captured)
            // A newer player edit must survive the already captured batch.
            assertTrue(s.storage.updateStorefront("alice", "changed", "[null,null]", arrayOf("Edited")))
            s.refresh.tick()
            assertTrue(s.refresh.status.running)
            assertEquals(0, s.refresh.status.completed)
            s.finish()
            assertFalse(s.refresh.status.running)
            assertEquals(1, s.refresh.status.completed)
            assertEquals(4, s.refresh.status.targets)
            assertEquals(1, s.refresh.status.changed)
            assertEquals(1, s.refresh.status.skippedUnloaded)
            assertEquals("[null,null]", s.storage.refreshTargets().first().contents)
            assertNotNull(s.storage.ownerUUID("unloaded"))
            assertNull(s.storage.ownerUUID("missing"))
            assertTrue(s.errors.isEmpty())
        }
    }

    @Test
    fun `capture failure drains prior writes before another sweep may begin`() {
        var fail = true
        Sweep(directory) { location ->
            if (location == "third" && fail) throw IllegalStateException("capture failed")
            ChestSnapshot.Loaded("[null]")
        }.use { s ->
            listOf("first", "second", "third").forEach(s::add)
            s.load()
            s.refresh.tick()
            s.refresh.tick()
            assertFalse(s.refresh.request(2, 1))
            assertTrue(s.refresh.status.running)
            s.finish()
            assertEquals("capture failed", s.refresh.status.error)
            assertEquals(1, s.errors.size)
            assertEquals(listOf("[null]", "[null]", "[]"), s.storage.refreshTargets().map { it.contents })
            fail = false
            s.load()
            s.refresh.tick()
            s.refresh.tick()
            s.finish()
            assertNull(s.refresh.status.error)
            assertEquals(2, s.refresh.status.completed)
            assertEquals(1, s.refresh.status.changed)
        }
    }

    @Test
    fun `database read and write failures complete with an error instead of wedging refresh`() {
        Sweep(directory) { ChestSnapshot.Loaded("[null]") }.use { s ->
            s.add("first")
            s.load()
            s.refresh.tick()
            s.dropTable()
            s.finish()
            assertFalse(s.refresh.status.running)
            assertNotNull(s.refresh.status.error)
            assertEquals(0, s.refresh.status.changed)
            assertTrue(s.refresh.request(2, 1))
            s.finish()
            assertFalse(s.refresh.status.running)
            assertEquals(2, s.refresh.status.completed)
            assertEquals(2, s.errors.size)
        }
    }

    @Test
    fun `shutdown ignores queued main thread callbacks and rejects future requests`() {
        var captures = 0
        val s = Sweep(directory) { captures++; ChestSnapshot.Loaded(null) }
        s.add("first")
        assertTrue(s.refresh.request(2, 1))
        s.worker.drain()
        s.close()
        s.main.drain()
        s.refresh.tick()
        assertFalse(s.refresh.request(2, 1))
        assertEquals(0, captures)
        assertNotNull(s.storage.ownerUUID("first"))
    }

    @Test
    fun `empty sweep completes once without writing`() {
        Sweep(directory) { error("No targets") }.use { s ->
            s.load()
            s.refresh.tick()
            s.finish()
            repeat(3) { s.refresh.tick() }
            assertEquals(1, s.refresh.status.completed)
            assertEquals(0, s.refresh.status.changed)
            assertTrue(s.errors.isEmpty())
        }
    }
}
