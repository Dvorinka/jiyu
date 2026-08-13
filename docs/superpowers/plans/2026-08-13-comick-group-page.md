# ComicK Group Page (Sub-projekt 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Klik na překladatelskou skupinu u ComicK kapitoly otevře novou obrazovku s mřížkou dalších titulů, které ta skupina překládá.

**Architecture:** Nová `ComicKSource.getGroup(slug)` volá `GET /group/{slug}` a vrátí `GroupInfo` (jméno, počet sledujících, počet kapitol, seznam titulů). `GlassChapterRow` přestane zobrazovat `scanlationGroup` jako prostý text a místo toho vykreslí každou skupinu z `deserializeChapterGroups(chapter.groupsJson)` jako klikací chip. Klik naviguje na novou route `group/{slug}?title={title}`, kde `GroupViewModel` (vzor `SourceResolverViewModel`) dotáhne `GroupInfo` a `GroupScreen` (vzor `SourceBrowseScreen`) ho zobrazí jako hlavičku + `LazyVerticalGrid`. Klik na titul v mřížce otevře normální ComicK detail přes `MangaRepository.openPreview()` — stejná cesta jako dnešní klik v Procházet/GlobalSearch.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, OkHttp, org.json, Coil (SubcomposeAsyncImage), JUnit + MockWebServer pro testy.

**Spec:** `docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md`, sekce "## Sub-projekt 4: Stránka skupiny".

## Global Constraints

- Práce se dělá přímo na `master`, žádná feature branch (zavedená konvence celé iniciativy).
- Po každém tasku: `compileDebugKotlin` musí projít, testy (pokud task nějaké přidává) musí být zelené, pak commit.
- Žádné nové závislosti — `org.json` (už používá `ComicKSource`/`MangaRepository`), Coil, Hilt, vše už je v projektu.
- Room migrace se NEPOTŘEBUJE — `ChapterEntity.groupsJson` už existuje ze Sub-projektu 2.

---

### Task 1: `ComicKSource.getGroup()` + `GroupInfo`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Produces: `data class GroupInfo(val title: String, val followCount: Int, val chapterCount: Int, val comics: List<SManga>)` (top-level v `ComicKSource.kt`, mimo třídu — stejný vzor jako `ComicKTitleInfo`). `suspend fun ComicKSource.getGroup(slug: String): GroupInfo`.

Živě ověřený tvar odpovědi `GET https://api.comick.dev/group/{slug}`:

```json
{
  "group": {"id": 12401, "title": "Asura", "slug": "asura", "follow_count": 51, "chapter_count": 30711, ...},
  "comics": [{"title": "...", "slug": "...", "country": "kr", "md_covers": [{"b2key": "..."}], ...}, ...],
  "chapters": [...],
  "total": 30711,
  "limit": 1000
}
```

`comics[]` má identický tvar jako položky `/v1.0/search` — dá se rovnou předat existující privátní `parseComicList(JSONArray)` beze změny.

- [ ] **Step 1: Napsat padající test**

Přidat do `ComicKSourceTest.kt` (za poslední `@Test` metodu, před uzavírací `}` třídy):

```kotlin
    @Test
    fun `getGroup parses group info and reuses the search-result comic parser for comics`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/group/asura" -> MockResponse().setBody(
                        """{"group": {"id": 12401, "title": "Asura", "slug": "asura", "follow_count": 51, "chapter_count": 30711},
                            "comics": [{"title": "Lord Xueying", "slug": "lord-xue-ying", "country": "cn", "md_covers": [{"b2key": "cover.jpg"}]}],
                            "chapters": [], "total": 30711, "limit": 1000}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val info = source.getGroup("asura")
        assertEquals("Asura", info.title)
        assertEquals(51, info.followCount)
        assertEquals(30711, info.chapterCount)
        assertEquals(1, info.comics.size)
        assertEquals("Lord Xueying", info.comics[0].title)
        assertEquals("MANHUA", info.comics[0].contentType)
        assertTrue(info.comics[0].coverUrl!!.endsWith("cover.jpg"))
    }

    @Test
    fun `getGroup falls back to the slug as title and zero counts when the group object is missing fields`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/group/no-name" -> MockResponse().setBody(
                        """{"group": {}, "comics": []}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val info = source.getGroup("no-name")
        assertEquals("no-name", info.title)
        assertEquals(0, info.followCount)
        assertEquals(0, info.chapterCount)
        assertTrue(info.comics.isEmpty())
    }
```

