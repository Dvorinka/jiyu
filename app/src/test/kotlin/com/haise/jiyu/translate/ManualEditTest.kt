package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Testy párování ručních oprav na bubliny po přepočtu překladu.
 *
 * Proč to stojí za testy: ruční oprava je jediná věc na stránce, kterou stroj nevyrobí, takže
 * chyba v párování se buď projeví jako ztráta práce (oprava se nenaparuje), nebo - mnohem hůř -
 * jako přepsání CIZÍ bubliny cizím textem, čehož si nikdo hned nevšimne.
 */
class ManualEditTest {

    private fun block(original: String, translated: String = "strojový") = TranslatedBlock(
        originalText = original,
        translatedText = translated,
        displayText = translated,
        leftF = 0.1f, topF = 0.1f, rightF = 0.4f, bottomF = 0.2f,
    )

    @Test
    fun `an edit is applied to the block with the same original text`() {
        val result = applyManualEdits(
            listOf(block("HELLO THERE")),
            mapOf("HELLO THERE" to "AHOJ TAM"),
        )
        assertEquals("AHOJ TAM", result.single().translatedText)
        assertEquals("AHOJ TAM", result.single().displayText)
    }

    @Test
    fun `line breaks from OCR do not break the match`() {
        // JADRO PROBLEMU: dvouradkova bublina se pri jednom pruchodu precte jako "AB\nCD",
        // pri dalsim jako "AB CD". O jinou bublinu ale nejde.
        val result = applyManualEdits(
            listOf(block("MOUNTAIN BEASTS\nOF ALL THINGS")),
            mapOf(normalizeOriginal("MOUNTAIN BEASTS OF ALL THINGS") to "HORSKÁ MONSTRA"),
        )
        assertEquals("HORSKÁ MONSTRA", result.single().translatedText)
    }

    @Test
    fun `an edit with no matching block changes nothing`() {
        // Kdyz OCR precte stranku jinak, oprava se nesmi naparovat na "nejblizsi" bublinu.
        val blocks = listOf(block("HELLO"), block("GOODBYE"))
        val result = applyManualEdits(blocks, mapOf("SOMETHING ELSE" to "NĚCO JINÉHO"))
        assertEquals(blocks, result)
    }

    @Test
    fun `only the matching block is touched`() {
        val result = applyManualEdits(
            listOf(block("HELLO"), block("GOODBYE", "sbohem")),
            mapOf("HELLO" to "AHOJ"),
        )
        assertEquals("AHOJ", result[0].translatedText)
        assertEquals("sbohem", result[1].translatedText)
    }

    @Test
    fun `case is part of the identity - two bubbles can differ only in case`() {
        val result = applyManualEdits(
            listOf(block("NE."), block("ne.")),
            mapOf("NE." to "NO."),
        )
        assertEquals("NO.", result[0].translatedText)
        assertEquals("strojový", result[1].translatedText)
    }

    @Test
    fun `an edited block is no longer considered untranslated`() {
        // Neprelozena bublina se vubec nekresli (viz BubbleOverlayLayer) - kdyby priznak zustal,
        // rucni oprava by se ulozila, ale nikdy neobjevila na obrazovce.
        val blocks = listOf(block("???").copy(isUntranslated = true))
        val result = applyManualEdits(blocks, mapOf("???" to "TADY NĚCO JE"))
        assertEquals(false, result.single().isUntranslated)
    }

    @Test
    fun `no edits means the list comes back untouched`() {
        val blocks = listOf(block("HELLO"))
        assertEquals(blocks, applyManualEdits(blocks, emptyMap()))
    }

    @Test
    fun `the id keeps chapter and page apart`() {
        assertNotEquals(
            manualEditId("ch1", 0, "HELLO"),
            manualEditId("ch1", 1, "HELLO"),
        )
        assertNotEquals(
            manualEditId("ch1", 0, "HELLO"),
            manualEditId("ch2", 0, "HELLO"),
        )
    }

    @Test
    fun `the id ignores whitespace differences, same as the matching does`() {
        assertEquals(
            manualEditId("ch1", 0, "AB\nCD"),
            manualEditId("ch1", 0, "  AB   CD  "),
        )
    }
}
