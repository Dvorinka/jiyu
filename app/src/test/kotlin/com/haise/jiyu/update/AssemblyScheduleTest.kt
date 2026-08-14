package com.haise.jiyu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyScheduleTest {

    private val vertices = 12

    @Test
    fun `at zero progress no vertex has arrived yet`() {
        for (i in 0 until vertices) {
            assertEquals(0f, AssemblySchedule.vertexArrival(0f, i, vertices), 0.0001f)
        }
    }

    @Test
    fun `at full progress every vertex has landed`() {
        for (i in 0 until vertices) {
            assertEquals(1f, AssemblySchedule.vertexArrival(1f, i, vertices), 0.0001f)
        }
    }

    @Test
    fun `first vertex leads the last one`() {
        val first = AssemblySchedule.vertexArrival(0.4f, 0, vertices)
        val last = AssemblySchedule.vertexArrival(0.4f, vertices - 1, vertices)
        assertTrue("first=$first should lead last=$last", first > last)
    }

    @Test
    fun `arrival never goes backwards as progress grows`() {
        for (i in 0 until vertices) {
            var previous = -1f
            var p = 0f
            while (p <= 1f) {
                val value = AssemblySchedule.vertexArrival(p, i, vertices)
                assertTrue("vertex $i went backwards at p=$p", value >= previous - 0.0001f)
                previous = value
                p += 0.02f
            }
        }
    }

    @Test
    fun `progress outside 0-1 is clamped instead of overshooting`() {
        assertEquals(0f, AssemblySchedule.vertexArrival(-5f, 0, vertices), 0.0001f)
        assertEquals(1f, AssemblySchedule.vertexArrival(9f, 0, vertices), 0.0001f)
    }

    @Test
    fun `out of range vertex index is clamped rather than crashing`() {
        val value = AssemblySchedule.vertexArrival(0.5f, 99, vertices)
        assertTrue(value in 0f..1f)
    }

    @Test
    fun `single vertex still lands fully`() {
        assertEquals(1f, AssemblySchedule.vertexArrival(1f, 0, 1), 0.0001f)
    }

    @Test
    fun `edge stays dark until both of its ends have nearly arrived`() {
        // Jeden konec na miste, druhy jeste letí - hrana se kreslit nesmi.
        assertEquals(0f, AssemblySchedule.edgeStrength(1f, 0.2f), 0.0001f)
        assertEquals(0f, AssemblySchedule.edgeStrength(0.2f, 1f), 0.0001f)
    }

    @Test
    fun `edge is fully lit only when both ends have landed`() {
        assertEquals(1f, AssemblySchedule.edgeStrength(1f, 1f), 0.0001f)
    }

    @Test
    fun `edge strength is symmetric in its two ends`() {
        assertEquals(
            AssemblySchedule.edgeStrength(0.9f, 0.75f),
            AssemblySchedule.edgeStrength(0.75f, 0.9f),
            0.0001f,
        )
    }
}
