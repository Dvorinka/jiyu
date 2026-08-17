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

    // ── rozdělovník OD MODELU na nesmyslném místě (nález z Vagabonda) ──

    @Test
    fun `a break that leaves a single letter dangling is rejected`() {
        // JÁDRO NÁLEZU: v bublině vyšlo "POSLEDN" a na dalším řádku osamocené "Í". Vlastní
        // slabikování appky by tohle nikdy neudělalo (hlídá si, že za zlomem zbydou aspoň dva
        // znaky), jenže rozdělovník OD MODELU se do teď kontroloval jen na to, jestli po
        // odstranění rozdělovníků sedí text - kam ten zlom padne, nekontroloval nikdo.
        assertFalse(isValidSyllableBreaks("poslední", "posledn${SH}í"))
    }

    @Test
    fun `the same word broken sensibly is still accepted`() {
        // Protipól: oprava nesmí zahodit rozdělovníky, které jsou v pořádku - jinak by se
        // model přestal používat úplně.
        assertTrue(isValidSyllableBreaks("poslední", "posle${SH}dní"))
    }

    @Test
    fun `a break glued to the start of a word is rejected too`() {
        assertFalse(isValidSyllableBreaks("poslední", "p${SH}oslední"))
    }

    @Test
    fun `only the offending word matters, not the rest of the sentence`() {
        // Rozdělovník na dobrém místě jinde ve větě nesmí vadit, špatný ve stejné větě ano.
        assertTrue(isValidSyllableBreaks("tyto poslední dny", "tyto posle${SH}dní dny"))
        assertFalse(isValidSyllableBreaks("tyto poslední dny", "tyto posledn${SH}í dny"))
    }

    @Test
    fun `trailing punctuation does not count as a syllable`() {
        // "DNY." má sice čtyři znaky, ale jen tři písmena; kdyby se počítaly znaky, prošel by
        // i zlom, po kterém zbyde jediné písmeno a tečka.
        assertFalse(isValidSyllableBreaks("poslední.", "posledn${SH}í."))
    }

    @Test
    fun `a rejected break falls back to the app's own hyphenation, not to nothing`() {
        // Tohle je ta část, kvůli které oprava vůbec dává smysl: zahodit model neznamená zůstat
        // bez rozdělovníku - ensureFallbackHyphens doplní vlastní, rozumně umístěný.
        val fallback = ensureFallbackHyphens("poslední")
        assertTrue("appka si musí poradit sama, dostala „$fallback\"", fallback.contains(SH))
        assertTrue(isValidSyllableBreaks("poslední", fallback))
    }

    // ── hyphenationSegments (viz longestIndivisibleRunWidthPx v BubbleTextFitTest) ──

    @Test
    fun `a word with one soft hyphen splits into two segments`() {
        assertEquals(listOf("Pante", "rí"), hyphenationSegments("Pante${SH}rí"))
    }

    @Test
    fun `a word with no soft hyphen is a single segment`() {
        assertEquals(listOf("houba"), hyphenationSegments("houba"))
    }

    @Test
    fun `a word with multiple soft hyphens splits into all its segments`() {
        assertEquals(listOf("gravi", "ta", "ce"), hyphenationSegments("gravi${SH}ta${SH}ce"))
    }
}
