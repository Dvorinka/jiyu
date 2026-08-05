# ComicK agregovaný režim — Sub-projekt 1: Přepínač režimu + navigace — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Přidat globální nastavení "Režim appky" (Klasický / ComicK) a zajistit, že v ComicK režimu záložka "Procházet" (a tlačítka "Procházet mangu" v prázdné Knihovně/Seznamu) vedou rovnou na ComicK vyhledávání místo výběru ze ~180 zdrojů.

**Architektura:** Nový string klíč v DataStore (`SettingsRepository`, vzor `ReadingDirection`/`ReadingMode`). `MainViewModel` ho vystaví jako `StateFlow<String>`. Bottom-nav záložka "Procházet" a `onOpenBrowse` callbacky v `NavGraph.kt` počítají cílovou cestu (`Routes.BROWSE` vs `Routes.sourceBrowse("comick")`) přes jednu sdílenou funkci `browseRoute(appMode)` — **cesty samotné se dynamicky mění podle režimu, ne že by se `Routes.BROWSE` vykresloval jinak** (viz Task 4, důležitá oprava oproti prvnímu návrhu designu kvůli zvýrazňování aktivní záložky). ComicK se zpátky zaregistruje jako běžný zdroj v `SourceManager`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, DataStore Preferences, JUnit4 (testDebugUnitTest), FakeDataStore test double.

## Global Constraints

- Všechny UI texty musí mít překlad ve všech 4 lokalizacích: `values/strings.xml` (čeština, hlavní), `values-en`, `values-fr`, `values-es`.
- Žádné nové závislosti (`build.gradle.kts` se nemění).
- Existující chování v Klasickém režimu (výchozí) se nesmí nijak změnit — `AppMode.SOURCES` musí být bitově identické s dnešním chováním.
- `versionCode`/`versionName` se nezvedá (dělá se jen při skutečném vydání, viz release proces).
- Po dokončení: `./gradlew testDebugUnitTest` a `./gradlew lintDebug` musí projít čistě.
- Commit po každém tasku zvlášť (standardní mechanika subagent-driven developmentu — recenzent porovnává diff commitu před/po tasku). Task 5 dělá jen finální ověření celku, ne souhrnný commit.

---

### Task 1: `AppMode` nastavení v `SettingsRepository`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/settings/SettingsRepository.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/settings/AppModeSettingTest.kt` (nový)

**Interfaces:**
- Produces: `SettingsKeys.APP_MODE: Preferences.Key<String>`, `object AppMode { const val SOURCES = "sources"; const val COMICK = "comick" }`, `SettingsRepository.appMode: Flow<String>`, `SettingsRepository.setAppMode(mode: String): suspend`. Task 2 a Task 4 na tyhle přesné názvy navazují.

- [ ] **Krok 1: Napsat padající test**

Soubor `app/src/test/kotlin/com/haise/jiyu/settings/AppModeSettingTest.kt`:

```kotlin
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
```

- [ ] **Krok 2: Spustit test a ověřit, že padá**

Run: `./gradlew testDebugUnitTest --tests "com.haise.jiyu.settings.AppModeSettingTest"`
Expected: FAIL s "unresolved reference: AppMode" (nebo `appMode`/`setAppMode`) - třída/členy zatím neexistují.

- [ ] **Krok 3: Přidat klíč, objekt konstant a repository metody**

V `SettingsRepository.kt` do `object SettingsKeys` přidat (za `val FAVORITE_SOURCE_IDS = stringSetPreferencesKey("favorite_source_ids")`):

```kotlin
    /**
     * Klasický režim (výběr ze všech zdrojů) vs. ComicK agregovaný režim (ComicK jako
     * jediný katalog, čtení se automaticky přeloží na skutečný zdroj) - viz
     * docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md.
     */
    val APP_MODE = stringPreferencesKey("app_mode")
```

Za `object ReadingMode { ... }` (před `@Singleton class SettingsRepository`) přidat:

```kotlin
object AppMode {
    const val SOURCES = "sources"
    const val COMICK  = "comick"
}
```

