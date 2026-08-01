package com.haise.jiyu.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy volby směru čtení podle jazyka.
 *
 * Proč zrovna tohle: směr rozhodoval jediný test na japonštinu. Tradiční čínština (Tchaj-wan,
 * Hongkong) se ale čte stejně zprava doleva jako manga - dostávala tedy bubliny seřazené
 * obráceně a překladový model četl repliky pozpátku, což kazí návaznost dialogu. Je to stejná
 * chyba, jaká se dřív projevila u zdrojového jazyka "Auto".
 *
 * Zjednodušená čínština (manhua, typicky webtoonový formát) se naopak čte zleva doprava, takže
 * to nejde vzít plošně přes "čínštinu".
 */
class ReadingDirectionTest {

    @Test
    fun `japanese is read right to left`() {
        assertTrue(isRightToLeftScript("Japanese"))
    }

    @Test
    fun `traditional chinese is read right to left too`() {
        assertTrue(isRightToLeftScript("Chinese (Traditional)"))
    }

    @Test
    fun `simplified chinese is left to right`() {
        // Manhua v zjednodusene cinstine se cte zleva doprava - proto se to nesmi
        // zobecnit na "cokoliv cinskeho".
        assertFalse(isRightToLeftScript("Chinese"))
    }

    @Test
    fun `korean and latin scripts are left to right`() {
        assertFalse(isRightToLeftScript("Korean"))
        assertFalse(isRightToLeftScript("English"))
        assertFalse(isRightToLeftScript("Czech"))
    }

    @Test
    fun `auto falls back to left to right`() {
        // Pod "Auto" se rozpoznavace pro tradicni cinstinu vubec nezkousi (viz
        // AUTO_CANDIDATE_LANGUAGES), takze se sem doslovne "Auto" dostat nema - ale kdyby
        // ano, nesmi to prohodit poradi nahodne.
        assertFalse(isRightToLeftScript(AUTO_LANGUAGE))
    }
}
