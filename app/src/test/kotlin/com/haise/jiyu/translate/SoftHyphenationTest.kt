package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SH = 173.toChar().toString()

/**
 * Reprodukuje uživatelskou zpětnou vazbu: "OKAMŽITĚ" vyšlo jako "OKAM" + rozbitý zbytek
 * (model vrátil poškozený syllable_breaks) a "BŘÍŠKO" se rozlomilo na "BŘÍŠ"/"KO" bez
 * jakéhokoli spojovníku (model žádný rozdělovník nevrátil vůbec).
 */
class SoftHyphenationTest {

    @Test
    fun `valid syllable breaks match translated text once hyphens are stripped`() {
        assertTrue(isValidSyllableBreaks("gravitace", "gravi${SH}tace"))
    }

    @Test
    fun `corrupted syllable breaks that do not match translated text are rejected`() {
        // Přesně tenhle druh poškození nahlásil uživatel - useknutý/rozbitý zbytek slova.
        assertFalse(isValidSyllableBreaks("Všimla by si tě okamžitě.", "Všimla by si tě okam${SH}těi'"))
    }

    @Test
    fun `text with no hyphens at all is trivially valid`() {
        assertTrue(isValidSyllableBreaks("Ahoj", "Ahoj"))
    }

    @Test
    fun `fallback hyphenation breaks a short word that overflowed with no hyphen`() {
        val result = ensureFallbackHyphens("Kuk na to břiško")
        assertEquals("Kuk na to bři${SH}ško", result)
    }

    @Test
    fun `fallback hyphenation leaves already-broken words untouched`() {
        val alreadyBroken = "gravi${SH}tace"
        assertEquals(alreadyBroken, ensureFallbackHyphens(alreadyBroken))
    }

    @Test
    fun `fallback hyphenation only touches the long word, not short ones around it`() {
        val result = ensureFallbackHyphens("Jsem si jistý gravitace")
        assertTrue(result.startsWith("Jsem si jistý "))
        assertTrue(result.contains(SH))
    }

    @Test
    fun `short words are never hyphenated`() {
        assertEquals("Co je?", ensureFallbackHyphens("Co je?"))
    }

    @Test
    fun `word with no valid vowel-consonant boundary is left whole`() {
        // Čistě samohlásky/souhlásky bez jasné hranice - žádný kandidát na zlom.
        assertEquals("AAAAAAAAA", ensureFallbackHyphens("AAAAAAAAA"))
    }

    @Test
    fun `hyphenation never produces a fragment shorter than 2 characters at the end`() {
        val result = ensureFallbackHyphens("okamžitě")
        val lastPart = result.substringAfterLast(SH)
        assertTrue("last fragment '$lastPart' must be at least 2 chars", lastPart.length >= 2)
    }
}
