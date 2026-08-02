package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slučování svisle sázených sloupců (japonština).
 *
 * Souřadnice vycházejí z toho, co ML Kit doopravdy vrátil na zařízení (viz
 * VerticalJapaneseOnDeviceTest): stránka 900x1200, sloupce široké ~50 px, sousední sloupce
 * jedné bubliny 26-31 px od sebe, dvě různé bubliny 350 px od sebe.
 */
class VerticalTextMergeTest {

    /** Sloupec podle skutečně naměřených pixelů, přepočtený na zlomky stránky 900x1200. */
    private fun column(text: String, leftPx: Int, topPx: Int, widthPx: Int = 50, heightPx: Int = 165) =
        RawTextBlock(
            text = text,
            leftF = leftPx / 900f,
            topF = topPx / 1200f,
            rightF = (leftPx + widthPx) / 900f,
            bottomF = (topPx + heightPx) / 1200f,
            isVertical = true,
        )

    @Test
    fun `two columns of the same bubble merge`() {
        val a = column("いそげ", leftPx = 220, topPx = 700)
        val b = column("はしれ", leftPx = 300, topPx = 700)

        assertTrue("sousedni sloupce jedne bubliny (mezera 30 px) se slucuji", shouldMerge(a, b))
    }

    @Test
    fun `columns of two different bubbles do not merge`() {
        // JÁDRO NÁLEZU: naměřeno na zařízení, že se slily. Vodorovné pravidlo porovnávalo
        // mezeru mezi sloupci s VÝŠKOU sloupce, a 1,8x výška sloupce je přes půl stránky.
        val a = column("はしれ", leftPx = 300, topPx = 700)
        val b = column("げんきですか", leftPx = 700, topPx = 140, heightPx = 356)

        assertFalse("bubliny 350 px od sebe se slucovat nesmi", shouldMerge(a, b))
    }

    @Test
    fun `a whole vertical page collapses to one block per bubble, not one for the page`() {
        val columns = listOf(
            column("こんにちは", leftPx = 780, topPx = 140, heightPx = 323),
            column("げんきですか", leftPx = 700, topPx = 140, heightPx = 356),
            column("はしれ", leftPx = 300, topPx = 700),
            column("いそげ", leftPx = 220, topPx = 700),
        )

        val merged = mergeNearbyLines(columns)

        assertEquals("dve bubliny, ne jedna placka pres celou stranku", 2, merged.size)
    }

    @Test
    fun `columns are joined right to left, the way Japanese is read`() {
        // Levý sloupec je v seznamu první, ale ve větě patří až za pravý.
        val merged = mergeNearbyLines(
            listOf(
                column("げんきですか", leftPx = 700, topPx = 140, heightPx = 356),
                column("こんにちは", leftPx = 780, topPx = 140, heightPx = 323),
            ),
        ).single()

        assertEquals("こんにちは げんきですか", merged.text)
    }

    @Test
    fun `a merged column block reports its width as the native line height`() {
        // Render z tohohle pole odvozuje velikost písma originálu. U sloupce ji určuje jeho
        // šířka - výška říká jen, kolik znaků v něm je, takže by z ní vyšel obří text.
        val merged = mergeNearbyLines(
            listOf(column("こんにちは", leftPx = 780, topPx = 140, widthPx = 51, heightPx = 323)),
        ).single()

        assertEquals(51f / 900f, merged.nativeLineHeightF, 0.001f)
    }

    @Test
    fun `horizontal lines keep the behaviour they always had`() {
        // Pojistka, ze se svislou vetvi nerozbila ta puvodni.
        val a = RawTextBlock(text = "HELLO", leftF = 0.10f, topF = 0.10f, rightF = 0.40f, bottomF = 0.14f)
        val b = RawTextBlock(text = "WORLD", leftF = 0.10f, topF = 0.15f, rightF = 0.40f, bottomF = 0.19f)

        assertTrue(shouldMerge(a, b))
        assertEquals("HELLO WORLD", mergeNearbyLines(listOf(a, b)).single().text)
    }

    @Test
    fun `a vertical column never merges with a horizontal line`() {
        val vertical = column("いそげ", leftPx = 220, topPx = 700)
        val horizontal = RawTextBlock(text = "GO", leftF = 0.25f, topF = 0.60f, rightF = 0.40f, bottomF = 0.64f)

        assertFalse("ruzne orientace nepatri do jedne bubliny", shouldMerge(vertical, horizontal))
    }

    @Test
    fun `the angle from ML Kit decides what counts as vertical`() {
        // Naměřeno na zařízení: svislé sloupce hlásily 89,9 až 90,4 stupně.
        assertTrue(isVerticalAngle(90f))
        assertTrue(isVerticalAngle(89.9f))
        assertTrue(isVerticalAngle(90.4f))
        assertTrue("opacny smer je tataz svislice", isVerticalAngle(270f))
        assertFalse("vodorovny radek", isVerticalAngle(0f))
        assertFalse("mirne pootoceny sken je porad vodorovny", isVerticalAngle(3f))
        assertFalse(isVerticalAngle(180f))
    }
}
