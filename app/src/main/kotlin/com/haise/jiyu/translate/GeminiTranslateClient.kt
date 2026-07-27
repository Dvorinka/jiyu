package com.haise.jiyu.translate

import com.haise.jiyu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Volá stejnou Supabase Edge Function "translate-proxy" jako [GroqTranslateClient], ale
 * novým "gemini" módem - ten na rozdíl od "manga"/"novel" módu neposílá jen holé texty,
 * posílá HOTOVÝ system+user prompt postavený v [GeminiUltraPrompt] (kompresní pravidla,
 * glosář, JSON schema - vše je verzovatelné v Kotlinu, ne skryté server-side).
 *
 * Od verze 10 proxy funkce umí ten samý "gemini" mód obsloužit i přes Groq (parametr
 * [provider] = "groq") - stejný system+user prompt, jen jiný upstream model. Díky tomu
 * komprese/sylabické dělení z [GeminiUltraPrompt] fungují i když samotné Gemini selže
 * (deprekovaný model, jeho vlastní výpadek), místo aby appka spadla na holý Groq překlad
 * bez těchhle pravidel - viz [TranslateRepository.translateWithGemini].
 *
 * Od verze 12 (2026-07-26) umí proxy stejný "gemini" mód obsloužit i přes OpenRouter
 * free-tier model (parametr [provider] = "openrouter") jako čtvrtou úroveň zálohy, než
 * appka klesne na holý Groq bez komprese - viz [TranslateRepository.translatePage].
 *
 * Google AI Studio / Groq / OpenRouter API klíč NENÍ nikde v appce - proxy je vloží
 * server-side ze Supabase secretů. Přímé volání z appky s klíčem v hlavičce by šlo
 * dekompilací APK triviálně ukrást a zneužít na cizí free-tier kvótu.
 */
@Singleton
class GeminiTranslateClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
        !BuildConfig.SUPABASE_URL.contains("placeholder")

    /**
     * Přeloží dávku bublin jedné stránky. SFX bubliny (viz [ClassifiedBubble.isSfx]) se
     * do requestu vůbec nezahrnují - filtruje [GeminiUltraPrompt.buildUserPrompt].
     *
     * @param provider "gemini" (výchozí), "groq" nebo "openrouter" - viz komentář u třídy. Groq i
     *   OpenRouter model se nastavují server-side (Groq: "llama-3.3-70b-versatile" jako
     *   [GroqTranslateClient]; OpenRouter: free-tier model, viz OPENROUTER_MODEL v translate-proxy),
     *   appka je nemusí posílat.
     * @return null při selhání (síť, rate limit mimo [RateLimitedException], neparsovatelná odpověď)
     * @throws RateLimitedException viz [GroqTranslateClient] - stejná sémantika, proxy je sdílená
     *   (kvóta je jedna společná pro gemini, groq i openrouter provider).
     */
    suspend fun translateBubbles(
        bubbles: List<ClassifiedBubble>,
        glossary: Map<String, String>,
        provider: String = "gemini",
        mangaContext: String = "",
    ): GeminiTranslationResponse? = withContext(Dispatchers.IO) {
        val toTranslate = bubbles.filterIndexed { _, b -> !b.isSfx }
        if (!isConfigured || toTranslate.isEmpty()) return@withContext null

        val requestBody = JSONObject().apply {
            put("mode", "gemini")
            put("provider", provider)
            if (provider == "gemini") put("model", GeminiUltraPrompt.MODEL)
            put("system", GeminiUltraPrompt.buildSystemPrompt(glossary, mangaContext))
            put("user", GeminiUltraPrompt.buildUserPrompt(bubbles))
        }

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/functions/v1/translate-proxy")
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // Stejný retry vzor jako GroqTranslateClient - přechodné chyby zkusíme znovu,
        // rate limit (429) propagujeme okamžitě jako RateLimitedException.
        repeat(3) { attempt ->
            try {
                val responseText = httpClient.newCall(request).execute().use { resp ->
                    if (resp.code == 429) throw RateLimitedException()
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
                val text = responseText?.let { JSONObject(it).optString("text") }?.takeIf { it.isNotBlank() }
                if (text != null) {
                    return@withContext try {
                        GeminiUltraPrompt.parseResponse(text)
                    } catch (_: Exception) {
                        null // neparsovatelná odpověď - nemá smysl retryovat, model to znovu nespraví
                    }
                }
            } catch (e: RateLimitedException) {
                throw e
            } catch (_: Exception) {
                // zkusíme to znovu, viz delay níže; po vyčerpání pokusů spadneme na null
            }
            if (attempt < 2) delay(800L * (attempt + 1))
        }
        null
    }
}
