package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nahlášeno se srovnávací dvojicí snímků ze stránky Vagabonda: originál „WE'RE TAKING OFF."
 * se vykreslil jako „ODLÉTÁME" a pod tím OSAMOCENÁ TEČKA na vlastním řádku - uživateli to
 * čte jako „bublina přišla o text". Viz [tidyStrandedPunctuation].
 */
class StrayPunctuationTest {

    @Test
    fun `a space before the closing period is removed`() {
        // JÁDRO: mezera nabídne zalamovači zlom, „ODLÉTÁME" se na řádek vejde a „." už ne.
        assertEquals("ODLÉTÁME.", tidyStrandedPunctuation("ODLÉTÁME ."))
    }

    @Test
    fun `a line break before the closing period is removed`() {
        assertEquals("ODLÉTÁME.", tidyStrandedPunctuation("ODLÉTÁME\n."))
    }

    @Test
    fun `other closing punctuation is handled too`() {
        assertEquals("MŮŽEŠ CHODIT?", tidyStrandedPunctuation("MŮŽEŠ CHODIT ?"))
        assertEquals("PRO ZÁBAVU!", tidyStrandedPunctuation("PRO ZÁBAVU !"))
        assertEquals("TAKEZO…", tidyStrandedPunctuation("TAKEZO …"))
        assertEquals("JSEM HOTOVÝ...", tidyStrandedPunctuation("JSEM HOTOVÝ ..."))
    }

    @Test
    fun `a leading ellipsis stays attached to its own word`() {
        // POJISTKA proti přestřelení. Věta pokračující z předchozí bubliny („...KONČÍ") je běžná
        // komiksová sazba - slepit ji na předchozí slovo by změnilo zalomení, které tam autor
        // chtěl. Pravidlo proto sahá jen na interpunkci, za kterou už nic není.
        assertEquals("TAK\n...KONEC", tidyStrandedPunctuation("TAK\n...KONEC"))
        assertEquals("BITVA ...SKONČILA", tidyStrandedPunctuation("BITVA ...SKONČILA"))
    }

    @Test
    fun `normal text is left exactly as it is`() {
        assertEquals("NEMŮŽU UŽ CHODIT.", tidyStrandedPunctuation("NEMŮŽU UŽ CHODIT."))
        assertEquals("ANO, JISTĚ.", tidyStrandedPunctuation("ANO, JISTĚ."))
        assertEquals("", tidyStrandedPunctuation(""))
    }

    @Test
    fun `spacing inside the sentence is not touched`() {
        // Mezera před čárkou UPROSTŘED věty se nechává být: za ní text pokračuje, takže osamocený
        // řádek z ní vzniknout nemůže, a přepisovat uživateli text nad rámec nahlášené chyby nemá
        // proč.
        assertEquals("ANO , JISTĚ.", tidyStrandedPunctuation("ANO , JISTĚ."))
    }

    @Test
    fun `a block without a single letter carries nothing translatable`() {
        assertFalse(hasTranslatableLetters("."))
        assertFalse(hasTranslatableLetters("..."))
        assertFalse(hasTranslatableLetters("  !? "))
        assertFalse(hasTranslatableLetters(""))
    }

    @Test
    fun `a block with letters is kept`() {
        assertTrue(hasTranslatableLetters("A"))
        assertTrue(hasTranslatableLetters("...SKONČILA."))
        assertTrue(hasTranslatableLetters("ODLÉTÁME"))
    }
}
