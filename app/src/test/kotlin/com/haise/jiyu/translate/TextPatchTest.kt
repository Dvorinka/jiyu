package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy záplaty textu pod bublinou.
 *
 * Proč to vzniklo: když text leží přímo na kresbě (žádná bublina), appka dosud přes celý box
 * natáhla JEDNU navzorkovanou barvu. Na barevné nebo členité kresbě z toho vznikla placka -
 * odtud uživatelské stížnosti na "hnědou skvrnu přes titulní kresbu" a "černou skvrnu přes
 * obličej". Jedna barva prostě nemůže nahradit kus obrázku.
 *
 * Nově se zakryjí jen samotné tahy písmen a každý takový pixel se dopočítá z okolního pozadí,
 * takže kresba mezi písmeny přežije. Není to skutečný inpainting (ten potřebuje neuronový
 * model a server), ale proti jednolité placce je to úplně jiná úroveň - a je to zadarmo.
 */
class TextPatchTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val white = argb(255, 255, 255)
    private val black = argb(0, 0, 0)
    private val red = argb(220, 40, 40)
    private val blue = argb(40, 60, 220)

    /** Zdroj z 2D pole - [0][0] je levý horní roh. */
    private fun sourceOf(rows: List<List<Int>>) = PixelSource { x, y ->
        rows.getOrNull(y)?.getOrNull(x) ?: white
    }

    private fun grid(w: Int, h: Int, fill: Int, marks: Map<Pair<Int, Int>, Int> = emptyMap()) =
        (0 until h).map { y -> (0 until w).map { x -> marks[x to y] ?: fill } }

    @Test
    fun `text strokes are replaced by the surrounding background`() {
        // Bílé pozadí s jedním černým "tahem" uprostřed.
        val rows = grid(9, 9, white, mapOf((4 to 4) to black))
        val patch = buildTextPatch(sourceOf(rows), 9, 9, 0, 0, 9, 9, bgArgb = white)

        assertEquals("černý pixel se má nahradit pozadím", white, patch[4 * 9 + 4])
    }

    @Test
    fun `the background itself is left untouched`() {
        val rows = grid(9, 9, white, mapOf((4 to 4) to black))
        val patch = buildTextPatch(sourceOf(rows), 9, 9, 0, 0, 9, 9, bgArgb = white)

        assertEquals(white, patch[0])
        assertEquals(white, patch[8 * 9 + 8])
    }

    @Test
    fun `a colour gradient in the art survives instead of being flattened`() {
        // JÁDRO NÁLEZU: levá půlka červená, pravá modrá, uprostřed černý text. Stará cesta
        // přebarvila celý box jednou "průměrnou" barvou - fialovou, která na obrázku vůbec
        // není. Záplata musí barvy po stranách nechat být.
        val rows = (0 until 9).map { y ->
            (0 until 9).map { x ->
                when {
                    x == 4 && y in 3..5 -> black
                    x < 4 -> red
                    else -> blue
                }
            }
        }
        val patch = buildTextPatch(sourceOf(rows), 9, 9, 0, 0, 9, 9, bgArgb = red)

        assertEquals("červená strana zůstává červená", red, patch[4 * 9 + 0])
        assertEquals("modrá strana zůstává modrá", blue, patch[4 * 9 + 8])
        assertNotEquals("zakrytý pixel nesmí zůstat černý", black, patch[4 * 9 + 4])
    }

    @Test
    fun `a patched pixel takes a colour that actually occurs nearby`() {
        val rows = (0 until 9).map { y ->
            (0 until 9).map { x -> if (x == 4 && y in 3..5) black else red }
        }
        val patch = buildTextPatch(sourceOf(rows), 9, 9, 0, 0, 9, 9, bgArgb = red)

        assertEquals("uprostřed červené plochy musí vzniknout červená", red, patch[4 * 9 + 4])
    }

    @Test
    fun `a box that is entirely text still produces something instead of failing`() {
        // Degenerovaný případ - nemá se z čeho dopočítat. Nesmí spadnout ani vrátit prázdno.
        val rows = grid(6, 6, black)
        val patch = buildTextPatch(sourceOf(rows), 6, 6, 0, 0, 6, 6, bgArgb = white)

        assertEquals(36, patch.size)
        assertTrue("všechny pixely musí být neprůhledné", patch.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `only the requested region is returned`() {
        val rows = grid(20, 20, white)
        val patch = buildTextPatch(sourceOf(rows), 20, 20, 5, 5, 12, 10, bgArgb = white)

        assertEquals("šířka 7 × výška 5", 7 * 5, patch.size)
    }

    @Test
    fun `a region reaching outside the image is clamped, not crashing`() {
        val rows = grid(10, 10, white)
        val patch = buildTextPatch(sourceOf(rows), 10, 10, -4, -4, 14, 14, bgArgb = white)

        assertEquals(10 * 10, patch.size)
    }

    @Test
    fun `an empty region yields an empty result`() {
        val rows = grid(10, 10, white)
        assertEquals(0, buildTextPatch(sourceOf(rows), 10, 10, 5, 5, 5, 5, bgArgb = white).size)
    }

    @Test
    fun `thick strokes are covered too, not just their edges`() {
        // Tah široký 3 px - kdyby se doplňovalo jen z přímých sousedů jednou, zůstal by
        // uprostřed neopravený proužek.
        val rows = (0 until 11).map {
            (0 until 11).map { x -> if (x in 4..6) black else white }
        }
        val patch = buildTextPatch(sourceOf(rows), 11, 11, 0, 0, 11, 11, bgArgb = white)

        (4..6).forEach { x ->
            assertEquals("sloupec $x se má celý zaplnit pozadím", white, patch[5 * 11 + x])
        }
    }

    // -- Textová oblast: záplata je větší než OCR box, prahovat se smí jen uvnitř ----------

    @Test
    fun `art outside the text region is copied through untouched`() {
        // Záplata pokrývá celý box, přes který se bublina kreslí, a ten je větší než OCR box
        // (viz patchPlan). Kresba mimo text nesmí projít prahováním ani dopočítáváním - jinak
        // by se rozmazala tam, kde žádné písmo nikdy nebylo.
        val rows = (0 until 15).map { y ->
            (0 until 15).map { x ->
                when {
                    x in 6..8 && y in 6..8 -> black // "písmeno" uvnitř textové oblasti
                    x == 1 && y in 2..12 -> black // detail kresby daleko od textu
                    else -> white
                }
            }
        }
        val patch = buildTextPatch(
            sourceOf(rows), 15, 15, 0, 0, 15, 15, bgArgb = white,
            textLeft = 5, textTop = 5, textRight = 10, textBottom = 10,
        )

        assertEquals("tah uvnitř textové oblasti se zakryje", white, patch[7 * 15 + 7])
        assertEquals("kresba mimo textovou oblast zůstává", black, patch[7 * 15 + 1])
    }

    @Test
    fun `without a text region the whole patch is thresholded as before`() {
        // Zpětná kompatibilita - volání bez textové oblasti se chová přesně jako dřív.
        val rows = (0 until 15).map { y ->
            (0 until 15).map { x -> if (x == 1 && y in 2..12) black else white }
        }
        val patch = buildTextPatch(sourceOf(rows), 15, 15, 0, 0, 15, 15, bgArgb = white)

        assertNotEquals("bez omezení se tah zakryje kdekoliv", black, patch[7 * 15 + 1])
    }

    @Test
    fun `the text region is read in image coordinates, not patch coordinates`() {
        // Záplata začíná na (4,4) obrázku, textová oblast je zadaná v souřadnicích OBRÁZKU -
        // kdyby se braly jako souřadnice záplaty, posunula by se o celý offset a maska by
        // dopadla na jiné písmeno.
        val rows = (0 until 20).map { y ->
            (0 until 20).map { x -> if (x in 12..13 && y in 12..13) black else white }
        }
        val patch = buildTextPatch(
            sourceOf(rows), 20, 20, 4, 4, 20, 20, bgArgb = white,
            textLeft = 11, textTop = 11, textRight = 15, textBottom = 15,
        )

        val w = 16
        assertEquals("tah na (12,12) obrázku leží v záplatě na (8,8)", white, patch[8 * w + 8])
    }
}
