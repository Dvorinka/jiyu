package com.haise.jiyu.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Testy hranice plnoletosti pro odemčení zdrojů s obsahem pro dospělé.
 *
 * Proč se to testuje takhle podrobně: "dnešní rok minus rok narození" je nejčastější způsob,
 * jak tenhle výpočet zkazit, a chyba je tichá - nikdo si nevšimne, že se někomu odemklo
 * o pár měsíců dřív. Datum se navíc nikam neukládá, takže to nejde zpětně dohledat.
 */
class AgeCheckTest {

    private val today = LocalDate.of(2026, 8, 2)

    @Test
    fun `someone well over eighteen is an adult`() {
        assertTrue(isAdultOn(LocalDate.of(1990, 5, 12), today))
    }

    @Test
    fun `someone clearly under eighteen is not`() {
        assertFalse(isAdultOn(LocalDate.of(2015, 5, 12), today))
    }

    @Test
    fun `the eighteenth birthday itself already counts`() {
        assertTrue(isAdultOn(LocalDate.of(2008, 8, 2), today))
    }

    @Test
    fun `one day before the eighteenth birthday does not count`() {
        assertFalse(isAdultOn(LocalDate.of(2008, 8, 3), today))
    }

    @Test
    fun `the same birth year is not enough on its own`() {
        // JADRO CASTE CHYBY: 2026 - 2008 = 18, takze vypocet "jen podle roku" by tvrdil, ze
        // plnolety je - jenze narozeniny prijdou az v prosinci.
        assertFalse(isAdultOn(LocalDate.of(2008, 12, 31), today))
        assertTrue(isAdultOn(LocalDate.of(2008, 1, 1), today))
    }

    @Test
    fun `a leap day birthday becomes adult on the first of march in a common year`() {
        // 29. 2. 2008 + 18 let = 29. 2. 2026, jenze 2026 neni prestupny. java.time to posune
        // na 28. 2., takze uz ten den je plnolety.
        assertTrue(isAdultOn(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28)))
        assertFalse(isAdultOn(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 27)))
    }

    @Test
    fun `a date in the future is not plausible`() {
        assertFalse(isPlausibleBirthDate(today.plusDays(1), today))
    }

    @Test
    fun `today is still plausible - a newborn is a valid answer`() {
        assertTrue(isPlausibleBirthDate(today, today))
    }

    @Test
    fun `an absurdly distant past is not plausible`() {
        assertFalse(isPlausibleBirthDate(LocalDate.of(1800, 1, 1), today))
    }

    @Test
    fun `a realistic old date is plausible`() {
        assertTrue(isPlausibleBirthDate(LocalDate.of(1940, 6, 3), today))
    }
}