V těle třídy `SettingsRepository`, za `toggleFavoriteSource` (řádek s uzavírací `}` po `FAVORITE_SOURCE_IDS] = if (sourceId in current) ...`), přidat:

```kotlin

    val appMode: Flow<String> =
        dataStore.data.map { it[SettingsKeys.APP_MODE] ?: AppMode.SOURCES }

    suspend fun setAppMode(mode: String) =
        dataStore.edit { it[SettingsKeys.APP_MODE] = mode }
```

- [ ] **Krok 4: Spustit test znovu a ověřit, že prochází**

Run: `./gradlew testDebugUnitTest --tests "com.haise.jiyu.settings.AppModeSettingTest"`
Expected: PASS (3 testy).

- [ ] **Krok 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/settings/SettingsRepository.kt \
        app/src/test/kotlin/com/haise/jiyu/settings/AppModeSettingTest.kt
git commit -m "feat: pridat AppMode nastaveni (Klasicky/ComicK rezim)

Task 1 z docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md."
```

---

### Task 2: Přepínač v Nastavení → Zdroje

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/settings/SourcesSettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-en/strings.xml`, `values-fr/strings.xml`, `values-es/strings.xml`

**Interfaces:**
- Consumes: `SettingsRepository.appMode`/`setAppMode` z Task 1.
- Produces: `SettingsViewModel.appMode: StateFlow<String>`, `SettingsViewModel.setAppMode(mode: String)` — Task 4 (MainViewModel) bude analogicky číst `settings.appMode` přímo z repository, ne z tohohle VM (jsou to různé obrazovky/ViewModely).

- [ ] **Krok 1: Přidat `appMode`/`setAppMode` do `SettingsViewModel`**

V `SettingsViewModel.kt` za blok `showAdultSources`/`setShowAdultSources` (řádky ~416-421) přidat:

```kotlin
    // Výchozí "sources" (Klasický režim) - musí sedět s SettingsRepository.appMode, jinak by
    // přepínač po startu na okamžik ukázal špatný stav, než dorazí hodnota z DataStore.
    val appMode: StateFlow<String> = settings.appMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.SOURCES)

    fun setAppMode(mode: String) = viewModelScope.launch { settings.setAppMode(mode) }
```

(Ověřit, že `com.haise.jiyu.settings.AppMode` je v souboru dostupný - pokud `SettingsViewModel.kt` nemá `import com.haise.jiyu.settings.*` nebo se na `settings.*` odkazuje jinak, doplnit `import com.haise.jiyu.settings.AppMode`.)

- [ ] **Krok 2: Přidat řetězce do všech 4 lokalizací**

Do `app/src/main/res/values/strings.xml`, za řádek `settings_sources_adult_toggle_desc` (řádek 113):

```xml
    <string name="settings_sources_mode_section_title">Režim appky</string>
    <string name="settings_sources_mode_toggle_title">ComicK agregovaný režim</string>
    <string name="settings_sources_mode_toggle_desc">Místo výběru ze všech zdrojů appka nabídne jen ComicK jako katalog - u titulu i kapitol uvidíš překladatelské skupiny přesně jako na ComicK. Čtení appka automaticky přeloží na skutečný zdroj, který danou kapitolu má.</string>
```

Do `values-en/strings.xml`, za `settings_sources_adult_toggle_desc` (řádek 782):

```xml
    <string name="settings_sources_mode_section_title">App mode</string>
    <string name="settings_sources_mode_toggle_title">ComicK aggregated mode</string>
    <string name="settings_sources_mode_toggle_desc">Instead of picking from every source, the app shows only ComicK as the catalog - you\'ll see scan groups per title and per chapter exactly like on ComicK. Reading is automatically resolved to a real source that actually has that chapter.</string>
```

Do `values-fr/strings.xml`, za `settings_sources_adult_toggle_desc` (řádek 779):

