package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateAheadTest {

    private fun ch(number: Float, read: Boolean = false, id: String = "ch$number") =
        TranslatableChapter(id = id, number = number, read = read)

    @Test
    fun `picks the next unread chapters, not the newest ones`() {
        // JÁDRO: seznam kapitol na detailu je běžně otočený (nejnovější nahoře). Bez řazení
        // podle čísla by "5 kapitol dopředu" přeložilo pět NEJNOVĚJŠÍCH - přesný opak toho,
        // co chce čtenář, který se chystá pokračovat.
        val chapters = listOf(ch(30f), ch(29f), ch(28f), ch(27f), ch(26f))

        val picked = chaptersToTranslateAhead(chapters, count = 2)

        assertEquals(listOf("ch26.0", "ch27.0"), picked)
    }

    @Test
    fun `already read chapters are skipped`() {
        val chapters = listOf(
            ch(1f, read = true),
            ch(2f, read = true),
            ch(3f),
            ch(4f),
        )

        val picked = chaptersToTranslateAhead(chapters, count = 3)

        assertEquals("přečtené se přeskočí a víc jich není", listOf("ch3.0", "ch4.0"), picked)
    }

    @Test
    fun `asking for more chapters than exist returns what there is`() {
        val picked = chaptersToTranslateAhead(listOf(ch(1f), ch(2f)), count = 10)

        assertEquals(2, picked.size)
    }

    @Test
    fun `zero and negative counts translate nothing`() {
        val chapters = listOf(ch(1f), ch(2f))

        assertTrue(chaptersToTranslateAhead(chapters, count = 0).isEmpty())
        assertTrue(chaptersToTranslateAhead(chapters, count = -3).isEmpty())
    }

    @Test
    fun `a fully read manga queues nothing`() {
        val chapters = listOf(ch(1f, read = true), ch(2f, read = true))

        assertTrue(chaptersToTranslateAhead(chapters, count = 5).isEmpty())
    }

    @Test
    fun `decimal chapter numbers keep their place in the order`() {
        // Extra kapitoly typu 10.5 jsou u skenlací běžné a patří mezi 10 a 11.
        val chapters = listOf(ch(11f), ch(10.5f), ch(10f))

        val picked = chaptersToTranslateAhead(chapters, count = 3)

        assertEquals(listOf("ch10.0", "ch10.5", "ch11.0"), picked)
    }

    @Test
    fun `the order of the queue is the reading order`() {
        // Fronta je sekvenční (viz TranslateQueue), takže pořadí není kosmetika - určuje,
        // která kapitola bude hotová první. Musí to být ta, kterou uživatel otevře nejdřív.
        val picked = chaptersToTranslateAhead(listOf(ch(7f), ch(5f), ch(6f)), count = 3)

        assertEquals(listOf("ch5.0", "ch6.0", "ch7.0"), picked)
    }
}
