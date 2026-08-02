package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPreprocessTest {

    private fun gray(value: Int): Int = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    @Test
    fun `luma weights green most, as the eye does`() {
        val red = luma((0xFF shl 24) or (255 shl 16))
        val green = luma((0xFF shl 24) or (255 shl 8))
        val blue = luma((0xFF shl 24) or 255)

        assertTrue("zelená musí vážit nejvíc ($green proti $red a $blue)", green > red && green > blue)
        assertEquals(255, luma(gray(255)))
        assertEquals(0, luma(gray(0)))
    }

    @Test
    fun `otsu splits a page of dark text on light background between the two peaks`() {
        // Typická stránka: hodně světlého pozadí, málo tmavých tahů písma.
        val histogram = IntArray(256)
        histogram[240] = 9000
        histogram[20] = 1000

        val threshold = otsuThreshold(histogram)

        assertTrue("práh musí padnout mezi obě špičky (vyšel $threshold)", threshold in 21..239)
    }

    @Test
    fun `otsu survives an empty histogram instead of dividing by zero`() {
        assertEquals(128, otsuThreshold(IntArray(256)))
    }

    @Test
    fun `contrast stretch opens up a washed out scan`() {
        // Vybledlý sken: všechno se mačká mezi 100 a 160, ani jeden konec rozsahu se nepoužívá.
        val histogram = IntArray(256)
        for (level in 100..160) histogram[level] = 100

        val lut = contrastStretchLut(histogram)

        assertEquals("dolní konec se musí posadit na černou", 0, lut[100])
        assertEquals("horní konec se musí posadit na bílou", 255, lut[160])
        assertTrue("mezi tím musí být rostoucí", lut[130] in 1..254)
    }

    @Test
    fun `contrast stretch leaves an already full range image alone`() {
        // Pojistka proti "vylepšení", které jen přidá zaokrouhlovací šum: když je předloha
        // rozeklaná přes celý rozsah, správná odpověď je nedělat nic.
        val histogram = IntArray(256) { 100 }

        val lut = contrastStretchLut(histogram)

        assertEquals(IntArray(256) { it }.toList(), lut.toList())
    }

    @Test
    fun `contrast stretch ignores a single stray pixel at each end`() {
        // JÁDRO: kdyby se bralo min a max místo percentilů, stačilo by jedno černé logo
        // a jeden přepálený pixel, aby roztažení nedělalo vůbec nic.
        val histogram = IntArray(256)
        for (level in 100..160) histogram[level] = 100
        histogram[0] = 1
        histogram[255] = 1

        val lut = contrastStretchLut(histogram)

        assertNotEquals("percentily musí přes ojedinělé pixely přejít", IntArray(256) { it }.toList(), lut.toList())
    }

    @Test
    fun `binarize sends everything to pure black or pure white`() {
        val pixels = intArrayOf(gray(10), gray(120), gray(200), gray(255))

        val result = binarize(pixels, threshold = 128)

        assertEquals(listOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()), result.toList())
    }

    @Test
    fun `a night panel is recognised as mostly dark, an ordinary page is not`() {
        val nightPanel = IntArray(256).also { it[20] = 900; it[240] = 100 }
        val ordinaryPage = IntArray(256).also { it[20] = 100; it[240] = 900 }

        assertTrue(isMostlyDark(nightPanel))
        assertFalse(isMostlyDark(ordinaryPage))
    }

    @Test
    fun `invert swaps black and white but keeps the alpha channel`() {
        val result = invert(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))

        assertEquals(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), result.toList())
    }

    @Test
    fun `applying the lut produces a gray image and drops colour`() {
        val red = (0xFF shl 24) or (200 shl 16)

        val result = applyLutToGray(intArrayOf(red), IntArray(256) { it }).single()

        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        assertEquals("kanály musí být shodné (šedá)", r, g)
        assertEquals(g, b)
        assertEquals("hodnota musí odpovídat jasu vstupu", luma(red), r)
    }

    @Test
    fun `the histogram counts every pixel exactly once`() {
        val pixels = intArrayOf(gray(0), gray(0), gray(128), gray(255))

        val histogram = lumaHistogram(pixels)

        assertEquals(pixels.size, histogram.sum())
        assertEquals(2, histogram[0])
        assertEquals(1, histogram[128])
        assertEquals(1, histogram[255])
    }
}
