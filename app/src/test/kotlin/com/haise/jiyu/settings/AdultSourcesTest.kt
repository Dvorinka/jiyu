package com.haise.jiyu.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Přepínač "Je mi 18 a více" a viditelnost zdrojů pro dospělé.
 *
 * Uživatelský nález: přepínač byl zapnutý, a žádné zdroje pro dospělé se stejně neukázaly -
 * ani ty se smíšeným obsahem. Příčina: obrazovka psala jen `IS_ADULT`, kdežto seznam zdrojů
 * (SourceManager.observeAll) se řídí `SHOW_ADULT_SOURCES`. Zapojené to bylo jen jedním
 * směrem - vypnutí zdroje schovalo, zapnutí je neodemklo - přestože pod přepínačem stojí
 * "Odemyká zdroje s obsahem pro dospělé".
 */
class AdultSourcesTest {

    private fun repository() = SettingsRepository(FakeDataStore())

    @Test
    fun `adult sources are hidden until someone confirms their age`() = runTest {
        val settings = repository()

        assertFalse("po instalaci se zdroje pro dospělé nenabízejí", settings.showAdultSources.first())
        assertFalse(settings.isAdult.first())
    }

    @Test
    fun `confirming age really unlocks the sources, not just the flag`() = runTest {
        // JÁDRO NÁLEZU. Dřív se nastavil jen isAdult a seznam zdrojů se nehnul.
        val settings = repository()

        settings.setAdultConfirmed(true)

        assertTrue("věk potvrzený", settings.isAdult.first())
        assertTrue("a zdroje se musí i odemknout", settings.showAdultSources.first())
    }

    @Test
    fun `revoking age hides the sources again`() = runTest {
        val settings = repository()
        settings.setAdultConfirmed(true)

        settings.setAdultConfirmed(false)

        assertFalse(settings.isAdult.first())
        assertFalse("odvolání nesmí nechat zdroje viditelné", settings.showAdultSources.first())
    }

    @Test
    fun `an install that already confirmed age sees the sources without touching anything`() = runTest {
        // Přesně stav, který uživatel nahlásil: plnoletost potvrzená z dřívějška, viditelnost
        // nikdy výslovně nenastavená. Bez tohohle by mu oprava přepínače nepomohla, dokud by
        // na něj nesáhl - a on by pořád koukal na prázdno.
        val settings = repository()
        settings.setIsAdult(true) // jen stará cesta, SHOW_ADULT_SOURCES se nezapisuje

        assertTrue("odvozeno z potvrzeného věku", settings.showAdultSources.first())
    }

    @Test
    fun `an explicit choice to hide them beats the age flag`() = runTest {
        // Kdo si je vypnul sám, má to zapsané - a vlastní volba má přednost před odvozením.
        val settings = repository()
        settings.setIsAdult(true)
        settings.setShowAdultSources(false)

        assertFalse(settings.showAdultSources.first())
    }

    @Test
    fun `confirming age re-enables sources that were explicitly hidden before`() = runTest {
        // Jediný případ, kde NESTAČÍ odvození chybějící volby z věku: klíč tu zapsaný JE
        // (uživatel si zdroje sám vypnul), takže se odvození neuplatní. Kdyby přepínač
        // "Je mi 18 a více" nezapisoval viditelnost sám, byl by pro takového uživatele
        // znovu mrtvý - přesně ta chyba, která se tu opravuje, jen o krok dál.
        val settings = repository()
        settings.setShowAdultSources(false)

        settings.setAdultConfirmed(true)

        assertTrue("potvrzení věku musí zdroje odemknout i po dřívějším skrytí", settings.showAdultSources.first())
    }

    @Test
    fun `the sources switch can still be turned off on its own`() = runTest {
        // Jemnější ovládání zůstává: potvrzený věk a "chci je vidět" jsou dvě různé věci.
        val settings = repository()
        settings.setAdultConfirmed(true)

        settings.setShowAdultSources(false)

        assertTrue("věk zůstává potvrzený", settings.isAdult.first())
        assertFalse("ale zdroje jsou schované", settings.showAdultSources.first())
    }
}
