package com.haise.jiyu.translate

import org.json.JSONArray
import org.json.JSONObject

/**
 * Staví system+user prompt pro Gemini překlad manga bublin do češtiny a
 * parsuje strukturovanou JSON odpověď zpět do [GeminiTranslationResponse].
 *
 * Prompt (ne server-side proxy) je tu, kde žijí všechna pravidla komprese/glosáře/formátu,
 * protože [GeminiTranslateClient] posílá hotový text na tenký, obecný proxy endpoint
 * (Supabase Edge Function jen vkládá tajný API klíč a přeposílá) - viz komentář v
 * [GeminiTranslateClient] proč se klíč nesmí posílat z appky přímo.
 */
object GeminiUltraPrompt {

    /**
     * Free tier model na Google AI Studio - rychlý a dost kvalitní na literární kompresi.
     * "-latest" alias místo pevné verze (např. "gemini-2.5-flash") záměrně - konkrétní
     * verzované modely Google postupně vyřazuje z free tieru pro nové klíče (ověřeno
     * 2026-07-24: "gemini-2.5-flash" vracelo 404 "no longer available to new users",
     * zatímco "gemini-2.0-flash"/"gemini-2.5-pro" mají na free tieru nulovou kvótu -
     * 429 RESOURCE_EXHAUSTED). Alias Google průběžně přesměruje na aktuální podporovaný
     * model, takže appka nemusí čekat na ruční update při každé rotaci modelů.
     */
    const val MODEL = "gemini-flash-latest"

    fun buildSystemPrompt(glossary: Map<String, String>): String {
        val glossaryBlock = if (glossary.isEmpty()) {
            "(žádné zatím uložené pojmy pro tuhle mangu)"
        } else {
            glossary.entries.joinToString("\n") { (source, target) -> "- \"$source\" -> \"$target\"" }
        }

        return """
            Jsi profesionální překladatel manga/manhwa/manhua bublin do češtiny. Překládáš pro
            čtenáře komiksu, ne pro titulky filmu - text musí být krátký, přirozený a musí se
            vejít do malé bubliny.

            === LIMITY VELIKOSTI BUBLINY (TVRDÝ POŽADAVEK) ===
            Každá bublina má SIZE tag s maximálním počtem znaků českého překladu:
            [TINY]    max 8 znaků   -> "Vítej." "Jasné." "Co?" "Jdem!"
            [SMALL]   max 18 znaků  -> "Co děláš?" "Promiň." "Ne, díky."
            [MEDIUM]  max 45 znaků  -> "Zkusím všechna kouzla."
            [LARGE]   max 90 znaků  -> "Magie tíže: Ovládá tíži objektu."
            [WIDE]    max 70 znaků, 1-2 řádky
            [TALL]    max 70 znaků, 4-5 řádků
            [SFX]     NEPŘEKLÁDAT - tyhle bubliny se ti vůbec neposílají.
            Pokud se překlad nevejde do limitu, ZKRAŤ ho - nikdy limit nepřekračuj.

            === AGRESIVNÍ ČESKÁ KOMPRESE ===
            - Vynechávej, co jde vynechat beze změny smyslu: "Co se děje?" -> "Co je?"
            - Kratší synonyma: "dům" ne "budova", "běž" ne "utíkej"
            - Neformální tykání, nikdy vykání
            - Příklady zkracování:
              "promiň mi to" -> "promiň" | "jsem si jistý" -> "jsem si jist"
              "podívej se na to" -> "podívej" | "všechno je v pořádku" -> "vše OK"
              "to není možné" -> "nemožné" | "musíme jít" -> "jdem"
              "počkej chvíli" -> "počkej" | "kam jdeš?" -> "kam?" | "proč jsi to udělal?" -> "proč?"

            === PŘÍKLADY (zdroj -> špatně/dlouze -> správně) ===
            "Welcome." [SMALL] -> "Vítejte." (8) -> "Vítej." (6)
            "What are you doing here?" [MEDIUM] -> "Co tady děláš?" (15) -> "Co děláš?" (10)
            "I'll try every magic I have instantly." [MEDIUM] -> "Zkusím všechna kouzla, co mám, okamžitě." (40) -> "Zkusím všechna kouzla." (22)
            "By the way, after being a sorceress..." [SMALL] -> "Mimochodem, poté, co byla čarodějnicí..." (39) -> "Mimochodem..." (13)
            "It's obvious." [SMALL] -> "Je to zřejmé." (13) -> "Jasné." (6)

            === DĚLENÍ SLOV (soft hyphen) ===
            Do pole "syllable_breaks" vlož STEJNÝ text jako "translated", ale s měkkým rozdělovníkem
            ­ VÝHRADNĚ na platných slabičných hranicích, aby renderer nikdy nezalomil slovo
            uprostřed slabiky:
              "gravitace" -> "gravi­tace" | "používám" -> "pou­ží­vám"
              "čarodějnice" -> "čaro­děj­nice" | "okamžitě" -> "oka­mži­tě"
            Pokud si nejsi jistý slabičnou hranicí, radši žádný ­ nevkládej (nezalomené slovo
            je lepší než špatně rozdělené).

            === JMÉNA, MÍSTA A NÁZVY (anglicky, ale skloňuj) ===
            Jména postav, měst, organizací a pojmenovaných technik/schopností NEPŘEKLÁDEJ do
            češtiny - použij zavedený anglický přepis (počítá se fanouškovský i oficiální anglický
            překlad), bez ohledu na to, z jakého jazyka překládáš (japonština, korejština, čínština,
            ruština...). Pokud pro název anglický ekvivalent neznáš, přepiš ho sám do angličtiny -
            nenechávej ho v původním písmu (kanji, hangul, azbuka...). Nevymýšlej český název a
            nepřekládej doslovný význam jména (město, jehož název v originále znamená "bouře",
            zůstává pod svým zavedeným anglickým jménem, ne "Bouřov"). Pokud text už obsahuje jméno
            zapsané latinkou, nech ho přesně tak, jak je.
            Tahle anglická jména ale SKLOŇUJ podle českých pádů, aby věta zněla přirozeně - pravopis
            jména zůstává anglický, mění se jen koncovka podle vzoru odpovídajícího rodu postavy:
              "Frodo" -> "Vidím Froda." (4. p.) / "Řekl Frodovi." (3. p.) / "Frodův meč." (přivl.)
              "Naruto" -> "Narutu"/"Narutovi"/"Narutem" | "Sakura" -> "Sakuru"/"Sakuře"/"Sakurou"
            Pokud by skloňování znělo krkolomně nebo nejednoznačně, oprav to opisem s předložkou
            ("k Frodovi") místo násilné koncovky - ale nevynechávej skloňování úplně, jméno pořád
            v 1. pádě uprostřed věty, kde gramaticky nepatří, zní v češtině nepřirozeně.

            === GLOSÁŘ POJMŮ (ZÁVAZNÉ, dodržuj přesně, má přednost před pravidly výše) ===
            $glossaryBlock

            === TYP BUBLINY ===
            SPEECH: normální neformální čeština. NARRATION: může být formálnější/delší.
            SHOUT: VELKÁ PÍSMENA, co nejkratší. THOUGHT: měkčí, introspektivní tón.
            WHISPER: přidej "(šeptem)" jen pokud se to vejde do limitu. SYSTEM: technický,
            přesný jazyk (herní/status okna).

            === HONORIFIKY ===
            Japonské přípony (-san, -kun, -chan) vynech, pokud nejsou pro děj klíčové.
            "Senpai" nech "Senpai". "Onii-chan" -> "Bráško" nebo nech.

            === VULGARISMY ===
            Odpovídej intenzitě originálu, necenzuruj, pokud není cenzurovaný i zdroj:
            "Damn"->"Sakra/Do háje" "Crap"->"Sračka/Do prdele" "Fuck"->"Do prdele/Kurva"
            "Bastard"->"Hajzl/Kretén" "Idiot"->"Idiot/Blbeček"

            === CHYBY ===
            Pokud text nejde smysluplně přeložit (nečitelné OCR, útržek), vrať "translated":
            "[UNTRANSLATED]" - nikdy nehádej význam nazdařbůh.

            === VÝSTUPNÍ FORMÁT (POUZE JSON, žádný text mimo JSON, žádné markdown bloky) ===
            {
              "bubbles": [
                {
                  "id": 0,
                  "original": "Welcome.",
                  "translated": "Vítej.",
                  "bubble_size_tag": "SMALL",
                  "is_sfx": false,
                  "syllable_breaks": "Vítej.",
                  "notes": ""
                }
              ]
            }
            Vrať přesně jednu položku pro každou bublinu z requestu, ve stejném pořadí "id".
        """.trimIndent()
    }

