package com.haise.jiyu.translate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy výběru rozpoznávače pod zdrojovým jazykem "Auto".
 *
 * Proč zrovna tohle: "Auto" je v rozbalovátku zdrojového jazyka ve čtečce nabízené jako
 * první možnost, ale `recognizerFor` pro něj neměl větev a spadl do `else`, tedy na
 * LATINKOVÝ model. Kdo si vybral "Auto" a otevřel japonskou mangu, dostal z OCR nesmysl
 * nebo nic - autodetekce žádná nebyla, jen se tak appka tvářila.
 *
 * Rozpoznávání se sem předává jako lambda, takže se rozhodování dá otestovat úplně bez
 * ML Kitu i bez Bitmapy.
 */
class AutoLanguageDetectionTest {

    private fun blocks(text: String) = listOf(
        RawTextBlock(text = text, leftF = 0.1f, topF = 0.1f, rightF = 0.4f, bottomF = 0.2f),
    )

    /** Delší než AUTO_CONFIDENT_CHARS (20 nebílých znaků). */
    private val plentyOfText = "Tohle je opravdu dost dlouhy kus textu na strance"

    @Test
    fun `latin is used when the page really is latin`() = runTest {
        val (language, found) = resolveAutoLanguage { candidate ->
            if (candidate == "English") blocks(plentyOfText) else emptyList()
        }

        assertEquals("English", language)
        assertEquals(1, found.size)
    }

    @Test
    fun `a page the latin model cannot read falls through to japanese`() = runTest {
        // JÁDRO NÁLEZU: přesně tenhle případ dřív skončil u latinky a uživatel neviděl nic.
        val (language, found) = resolveAutoLanguage { candidate ->
            when (candidate) {
                "Japanese" -> blocks("これは日本語のテキストです、かなり長い行です")
                else -> emptyList()
            }
        }

        assertEquals("Japanese", language)
        assertTrue(found.isNotEmpty())
    }

    @Test
    fun `korean and chinese are reachable too, not just the first two`() = runTest {
        val korean = resolveAutoLanguage { c ->
            if (c == "Korean") blocks("이것은 한국어 텍스트입니다 상당히 긴 줄입니다") else emptyList()
        }
        val chinese = resolveAutoLanguage { c ->
            if (c == "Chinese") blocks("这是一段相当长的中文文本内容用于测试识别") else emptyList()
        }

        assertEquals("Korean", korean.first)
        assertEquals("Chinese", chinese.first)
    }

    @Test
    fun `a confident first hit stops the remaining passes`() = runTest {
        // Výkon: běžná (latinková) stránka nesmí platit čtyři průchody ML Kitem.
        val tried = mutableListOf<String>()
        resolveAutoLanguage { candidate ->
            tried += candidate
            if (candidate == "English") blocks(plentyOfText) else emptyList()
        }

        assertEquals("po jistém nálezu se další modely nemají spouštět", listOf("English"), tried)
    }

    @Test
    fun `a few garbage characters are not enough to stop the search`() = runTest {
        // Latinkový model puštěný na japonskou stránku typicky vrátí pár znaků nesmyslu.
        // Kdyby to stačilo, zůstalo by to na latince a zbytek stránky by se ztratil.
        val (language, _) = resolveAutoLanguage { candidate ->
            when (candidate) {
                "English" -> blocks("l'i.")
                "Japanese" -> blocks("これは日本語のテキストです、かなり長い行です")
                else -> emptyList()
            }
        }

        assertEquals("Japanese", language)
    }

    @Test
    fun `when nothing reads anything the result is empty instead of crashing`() = runTest {
        val (language, found) = resolveAutoLanguage { emptyList() }

        assertTrue(found.isEmpty())
        assertTrue("musí vrátit nějaký smysluplný jazyk", language in AUTO_CANDIDATE_LANGUAGES)
    }

    @Test
    fun `the model that found the most text wins when none is confident`() = runTest {
        val (language, _) = resolveAutoLanguage { candidate ->
            when (candidate) {
                "English" -> blocks("ab")
                "Japanese" -> blocks("abcd")
                "Korean" -> blocks("abcdef")
                else -> emptyList()
            }
        }

        assertEquals("Korean", language)
    }
}
