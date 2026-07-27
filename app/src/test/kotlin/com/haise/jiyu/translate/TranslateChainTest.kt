package com.haise.jiyu.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Čistý JVM test [translateChain] - žádná síťová/Android závislost, jen fake suspend
 * kroky. Ověřuje klíčovou opravu: rate limit z JEDNOHO kroku (provider) už neznamená
 * okamžitý konec celého řetězce - Gemini/Groq/OpenRouter jsou nezávislé služby s
 * vlastní kvótou (viz uživatelská diskuze), takže appka teď zkusí i zbylé kroky.
 */
class TranslateChainTest {

    private fun block(text: String) =
        TranslatedBlock(originalText = text, translatedText = text, leftF = 0f, topF = 0f, rightF = 0.1f, bottomF = 0.1f)

    @Test
    fun `first successful step wins, later steps are never called`() = runBlocking {
        var secondStepCalled = false
        val result = translateChain(
            { listOf(block("from-first")) },
            { secondStepCalled = true; listOf(block("from-second")) },
        )
        assertEquals("from-first", result.single().originalText)
        assertTrue("second step must not run once an earlier step already succeeded", !secondStepCalled)
    }

    @Test
    fun `null step falls through to the next step`() = runBlocking {
        val result = translateChain(
            { null },
            { listOf(block("from-second")) },
        )
        assertEquals("from-second", result.single().originalText)
    }

    @Test
    fun `rate limit on one step tries the next step instead of giving up immediately`() = runBlocking {
        // Toto je presne oprava z uzivatelske diskuze - drivejsi kod (prosty retezec "?:")
        // by tady vubec nezkusil druhy krok, jen by rovnou propagoval RateLimitedException.
        var secondStepCalled = false
        val result = translateChain(
            { throw RateLimitedException() },
            { secondStepCalled = true; listOf(block("from-second")) },
        )
        assertTrue("second provider must still be tried after the first was rate-limited", secondStepCalled)
        assertEquals("from-second", result.single().originalText)
    }

    @Test
    fun `rate limit exception only propagates when every single step was rate-limited or failed`() = runBlocking {
        try {
            translateChain(
                { throw RateLimitedException() },
                { throw RateLimitedException() },
                { null },
            )
            fail("expected RateLimitedException when all steps were rate-limited or failed")
        } catch (_: RateLimitedException) {
            // ocekavane
        }
    }

    @Test
    fun `plain failure (null) on every step returns empty list, not an exception`() = runBlocking {
        val result = translateChain({ null }, { null })
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a later success after an earlier rate limit does not throw`() = runBlocking {
        // Rate limit na kroku 1, ale krok 2 uspeje - zadna vyjimka, jen vysledek kroku 2.
        val result = translateChain(
            { throw RateLimitedException() },
            { listOf(block("recovered")) },
        )
        assertEquals("recovered", result.single().originalText)
    }
}
