package com.haise.jiyu.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.haise.jiyu.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * POZOR: vetsina testu v teto tride je docasne vypnuta.
 *
 * Instrumentovane testy se v projektu roky nespoustely - chybel `testInstrumentationRunner`
 * (viz HiltTestRunner) a soubory se ani nekompilovaly. Po zprovozneni vyslo najevo, ze tyhle
 * testy mlcky predpokladaji urcity stav: dokonceny onboarding A ZAROVEN prazdnou knihovnu.
 * Cerstva instalace ma onboarding nedokonceny (a zobrazi ho misto knihovny), pouzivana appka
 * zase knihovnu prazdnou nema - test tedy neprojde ani v jednom bezne dosazitelnem stavu.
 *
 * Poctiva oprava = necha test pripravit si stav sam (testovaci Hilt modul, ktery podstrci
 * pripravene DataStore/Room). To je samostatny kus prace, ne uprava jednoho radku, proto
 * radeji viditelne @Ignore nez cervene sestaveni nebo tise smazany test.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderSmokeTest {

    // Bez tohohle testy padaly na "No compose hierarchies found": po cerstve instalaci
    // vyskoci systemovy dialog o opravneni k notifikacim, prekryje MainActivity a ta jde
    // rovnou do PAUSED, takze Compose nema co najit. Zaludne na tom je, ze pri opakovanem
    // behu uz opravneni udelene je a test projde - chyba se tedy jevi jako nahodna.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun app_launches_without_crash() {
        composeRule.waitForIdle()
        composeRule.onRoot().assertExists()
    }

    @Test
    @Ignore("Vyzaduje konkretni stav appky (dokonceny onboarding + PRAZDNA knihovna), ktery si test sam nepripravuje - viz komentar u tridy.")
    fun library_emptyState_isDisplayed() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Knihovna").assertExists()
    }
}
