package com.haise.jiyu.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.platform.app.InstrumentationRegistry
import com.haise.jiyu.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

/**
 * Nastaví příznak "onboarding dokončen" JEŠTĚ NEŽ se spustí `MainActivity`.
 *
 * Proč to musí být pravidlo a ne `@Before`: `createAndroidComposeRule` spouští Activity už
 * ve chvíli, kdy se vyhodnocuje jeho `Statement` - tedy PŘED `@Before`. Kdyby se příznak
 * nastavoval až tam, appka by mezitím stihla vykreslit onboarding a testy hledající knihovnu
 * by nic nenašly. Proto se tohle pravidlo řadí mezi `HiltAndroidRule` (potřebuje hotovou
 * komponentu, aby šel vytáhnout SettingsRepository) a compose rule.
 *
 * Knihovnu záměrně NEČISTÍ. Původní komentář u obou testovacích tříd tvrdil, že vyžadují
 * prázdnou knihovnu, ale žádné z jejich tvrzení na obsahu knihovny nestojí - všechna se
 * ptají na popisky dolní lišty a na prvky cílových obrazovek. Mazat kvůli tomu databázi by
 * znamenalo, že spuštění testů na skutečném telefonu smaže uživateli knihovnu.
 */
class OnboardingCompletedRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val app = InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .applicationContext
                val settings = EntryPointAccessors
                    .fromApplication(app, SettingsEntryPoint::class.java)
                    .settingsRepository()
                runBlocking { settings.setOnboardingCompleted() }
                base.evaluate()
            }
        }
}

/**
 * Počká, až se na obrazovce objeví uzel s daným textem.
 *
 * Proč nestačí `waitForIdle()`: `MainActivity` čte příznak onboardingu jako
 * `collectAsState(initial = null)` a dokud nedorazí první hodnota z DataStore, nevykreslí
 * VŮBEC nic - startovní cíl navigace se rozhoduje až v ten okamžik. `waitForIdle` čeká jen na
 * dokončení kompozice a animací, o čekajícím Flow nic neví, takže se vrátí dřív a tvrzení
 * hledá uzel na prázdné obrazovce.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitText(text: String, timeoutMillis: Long = 15_000) =
    waitUntilAtLeastOneExists(hasText(text), timeoutMillis)
