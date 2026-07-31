package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Čistý JVM test [GeminiUltraPrompt.buildSystemPrompt] - žádná síťová/Android závislost. */
class GeminiUltraPromptTest {

    @Test
    fun `includes manga context when provided`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap(), mangaContext = "Název: \"Test Manga\" (manga), žánry: Akce")
        assertTrue(prompt.contains("Test Manga"))
        assertTrue(prompt.contains("Akce"))
    }

    @Test
    fun `falls back to placeholder when manga context is blank`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap(), mangaContext = "")
        assertTrue(prompt.contains("neznámé"))
    }

    @Test
    fun `size limits in prompt text always match the SizeTag enum, never hardcoded`() {
        // Regrese proti budoucímu rozjetí prompt-textu a skutečných hodnot v SizeTag - obojí
        // musí jít ze STEJNÉHO zdroje (viz interpolace v buildSystemPrompt).
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertTrue(prompt.contains("max ${SizeTag.TINY.maxChars} znaků"))
        assertTrue(prompt.contains("max ${SizeTag.SMALL.maxChars} znaků"))
        assertTrue(prompt.contains("max ${SizeTag.MEDIUM.maxChars} znaků"))
        assertTrue(prompt.contains("max ${SizeTag.LARGE.maxChars} znaků"))
        assertTrue(prompt.contains("max ${SizeTag.WIDE.maxChars} znaků"))
        assertTrue(prompt.contains("max ${SizeTag.TALL.maxChars} znaků"))
    }

    @Test
    fun `references the untranslated marker constant, not a duplicated literal`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertTrue(prompt.contains(GeminiUltraPrompt.UNTRANSLATED_MARKER))
    }

    @Test
    fun `glossary entries are included verbatim`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(mapOf("Gravity Magic" to "Magie tíže"))
        assertTrue(prompt.contains("\"Gravity Magic\" -> \"Magie tíže\""))
    }

    @Test
    fun `empty glossary does not crash and shows the empty-glossary note`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertFalse(prompt.isBlank())
        assertTrue(prompt.contains("žádné zatím uložené pojmy"))
    }

    @Test
    fun `warns against literal word-for-word translation of idioms`() {
        // Uzivatelska zpetna vazba: "coming all this way" prelozeno doslovne ("po tom, co jsme
        // se sem vydali") ztratilo duraz na delku/namahu cesty, ktery idiom nese.
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertTrue(prompt.contains("coming all this way"))
        assertTrue(prompt.contains("IDIOMY"))
    }

    @Test
    fun `warns against the non-standard reflexive verb combination`() {
        // Uzivatelska zpetna vazba: "zbloudit se" je negramaticke - "zbloudit" uz zvratnost
        // vyjadruje samo, pridane "se" mixuje dva vzory.
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertTrue(prompt.contains("zbloudit"))
        assertTrue(prompt.contains("ZVRATNÁ SLOVESA"))
    }

    @Test
    fun `parses new_terms from the response`() {
        val json = """
            {
              "bubbles": [
                {"id": 0, "original": "Hi Frodo", "translated": "Ahoj Frodo", "bubble_size_tag": "SMALL", "is_sfx": false, "syllable_breaks": "Ahoj Frodo"}
              ],
              "new_terms": [
                {"source": "Frodo", "target": "Frodo"},
                {"source": "Gravity Magic", "target": "Magie tíže"}
              ]
            }
        """.trimIndent()

        val response = GeminiUltraPrompt.parseResponse(json)

        assertEquals(2, response.newTerms.size)
        assertEquals(GlossarySuggestion("Frodo", "Frodo"), response.newTerms[0])
        assertEquals(GlossarySuggestion("Gravity Magic", "Magie tíže"), response.newTerms[1])
    }

    @Test
    fun `missing new_terms field parses as empty list, not a crash`() {
        val json = """{"bubbles": [{"id": 0, "original": "Hi", "translated": "Ahoj", "bubble_size_tag": "TINY", "is_sfx": false, "syllable_breaks": "Ahoj"}]}"""
        val response = GeminiUltraPrompt.parseResponse(json)
        assertTrue(response.newTerms.isEmpty())
    }

    @Test
    fun `new_terms entries with blank source or target are skipped`() {
        val json = """
            {
              "bubbles": [],
              "new_terms": [
                {"source": "", "target": "Something"},
                {"source": "Valid", "target": ""},
                {"source": "Frodo", "target": "Frodo"}
              ]
            }
        """.trimIndent()
        val response = GeminiUltraPrompt.parseResponse(json)
        assertEquals(1, response.newTerms.size)
        assertEquals("Frodo", response.newTerms[0].source)
    }
}
