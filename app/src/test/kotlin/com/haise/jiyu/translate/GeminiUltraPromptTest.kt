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
    fun `the untranslated marker is reserved for unreadable text, never for a short fragment`() {
        // Uzivatelska zpetna vazba: horni lalok kaskadove bubliny ("...SAY,") zustal anglicky.
        // Prompt si protirecil - sekce o vetach pres vic bublin zakazuje nechat bublinu
        // prazdnou, ale sekce CHYBY uvadela "utrzek" jako duvod pro marker. A horni lalok
        // JE utrzek, takze model poslusne vratil marker a appka bublinu vubec nevykreslila.
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertFalse(
            "utrzek nesmi byt uvedeny jako duvod pro ${GeminiUltraPrompt.UNTRANSLATED_MARKER}",
            prompt.contains("nečitelné OCR, útržek"),
        )
        assertTrue("marker musi byt vyhrazeny necitelnemu textu", prompt.contains("nedá PŘEČÍST"))
        assertTrue("prompt musi vyslovne zakazat marker u kratke bubliny", prompt.contains("NEVRACEJ"))
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

    // ── Věty rozdělené do víc bublin ────────────────────────────────────────────

    private fun bubble(
        text: String,
        topF: Float,
        bottomF: Float,
        leftF: Float = 0.1f,
        rightF: Float = 0.5f,
        isSfx: Boolean = false,
    ) = ClassifiedBubble(
        raw = RawTextBlock(text = text, leftF = leftF, topF = topF, rightF = rightF, bottomF = bottomF),
        sizeTag = SizeTag.SMALL,
        bubbleType = if (isSfx) BubbleType.SFX else BubbleType.SPEECH,
        isSfx = isSfx,
        lineCount = 1,
    )

    @Test
    fun `a continued bubble is marked so the model knows the sentence carries over`() {
        // Bez tehle znacky mel model jen plochy seznam textu a nemel jak poznat, ze dve
        // bubliny tvori jednu repliku - preklad se pak rozpadl na dva samostatne utrzky.
        val prompt = GeminiUltraPrompt.buildUserPrompt(
            listOf(
                bubble("PROBOHA,", topF = 0.10f, bottomF = 0.18f),
                bubble("TAKOVA DALKA", topF = 0.20f, bottomF = 0.30f),
            ),
        )
        assertTrue("druha bublina ma byt oznacena jako pokracovani", prompt.contains("POKRAČUJE Z: [BUBBLE 0]"))
    }

    @Test
    fun `unrelated bubbles carry no continuation marker`() {
        val prompt = GeminiUltraPrompt.buildUserPrompt(
            listOf(
                bubble("HOTOVO.", topF = 0.10f, bottomF = 0.18f),
                bubble("KAM JDEŠ?", topF = 0.20f, bottomF = 0.30f),
            ),
        )
        assertFalse("ukoncena veta nepokracuje", prompt.contains("POKRAČUJE Z"))
    }

    @Test
    fun `the system prompt forbids moving text between bubbles`() {
        val prompt = GeminiUltraPrompt.buildSystemPrompt(emptyMap())
        assertTrue(prompt.contains("VĚTY PŘES VÍC BUBLIN"))
        assertTrue("musi zakazat presouvani textu", prompt.contains("NIKDY nepřesouvá"))
        assertTrue("musi zakazat slucovani bublin", prompt.contains("nesluč"))
    }
}