Ověřit, že `ComicKSourceTest.kt` už má importy `assertTrue`/`assertEquals`/`runTest`/`Dispatcher`/`MockResponse`/`RecordedRequest` (ano, používají je existující testy v souboru — žádný nový import netřeba).

- [ ] **Step 2: Spustit testy a ověřit, že padají na "unresolved reference: getGroup"**

Run (PowerShell, `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` nejdřív, viz `project_jiyu_environment` paměť):
```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: FAIL, kompilace testu spadne na `Unresolved reference: getGroup`.

- [ ] **Step 3: Přidat `GroupInfo` a `getGroup()` do `ComicKSource.kt`**

Přidat novou veřejnou metodu do třídy `ComicKSource` — umístit hned za `getAlternateTitles`/`getTitleInfo` (viz oddíl `// ─── Detail mangy ───` v souboru), a `GroupInfo` jako top-level data class na konec souboru vedle `ComicKTitleInfo`:

```kotlin
    /**
     * Vrátí metadata překladatelské skupiny + seznam titulů, které přeložila
     * (viz [Sub-projekt 4 v design docu]). `comics[]` v odpovědi má stejný
     * tvar jako položky `/v1.0/search`, proto se parsuje stejnou [parseComicList].
     */
    suspend fun getGroup(slug: String): GroupInfo =
        withContext(Dispatchers.IO) {
            val json = getObject("$apiBase/group/$slug")
            val group = json.optJSONObject("group") ?: JSONObject()
            GroupInfo(
                title = group.optString("title").ifBlank { slug },
                followCount = group.optInt("follow_count", 0),
                chapterCount = group.optInt("chapter_count", 0),
                comics = parseComicList(json.optJSONArray("comics") ?: JSONArray()),
            )
        }
```

Na konec souboru (za `data class ComicKTitleInfo`) přidat:

```kotlin
/** Výsledek [ComicKSource.getGroup] - metadata skupiny + tituly, které přeložila. */
data class GroupInfo(
    val title: String,
    val followCount: Int,
    val chapterCount: Int,
    val comics: List<SManga>,
)
```

- [ ] **Step 4: Spustit testy znovu a ověřit, že projdou**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: PASS, všechny testy v souboru zelené (existující i 2 nové).

- [ ] **Step 5: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "feat: pridat ComicKSource.getGroup() pro stranku skupiny (Sub-projekt 4)"
```

---

### Task 2: `MangaRepository.deserializeChapterGroups()`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/data/repository/MangaRepository.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/data/repository/SerializeChapterGroupsTest.kt`

**Interfaces:**
- Consumes: `SGroup(name: String, slug: String?)` z `com.haise.jiyu.source.SGroup` (existuje).
- Produces: `internal fun deserializeChapterGroups(json: String?): List<SGroup>` (top-level v `MangaRepository.kt`, vedle existující `serializeChapterGroups`).

- [ ] **Step 1: Napsat padající testy**

Přidat do `SerializeChapterGroupsTest.kt` (za poslední `@Test`, před uzavírací `}`):

```kotlin
    @Test
    fun `deserializeChapterGroups returns empty list for null or blank input`() {
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups(null))
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups(""))
    }

    @Test
    fun `deserializeChapterGroups round-trips what serializeChapterGroups produced`() {
        val original = listOf(SGroup(name = "Asura", slug = "asura"), SGroup(name = "Official", slug = null))
        val json = serializeChapterGroups(original)
        assertEquals(original, deserializeChapterGroups(json))
    }

    @Test
    fun `deserializeChapterGroups returns empty list for malformed JSON instead of throwing`() {
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups("not json"))
    }
```

`SGroup` už je datová třída (`data class`), takže `assertEquals` na seznamy funguje strukturálně beze změny testu.

