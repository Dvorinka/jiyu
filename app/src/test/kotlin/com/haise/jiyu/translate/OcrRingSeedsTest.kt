package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRingSeedsTest {

    @Test
    fun `produces four seed points around the block with margin`() {
        // left=20, top=6, right=80, bottom=30 (leftF*w atd.), midX=50, midY=18
        val seeds = ringSeeds(leftF = 0.2f, topF = 0.1f, rightF = 0.8f, bottomF = 0.5f, w = 100, h = 60, margin = 4)

        assertEquals(4, seeds.size)
        // Top mid: x = (20+80)/2 = 50, y = 6 - 4 = 2
        assertTrue(seeds.contains(50 to 2))
        // Bottom mid: x = 50, y = 30 + 4 = 34
        assertTrue(seeds.contains(50 to 34))
        // Left mid: x = 20 - 4 = 16, y = (6+30)/2 = 18
        assertTrue(seeds.contains(16 to 18))
        // Right mid: x = 80 + 4 = 84, y = 18
        assertTrue(seeds.contains(84 to 18))
    }

    @Test
    fun `clamps seeds to canvas bounds near edges`() {
        val seeds = ringSeeds(leftF = 0f, topF = 0f, rightF = 0.1f, bottomF = 0.1f, w = 100, h = 60, margin = 4)

        seeds.forEach { (x, y) ->
            assertTrue("x=$x out of bounds", x in 0..99)
            assertTrue("y=$y out of bounds", y in 0..59)
        }
    }
}
