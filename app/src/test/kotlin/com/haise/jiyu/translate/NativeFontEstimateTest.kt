package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy odhadu původní velikosti písma z výšky OCR boxu.
 *
 * Proč: velikost písma překladu je zastropovaná velikostí písma v originálu. Ta se odvozovala
 * tak, že se výška OCR boxu vydělila 1.25 - jako by šlo o řádkovou rozteč. OCR box ale obepíná
 * jen samotná písmena, takže výsledek vycházel výrazně menší než skutečnost a text zůstával
 * malý i v obří bublině (viz uživatelská zpětná vazba).
 *
 * Poměry jsou ZMĚŘENÉ na zařízení, ne odhadnuté z typografie - viz NativeFontSizeOnDeviceTest:
 *   verzálky            box/font = 0.725, 0.733
 *   smíšené s dotahy    box/font = 1.050
 * Starý přepočet dával 0.62 násobek skutečné velikosti.
 */
class NativeFontEstimateTest {

    @Test
    fun `all-caps text is estimated from cap height`() {
        // 44px box pri verzalkach odpovida pismu ~60px (mereno).
        val font = estimateNativeFontPx(boxHeightPx = 44f, originalText = "PROBOHA,")
        assertEquals(60f, font, 6f)
    }

    @Test
    fun `mixed case text is estimated from the full ascender to descender span`() {
        // 63px box u smiseneho textu s dotahy odpovida pismu ~60px (mereno).
        val font = estimateNativeFontPx(boxHeightPx = 63f, originalText = "Ztratit se py")
        assertEquals(60f, font, 6f)
    }

    @Test
    fun `the estimate is always bigger than the box for all-caps`() {
        // Kdyby vyslo mensi, znamenalo by to, ze se text vykresli drobnejsi nez original.
        assertTrue(estimateNativeFontPx(50f, "VELKA PISMENA") > 50f)
    }

    @Test
    fun `the new estimate is markedly bigger than the old formula`() {
        // Stary prepocet: box / 1.25. Tohle je jadro nahlasene chyby.
        val box = 44f
        val old = box / 1.25f
        val new = estimateNativeFontPx(box, "PROBOHA,")
        assertTrue("novy odhad ($new) ma byt vyrazne vetsi nez stary ($old)", new > old * 1.5f)
    }

    @Test
    fun `text with digits and punctuation only is treated as all-caps`() {
        // Zadna mala pismena -> chova se jako verzalky, ne jako smisene.
        assertEquals(
            estimateNativeFontPx(44f, "PROBOHA"),
            estimateNativeFontPx(44f, "1234!?"),
            0.01f,
        )
    }

    @Test
    fun `a nonsensical box height never yields a negative or zero font`() {
        assertTrue(estimateNativeFontPx(0f, "COKOLIV") > 0f)
        assertTrue(estimateNativeFontPx(-5f, "COKOLIV") > 0f)
    }

    @Test
    fun `cjk text without latin case is treated as all-caps`() {
        // Japonstina nema velka a mala pismena - box tam odpovida cele vysce znaku.
        assertTrue(estimateNativeFontPx(44f, "ここにいる") > 44f)
    }
}
