package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test rozhodování "je tohle použitelný překlad?".
 *
 * Cíl: když model bublinu vynechá nebo vrátí prázdno, NESMÍ se do ní vysázet anglický
 * originál jako plnohodnotný překlad - viz [TranslationMerge] a uživatelské screenshoty
 * s "THE FIRST PLACE." v české bublině.
 */
class TranslationMergeTest {

    private fun bubble(id: Int, translated: String) = GeminiBubbleTranslation(
        id = id,
        original = "orig$id",
        translated = translated,
        bubbleSizeTag = "MEDIUM",
        isSfx = false,
        syllableBreaks = "",
    )

    private fun classified(text: String, isSfx: Boolean = false) = ClassifiedBubble(
        raw = RawTextBlock(text = text, leftF = 0f, topF = 0f, rightF = 0.1f, bottomF = 0.1f),
        sizeTag = SizeTag.MEDIUM,
        bubbleType = if (isSfx) BubbleType.SFX else BubbleType.SPEECH,
        isSfx = isSfx,
        lineCount = 1,
    )

    // ── isUsableTranslation ──

    @Test
    fun `a real translation is usable`() {
        assertTrue(isUsableTranslation(bubble(0, "Ahoj")))
    }

    @Test
    fun `a missing entry is not usable`() {
        assertFalse(isUsableTranslation(null))
    }

    @Test
    fun `an empty translation is not usable`() {
        assertFalse(isUsableTranslation(bubble(0, "")))
    }

    @Test
    fun `a whitespace only translation is not usable`() {
        assertFalse(isUsableTranslation(bubble(0, "   \n ")))
    }

    @Test
    fun `the untranslated marker is not usable`() {
        assertFalse(isUsableTranslation(bubble(0, GeminiUltraPrompt.UNTRANSLATED_MARKER)))
    }

    // ── missingTranslationIndices ──

    @Test
    fun `nothing is missing when the model answered every bubble`() {
        val classified = listOf(classified("A"), classified("B"))
        val byId = mapOf(0 to bubble(0, "Á"), 1 to bubble(1, "Bé"))
        assertEquals(emptyList<Int>(), missingTranslationIndices(classified, byId))
    }

    @Test
    fun `a skipped bubble is reported as missing`() {
        val classified = listOf(classified("A"), classified("B"), classified("C"))
        val byId = mapOf(0 to bubble(0, "Á"), 2 to bubble(2, "Cé"))
        assertEquals(listOf(1), missingTranslationIndices(classified, byId))
    }

    @Test
    fun `a blank translation is reported as missing`() {
        val classified = listOf(classified("A"), classified("B"))
        val byId = mapOf(0 to bubble(0, "Á"), 1 to bubble(1, "  "))
        assertEquals(listOf(1), missingTranslationIndices(classified, byId))
    }

    @Test
    fun `sfx bubbles are never reported as missing`() {
        // SFX se schvalne neprekladaji - chybejici odpoved u nich neni chyba.
        val classified = listOf(classified("BOOM", isSfx = true), classified("A"))
        val byId = mapOf(1 to bubble(1, "Á"))
        assertEquals(emptyList<Int>(), missingTranslationIndices(classified, byId))
    }

    @Test
    fun `a deliberate untranslated marker is not retried`() {
        // Model uz jednou vedome rekl "tohle neprelozim" - opakovany dotaz by jen stal request.
        val classified = listOf(classified("???"))
        val byId = mapOf(0 to bubble(0, GeminiUltraPrompt.UNTRANSLATED_MARKER))
        assertEquals(emptyList<Int>(), missingTranslationIndices(classified, byId))
    }

    // ── mergeRetry ──

    @Test
    fun `retry fills in the bubble the first pass skipped`() {
        val byId = mapOf(0 to bubble(0, "Á"))
        // Opravny dotaz poslal jen bublinu c. 1, takze v jeho odpovedi ma id 0.
        val retry = GeminiTranslationResponse(bubbles = listOf(bubble(0, "Bé")))

        val merged = mergeRetry(byId, retriedIndices = listOf(1), retryResponse = retry)

        assertEquals("Bé", merged[1]?.translated)
        assertEquals("Á", merged[0]?.translated)
    }

    @Test
    fun `retry ids are mapped back to the original positions`() {
        val retry = GeminiTranslationResponse(bubbles = listOf(bubble(0, "prvni"), bubble(1, "druhy")))

        val merged = mergeRetry(emptyMap(), retriedIndices = listOf(3, 7), retryResponse = retry)

        assertEquals("prvni", merged[3]?.translated)
        assertEquals("druhy", merged[7]?.translated)
        assertEquals(2, merged.size)
    }

    @Test
    fun `an unusable retry answer does not overwrite a good one`() {
        val byId = mapOf(2 to bubble(2, "dobry preklad"))
        val retry = GeminiTranslationResponse(bubbles = listOf(bubble(0, "")))

        val merged = mergeRetry(byId, retriedIndices = listOf(2), retryResponse = retry)

        assertEquals("dobry preklad", merged[2]?.translated)
    }

    @Test
    fun `a failed retry leaves the original map untouched`() {
        val byId = mapOf(0 to bubble(0, "Á"))
        assertEquals(byId, mergeRetry(byId, retriedIndices = listOf(1), retryResponse = null))
    }

    @Test
    fun `an out of range retry id is ignored rather than crashing`() {
        val retry = GeminiTranslationResponse(bubbles = listOf(bubble(9, "mimo")))
        assertEquals(emptyMap<Int, GeminiBubbleTranslation>(), mergeRetry(emptyMap(), listOf(0), retry))
    }
}
