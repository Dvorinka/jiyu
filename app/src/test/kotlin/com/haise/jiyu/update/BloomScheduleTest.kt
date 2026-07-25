package com.haise.jiyu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomScheduleTest {

    private val layers = 3

    @Test
    fun `at zero progress every layer sits at the closed bud minimum`() {
        for (i in 0 until layers) {
            assertEquals(BloomSchedule.MIN_OPENNESS, BloomSchedule.layerOpenness(0f, i, layers), 0.0001f)
        }
    }

    @Test
    fun `at full progress every layer is fully open`() {
        for (i in 0 until layers) {
            assertEquals(1f, BloomSchedule.layerOpenness(1f, i, layers), 0.0001f)
        }
    }

    @Test
    fun `back layer opens ahead of the front layer`() {
        val back = BloomSchedule.layerOpenness(0.4f, 0, layers)
        val front = BloomSchedule.layerOpenness(0.4f, layers - 1, layers)
        assertTrue("back=$back should lead front=$front", back > front)
    }

    @Test
    fun `openness never decreases as progress grows`() {
        for (i in 0 until layers) {
            var previous = -1f
            var p = 0f
            while (p <= 1f) {
                val value = BloomSchedule.layerOpenness(p, i, layers)
                assertTrue("layer $i went backwards at p=$p", value >= previous - 0.0001f)
                previous = value
                p += 0.02f
            }
        }
    }

    @Test
    fun `progress outside 0-1 is clamped instead of overshooting`() {
        assertEquals(BloomSchedule.MIN_OPENNESS, BloomSchedule.layerOpenness(-5f, 0, layers), 0.0001f)
        assertEquals(1f, BloomSchedule.layerOpenness(9f, 0, layers), 0.0001f)
    }

    @Test
    fun `out of range layer index is clamped rather than crashing`() {
        val value = BloomSchedule.layerOpenness(0.5f, 99, layers)
        assertTrue(value in BloomSchedule.MIN_OPENNESS..1f)
    }

    @Test
    fun `single layer flower still opens fully`() {
        assertEquals(1f, BloomSchedule.layerOpenness(1f, 0, 1), 0.0001f)
    }
}
