package com.haise.jiyu.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy brány, kterou musí projít termín, než se sám uloží do glosáře.
 *
 * Do teď se ukládalo všechno, co model vrátil. Glosář je přitom v promptu závazný, takže jeden
 * nesmyslný záznam si model vnucuje ve všech dalších kapitolách - nahlášeno jako
 * `SHUT YOUR MOUTH...` -> `ZAVŘI PÁNU...`.
 */
class GlossaryFilterTest {

    @Test
    fun `a character name is accepted`() {
        assertTrue(isPlausibleGlossaryTerm("Frodo", "Frodo"))
        assertTrue(isPlausibleGlossaryTerm("Sung Jinwoo", "Sung Jinwoo"))
    }

    @Test
    fun `a named place or technique is accepted`() {
        assertTrue(isPlausibleGlossaryTerm("Shadow Monarch", "Vládce stínů"))
        assertTrue(isPlausibleGlossaryTerm("House of the Red Moon", "Dům rudého měsíce"))
    }

    @Test
    fun `an ordinary word is rejected`() {
        // JADRO NAHLASENE CHYBY: "mouth" neni jmeno a v glosari jmen nema co delat.
        assertFalse(isPlausibleGlossaryTerm("mouth", "pán"))
        assertFalse(isPlausibleGlossaryTerm("MOUTH", "pán"))
        assertFalse(isPlausibleGlossaryTerm("count", "spojovat"))
        assertFalse(isPlausibleGlossaryTerm("beast", "bestie"))
    }

    @Test
    fun `a whole sentence is rejected`() {
        assertFalse(
            isPlausibleGlossaryTerm(
                "Shut your mouth before I tear you apart",
                "Drž hubu, nebo tě rozsápu",
            )
        )
    }

    @Test
    fun `a target that ends like a sentence is rejected`() {
        // Termin neni veta - koncova interpunkce znamena, ze model ulozil kus prekladu.
        assertFalse(isPlausibleGlossaryTerm("Frodo", "Frodo."))
        assertFalse(isPlausibleGlossaryTerm("Frodo", "Frodo!"))
    }

    @Test
    fun `empty or too short input is rejected`() {
        assertFalse(isPlausibleGlossaryTerm("", "Frodo"))
        assertFalse(isPlausibleGlossaryTerm("F", "Frodo"))
        assertFalse(isPlausibleGlossaryTerm("Frodo", ""))
        assertFalse(isPlausibleGlossaryTerm("Frodo", "   "))
    }

    @Test
    fun `an absurdly long term is rejected`() {
        assertFalse(isPlausibleGlossaryTerm("x".repeat(60), "y"))
        assertFalse(isPlausibleGlossaryTerm("Frodo", "y".repeat(60)))
    }

    @Test
    fun `common words inside a longer name do not disqualify it`() {
        // "of" a "the" jsou bezna slova, ale nazev jako celek je legitimni.
        assertTrue(isPlausibleGlossaryTerm("Eye of the Storm", "Oko bouře"))
    }

    @Test
    fun `surrounding whitespace does not sneak a term through`() {
        assertFalse(isPlausibleGlossaryTerm("  mouth  ", "pán"))
    }
}