```xml
    <string name="settings_sources_mode_section_title">Mode de l\'application</string>
    <string name="settings_sources_mode_toggle_title">Mode agrégé ComicK</string>
    <string name="settings_sources_mode_toggle_desc">Au lieu de choisir parmi toutes les sources, l\'application n\'affiche que ComicK comme catalogue - vous verrez les groupes de scan par titre et par chapitre exactement comme sur ComicK. La lecture est automatiquement redirigée vers une vraie source qui possède ce chapitre.</string>
```

Do `values-es/strings.xml`, za `settings_sources_adult_toggle_desc` (řádek 779):

```xml
    <string name="settings_sources_mode_section_title">Modo de la aplicación</string>
    <string name="settings_sources_mode_toggle_title">Modo agregado ComicK</string>
    <string name="settings_sources_mode_toggle_desc">En lugar de elegir entre todas las fuentes, la app muestra solo ComicK como catálogo - verás los grupos de scan por título y por capítulo igual que en ComicK. La lectura se resuelve automáticamente a una fuente real que tenga ese capítulo.</string>
```

- [ ] **Krok 3: Přidat toggle Row do `SourcesSettingsScreen.kt`**

V `SourcesSettingsScreen.kt` přidat čtení stavu (za `val showAdultSources by viewModel.showAdultSources.collectAsState()`):

```kotlin
    val appMode by viewModel.appMode.collectAsState()
```

Přidat novou `SettingsSection` hned za první sekci (`settings_sources_section_title` s tlačítky katalogu/CSS), před sekci `settings_sources_adult_section_title`, ať je nejdůležitější přepínač appky nahoře:

```kotlin
                Spacer(Modifier.height(12.dp))

                // ── Režim appky (Klasický vs. ComicK agregovaný) ──────────────
                SettingsSection(title = stringResource(R.string.settings_sources_mode_section_title)) {
                    val isComicKMode = appMode == AppMode.COMICK
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isComicKMode,
                                role = Role.Switch,
                                onValueChange = { viewModel.setAppMode(if (it) AppMode.COMICK else AppMode.SOURCES) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_sources_mode_toggle_title), color = TextPrimary, fontSize = 14.sp)
                            Text(stringResource(R.string.settings_sources_mode_toggle_desc), color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isComicKMode,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedThumbColor = GlowViolet, checkedTrackColor = GlowViolet.copy(alpha = 0.5f)),
                        )
                    }
                }
```

Doplnit import `com.haise.jiyu.settings.AppMode`, pokud tam ještě není (zkontrolovat existující importy `com.haise.jiyu.settings.*` v hlavičce souboru).

- [ ] **Krok 4: Build a manuální ověření**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

(Manuální ověření přepínače na zařízení proběhne až po Task 4, kdy má viditelný efekt.)

- [ ] **Krok 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/settings/SettingsViewModel.kt \
        app/src/main/kotlin/com/haise/jiyu/ui/settings/SourcesSettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-fr/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat: prepinac ComicK agregovaneho rezimu v Nastaveni - Zdroje

Task 2 z docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md."
```

---

### Task 3: Znovu zaregistrovat ComicK jako zdroj

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/SourceManager.kt`

**Interfaces:**
- Consumes: `ComicKSource` (existující třída, `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt`, beze změny).
- Produces: `SourceManager` opět obsahuje zdroj s `id = "comick"`, dostupný přes `sourceManager.getById("comick")` a v `staticSources`.

**Důležitá poznámka k dočasnému stavu:** Dokud nebude hotový Sub-projekt 3 (motor pro křížové vyhledání zdroje), otevření kapitoly u ComicK titulu půjde běžnou čtečkou, která u ComicK nefunguje (`getPageList()` nevrací použitelné obrázky - proto byl zdroj původně odstraněný, viz komentář níž). To je očekávaný, dočasný stav v rámci postupné dodávky - řeší ho až Sub-projekt 3. Uživatel v ComicK režimu prohlíží katalog/kapitoly/skupiny normálně, jen ještě nemá odangličenou ochranu proti kliknutí na "Číst".

- [ ] **Krok 1: Přidat import a constructor parametr**

