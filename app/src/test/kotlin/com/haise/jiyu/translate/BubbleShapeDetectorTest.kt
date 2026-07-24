package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test [BubbleShapeDetector] (žádná Android/Bitmap závislost) - syntetický
 * PixelSource kreslí jednoduché tvary do IntArray a ověřuje, že flood-fill najde
 * očekávaný obrys / správně selže na moc velké nebo neplatné ploše.
 */
class BubbleShapeDetectorTest {

    private class FakeCanvas(val width: Int, val height: Int, fill: Int) : PixelSource {
        val pixels = IntArray(width * height) { fill }
        override fun colorAt(x: Int, y: Int): Int = pixels[y * width + x]
        fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top..bottom) for (x in left..right) pixels[y * width + x] = color
        }
    }

    private val BG = 0xFFCCCCCC.toInt()
    private val ART = 0xFF000000.toInt()

    @Test
    fun `detects bounding box of a solid rectangle bubble`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
            // Testovací obdélník (61x41 = ~42 % plátna) je uměle velký vůči malému plátnu -
            // reálná bublina na skutečné stránce manga bývá zlomek celé plochy. Výchozí
            // maxAreaFraction (0.25) je testovaný zvlášť níž ("leaks past the area cap").
            maxAreaFraction = 0.5f,
        )

        assertNotNull(shape)
        val left = shape!!.minOf { it.leftF }
        val right = shape.maxOf { it.rightF }
        val top = shape.minOf { it.yF }
        val bottom = shape.maxOf { it.yF }
        assertEquals(0.20f, left, 0.02f)
        assertEquals(0.80f, right, 0.02f)
        assertEquals(10f / 60f, top, 0.02f)
        assertEquals(50f / 60f, bottom, 0.02f)
    }

    @Test
    fun `returns null when no seed matches background color`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        // Seed sedí uvnitř obdélníku, ale bgColorArgb neodpovídá ničemu na plátně.
        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = 0xFFFF00FF.toInt(),
        )

        assertNull(shape)
    }

    @Test
    fun `returns null when flood fill leaks past the area cap`() {
        // Skoro celé plátno je "pozadí" - žádná uzavřená bublina, flood-fill by se
        // rozlil přes většinu stránky (simuluje SFX text přímo na kresbě bez bubliny).
        val canvas = FakeCanvas(100, 60, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
            maxAreaFraction = 0.25f,
        )

        assertNull(shape)
    }

    @Test
    fun `sampled points are ordered from top to bottom`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(50 to 30),
            bgColorArgb = BG,
            maxAreaFraction = 0.5f,
        )

        assertNotNull(shape)
        for (i in 1 until shape!!.size) {
            assertTrue(shape[i].yF >= shape[i - 1].yF)
        }
    }

    @Test
    fun `ignores invalid seeds outside the canvas`() {
        val canvas = FakeCanvas(100, 60, ART)
        canvas.fillRect(20, 10, 80, 50, BG)

        val shape = BubbleShapeDetector.detectShape(
            source = canvas,
            width = 100,
            height = 60,
            seeds = listOf(-5 to -5, 50 to 30), // první seed mimo plátno, druhý platný
            bgColorArgb = BG,
            maxAreaFraction = 0.5f,
        )

        assertNotNull(shape)
    }
}
