package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Test

/** Čistý JVM test [sortIntoReadingOrder] - žádná Android/Bitmap závislost. */
class ReadingOrderTest {

    private fun block(text: String, l: Float, t: Float, r: Float, b: Float) =
        RawTextBlock(text = text, leftF = l, topF = t, rightF = r, bottomF = b)

    @Test
    fun `japanese manga row reads right to left`() {
        val left = block("levá", 0.1f, 0.1f, 0.3f, 0.2f)
        val right = block("pravá", 0.6f, 0.1f, 0.8f, 0.2f)
        // Vloženo v "nepřirozeném" pořadí (levá první) - řadič musí přehodit.
        val result = sortIntoReadingOrder(listOf(left, right), rightToLeft = true)
        assertEquals(listOf("pravá", "levá"), result.map { it.text })
    }

    @Test
    fun `non-japanese row reads left to right`() {
        val left = block("levá", 0.1f, 0.1f, 0.3f, 0.2f)
        val right = block("pravá", 0.6f, 0.1f, 0.8f, 0.2f)
        val result = sortIntoReadingOrder(listOf(left, right), rightToLeft = false)
        assertEquals(listOf("levá", "pravá"), result.map { it.text })
    }

    @Test
    fun `top row always comes before bottom row regardless of horizontal position`() {
        val topRight = block("nahoře", 0.6f, 0.1f, 0.8f, 0.2f)
        val bottomLeft = block("dole", 0.1f, 0.5f, 0.3f, 0.6f)
        val result = sortIntoReadingOrder(listOf(bottomLeft, topRight), rightToLeft = true)
        assertEquals(listOf("nahoře", "dole"), result.map { it.text })
    }

    @Test
    fun `reconstructs a realistic two-row manga page in correct reading order`() {
        // Horní řádek: 2 bubliny vedle sebe (čte se zprava doleva). Dolní řádek: 1 bublina.
        val topLeftBubble = block("horní-levá", 0.05f, 0.05f, 0.35f, 0.20f)
        val topRightBubble = block("horní-pravá", 0.55f, 0.04f, 0.90f, 0.19f)
        val bottomBubble = block("dolní", 0.20f, 0.60f, 0.70f, 0.75f)

        val shuffled = listOf(bottomBubble, topLeftBubble, topRightBubble)
        val result = sortIntoReadingOrder(shuffled, rightToLeft = true)

        assertEquals(listOf("horní-pravá", "horní-levá", "dolní"), result.map { it.text })
    }

    @Test
    fun `single block is returned unchanged`() {
        val only = block("sám", 0.1f, 0.1f, 0.3f, 0.2f)
        assertEquals(listOf(only), sortIntoReadingOrder(listOf(only), rightToLeft = true))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<RawTextBlock>(), sortIntoReadingOrder(emptyList(), rightToLeft = true))
    }
}
