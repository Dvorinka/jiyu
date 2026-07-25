package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleRenderStyleTest {

    // ── snapBubbleBg ────────────────────────────────────────────────────────

    @Test
    fun `near white is snapped to pure white keeping alpha`() {
        val nearWhite = 0xFFF2F2F2.toInt()
        assertEquals(0xFFFFFFFF.toInt(), snapBubbleBg(nearWhite))
    }

    @Test
    fun `near black is snapped to pure black keeping alpha`() {
        val nearBlack = 0xFF0A0A0A.toInt()
        assertEquals(0xFF000000.toInt(), snapBubbleBg(nearBlack))
    }

    @Test
    fun `mid-tone colored bubble is not snapped`() {
        val orange = 0xFFE8A030.toInt()
        assertEquals(orange, snapBubbleBg(orange))
    }

    @Test
    fun `alpha channel is preserved when snapping`() {
        val translucentNearWhite = 0x80F5F5F5.toInt()
        assertEquals(0x80FFFFFF.toInt(), snapBubbleBg(translucentNearWhite))
    }

    // ── matchOriginalCase ───────────────────────────────────────────────────

    @Test
    fun `uppercase latin original uppercases the translation`() {
        assertEquals("PO ZAPLACENÍ VODY", matchOriginalCase("Po zaplacení vody", "AFTER PAYING THE WATER BILL"))
    }

    @Test
    fun `lowercase latin original keeps translation casing`() {
        assertEquals("Ahoj kámo", matchOriginalCase("Ahoj kámo", "hey there buddy"))
    }

    @Test
    fun `cjk original defaults to uppercase (comic lettering convention)`() {
        assertEquals("TO JE JASNÉ.", matchOriginalCase("To je jasné.", "내일 어둠 탐사"))
    }

    @Test
    fun `czech diacritics uppercase correctly`() {
        assertEquals("ŘÍKÁŠ ŽE?", matchOriginalCase("Říkáš že?", "YOU SAY?"))
    }

    @Test
    fun `soft hyphens in display text survive uppercasing`() {
        val withSoftHyphen = "roz­dělit"
        assertEquals("ROZ­DĚLIT", matchOriginalCase(withSoftHyphen, "SPLIT"))
    }
}
