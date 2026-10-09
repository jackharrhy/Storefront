package com.jackharrhy.storefront

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Chest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class PaperChestTest {
    private inline fun <reified T> stub(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T

    @Test
    fun `double chest reads require both chunks for each orientation and half`() {
        for ((facing, dx, dz) in listOf(
            Triple(BlockFace.NORTH, 1, 0), Triple(BlockFace.EAST, 0, 1),
            Triple(BlockFace.SOUTH, -1, 0), Triple(BlockFace.WEST, 0, -1)
        )) {
            for (half in listOf(Chest.Type.LEFT, Chest.Type.RIGHT)) {
                val turn = if (half == Chest.Type.LEFT) 1 else -1
                val x = if (dx * turn > 0) 15 else 0
                val z = if (dz * turn > 0) 15 else 0
                var loaded = emptySet<Pair<Int, Int>>()
                val reads = mutableListOf<Pair<Int, Int>>()
                var blockReads = 0
                val data = stub<Chest> { name, _ -> when (name) {
                    "getType" -> half
                    "getFacing" -> facing
                    else -> error(name)
                } }
                val block = stub<Block> { name, _ -> check(name == "getBlockData"); data }
                val world = stub<World> { name, args -> when (name) {
                    "isChunkLoaded" -> (args[0] as Int to args[1] as Int).let { reads.add(it); it in loaded }
                    "getBlockAt" -> { blockReads++; block }
                    else -> error("Unexpected world access: $name")
                } }
                val location = Location(world, x.toDouble(), 64.0, z.toDouble())
                assertFalse(chestChunksLoaded(location))
                assertEquals(0, blockReads)
                loaded = setOf(0 to 0)
                assertFalse(chestChunksLoaded(location))
                val other = ((x + dx * turn) shr 4) to ((z + dz * turn) shr 4)
                assertEquals(other, reads.last())
                loaded = loaded + other
                assertTrue(chestChunksLoaded(location))
            }
        }
    }
}
