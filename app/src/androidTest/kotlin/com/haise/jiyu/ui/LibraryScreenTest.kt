package com.haise.jiyu.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.haise.jiyu.MainActivity
import com.haise.jiyu.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testy dolní navigace a přechodů mezi hlavními obrazovkami.
 *
 * Dřív byly celé vypnuté `@Ignore` s odůvodněním, že vyžadují dokončený onboarding A ZÁROVEŇ
 * prázdnou knihovnu, což si samy nepřipraví. Při bližším pohledu platila jen první polovina:
 * žádné z tvrzení níž na obsahu knihovny nestojí - všechna se ptají na popisky dolní lišty
 * nebo na prvek, který je jen na cílové obrazovce. Stačí tedy dokončený onboarding, a ten si
 * test nastaví sám (viz [OnboardingCompletedRule]); databáze se nesahá.
 *
 * Texty se berou ze STRING RESOURCES, ne natvrdo. Onboarding si jazyk vybírá sám (a nastaví
 * ho přes AppCompatDelegate), takže appka spuštěná bez něj mluví jazykem ZAŘÍZENÍ - na
 * anglickém emulátoru tedy anglicky. Testy s natvrdo napsanou češtinou by tam hledaly text,
 * který na obrazovce nikdy není.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    // Bez tohohle testy padaly na "No compose hierarchies found": po cerstve instalaci
    // vyskoci systemovy dialog o opravneni k notifikacim, prekryje MainActivity a ta jde
    // rovnou do PAUSED, takze Compose nema co najit. Zaludne na tom je, ze pri opakovanem
    // behu uz opravneni udelene je a test projde - chyba se tedy jevi jako nahodna.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    // Musí být mezi Hiltem (potřebuje hotovou komponentu) a compose rule (ten už spouští
    // Activity, takže později by bylo pozdě) - viz [OnboardingCompletedRule].
    @get:Rule(order = 2)
    val onboardingRule = OnboardingCompletedRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    private fun text(resId: Int): String = composeRule.activity.getString(resId)

    @Test
    fun libraryScreen_isDisplayed() {
        // Vyhledávací pole je JEN na knihovně, na rozdíl od popisku v dolní liště, který je
        // vidět ze všech obrazovek - to je rozdíl mezi "jsme na knihovně" a "appka běží".
        val search = text(R.string.library_search_placeholder)
        composeRule.awaitText(search)
        composeRule.onNodeWithText(search).assertIsDisplayed()
    }

    @Test
    fun bottomNavigation_tabsExist() {
        composeRule.awaitText(text(R.string.library_search_placeholder))
        composeRule.onAllNodesWithText(text(R.string.main_screen_tab_browse)).onFirst().assertExists()
        composeRule.onAllNodesWithText(text(R.string.main_screen_tab_history)).onFirst().assertExists()
        composeRule.onAllNodesWithText(text(R.string.settings_title)).onFirst().assertExists()
    }

    // Oba testy níž dřív klikly na položku dolní lišty a pak ověřily, že je vidět text se
    // STEJNÝM názvem - jenže ten je v liště pořád, i kdyby se navigace vůbec nepovedla. Navíc
    // po přechodu existují uzly dva (lišta + nadpis obrazovky), na což `onNodeWithText` hlásí
    // "Expected at most 1 node but found 2". Nikdo si toho nevšiml, protože instrumentované
    // testy se roky nespouštěly (chyběl testInstrumentationRunner, viz HiltTestRunner).
    // Teď se klikne na položku lišty a ověří se prvek, který je JEN na cílové obrazovce.

    @Test
    fun navigateToBrowse_works() {
        composeRule.awaitText(text(R.string.library_search_placeholder))
        composeRule.onAllNodesWithText(text(R.string.main_screen_tab_browse)).onFirst().performClick()
        val browseSearch = text(R.string.browse_search_placeholder)
        composeRule.awaitText(browseSearch)
        composeRule.onNodeWithText(browseSearch).assertIsDisplayed()
    }

    @Test
    fun navigateToSettings_works() {
        composeRule.awaitText(text(R.string.library_search_placeholder))
        composeRule.onAllNodesWithText(text(R.string.settings_title)).onFirst().performClick()
        val sources = text(R.string.settings_main_sources_title)
        composeRule.awaitText(sources)
        composeRule.onNodeWithText(sources).assertIsDisplayed()
    }
}
