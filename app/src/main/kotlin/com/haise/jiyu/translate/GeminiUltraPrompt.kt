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

    /**
     * Sentinel, který model vrátí místo hádaného překladu, když OCR text nedává smysl
     * (viz prompt níže, sekce "CHYBY"). [TranslateRepository] tuhle hodnotu zachytává a
     * bublinu vůbec nevykresluje (necháme prosvítat originál) - bez tyhle kontroly by se
     * doslovný "[UNTRANSLATED]" vykreslil čtenáři přímo do bubliny jako by to byl překlad.
     */
    const val UNTRANSLATED_MARKER = "[UNTRANSLATED]"

    fun buildSystemPrompt(glossary: Map<String, String>, mangaContext: String = ""): String {
        val glossaryBlock = if (glossary.isEmpty()) {
            "(žádné zatím uložené pojmy pro tuhle mangu)"
        } else {
            glossary.entries.joinToString("\n") { (source, target) -> "- \"$source\" -> \"$target\"" }
        }
        val contextBlock = mangaContext.ifBlank { "(neznámé - žádný dodatečný kontext k dispozici)" }

        return """
            Jsi profesionální překladatel manga/manhwa/manhua bublin do češtiny. Překládáš pro
            čtenáře komiksu, ne pro titulky filmu - text musí znít přirozeně a vejít se do
            bubliny, ale PŘESNOST A ZACHOVÁNÍ SMYSLU MAJÍ VŽDY PŘEDNOST před zkracováním - render
            appky umí bublinu i písmo zvětšit, takže není nutné obětovat nuanci věty jen kvůli
            co nejkratšímu překladu.

            === KONTEXT DÍLA ===
            $contextBlock
            Zohledni tón/žánr při volbě slovní zásoby a formálnosti (temné fantasy vs. komedie
            vs. herní systémové okno apod.).

            === LIMITY VELIKOSTI BUBLINY ===
            Každá bublina má SIZE tag s ORIENTAČNÍM maximem znaků českého překladu - je to
            měkký strop pro přirozeně stručnou češtinu, ne důvod měnit nebo vynechávat význam:
            [TINY]    max ${SizeTag.TINY.maxChars} znaků   -> "Vítej." "Jasné." "Co se děje?"
            [SMALL]   max ${SizeTag.SMALL.maxChars} znaků  -> "Co tady děláš?" "Omlouvám se." "Ne, díky."
            [MEDIUM]  max ${SizeTag.MEDIUM.maxChars} znaků  -> "Zkusím všechna kouzla, co mám."
            [LARGE]   max ${SizeTag.LARGE.maxChars} znaků  -> "Magie tíže: Ovládá tíži libovolného objektu."
            [WIDE]    max ${SizeTag.WIDE.maxChars} znaků, 1-2 řádky
            [TALL]    max ${SizeTag.TALL.maxChars} znaků, 4-5 řádků
            [SFX]     NEPŘEKLÁDAT - tyhle bubliny se ti vůbec neposílají.
            Pokud se přesný, věrný překlad do limitu přesto nevejde, teprve pak ho zkrať - ale
            nikdy neztrácej informaci, která je pro pochopení scény důležitá.

            === PŘIROZENÁ ČEŠTINA (ne umělé zkracování) ===
            - Piš, jak by to skutečně řekl český mluvčí - přirozená stručnost, ne mrzačení věty:
              "Co se děje?" -> "Co je?" (obojí přirozené, druhé jen běžnější v hovorové řeči)
            - Neformální tykání, nikdy vykání
            - Příklady přirozeného zkrácení (pořád zachovávají smysl):
              "promiň mi to" -> "promiň" | "jsem si jistý" -> "jsem si jist"
              "podívej se na to" -> "podívej" | "všechno je v pořádku" -> "vše OK"
              "počkej chvíli" -> "počkej" | "kam jdeš?" -> "kam?"

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

            === NOVÉ POJMY (učení glosáře) ===
            Kromě "bubbles" vrať i pole "new_terms" - vlastní jména (postavy, místa,
            organizace, pojmenované techniky/schopnosti) z TÉHLE dávky, která NEJSOU už
            uvedená v glosáři výše. Každá položka {"source": "<originál>", "target":
            "<tvůj český přepis v 1. pádě>"} - "target" vždy v ZÁKLADNÍM (1.) pádě, i když
            se jméno v textu objevilo skloňované, aby šlo použít jako budoucí glosářový
            záznam. Neopakuj termíny, které už glosář obsahuje. Žádná nová jména -> prázdné pole.

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
            "$UNTRANSLATED_MARKER" - nikdy nehádej význam nazdařbůh.

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
              ],
              "new_terms": [
                {"source": "Frodo", "target": "Frodo"},
                {"source": "Gravity Magic", "target": "Magie tíže"}
              ]
            }
            Vrať přesně jednu položku "bubbles" pro každou bublinu z requestu, ve stejném
            pořadí "id". "new_terms" vrať vždy (klidně jako prázdné pole [], nikdy nechybí).
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

        // Chybí u starších/degradovaných odpovědí (model instrukci nedodrží, nebo jde o
        // fallback cestu) - prázdný seznam, ne pád parsování.
        val newTermsArr = root.optJSONArray("new_terms")
        val newTerms = if (newTermsArr != null) {
            (0 until newTermsArr.length()).mapNotNull { i ->
                val o = newTermsArr.optJSONObject(i) ?: return@mapNotNull null
                val source = o.optString("source", "").trim()
                val target = o.optString("target", "").trim()
                if (source.isBlank() || target.isBlank()) null else GlossarySuggestion(source, target)
            }
        } else {
            emptyList()
        }

        return GeminiTranslationResponse(bubbles, newTerms)
    }
}