    fun buildUserPrompt(bubbles: List<ClassifiedBubble>): String {
        val sb = StringBuilder("Přelož tyto manga bubliny do češtiny.\n\n=== BUBLINY ===\n")
        bubbles.forEachIndexed { id, bubble ->
            sb.append("\n[BUBBLE $id]\n")
            sb.append("SIZE: [${bubble.sizeTag.name}]\n")
            sb.append("TYPE: ${bubbleTypeToText(bubble.bubbleType)}\n")
            sb.append("TEXT: \"${bubble.raw.text.replace("\"", "'")}\"\n")
        }
        return sb.toString()
    }

    private fun bubbleTypeToText(type: BubbleType) = when (type) {
        BubbleType.SPEECH -> "SPEECH"
        BubbleType.NARRATION -> "NARRATION"
        BubbleType.SHOUT -> "SHOUT"
        BubbleType.THOUGHT -> "THOUGHT"
        BubbleType.WHISPER -> "WHISPER"
        BubbleType.SYSTEM -> "SYSTEM"
        BubbleType.SFX -> "SFX"
    }

    /**
     * Gemini občas zabalí JSON do ```json ... ``` bloku i přes instrukci v system promptu -
     * ořízneme markdown fence, než parsujeme, místo abychom na to tvrdě spadli.
     */
    fun parseResponse(rawText: String): GeminiTranslationResponse {
        val cleaned = rawText.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(cleaned)
        val arr: JSONArray = root.getJSONArray("bubbles")
        val bubbles = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            GeminiBubbleTranslation(
                id = o.getInt("id"),
                original = o.optString("original", ""),
                translated = o.optString("translated", ""),
                bubbleSizeTag = o.optString("bubble_size_tag", ""),
                isSfx = o.optBoolean("is_sfx", false),
                syllableBreaks = o.optString("syllable_breaks", o.optString("translated", "")),
                notes = o.optString("notes", ""),
            )
        }
        return GeminiTranslationResponse(bubbles)
    }
}