V `SourceManager.kt` přidat import (za `import com.haise.jiyu.source.demonicscans.DemonicScansSource`):

```kotlin
import com.haise.jiyu.source.comick.ComicKSource
```

Přidat constructor parametr (za `mangaPlusSource: MangaPlusSource,`):

```kotlin
    comicKSource: ComicKSource,
```

- [ ] **Krok 2: Zpátky zapojit do `staticSources` a upravit komentář**

Nahradit blok komentáře (řádky ~143-148):

```kotlin
        // ComicK (api.comick.dev) odstraněno 2026-07-27 - web i API teď fungují jen jako
        // "tracker" (odkazuje na oficiální licencované platformy jako Tappytoon/MangaPlus),
        // reálné stránky kapitol (md_images) API nevrací a ani samotný web comick.dev je
        // v čtečce nezobrazí (ověřeno naživo v prohlížeči) - žádné reálné obrázky ke stažení.
        // Metadata/seznam kapitol by šly, ale appka bez čitelných stránek by byla zavádějící.
        // Viz ComicKSource.kt / ComicKSourceTest.kt (ponecháno pro případ, že by se to vrátilo).
```

za:

```kotlin
        // ComicK (api.comick.dev) bylo 2026-07-27 vypnuto, protože web i API fungují jen
        // jako "tracker" (odkazuje na oficiální licencované platformy) a getPageList()
        // nevrací použitelné obrázky - jako běžný ČTECÍ zdroj proto pořád nefunguje.
        // Znovu zapojeno 2026-08-05 jako podklad pro ComicK agregovaný režim (viz
        // docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md) - appka
        // ho používá k prohlížení katalogu/metadat/skupin, NE ke čtení. Ochrana proti
        // omylem otevřené nečitelné kapitole je úkol Sub-projektu 3 (motor pro křížové
        // vyhledání skutečného zdroje) - do té doby otevření kapitoly u ComicK titulu
        // skončí chybou v čtečce, ne pádem appky.
        comicKSource,
```

(Zbytek seznamu `staticSources` za tímhle blokem - `hitomiSource, nhentaiSource, ...` - zůstává beze změny, jen se `comicKSource,` vloží na místo starého komentáře.)

- [ ] **Krok 3: Build a test existující `ComicKSourceTest`**

Run: `./gradlew compileDebugKotlin testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: BUILD SUCCESSFUL, existující testy ComicK zdroje (nezávislé na registraci v `SourceManager`) prochází beze změny.

Run: `./gradlew testDebugUnitTest --tests "com.haise.jiyu.source.SourceManagerTest"` (pokud takový test existuje - zkontrolovat `app/src/test/kotlin/com/haise/jiyu/source/` a spustit, ať se ověří, že přidání dalšího zdroje nerozbilo nic, co počítá s pevným seznamem/počtem zdrojů).
Expected: PASS. Pokud test počítá s přesným počtem zdrojů nebo jejich seznamem, upravit očekávanou hodnotu o nově přidaný ComicK.

- [ ] **Krok 4: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/SourceManager.kt
git commit -m "feat: znovu zaregistrovat ComicK jako zdroj (jen prohlizeni/metadata)

Task 3 z docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md.
Cteni pres ComicK zatim neni chranene - resi az Sub-projekt 3."
```

---

### Task 4: Navigace — "Procházet" respektuje režim appky

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/MainViewModel.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/MainScreen.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `SettingsRepository.appMode`/`AppMode` z Task 1.
- Produces: `browseRoute(appMode: String): String` (sdílená pomocná funkce), použitá na 3 místech (bottom-nav tab + 2× `onOpenBrowse`).

**Proč se cesty (routes) nepřesměrovávají, ale počítají dynamicky:** Bottom-nav pozná aktivní záložku přesnou shodou `it.route == tab.route` (`MainScreen.kt`, `NavigationBarItem.selected`). Kdyby `onOpenBrowse`/klik na záložku v ComicK režimu navigoval na jinou cestu než tu, kterou má `tab.route` nastavenou, záložka by se přestala zvýrazňovat jako aktivní a `saveState`/`restoreState` mezi záložkami by se rozbilo. Řešení: `tab.route` samotné se pro záložku "Procházet" počítá podle aktuálního režimu (`browseRoute(appMode)`), takže klik i zvýraznění vždy sedí.

