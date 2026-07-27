package com.haise.jiyu.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test [ChapterStorage.chapterFolderName] - žádná Android/Context závislost
 * (createChapterDir/writePage samotné potřebují Context/DocumentFile, netestováno tady,
 * stejná konvence jako zbytek projektu - jen čistá logika bez Android API).
 *
 * Reprodukuje uživatelský dotaz "stáhnu celou mangu, pošlu na PC, přečtu si to tam" -
 * dřív se kapitola pojmenovávala "sourceId::URL kapitoly" (nečitelné a na Windows
 * doslova nefunkční jméno souboru kvůli ':'/'/' v URL).
 */
class ChapterStorageTest {

    @Test
    fun `whole chapter number is zero-padded without decimal point`() {
        val name = ChapterStorage.chapterFolderName(12f, "Útok")
        assertEquals("0012 - Útok", name)
    }

    @Test
    fun `fractional chapter number keeps one decimal place`() {
        val name = ChapterStorage.chapterFolderName(10.5f, "Bonus")
        assertEquals("0010.5 - Bonus", name)
    }

    @Test
    fun `windows-forbidden characters in chapter name are replaced, not dropped`() {
        // Bez mezery navíc by se slova bez oddělovače kolem zakázaného znaku slepila.
        val name = ChapterStorage.chapterFolderName(1f, "Chapter: The Return/Home?")
        assertFalse("must not contain any Windows-forbidden filename character", name.any { it in "\\/:*?\"<>|" })
        assertTrue(name.contains("Chapter"))
        assertTrue(name.contains("Return"))
        assertTrue(name.contains("Home"))
    }

    @Test
    fun `blank chapter name falls back to just the number, no trailing separator`() {
        val name = ChapterStorage.chapterFolderName(3f, "   ")
        assertEquals("0003", name)
    }

    @Test
    fun `very long chapter name is truncated to a safe length`() {
        val longName = "a".repeat(500)
        val name = ChapterStorage.chapterFolderName(1f, longName)
        assertTrue("folder name must stay under a safe path-segment length", name.length < 150)
    }
}
