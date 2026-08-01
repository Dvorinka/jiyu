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
        val rows = (0 until 11).map { y ->
            (0 until 11).map { x -> if (x in 4..6) black else white }
        }
        val patch = buildTextPatch(sourceOf(rows), 11, 11, 0, 0, 11, 11, bgArgb = white)

        (4..6).forEach { x ->
            assertEquals("sloupec $x se má celý zaplnit pozadím", white, patch[5 * 11 + x])
        }
    }
}
