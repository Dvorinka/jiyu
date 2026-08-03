package com.haise.jiyu.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jednolitost pozadí rozhoduje, jestli se originál zakryje plnou výplní jednou barvou, nebo
 * jen záplatou, která nechá kresbu prosvítat (viz [isBackgroundUniform]).
 *
 * Špatný verdikt stojí v každém směru něco jiného, a NENÍ to symetrické:
 *  - "kresba" u obyčejné bubliny => záplata, ta nedočistí všechno => drobný zbytek originálu,
 *  - "jednolité pozadí" u kresby => plná výplň => PLACKA přes obrázek.
 *
 * To druhé je nesrovnatelně horší a uživatel to tak i nahlásil. Testy tady proto hlídají
 * hlavně směr "kresba se nesmí splést s pozadím".
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
    fun `a gentle shade inside a bubble stays uniform`() {
        // Bubliny bývají lehce stínované - to je pořád jedna výplň, ne kresba.
        val shaded = (0 until 80).map { i -> gray(230 + i / 4) }
        assertTrue(isBackgroundUniform(shaded))
    }

    @Test
    fun `real artwork is still recognised as non-uniform`() {
        val artwork = List(40) { rgb(90, 120, 190) } + List(40) { rgb(70, 110, 80) }
        assertFalse("modré nebe a zelená pláň není jedno pozadí", isBackgroundUniform(artwork))
    }

    @Test
    fun `mostly even artwork with a dark minority is not uniform`() {
        // REGRESE, kterou uživatel nahlásil na vodovkové bitevní scéně z Vagabonda: popisek
        // "BITVA U SEKIGAHARY" dostal plnou výplň a přes kresbu se rozlila modrá placka.
        //
        // Takhle ta scéna vypadá čísly: většina prstence je jedna dost vyrovnaná modř a mimo
        // toleranci je jen MENŠINA vzorků - kmen stromu a tmavý terén pod popiskem. Dokud
        // podmínka brala 85. percentil, menšina se do rozpočtu vešla a scéna prošla jako
        // "jednolité pozadí".
        val evenBlue = (0 until 68).map { i -> rgb(96 + i / 8, 118 + i / 8, 172 + i / 8) }
        val darkTerrain = List(12) { rgb(28, 34, 62) }
        assertFalse("kresba s tmavým detailem není bublina", isBackgroundUniform(evenBlue + darkTerrain))
    }

    @Test
    fun `a background that is mostly noise is not uniform`() {
        val noisy = whiteRing(30) + List(50) { gray(20) }
        assertFalse(isBackgroundUniform(noisy))
    }

    @Test
    fun `a single sample far outside the tolerance is enough to reject`() {
        // ZÁMĚRNÁ přísnost, ne přehlédnutí. Odlehlý vzorek vzniká dvěma způsoby, které od sebe
        // nejdou ze vzorků odlišit: (a) prstenec sáhl na tah písmene, (b) v kresbě je tmavý
        // detail. Kdo odfiltruje (a), odfiltruje i (b) - a tím se vrací placka přes obrázek.
        //
        // Cena je známá: v bublině může pod překladem zůstat drobný zbytek originálu. Řešit se
        // to musí tím, KDE se prstenec vzorkuje, ne tolerancí. Viz [isBackgroundUniform].
        assertFalse(isBackgroundUniform(whiteRing(79) + listOf(gray(0))))
    }

    @Test
    fun `noise within the tolerance never rejects`() {
        // Protipól přísnosti: papírové zrno a JPEG artefakty se do tolerance vejít MUSÍ,
        // jinak by záplatu dostala každá bublina na skenované stránce.
        val grainy = (0 until 80).map { i -> gray(216 + (i * 7) % 40) }
        assertTrue(isBackgroundUniform(grainy))
    }

    @Test
    fun `too few samples are treated as uniform`() {
        // Z jednoho vzorku se nedá nic rozhodnout - výplň je bezpečnější výchozí stav než
        // záplata počítaná z ničeho.
        assertTrue(isBackgroundUniform(listOf(gray(255))))
    }
}
