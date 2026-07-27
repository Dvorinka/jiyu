package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleClassifierTest {

    private fun rawBlock(text: String, shape: List<BubbleShapePoint>? = null) = RawTextBlock(
        text = text,
        leftF = 0.1f,
        topF = 0.1f,
        rightF = 0.2f,
        bottomF = 0.15f,
        shape = shape,
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
}