- [ ] **Krok 1: Vystavit `appMode` z `MainViewModel`**

V `MainViewModel.kt` přidat (za `val newChaptersCount`):

```kotlin
    val appMode: StateFlow<String> = settings.appMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppMode.SOURCES)
```

Doplnit import `com.haise.jiyu.settings.AppMode`.

- [ ] **Krok 2: Přidat sdílenou pomocnou funkci `browseRoute`**

V `NavGraph.kt`, do `internal object Routes` (za `fun sourceBrowse(sourceId: String) = ...`), přidat:

```kotlin
    /**
     * Cesta, na kterou vede záložka "Procházet" - závisí na režimu appky (Task 4,
     * docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md). V ComicK
     * režimu appka nevykresluje jinak stejnou cestu, ale rovnou naviguje/zvýrazňuje
     * jinou cestu (source_browse/comick) - jinak by se rozbilo zvýraznění aktivní
     * záložky, které porovnává přesnou shodu cesty.
     */
    fun browseRoute(appMode: String): String =
        if (appMode == com.haise.jiyu.settings.AppMode.COMICK) sourceBrowse("comick") else BROWSE
```

- [ ] **Krok 3: Použít `browseRoute` v `MainScreen.kt`**

Změnit `rememberNavTabs()` tak, aby přijímal `appMode` a počítal cestu záložky "Procházet" dynamicky:

```kotlin
@Composable
private fun rememberNavTabs(appMode: String): List<NavTab> = listOf(
    NavTab(Routes.LIBRARY,  stringResource(R.string.main_screen_tab_library),  TablerIcons.Book,        TablerIcons.Book),
    NavTab(Routes.MY_LIST,  stringResource(R.string.main_screen_tab_list),     TablerIcons.ListCheck,   TablerIcons.ListCheck),
    NavTab(Routes.UPDATES,  stringResource(R.string.main_screen_tab_updates), TablerIcons.Compass,     TablerIcons.Compass),
    NavTab(Routes.browseRoute(appMode), stringResource(R.string.main_screen_tab_browse),  TablerIcons.Search,      TablerIcons.Search),
    NavTab(Routes.HISTORY,  stringResource(R.string.main_screen_tab_history), TablerIcons.History,     TablerIcons.History),
    NavTab(Routes.SETTINGS, stringResource(R.string.settings_title),          TablerIcons.User,        TablerIcons.User),
)
```

V `MainScreen()` číst `appMode` z `viewModel` a předat ho do `rememberNavTabs`:

```kotlin
    val newChaptersCount by viewModel.newChaptersCount.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val tabs = rememberNavTabs(appMode)
```

(Nahrazuje původní `val newChaptersCount by viewModel.newChaptersCount.collectAsState()` + `val tabs = rememberNavTabs()`.)

Zkontrolovat `showNavBar` logiku (řádky ~69-80) - `currentRoute != null && !currentRoute.startsWith(...)` porovnává s pevnými `Routes.X` konstantami, ne s `tab.route`, takže se `browseRoute` úpravou neovlivní a není potřeba ji měnit.

- [ ] **Krok 4: Použít `browseRoute` v `NavGraph.kt` u `onOpenBrowse`**

`JiyuNavGraph` potřebuje znát `appMode`, aby mohl spočítat `onOpenBrowse` cíl pro `LibraryScreen`/`MyListScreen`. Přidat parametr:

```kotlin
@Composable
fun JiyuNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.LIBRARY,
    appMode: String,
) {
```

V `composable(Routes.LIBRARY)` změnit:

```kotlin
                onOpenBrowse = { navController.navigate(Routes.browseRoute(appMode)) },
```

V `composable(Routes.MY_LIST)` změnit stejně:

