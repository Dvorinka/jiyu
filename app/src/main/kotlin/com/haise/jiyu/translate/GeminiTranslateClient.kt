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
 * Google AI Studio API klíč NENÍ nikde v appce - proxy ho vloží server-side ze Supabase
 * secretu, stejně jako u Groq. Přímé volání z appky s klíčem v hlavičce by šlo dekompilací
 * APK triviálně ukrást a zneužít na cizí free-tier kvótu.
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
     * @return null při selhání (síť, rate limit mimo [RateLimitedException], neparsovatelná odpověď)
     * @throws RateLimitedException viz [GroqTranslateClient] - stejná sémantika, proxy je sdílená.
     */
    suspend fun translateBubbles(
        bubbles: List<ClassifiedBubble>,
        glossary: Map<String, String>,
    ): GeminiTranslationResponse? = withContext(Dispatchers.IO) {
        val toTranslate = bubbles.filterIndexed { _, b -> !b.isSfx }
        if (!isConfigured || toTranslate.isEmpty()) return@withContext null

        val requestBody = JSONObject().apply {
            put("mode", "gemini")
            put("model", GeminiUltraPrompt.MODEL)
            put("system", GeminiUltraPrompt.buildSystemPrompt(glossary))
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
