package com.haise.jiyu.translate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tělo požadavku na translate-proxy - záložní cesta, na kterou se spadne, když Gemini
 * selže (vyčerpaná kvóta, výpadek modelu).
 *
 * NÁLEZ, kvůli kterému tenhle test vznikl: záložní cesta posílala jen holý seznam vět.
 * Žádný název díla, žádné "tohle je manga", žádná návaznost na to, co padlo v předchozích
 * bublinách - přestože appka obojí zná a Gemini cestě to posílá. Model tak překládal
 * jednotlivé věty naslepo a vracel doslovné nesmysly ("JUST LEAVE ME HERE." ->
 * "ZŮSTAŇTE MĚ TADY").
 */
class ProxyRequestBodyTest {

    private fun body(
        texts: List<String> = listOf("HELLO"),
        mangaContext: String = "",
        previousLines: List<String> = emptyList(),
    ): JSONObject = JSONObject(
        buildProxyRequestBody(
            texts = texts,
            targetLanguage = "Czech",
            sourceLanguage = "English",
            glossary = emptyMap(),
            mode = "manga",
            provider = "groq",
            mangaContext = mangaContext,
            previousLines = previousLines,
        ),
    )

    @Test
    fun `the work being translated travels with the request`() {
        val json = body(mangaContext = "Název: \"Vagabond\" (manga), žánry: akce, historické")
        assertEquals("Název: \"Vagabond\" (manga), žánry: akce, historické", json.getString("context"))
    }

    @Test
    fun `previously translated lines travel with the request`() {
        val json = body(previousLines = listOf("Můžeš chodit?", "Pro zábavu!"))
        val recent = json.getJSONArray("recent")
        assertEquals(2, recent.length())
        assertEquals("Můžeš chodit?", recent.getString(0))
        assertEquals("Pro zábavu!", recent.getString(1))
    }

    @Test
    fun `the context tail is capped so it cannot eat the character budget`() {
        // Stejný rozpočet, jaký má Gemini cesta (viz recentContextLines) - kontext je ocásek,
        // ne druhá dávka. Bez stropu by dlouhá kapitola posílala pokaždé celou historii.
        val json = body(previousLines = (1..50).map { "Replika číslo $it, dost dlouhá na to, aby se rozpočet vyčerpal." })
        val recent = json.getJSONArray("recent")
        assertTrue("kontext musí být oříznutý, bylo ${recent.length()} replik", recent.length() <= 6)
    }

    @Test
    fun `nothing extra is sent when there is nothing to say`() {
        // Prázdný kontext se do těla nedává vůbec - proxy pak staví prompt jako dřív a
        // nespotřebuje ani znak navíc.
        val json = body()
        assertFalse("prázdný kontext nemá co posílat", json.has("context"))
        assertFalse("prázdná historie nemá co posílat", json.has("recent"))
    }

    @Test
    fun `the fields the proxy already relied on are untouched`() {
        val json = body(texts = listOf("A", "B"), mangaContext = "Název: \"X\" (manga)")
        assertEquals("manga", json.getString("mode"))
        assertEquals("groq", json.getString("provider"))
        assertEquals("Czech", json.getString("targetLanguage"))
        assertEquals("English", json.getString("sourceLanguage"))
        assertEquals(2, json.getJSONArray("texts").length())
    }
}
