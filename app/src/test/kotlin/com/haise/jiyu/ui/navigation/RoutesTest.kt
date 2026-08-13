package com.haise.jiyu.ui.navigation

import com.haise.jiyu.settings.AppMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `browseRoute returns the all-sources picker in Classic mode`() {
        assertEquals(Routes.BROWSE, Routes.browseRoute(AppMode.SOURCES))
    }

    @Test
    fun `browseRoute returns the ComicK home feed in ComicK mode`() {
        assertEquals(Routes.COMICK_HOME, Routes.browseRoute(AppMode.COMICK))
    }
}
