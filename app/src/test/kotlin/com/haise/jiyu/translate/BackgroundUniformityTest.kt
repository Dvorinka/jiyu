package com.haise.jiyu.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jednolitost pozadí rozhoduje, jestli se originál zakryje plnou výplní, nebo jen záplatou
 * (viz [isBackgroundUniform]). Špatný verdikt "pestrá kresba" u obyčejné bílé bubliny znamená
 * záplatu, a ta z principu nedočistí všechno - přesně tak vypadaly nahlášené snímky, kde pod
 * přeloženým textem zůstávaly zbytky originálu.
 */
class BackgroundUniformityTest {

    private fun gray(v: Int) = intArrayOf(v, v, v)
    private fun rgb(r: Int, g: Int, b: Int) = intArrayOf(r, g, b)

    /** Prstenec kolem textu v bílé bublině - 80 vzorků, jako je jich ve skutečnosti. */
    private fun whiteRing(count: Int = 80) = List(count) { gray(255) }

    @Test
    fun `a clean white bubble is uniform`() {
        assertTrue(isBackgroundUniform(whiteRing()))
    }

    @Test
    fun `a few samples that landed on a letter stroke do not flip the verdict`() {
        // JÁDRO NÁLEZU (změřeno sondou na zařízení, viz PunctuationBlockProbeTest):
        // "SURVIVOR..." uprostřed čistě bílé bubliny vyšlo jako bgUniform=false. Prstenec se
        // vzorkuje jen pár pixelů od OCR boxu a ten občas kraj písmene ořízne, takže pár vzorků
        // padne rovnou na černý tah. Stará podmínka brala NEJVĚTŠÍ odchylku, takže stačil
        // jediný takový vzorek.
        val ring = whiteRing(72) + List(8) { gray(0) }
        assertTrue("osm černých vzorků z osmdesáti je písmeno, ne kresba", isBackgroundUniform(ring))
    }

    @Test
    fun `a single stray sample never decides`() {
        assertTrue(isBackgroundUniform(whiteRing(79) + listOf(gray(0))))
    }

    @Test
    fun `real artwork is still recognised as non-uniform`() {
        // Pojistka proti přestřelení: text vysázený do kresby MUSÍ dál dostat záplatu, jinak
        // se vrátí ta jednobarevná placka přes obrázek, kvůli které záplata vznikla.
        val artwork = List(40) { rgb(90, 120, 190) } + List(40) { rgb(70, 110, 80) }
        assertFalse("modré nebe a zelená pláň není jedno pozadí", isBackgroundUniform(artwork))
    }

    @Test
    fun `a gentle shade inside a bubble stays uniform`() {
        // Bubliny bývají lehce stínované - to je pořád jedna výplň, ne kresba.
        val shaded = (0 until 80).map { i -> gray(230 + i / 4) }
        assertTrue(isBackgroundUniform(shaded))
    }

    @Test
    fun `a background that is mostly noise is not uniform`() {
        // Přesný protipól k testu s osmi vzorky: když je mimo VĚTŠINA, je to kresba.
        val noisy = whiteRing(30) + List(50) { gray(20) }
        assertFalse(isBackgroundUniform(noisy))
    }

    @Test
    fun `the tolerance sits between the two reported cases`() {
        // Hranice se hledala měřením, ne odhadem: 15 % vzorků je rozpočet na jednu zasaženou
        // stranu prstence (ten má ~80 vzorků na čtyřech stranách). Test drží obě strany hranice.
        assertTrue("12 z 80 (15 %) je pořád bublina", isBackgroundUniform(whiteRing(68) + List(12) { gray(0) }))
        assertFalse("24 z 80 (30 %) už je kresba", isBackgroundUniform(whiteRing(56) + List(24) { gray(0) }))
    }

    @Test
    fun `too few samples fall back to the old strict behaviour`() {
        // Z hrstky vzorků se nedá nic "odlehlého" spolehlivě vyloučit - tam se percentil
        // schválně chová jako dřívější maximum.
        assertFalse(isBackgroundUniform(listOf(gray(255), gray(0))))
    }
}
