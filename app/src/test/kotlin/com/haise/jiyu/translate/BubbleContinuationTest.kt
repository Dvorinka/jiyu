package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy rozpoznání vět, které pokračují z jedné bubliny do druhé.
 *
 * Proč to vzniklo: model dostával bubliny jako plochý seznam textů bez jakékoli informace o
 * tom, které spolu souvisí. Kaskádová replika rozdělená do dvou bublin ("GOOD HEAVENS," nahoře
 * a pokračování dole) se tak překládala po kouscích - každá půlka bez druhé, takže z první
 * vypadlo citoslovce nebo se druhá přeložila jako samostatná věta.
 *
 * Návaznost se počítá TADY, v kódu, a modelu se předává jako fakt. Spoléhat na to, že si ji
 * odvodí sám z pořadí, nestačí: v jedné dávce jde i několik stránek najednou, takže sousedící
 * položky seznamu spolu vůbec nemusí souviset.
 */
class BubbleContinuationTest {

    private fun bubble(
        text: String,
        leftF: Float = 0.1f,
        topF: Float = 0.1f,
        rightF: Float = 0.5f,
        bottomF: Float = 0.2f,
        isSfx: Boolean = false,
    ) = ClassifiedBubble(
        raw = RawTextBlock(text = text, leftF = leftF, topF = topF, rightF = rightF, bottomF = bottomF),
        sizeTag = SizeTag.SMALL,
        bubbleType = if (isSfx) BubbleType.SFX else BubbleType.SPEECH,
        isSfx = isSfx,
        lineCount = 1,
    )

    @Test
    fun `a comma at the end marks the next bubble as a continuation`() {
        // Presne nahlaseny pripad: uvodni citoslovce nahore, zbytek vety dole.
        val bubbles = listOf(
            bubble("GOOD HEAVENS,", topF = 0.10f, bottomF = 0.18f),
            bubble("TO GET LOST AFTER COMING ALL THIS WAY", topF = 0.20f, bottomF = 0.30f),
        )
        assertEquals(setOf(1), detectContinuations(bubbles))
    }

    @Test
    fun `a finished sentence does not continue into the next bubble`() {
        val bubbles = listOf(
            bubble("I FOUND IT.", topF = 0.10f, bottomF = 0.18f),
            bubble("WHERE ARE YOU GOING?", topF = 0.20f, bottomF = 0.30f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }

    @Test
    fun `an unfinished sentence without punctuation also continues`() {
        val bubbles = listOf(
            bubble("IF WE DON'T HURRY", topF = 0.10f, bottomF = 0.18f),
            bubble("WE WILL NEVER MAKE IT", topF = 0.20f, bottomF = 0.30f),
        )
        assertEquals(setOf(1), detectContinuations(bubbles))
    }

    @Test
    fun `trailing dots continue as well`() {
        val bubbles = listOf(
            bubble("I THOUGHT...", topF = 0.10f, bottomF = 0.18f),
            bubble("...YOU WERE GONE", topF = 0.20f, bottomF = 0.30f),
        )
        assertEquals(setOf(1), detectContinuations(bubbles))
    }

    @Test
    fun `bubbles far apart are not linked even with a trailing comma`() {
        // Dole na strance u uplne jineho panelu - geometricky to spolu nesouvisi.
        val bubbles = listOf(
            bubble("WELL,", topF = 0.05f, bottomF = 0.12f),
            bubble("ANOTHER PANEL ENTIRELY", topF = 0.80f, bottomF = 0.90f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }

    @Test
    fun `bubbles side by side are not a continuation`() {
        // Vedle sebe, ne pod sebou - typicky dva ruzni mluvci.
        val bubbles = listOf(
            bubble("HEY,", leftF = 0.05f, rightF = 0.30f, topF = 0.10f, bottomF = 0.20f),
            bubble("WHAT NOW", leftF = 0.65f, rightF = 0.95f, topF = 0.11f, bottomF = 0.21f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }

    @Test
    fun `sfx never takes part in a continuation`() {
        val bubbles = listOf(
            bubble("WAIT,", topF = 0.10f, bottomF = 0.18f),
            bubble("BOOM", topF = 0.20f, bottomF = 0.30f, isSfx = true),
            bubble("I SAID STOP", topF = 0.32f, bottomF = 0.42f),
        )
        val result = detectContinuations(bubbles)
        assertTrue("zvuk nesmi byt oznacen jako pokracovani", 1 !in result)
    }

    @Test
    fun `a chain of three bubbles links each to the previous one`() {
        val bubbles = listOf(
            bubble("FIRST,", topF = 0.10f, bottomF = 0.16f),
            bubble("THEN,", topF = 0.18f, bottomF = 0.24f),
            bubble("AND FINALLY.", topF = 0.26f, bottomF = 0.32f),
        )
        assertEquals(setOf(1, 2), detectContinuations(bubbles))
    }

    @Test
    fun `japanese sentence-final punctuation ends the sentence`() {
        val bubbles = listOf(
            bubble("行こう。", topF = 0.10f, bottomF = 0.18f),
            bubble("どこへ", topF = 0.20f, bottomF = 0.30f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }

    @Test
    fun `the very first bubble is never a continuation`() {
        assertTrue(detectContinuations(listOf(bubble("ANYTHING,"))).isEmpty())
    }

    // -- Kaskadova ("snehulakova") bublina - laloky posunute do stran ----------------

    /**
     * Souradnice zmerene na SKUTECNE nahlasene strance (1440x3120), ne odhadnute: horni lalok
     * x=0.321..0.540 y=0.572..0.627, spodni x=0.501..0.815 y=0.664..0.780. Vodorovny prekryv
     * vyjde 0,178 uzsiho z nich - puvodni prah 0,35 tedy neprosel a model se o navaznosti
     * nedozvedel, takze obe pulky jedne vety prelozil oddelene.
     */
    @Test
    fun `offset lobes of a cascading balloon still count as one utterance`() {
        val bubbles = listOf(
            bubble("IF THOSE MERCHANTS ARE NOT LYING,", leftF = 0.321f, rightF = 0.540f, topF = 0.572f, bottomF = 0.627f),
            bubble("DOES THAT NOT MEAN THE TERRAIN CHANGED?", leftF = 0.501f, rightF = 0.815f, topF = 0.664f, bottomF = 0.780f),
        )
        assertEquals(setOf(1), detectContinuations(bubbles))
    }

    @Test
    fun `a bubble with no horizontal overlap at all is still rejected`() {
        // Uvolneni prahu nesmi spojit repliky ze dvou ruznych sloupcu dialogu.
        val bubbles = listOf(
            bubble("WAIT,", leftF = 0.05f, rightF = 0.35f, topF = 0.10f, bottomF = 0.18f),
            bubble("WHO SAID THAT", leftF = 0.60f, rightF = 0.95f, topF = 0.20f, bottomF = 0.28f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }

    @Test
    fun `a barely touching pair below the threshold is still rejected`() {
        // Prekryv 0,02 z uzsi bubliny sirky 0,30 = 0,067 - pod prahem, porad se nespojuje.
        val bubbles = listOf(
            bubble("HMM,", leftF = 0.10f, rightF = 0.40f, topF = 0.10f, bottomF = 0.18f),
            bubble("NEVER MIND", leftF = 0.38f, rightF = 0.90f, topF = 0.20f, bottomF = 0.28f),
        )
        assertTrue(detectContinuations(bubbles).isEmpty())
    }
}
