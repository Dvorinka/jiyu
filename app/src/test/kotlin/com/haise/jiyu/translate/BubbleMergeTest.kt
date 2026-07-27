package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test [mergeNearbyLines]/[hasWallBetween] (žádná Android/Bitmap závislost) -
 * reprodukuje uživatelskou zpětnou vazbu: bublina "HOW DID YOU MANAGE TO ATTACK HER..."
 * úplně zmizela (sloučila se s jinou, sousední bublinou) a stránka s reklamou na anime
 * dostala jednu přebujelou barevnou plochu přes tři původně samostatné captions.
 */
class BubbleMergeTest {

    private fun block(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        RawTextBlock(text = text, leftF = left, topF = top, rightF = right, bottomF = bottom)

    private class FakeCanvas(val width: Int, val height: Int, fill: Int) : PixelSource {
        val pixels = IntArray(width * height) { fill }
        override fun colorAt(x: Int, y: Int): Int = pixels[(y.coerceIn(0, height - 1)) * width + x.coerceIn(0, width - 1)]
        fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top..bottom) for (x in left..right) pixels[y * width + x] = color
        }
    }

    // ── mergeNearbyLines (bez wall-check, výchozí = stará čistě geometrická logika) ──

    @Test
    fun `merges two vertically stacked lines within the same bubble`() {
        val a = block("Ahoj", 0.30f, 0.10f, 0.50f, 0.14f)
        val b = block("light", 0.30f, 0.145f, 0.50f, 0.185f)

        val merged = mergeNearbyLines(listOf(a, b))

        assertEquals(1, merged.size)
        assertEquals("Ahoj light", merged[0].text)
        assertEquals(2, merged[0].lineCount)
    }

    @Test
    fun `does not merge lines with a large gap`() {
        val a = block("Ahoj", 0.30f, 0.10f, 0.50f, 0.14f)
        val b = block("Nazdar", 0.30f, 0.50f, 0.50f, 0.54f)

        val merged = mergeNearbyLines(listOf(a, b))

        assertEquals(2, merged.size)
    }

    @Test
    fun `wall veto blocks a merge that geometry alone would allow`() {
        val a = block("Ahoj", 0.30f, 0.10f, 0.50f, 0.14f)
        val b = block("Nazdar", 0.30f, 0.145f, 0.50f, 0.185f)
        // Bez veta by se sloučily (stejné jako "merges two vertically stacked lines" výše) -
        // noWallBetween teď vrátí false, jako by tam skutečně byla vizuální hranice.
        val merged = mergeNearbyLines(listOf(a, b)) { _, _ -> false }

        assertEquals(2, merged.size)
    }

    // ── hasWallBetween (skutečná pixelová detekce hranice) ──

    @Test
    fun `no wall between two lines inside the same uniform bubble`() {
        val canvas = FakeCanvas(200, 200, 0xFF000000.toInt())
        canvas.fillRect(20, 20, 180, 100, 0xFFFFFFFF.toInt()) // jedna bílá bublina

        val a = block("Ahoj", 0.15f, 0.15f, 0.60f, 0.30f)
        val b = block("light", 0.15f, 0.32f, 0.60f, 0.47f)

        assertFalse(hasWallBetween(canvas, 200, 200, a, b))
    }

    @Test
    fun `wall detected between two separate bubbles with art in between`() {
        // Kreslené bubliny sahají o kousek DÁL než OCR box (reálná bublina je vždycky
        // o něco větší než text uvnitř) - proto mají navíc 6px odsazení oproti bloku a/b,
        // aby ringSeeds (margin 4px) sáhl pořád na bílou výplň, ne mimo ni.
        val canvas = FakeCanvas(400, 400, 0xFF000000.toInt()) // černá kresba/pozadí mezi bublinami
        canvas.fillRect(14, 14, 186, 86, 0xFFFFFFFF.toInt())    // bublina A (bílá)
        canvas.fillRect(14, 194, 186, 266, 0xFFFFFFFF.toInt())  // bublina B (bílá), stejná barva jako A!

        val a = block("HOW DID YOU MANAGE", 20f / 400, 20f / 400, 180f / 400, 80f / 400)
        val b = block("THIS IS MAKIMA", 20f / 400, 200f / 400, 180f / 400, 260f / 400)

        // I když obě bubliny mají STEJNOU barvu výplně, mezi nimi je pruh černé kresby -
        // vzorkované body na úsečce střed-střed ho musí zachytit.
        assertTrue(hasWallBetween(canvas, 400, 400, a, b))
    }

    @Test
    fun `wall detected between two differently colored caption boxes`() {
        val canvas = FakeCanvas(300, 300, 0xFF808080.toInt()) // šedá ilustrace na pozadí
        canvas.fillRect(20, 20, 280, 90, 0xFF2E7D32.toInt())   // zelený box ("MAPPA")
        canvas.fillRect(20, 150, 280, 220, 0xFFAD1457.toInt()) // růžový box (jiná caption)

        val a = block("FROM THE MAKERS OF JUJUTSU KAISEN", 0.10f, 0.08f, 0.90f, 0.28f)
        val b = block("A MESSAGE FROM THE STUDIO", 0.10f, 0.55f, 0.90f, 0.68f)

        assertTrue(hasWallBetween(canvas, 300, 300, a, b))
    }

    @Test
    fun `no wall reported when blocks are adjacent inside one continuous caption box`() {
        val canvas = FakeCanvas(300, 300, 0xFF2E7D32.toInt()) // celý box jedna barva
        val a = block("MAPPA", 0.10f, 0.10f, 0.90f, 0.30f)
        val b = block("A MESSAGE FROM THE STUDIO", 0.10f, 0.32f, 0.90f, 0.50f)

        assertFalse(hasWallBetween(canvas, 300, 300, a, b))
    }
}
