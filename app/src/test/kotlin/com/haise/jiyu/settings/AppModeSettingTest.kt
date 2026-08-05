package com.haise.jiyu.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ukotvuje výchozí a přepínané chování režimu appky (Klasický/ComicK) - viz
 * docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md.
 */
class AppModeSettingTest {

    private fun repository() = SettingsRepository(FakeDataStore())

    @Test
    fun `the default app mode is Sources, not ComicK`() = runTest {
        assertEquals(AppMode.SOURCES, repository().appMode.first())
    }

    @Test
    fun `switching to ComicK mode persists and is read back`() = runTest {
        val settings = repository()
        settings.setAppMode(AppMode.COMICK)

        assertEquals(AppMode.COMICK, settings.appMode.first())
    }

    @Test
    fun `switching back to Sources mode persists`() = runTest {
        val settings = repository()
        settings.setAppMode(AppMode.COMICK)
        settings.setAppMode(AppMode.SOURCES)

        assertEquals(AppMode.SOURCES, settings.appMode.first())
    }
}
