package com.haise.jiyu.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TallImageSlicerTest {

    @Test
    fun `image shorter than threshold is not sliced`() {
        val slices = TallImageSlicer.computeSlices(height = 2000, maxSliceHeight = 4000)
        assertEquals(listOf(0 until 2000), slices)
    }

    @Test
    fun `image exactly at threshold is not sliced`() {
        val slices = TallImageSlicer.computeSlices(height = 4000, maxSliceHeight = 4000)
        assertEquals(listOf(0 until 4000), slices)
    }

    @Test
    fun `real demonicscans page is sliced into three contiguous pieces`() {
        val slices = TallImageSlicer.computeSlices(height = 11400, maxSliceHeight = 4000)
        assertEquals(listOf(0 until 4000, 4000 until 8000, 8000 until 11400), slices)
    }

    @Test
    fun `slices are always contiguous and cover the full height with no gaps or overlap`() {
        val height = 11401 // odd remainder on purpose
        val slices = TallImageSlicer.computeSlices(height = height, maxSliceHeight = 4000)

        assertEquals(0, slices.first().first)
        assertEquals(height, slices.last().last + 1)
        for (i in 0 until slices.size - 1) {
            assertEquals("slice $i must end exactly where slice ${i + 1} starts", slices[i + 1].first, slices[i].last + 1)
        }
    }

    @Test
    fun `no slice exceeds the max height`() {
        val slices = TallImageSlicer.computeSlices(height = 11400, maxSliceHeight = 4000)
        slices.forEach { assertTrue("slice $it must not exceed maxSliceHeight", (it.last - it.first + 1) <= 4000) }
    }
}
