package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleClassifierTest {

    private fun rawBlock(
        text: String,
        shape: List<BubbleShapePoint>? = null,
        leftF: Float = 0.1f,
        topF: Float = 0.1f,
        rightF: Float = 0.2f,
        bottomF: Float = 0.15f,
        lineCount: Int = 1,
    ) = RawTextBlock(
        text = text,
        leftF = leftF,
        topF = topF,
        rightF = rightF,
        bottomF = bottomF,
        shape = shape,
        lineCount = lineCount,
    )

    /** Trsovitý/hvězdicovitý obrys (24 vzorků, hroty/prohlubně střídající se každý vzorek) - viz [isJaggedShape]. */
    private fun jaggedShoutShape(): List<BubbleShapePoint> =
        (0 until 24).map { i ->
            val width = if (i % 2 == 0) 0.85f else 0.35f
            BubbleShapePoint(yF = i / 23f, leftF = 0.5f - width / 2f, rightF = 0.5f + width / 2f)
        }

    @Test
    fun `bare page or panel number is classified as sfx (noise, not dialogue)`() {
        val result = BubbleClassifier.classify(rawBlock("3"), lineCount = 1)
        assertTrue(result.isSfx)
        assertEquals(BubbleType.SFX, result.bubbleType)
    }

    @Test
    fun `multi-digit bare number is classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("12"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `number embedded in real dialogue is not sfx`() {
        val result = BubbleClassifier.classify(rawBlock("THERE'S A MEASLY 1800 YEN LEFT."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `ellipsis only bubble is not treated as noise`() {
        val result = BubbleClassifier.classify(rawBlock("..."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `real sfx word is still detected`() {
        val result = BubbleClassifier.classify(rawBlock("BOOM!!!"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `jagged bubble shape is classified as shout even when text alone would not suggest it`() {
        // Text sam o sobe (male pismeno, bez vykricniku) by dal SPEECH - jagged obrys
        // (skutecna kresba, viz isJaggedShape) musi klasifikaci pretlacit na SHOUT.
        val result = BubbleClassifier.classify(rawBlock("Uz jdou", shape = jaggedShoutShape()), lineCount = 1)
        assertEquals(BubbleType.SHOUT, result.bubbleType)
    }

    @Test
    fun `smooth bubble shape does not force shout classification`() {
        val smoothShape = (0 until 24).map { i -> BubbleShapePoint(yF = i / 23f, leftF = 0.2f, rightF = 0.8f) }
        val result = BubbleClassifier.classify(rawBlock("Uz jdou", shape = smoothShape), lineCount = 1)
        assertEquals(BubbleType.SPEECH, result.bubbleType)
    }

    // ── krátká skutečná slova nesmí spadnout do SFX (viz uživatelská zpětná vazba -
    //    "DAMN..." zůstalo nepřeložené, protože SFX bublina se nikdy nevykresluje) ──

    @Test
    fun `short common interjection is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("DAMN..."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `short common word wait is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("WAIT!"), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `short common word stop is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("STOP"), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `real sfx word boom is still detected even though it is short and all caps`() {
        // Sanity - vyjimka pro bezna slova nesmi rozbit skutecne SFX detekce.
        val result = BubbleClassifier.classify(rawBlock("BOOM"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    // ── vodoznak scanlation skupiny (viz uzivatelska zpetna vazba - cerna skvrna pres kresbu) ──

    @Test
    fun `scanlation domain watermark is classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("SIRENSCANS.COM"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `watermark read letter by letter with spaces is still detected as domain`() {
        val result = BubbleClassifier.classify(rawBlock("E N S C A N S . C O M"), lineCount = 8)
        assertTrue(result.isSfx)
    }

    @Test
    fun `very tall narrow block with many merged lines is treated as decorative watermark`() {
        val result = BubbleClassifier.classify(
            rawBlock("some vertical text", leftF = 0.5f, topF = 0.1f, rightF = 0.52f, bottomF = 0.9f, lineCount = 10),
            lineCount = 10,
        )
        assertTrue(result.isSfx)
    }

    @Test
    fun `normal long narration block is not mistaken for a watermark`() {
        val result = BubbleClassifier.classify(
            rawBlock(
                "This is a perfectly normal long narration line that spans the width of the panel comfortably.",
                leftF = 0.05f, topF = 0.1f, rightF = 0.95f, bottomF = 0.30f, lineCount = 4,
            ),
            lineCount = 4,
        )
        assertFalse(result.isSfx)
    }

    @Test
    fun `dialogue containing the word scan as a normal sentence is not a watermark`() {
        val result = BubbleClassifier.classify(rawBlock("Let me scan the area first."), lineCount = 1)
        assertFalse(result.isSfx)
    }
}