```kotlin
                onOpenBrowse = { navController.navigate(Routes.browseRoute(appMode)) },
```

V `MainScreen.kt` upravit volání `JiyuNavGraph`:

```kotlin
            JiyuNavGraph(navController = navController, startDestination = startDestination, appMode = appMode)
```

- [ ] **Krok 5: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Pokud `JiyuNavGraph` má i jiná volací místa - např. v testech/preview - doplnit jim `appMode = AppMode.SOURCES` jako výchozí, ať nespadne kompilace. Zkontrolovat `grep -rn "JiyuNavGraph(" app/src`.)

- [ ] **Krok 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/MainViewModel.kt \
        app/src/main/kotlin/com/haise/jiyu/ui/navigation/MainScreen.kt \
        app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt
git commit -m "feat: zalozka Prochazet respektuje ComicK agregovany rezim

Task 4 z docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md."
```

---

### Task 5: Závěrečné ověření celku (bez commitu — vše už je commitnuté z Tasků 1-4)

- [ ] **Krok 1: Celá testovací sada**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, žádný regresní pád.

- [ ] **Krok 2: Lint**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Krok 3: `git status` je čistý**

Run: `git status --short`
Expected: prázdný výstup — všechno z Tasků 1-4 je už commitnuté, nic nezůstalo neuložené.

- [ ] **Krok 4: Manuální ověření (na zařízení, pokud je připojené)**

1. V Nastavení → Zdroje zapnout "ComicK agregovaný režim".
2. Přejít na záložku "Procházet" ve spodní liště - musí ukázat ComicK vyhledávání/populární (ne výběr ze zdrojů) a záložka musí zůstat zvýrazněná jako aktivní.
3. V Knihovně (prázdný stav, pokud lze simulovat) kliknout "Procházet mangu" - musí vést na stejné ComicK browse.
4. Vypnout přepínač zpět, ověřit, že "Procházet" ukazuje zase původní výběr ze všech zdrojů.
5. V ComicK režimu otevřít libovolný titul a kliknout na kapitolu - OČEKÁVANÝ, dočasný stav: čtečka skončí chybou (žádné obrázky), appka nespadne. To je záměrné - opraví Sub-projekt 3.

---

## Self-review (proveden při psaní plánu)

1. **Pokrytí specu:** Sub-projekt 1 ze specifikace pokrývá — datový model (Task 1), UI přepínač (Task 2), navigace (Task 4), re-registrace ComicK potřebná pro smysluplné manuální ověření (Task 3, i když spec ji formálně zařadila mezi "mimo rozsah" - bez ní by přepnutí režimu vedlo na prázdnou obrazovku a nešlo by ověřit, že to funguje).
2. **Oprava oproti designu:** Design dokument popisoval "destinace `Routes.BROWSE` se větví podle režimu" - při psaní plánu se ukázalo, že by to rozbilo zvýraznění aktivní záložky (`tab.route` pevně `Routes.BROWSE`). Plán místo toho dělá cestu samotnou závislou na režimu (`browseRoute()`), použitou konzistentně na všech 3 místech. Design dokument bude potřeba dodatečně opravit, aby odpovídal (poznamenáno, ale nemění to chování pro uživatele).
3. **Bez placeholderů:** Zkontrolováno - všechny kroky mají konkrétní kód, ne popis "přidej podporu pro X".
4. **Typová konzistence:** `AppMode.SOURCES`/`AppMode.COMICK` (String konstanty, ne enum - kvůli konzistenci s `ReadingDirection`/`ReadingMode` ve stejném souboru) používány stejně ve `SettingsRepository`, `SettingsViewModel`, `MainViewModel`, `NavGraph.kt`.

## Execution Handoff

Plán je hotový a uložený v `docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md`. Dvě možnosti provedení:

1. **Subagent-Driven (doporučeno)** — čerstvý subagent na každý task, review mezi kroky.
2. **Inline Execution** — provedu úkoly v týhle konverzaci, dávkově s kontrolními body.

Kterou variantu chceš?