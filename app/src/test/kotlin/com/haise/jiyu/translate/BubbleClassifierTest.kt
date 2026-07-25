package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleClassifierTest {

    private fun rawBlock(text: String) = RawTextBlock(
        text = text,
        leftF = 0.1f,
        topF = 0.1f,
        rightF = 0.2f,
        bottomF = 0.15f,
    )

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
}
