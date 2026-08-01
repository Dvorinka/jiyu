package com.haise.jiyu.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ukotvuje výchozí zdrojový jazyk překladu.
 *
 * Proč zrovna tohle: výchozí hodnota byla "English", takže kdo si appku nainstaloval a otevřel
 * japonskou (korejskou, čínskou) mangu, pustil na ni latinkový rozpoznávač. Ten na CJK stránce
 * nenajde nic - měřeno na zařízení doslova 0 znaků, viz AutoLanguageOnDeviceTest - takže se
 * nevytvořil žádný překlad a uživatel dostal prázdný výsledek bez jediného vysvětlení.
 *
 * "Auto" si rozpoznávač vybere podle toho, co na stránce opravdu je (viz [resolveAutoLanguage]).
 * Na běžné anglické stránce se rozhodne hned prvním průchodem, takže nic nestojí navíc.
 *
 * POZOR na souvislost s cache: zdrojový jazyk je součástí klíče uložených překladů
 * (TranslateRepository.cacheId). Změna téhle konstanty tedy zneplatní překlady všem, kdo si
 * jazyk nikdy ručně nenastavili - nic se neztratí, jen se přepočítá.
 */
class SourceLanguageDefaultTest {

    private fun repository() = SettingsRepository(FakeDataStore())

    @Test
    fun `the default source language is Auto, not a fixed script`() = runTest {
        assertEquals("Auto", repository().sourceLanguage.first())
    }

    @Test
    fun `an explicitly chosen language still wins over the default`() = runTest {
        val settings = repository()
        settings.setSourceLanguage("Japanese")

        assertEquals("Japanese", settings.sourceLanguage.first())
    }

    @Test
    fun `the default target language stays Czech`() = runTest {
        // Cílový jazyk se nemění - jen se hlídá, ať ho úprava zdrojového nerozhodí.
        assertEquals("Czech", repository().targetLanguage.first())
    }
}
