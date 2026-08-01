package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy rozdělení jedné dávkové odpovědi zpátky po stránkách.
 *
 * Proč zrovna tohle: `translateChapter` posílá bubliny z několika stránek jako JEDEN plochý
 * seznam a podle počtu bublin na stránku je pak rozděluje zpět. Je to přesně to místo, kde
 * se překlad může tiše ztratit nebo - mnohem hůř - přesunout k cizí bublině. Přesně tenhle
 * druh chyby už appka jednou měla (posunuté číslování v odpovědi modelu, viz PIPELINE_VERSION
 * v4). Logika přitom byla schovaná uvnitř dlouhé suspend funkce se sítí, OCR i databází,
 * takže se nedala otestovat jinak než překladem celé kapitoly.
 */
class SplitBlocksByPageTest {

    private fun block(text: String) = TranslatedBlock(
        originalText = text, translatedText = text,
        leftF = 0f, topF = 0f, rightF = 1f, bottomF = 1f,
    )

    private fun texts(blocks: List<TranslatedBlock>) = blocks.map { it.translatedText }

    @Test
    fun `each page gets exactly the blocks it sent bubbles for`() {
        val blocks = listOf(block("a1"), block("a2"), block("b1"), block("c1"), block("c2"), block("c3"))

        val result = splitBlocksByPage(listOf(4, 5, 6), listOf(2, 1, 3), blocks)

        assertEquals(listOf(4, 5, 6), result.map { it.first })
        assertEquals(listOf("a1", "a2"), texts(result[0].second))
        assertEquals(listOf("b1"), texts(result[1].second))
        assertEquals(listOf("c1", "c2", "c3"), texts(result[2].second))
    }

    @Test
    fun `a page that sent no bubbles gets nothing and does not shift the others`() {
        val blocks = listOf(block("a1"), block("b1"))

        val result = splitBlocksByPage(listOf(0, 1, 2), listOf(1, 0, 1), blocks)

        assertEquals(listOf("a1"), texts(result[0].second))
        assertTrue(result[1].second.isEmpty())
        assertEquals("prazdna stranka nesmi posunout nasledujici", listOf("b1"), texts(result[2].second))
    }

    @Test
    fun `a short answer leaves the tail pages empty instead of crashing`() {
        // Model vratil min bloku, nez dostal bublin. Driv by `subList(offset, ...)` s offsetem
        // za koncem seznamu vyhodilo IndexOutOfBounds a shodilo preklad cele kapitoly.
        val blocks = listOf(block("a1"), block("a2"))

        val result = splitBlocksByPage(listOf(0, 1, 2), listOf(2, 2, 2), blocks)

        assertEquals(listOf("a1", "a2"), texts(result[0].second))
        assertTrue("na druhou stranku uz nic nezbylo", result[1].second.isEmpty())
        assertTrue("ani na treti", result[2].second.isEmpty())
    }

    @Test
    fun `a partially covered page keeps the blocks that did arrive`() {
        val blocks = listOf(block("a1"), block("a2"), block("b1"))

        val result = splitBlocksByPage(listOf(0, 1), listOf(2, 3), blocks)

        assertEquals(listOf("a1", "a2"), texts(result[0].second))
        assertEquals(listOf("b1"), texts(result[1].second))
    }

    @Test
    fun `an empty answer yields empty pages, never a wrong assignment`() {
        val result = splitBlocksByPage(listOf(0, 1), listOf(2, 2), emptyList())

        assertTrue(result.all { it.second.isEmpty() })
        assertEquals(2, result.size)
    }

    @Test
    fun `every block ends up on exactly one page`() {
        // Nejdulezitejsi vlastnost: zadny blok se nesmi ztratit ani zdvojit.
        val blocks = (1..9).map { block("b$it") }

        val result = splitBlocksByPage(listOf(0, 1, 2, 3), listOf(3, 1, 4, 1), blocks)

        assertEquals(blocks.map { it.translatedText }, result.flatMap { texts(it.second) })
    }
}