- [ ] **Step 2: Spustit testy a ověřit, že padají na "unresolved reference"**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.repository.SerializeChapterGroupsTest" --console=plain
```
Expected: FAIL, `Unresolved reference: deserializeChapterGroups`.

- [ ] **Step 3: Přidat `deserializeChapterGroups` do `MangaRepository.kt`**

Přidat hned za existující `serializeChapterGroups` (na konci souboru):

```kotlin
/** Protějšek [serializeChapterGroups] - přečte `ChapterEntity.groupsJson` zpátky do [SGroup] seznamu. */
internal fun deserializeChapterGroups(json: String?): List<SGroup> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SGroup(
                name = obj.optString("name"),
                slug = if (obj.isNull("slug")) null else obj.optString("slug").ifBlank { null },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
```

- [ ] **Step 4: Spustit testy znovu a ověřit, že projdou**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.repository.SerializeChapterGroupsTest" --console=plain
```
Expected: PASS.

- [ ] **Step 5: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/data/repository/MangaRepository.kt app/src/test/kotlin/com/haise/jiyu/data/repository/SerializeChapterGroupsTest.kt
git commit -m "feat: pridat MangaRepository.deserializeChapterGroups() pro stranku skupiny (Sub-projekt 4)"
```

---

### Task 3: Route + string zdroje

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `Routes.GROUP = "group/{slug}?title={title}"`, `fun Routes.group(slug: String, title: String): String`. String resources `group_screen_loading`, `group_screen_load_failed`, `group_screen_no_titles`, `group_screen_follow_count`, `group_screen_chapter_count`.

Tenhle task jen připraví route/stringy beze skutečné obrazovky (ta přijde v Task 4-5) — drží se tak malý, testovatelný krok (kompilace) předtím, než přibude větší UI kód.

- [ ] **Step 1: Přidat route konstantu a builder do `Routes` objektu**

V `NavGraph.kt`, do `object Routes` přidat za `const val SOURCE_RESOLVER = ...` (řádek 70):

```kotlin
    const val GROUP = "group/{slug}?title={title}"
```

A do sekce s funkcemi (za `fun sourceResolver(...)`, řádek ~103) přidat:

```kotlin
    fun group(slug: String, title: String) =
        "group/${android.net.Uri.encode(slug)}?title=${android.net.Uri.encode(title)}"
```

- [ ] **Step 2: Přidat string zdroje**

Do `app/src/main/res/values/strings.xml`, za blok `resolver_*` (řádky 416-423), přidat:

```xml
    <string name="group_screen_loading">Načítám skupinu…</string>
    <string name="group_screen_load_failed">Nepodařilo se načíst skupinu.</string>
    <string name="group_screen_no_titles">Tahle skupina zatím nemá žádné tituly.</string>
    <string name="group_screen_follow_count">%1$d sledujících</string>
    <string name="group_screen_chapter_count">%1$d přeložených kapitol</string>
```

- [ ] **Step 3: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL (route/stringy zatím nikde nepoužité, ale platný Kotlin/XML).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt app/src/main/res/values/strings.xml
git commit -m "feat: pridat route a string zdroje pro stranku skupiny (Sub-projekt 4)"
```

---

### Task 4: `GroupViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/group/GroupViewModel.kt`

**Interfaces:**
- Consumes: `ComicKSource.getGroup(slug): GroupInfo` (Task 1), `MangaRepository.openPreview(manga: SManga): String` (existuje, používá ho `SourceBrowseViewModel.openManga`).
- Produces: `class GroupViewModel` s `StateFlow`y: `title: StateFlow<String>` (okamžitě z nav argumentu), `groupInfo: StateFlow<GroupInfo?>`, `loading: StateFlow<Boolean>`, `error: StateFlow<String?>`, `openingManga: StateFlow<SManga?>`, `openError: StateFlow<String?>`; metoda `fun openManga(manga: SManga, onOpened: (String) -> Unit)`.

Vzor: `SourceResolverViewModel` (`init` blok rovnou spouští načtení, `SavedStateHandle` pro nav argumenty) + `SourceBrowseViewModel.openManga`/`_openingManga`/`_openError` (přesně stejná logika, jen jiný zdroj dat).

Žádný dedikovaný unit test pro tenhle ViewModel — stejný precedens jako `SourceResolverViewModel` a `SourceBrowseViewModel` (v repu nemají test, ověřují se manuálně kvůli závislosti na Compose navigaci/`Context`; viz "Testování" sekce Sub-projektu 1 a 3 v design docu).

- [ ] **Step 1: Napsat `GroupViewModel.kt`**

```kotlin
package com.haise.jiyu.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ComicKSource
import com.haise.jiyu.source.comick.GroupInfo
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Obrazovka "Skupina" - další tituly, které daná ComicK překladatelská skupina přeložila. */
@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicKSource: ComicKSource,
    private val repository: MangaRepository,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    /** Název skupiny přijde jako nav argument (z `chapter.groups`), takže hlavička nemusí čekat na network round-trip. */
    private val _title = MutableStateFlow(savedStateHandle.get<String>("title").orEmpty())
    val title: StateFlow<String> = _title.asStateFlow()

    private val _groupInfo = MutableStateFlow<GroupInfo?>(null)
    val groupInfo: StateFlow<GroupInfo?> = _groupInfo.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openingManga = MutableStateFlow<SManga?>(null)
    val openingManga: StateFlow<SManga?> = _openingManga.asStateFlow()

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val info = comicKSource.getGroup(slug)
                _groupInfo.value = info
                if (info.title.isNotBlank()) _title.value = info.title
            } catch (e: Exception) {
                e.report("group:getGroup:$slug")
                _error.value = e.toFriendlyMessage()
            } finally {
                _loading.value = false
            }
        }
    }

    fun openManga(manga: SManga, onOpened: (String) -> Unit) {
        if (_openingManga.value != null) return
        _openingManga.value = manga
        viewModelScope.launch {
            try {
                val id = repository.openPreview(manga)
                onOpened(id)
            } catch (e: Exception) {
                e.report("group:openManga")
                _openError.value = e.toFriendlyMessage()
            } finally {
                _openingManga.value = null
            }
        }
    }

    fun clearOpenError() { _openError.value = null }
}
```

- [ ] **Step 2: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL. (`GroupViewModel` zatím nikde nepoužitý, ale platný Kotlin - Hilt anotace se ověří až `kspDebugKotlin`, což `compileDebugKotlin` v sobě zahrnuje.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/group/GroupViewModel.kt
git commit -m "feat: pridat GroupViewModel pro stranku skupiny (Sub-projekt 4)"
```

---

### Task 5: `GroupScreen` composable

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/group/GroupScreen.kt`

**Interfaces:**
- Consumes: `GroupViewModel` (Task 4) - všechny jeho `StateFlow`y a `openManga`/`clearOpenError`.
- Produces: `@Composable fun GroupScreen(onBack: () -> Unit, onOpenManga: (String) -> Unit, viewModel: GroupViewModel = hiltViewModel())`.

Vzor: `SourceResolverScreen` (top bar s nadpisem + podnadpisem, loading/empty/list větvení) + `SourceBrowseScreen`'s `BrowseMangaCard`/`LazyVerticalGrid` (karta se stejným střihem, jen jako nová privátní kopie v tomhle souboru - zavedená konvence, karty se v kódu nesdílí mezi soubory).

- [ ] **Step 1: Napsat `GroupScreen.kt`**

```kotlin
package com.haise.jiyu.ui.group

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.haise.jiyu.R
import com.haise.jiyu.source.SManga
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.GlowCyan
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Book

@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenManga: (String) -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val openingManga by viewModel.openingManga.collectAsState()
    val openError by viewModel.openError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openError) {
        openError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOpenError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = null, tint = TextPrimary)
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val info = groupInfo
                    if (info != null) {
                        Text(
                            stringResource(R.string.group_screen_follow_count, info.followCount) +
                                " · " + stringResource(R.string.group_screen_chapter_count, info.chapterCount),
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenGradient)
                .padding(innerPadding),
        ) {
            val comics = groupInfo?.comics.orEmpty()
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.group_screen_loading), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.group_screen_load_failed), color = TextSecondary, fontSize = 14.sp)
                }
                comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.group_screen_no_titles), color = TextSecondary, fontSize = 14.sp)
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp + navBottom),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(comics, key = { it.sourceId + it.url }) { manga ->
                            val isOpening = openingManga?.let { it.sourceId == manga.sourceId && it.url == manga.url } == true
                            GroupTitleCard(manga = manga, isLoading = isOpening, onClick = {
                                viewModel.openManga(manga, onOpenManga)
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Stejný vizuální střih jako `SourceBrowseScreen.BrowseMangaCard` - karty se v kódu nesdílí mezi soubory (zavedená konvence). */
@Composable
private fun GroupTitleCard(manga: SManga, isLoading: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "group_card_scale",
    )

    Box(
        modifier = Modifier
            .aspectRatio(0.68f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { if (!isLoading) onClick() },
                )
            },
    ) {
        SubcomposeAsyncImage(
            model = manga.coverUrl,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            val state = painter.state
            if (manga.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)), contentAlignment = Alignment.Center) {
                    Icon(TablerIcons.Book, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                }
            } else {
                SubcomposeAsyncImageContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xEA070B14)))),
        )

        Text(
            text = manga.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 6.dp),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                JiyuLoadingIndicator(size = 28.dp, strokeWidth = 3.dp)
            }
        }
    }
}
```

Poznámka: `Modifier.size(40.dp)` uvnitř `GroupTitleCard` vyžaduje `androidx.compose.foundation.layout.size` - přidat tento import (chybí v seznamu výš, doplnit vedle ostatních `layout.*` importů).

- [ ] **Step 2: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL. Pokud spadne na chybějící import (`size`), doplnit podle poznámky výš.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/group/GroupScreen.kt
git commit -m "feat: pridat GroupScreen (mrizka titulu skupiny) pro Sub-projekt 4"
```

---

### Task 6: Klikací chipy skupin v `GlassChapterRow` + navigační wiring

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `GroupScreen` (Task 5), `Routes.GROUP`/`Routes.group()` (Task 3), `deserializeChapterGroups` (Task 2), `SGroup` (existuje).
- Produces: `GlassChapterRow` získá nový parametr `onGroupClick: (SGroup) -> Unit = {}`; `MangaDetailScreen` získá nový parametr `onOpenGroup: (slug: String, title: String) -> Unit = { _, _ -> }`.

Poslední task téhle iniciativy - propojí všechno dohromady a udělá funkci dosažitelnou z appky.

- [ ] **Step 1: Přidat importy do `MangaDetailScreen.kt`**

Za `import com.haise.jiyu.data.db.entity.DownloadStatus` (řádek 86) přidat:

```kotlin
import com.haise.jiyu.data.repository.deserializeChapterGroups
import com.haise.jiyu.source.SGroup
```

- [ ] **Step 2: Přidat `onOpenGroup` parametr do `MangaDetailScreen`**

V signatuře `fun MangaDetailScreen(...)` (řádek 101-108) přidat za `onResolveChapter`:

```kotlin
    onOpenGroup: (slug: String, title: String) -> Unit = { _, _ -> },
```

- [ ] **Step 3: Přepsat `Text(chapter.scanlationGroup)` na klikací chipy v `GlassChapterRow`**

Nahradit (řádky 906-914, přidat `onGroupClick` parametr a `@OptIn`):

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlassChapterRow(
    chapter: ChapterEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onMarkReadUpTo: () -> Unit,
    onMarkAllOlderRead: () -> Unit = {},
    onMarkAllNewerUnread: () -> Unit = {},
    onToggleRead: () -> Unit = {},
    onGroupClick: (SGroup) -> Unit = {},
) {
```

A nahradit tělo (řádky 937-939):

```kotlin
                if (!chapter.scanlationGroup.isNullOrBlank()) {
                    Text(text = chapter.scanlationGroup, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
```

za:

```kotlin
                val groups = remember(chapter.groupsJson) { deserializeChapterGroups(chapter.groupsJson) }
                if (groups.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groups.forEach { group ->
                            Text(
                                text = group.name,
                                color = if (group.slug != null) Violet else TextSecondary.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (group.slug != null) {
                                    Modifier.clickable { onGroupClick(group) }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                } else if (!chapter.scanlationGroup.isNullOrBlank()) {
                    Text(text = chapter.scanlationGroup, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
```

`groups.isEmpty()` nastane u všech ne-ComicK zdrojů (`groupsJson == null`, `deserializeChapterGroups` vrátí `emptyList()`) - beze změny chování spadnou na starý `scanlationGroup` text. `FlowRow` a `Arrangement` už jsou v souboru importované (`ExperimentalLayoutApi` na řádku 17, `FlowRow` na řádku 18).

- [ ] **Step 4: Propojit oba volání `GlassChapterRow` s `onOpenGroup`**

Na obou místech, kde se `GlassChapterRow(...)` volá (řádky ~814-822 a ~827-835), přidat za `onToggleRead = ...`:

```kotlin
                                onGroupClick = { group -> group.slug?.let { onOpenGroup(it, group.name) } },
```

(s odpovídajícím odsazením podle toho, který ze dvou bloků se upravuje - druhý blok má o 4 mezery míň odsazení než první, viz existující řádky).

- [ ] **Step 5: Zaregistrovat `GROUP` route a napojit `onOpenGroup` v `NavGraph.kt`**

Import na začátek souboru (za `import com.haise.jiyu.ui.goals.ReadingGoalsScreen`, abecedně):

```kotlin
import com.haise.jiyu.ui.group.GroupScreen
```

V `composable(route = Routes.DETAIL, ...)` bloku (řádky 189-203) přidat za `onResolveChapter = ...`:

```kotlin
                onOpenGroup = { slug, title -> navController.navigate(Routes.group(slug, title)) },
```

Za celý blok `composable(route = Routes.SOURCE_RESOLVER, ...)` (končí řádkem 244) přidat novou route:

```kotlin
        composable(
            route = Routes.GROUP,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            GroupScreen(
                onBack = { navController.popBackStack() },
                onOpenManga = { mangaId -> navController.navigate(Routes.detail(mangaId)) },
            )
        }
```

- [ ] **Step 6: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Spustit celou testovací sadu a lint**

```
.\gradlew.bat testDebugUnitTest lintDebug --console=plain
```
Expected: BUILD SUCCESSFUL, všechny testy zelené (Task 1 a 2 nové testy včetně).

- [ ] **Step 8: Manuální ověření na zařízení/emulátoru**

1. Přepnout appku do ComicK režimu (Nastavení).
2. Otevřít libovolný ComicK titul s přeloženými kapitolami (např. Solo Leveling).
3. Ověřit, že se u kapitol zobrazuje jméno skupiny jako klikací (jinou barvou/podtržené) text, ne obyčejný šedý text jako dřív.
4. Kliknout na jméno skupiny → ověřit, že se otevře nová obrazovka s hlavičkou (název skupiny, počet sledujících, počet kapitol) a mřížkou titulů.
5. Kliknout na titul v mřížce → ověřit, že se otevře jeho ComicK detail (ne pád, ne prázdná obrazovka).
6. Zpět tlačítkem ověřit návrat na obrazovku skupiny, pak zpět znovu na detail titulu.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt
git commit -m "feat: propojit klikaci chipy skupin s novou strankou skupiny (Sub-projekt 4)"
```

---

## Self-Review (proveden při psaní plánu)

**Pokrytí spec:** ComicK API vrstva (Task 1), UI vrstva `groupsJson` → chipy (Task 2 + 6), nová obrazovka s hlavičkou + mřížkou (Task 3-5), navigace (Task 3 + 6), klik na titul → normální ComicK detail (Task 4 `openManga` + Task 5 `onOpenManga`) - všechny body sekce "Sub-projekt 4" ve spec dokumentu mají odpovídající task.

**Typová konzistence:** `GroupInfo` (Task 1) používá pole `title/followCount/chapterCount/comics` - přesně tahle jména používá `GroupViewModel` (Task 4) i `GroupScreen` (Task 5). `deserializeChapterGroups` (Task 2) vrací `List<SGroup>` - přesně to, co `GlassChapterRow` (Task 6) iteruje. `Routes.group(slug, title)` (Task 3) bere stejné dva parametry, jaké `onOpenGroup: (slug: String, title: String) -> Unit` (Task 6) posílá.

**Mimo rozsah** (shoda se spec dokumentem): `chapters[]` feed skupiny, stránkování mřížky, filtrace/řazení titulů - nikde v plánu implementováno, záměrně.
