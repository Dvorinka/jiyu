package com.haise.jiyu.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Referenční hodnoty (`w=100,h=80,grid=4,seed=12345`) spočítané nezávislou Python
 * reimplementací téhož algoritmu, ověřenou pixel-perfect proti reálné zamíchané
 * stránce z mangadenizi.net - viz komentář u [TileScramble].
 */
class TileScrambleTest {

    @Test
    fun `computeTileCopies produces exact reference layout for known seed`() {
        val copies = TileScramble.computeTileCopies(width = 100, height = 80, grid = 4, seed = 12345L)
        assertEquals(16, copies.size)

        assertEquals(TileScramble.TileCopy(0, 0, 25, 20, 0, 20, 25, 20), copies[0])
        assertEquals(TileScramble.TileCopy(25, 0, 25, 20, 75, 20, 25, 20), copies[1])
        assertEquals(TileScramble.TileCopy(50, 0, 25, 20, 25, 20, 25, 20), copies[2])
        assertEquals(TileScramble.TileCopy(75, 0, 25, 20, 50, 20, 25, 20), copies[3])
    }

    @Test
    fun `same seed and dimensions always produce the same layout (deterministic)`() {
        val a = TileScramble.computeTileCopies(1100, 1463, 10, 3849681284L)
        val b = TileScramble.computeTileCopies(1100, 1463, 10, 3849681284L)
        assertEquals(a, b)
        assertEquals(100, a.size)
    }

    @Test
    fun `different seeds produce different layouts`() {
        val a = TileScramble.computeTileCopies(200, 200, 5, 111L)
        val b = TileScramble.computeTileCopies(200, 200, 5, 222L)
        assertTrue(a != b)
    }

    @Test
    fun `destination tiles exactly tile the whole image with no gaps or overlaps`() {
        val width = 137
        val height = 211
        val grid = 6
        val copies = TileScramble.computeTileCopies(width, height, grid, seed = 987654321L)

        val covered = Array(height) { BooleanArray(width) }
        for (c in copies) {
            for (y in c.dstY until c.dstY + c.dstH) {
                for (x in c.dstX until c.dstX + c.dstW) {
                    assertTrue("pixel ($x,$y) covered twice", !covered[y][x])
                    covered[y][x] = true
                }
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertTrue("pixel ($x,$y) never covered", covered[y][x])
            }
        }
    }

    @Test
    fun `grid larger than image dimensions is clamped, still produces valid full coverage`() {
        val copies = TileScramble.computeTileCopies(width = 5, height = 3, grid = 50, seed = 42L)
        assertEquals(9, copies.size) // clamped to min(width,height) = 3 -> 3x3
        val totalDstArea = copies.sumOf { it.dstW.toLong() * it.dstH }
        assertEquals(15L, totalDstArea) // 5*3
    }
}
