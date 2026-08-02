package com.haise.jiyu.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
 * Nejzákladnější kouřová zkouška: appka nastartuje a po onboardingu přistane na knihovně.
 *
 * Druhý test byl dřív vypnutý `@Ignore` s odůvodněním, že vyžaduje prázdnou knihovnu - přitom
 * jeho jediné tvrzení (`onNodeWithText("Knihovna")`) mířilo na popisek dolní lišty, který je
 * vidět úplně vždycky, takže o prázdném stavu nic neříkal ani jeho název neseděl. Teď se ptá
 * na prvek, který je jen na knihovně, a stav si připraví sám (viz [OnboardingCompletedRule]).
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
    val onboardingRule = OnboardingCompletedRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun app_launches_without_crash() {
        composeRule.waitForIdle()
        composeRule.onRoot().assertExists()
    }

    @Test
    fun library_isTheStartDestination() {
        // Text ze string resource, ne natvrdo - appka spuštěná bez onboardingu mluví jazykem
        // zařízení, takže na anglickém emulátoru by česká konstanta nikdy nesedla.
        val search = composeRule.activity.getString(R.string.library_search_placeholder)
        composeRule.awaitText(search)
        composeRule.onNodeWithText(search).assertIsDisplayed()
    }
}
