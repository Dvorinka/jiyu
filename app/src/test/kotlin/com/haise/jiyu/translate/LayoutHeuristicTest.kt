package com.haise.jiyu.translate

import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutHeuristicTest {

    @Test
    fun `heuristic box does not expand beyond 3x the OCR width for a uniform bubble`() {
        val block = TranslatedBlock(
            originalText = "...",
            translatedText = "ABYCH SI VYTVORIL JMÉNO",
            leftF = 0.55f,
            topF = 0.72f,
            rightF = 0.75f,
            bottomF = 0.78f,
            bgUniform = true,
            lineCount = 1,
        )

        val positioned = layoutTranslationBlocks(listOf(block)).single()

        val ownWidth = block.rightF - block.leftF
        val actualWidth = positioned.rightF - positioned.leftF

        assertTrue(
            "width $actualWidth exceeds 3x ownWidth ${3 * ownWidth}",
            actualWidth <= 3 * ownWidth + 0.001f,
        )
    }

    @Test
    fun `heuristic box does not expand beyond 3x the OCR width for a bubble near the right edge`() {
        val block = TranslatedBlock(
            originalText = "...",
            translatedText = "ODPUST MI",
            leftF = 0.78f,
            topF = 0.72f,
            rightF = 0.88f,
            bottomF = 0.78f,
            bgUniform = true,
            lineCount = 1,
        )

        val positioned = layoutTranslationBlocks(listOf(block)).single()

        val ownWidth = block.rightF - block.leftF
        val actualWidth = positioned.rightF - positioned.leftF

        assertTrue(
            "width $actualWidth exceeds 3x ownWidth ${3 * ownWidth}",
            actualWidth <= 3 * ownWidth + 0.001f,
        )
    }

    @Test
    fun `heuristic box does not expand beyond 1_15x for non-uniform background`() {
        val block = TranslatedBlock(
            originalText = "...",
            translatedText = "Překlad",
            leftF = 0.45f,
            topF = 0.45f,
            rightF = 0.55f,
            bottomF = 0.50f,
            bgUniform = false,
            lineCount = 1,
        )

        val positioned = layoutTranslationBlocks(listOf(block)).single()

        val ownWidth = block.rightF - block.leftF
        val actualWidth = positioned.rightF - positioned.leftF

        assertTrue(
            "width $actualWidth exceeds 1.15x ownWidth ${1.15f * ownWidth}",
            actualWidth <= 1.15f * ownWidth + 0.001f,
        )
    }
}
