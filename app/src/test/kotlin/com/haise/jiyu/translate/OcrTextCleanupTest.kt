package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy spojování slov, která lettering rozdělil na konci řádku.
 *
 * Nahlášený případ: `EVERY-` / `ONE DON'T SCATTER, STAY TOGETHER!` dorazilo k modelu jako
 * `EVERY- ONE DON'T SCATTER...` a překlad z toho vyšel jako věta, která si odporuje sama.
 */
class OcrTextCleanupTest {

    @Test
    fun `a word split at the end of a line is put back together`() {
        assertEquals(
            "EVERY-ONE DON'T SCATTER, STAY TOGETHER!",
            joinHyphenatedLineBreaks("EVERY-\nONE DON'T SCATTER, STAY TOGETHER!"),
        )
    }

    @Test
    fun `the hyphen stays, so a real compound survives`() {
        // JADRO ROZHODNUTI: rozdil mezi delenim slova a skutecnym spojovnikem z textu
        // nepoznáme. Ponechani pomlcky je spravne v obou pripadech - smazat ji by rozbilo
        // prave tenhle.
        assertEquals("well-known", joinHyphenatedLineBreaks("well-\nknown"))
    }

    @Test
    fun `spaces around the line break do not matter`() {
        assertEquals("AR-MOR?", joinHyphenatedLineBreaks("AR-  \n  MOR?"))
    }

    @Test
    fun `windows line endings work too`() {
        assertEquals("AR-MOR?", joinHyphenatedLineBreaks("AR-\r\nMOR?"))
    }

    @Test
    fun `a line break without a hyphen is left alone`() {
        // Zalomeni mezi vetami nese informaci o sazbe bubliny - neslucovat.
        assertEquals("HELLO\nTHERE", joinHyphenatedLineBreaks("HELLO\nTHERE"))
    }

    @Test
    fun `a dash used as punctuation is not glued to the next line`() {
        // Pomlcka po mezere neni deleni slova, ale interpunkce.
        assertEquals("WAIT -\nWHAT?", joinHyphenatedLineBreaks("WAIT -\nWHAT?"))
    }

    @Test
    fun `a hyphen followed by something other than a letter is left alone`() {
        assertEquals("PAGE-\n42", joinHyphenatedLineBreaks("PAGE-\n42"))
    }

    @Test
    fun `several splits in one bubble are all joined`() {
        assertEquals(
            "MOUN-TAIN BEASTS OF ALL THINGS... WEAR-ING ORCISH AR-MOR?",
            joinHyphenatedLineBreaks("MOUN-\nTAIN BEASTS OF ALL THINGS... WEAR-\nING ORCISH AR-\nMOR?"),
        )
    }

    @Test
    fun `text without any hyphen comes back unchanged`() {
        val text = "SHUT YOUR MOUTH BEFORE I TEAR YOU APART."
        assertEquals(text, joinHyphenatedLineBreaks(text))
    }
}
