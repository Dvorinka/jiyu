# Motor pro křížové vyhledání zdroje — Implementační plán (Sub-projekt 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nahradit dočasnou "Čtení přes ComicK ještě nefunguje" hlášku (Sub-projekt 2) skutečným vyřešením na reálný, čitelný zdroj — appka najde mezi ~180 zdroji ty, co daný ComicK titul mají, ukáže je s mírou úplnosti na nové obrazovce a po potvrzení otevře čtečku na odpovídajícím reálném zdroji.

**Architecture:** Nová injectovatelná třída `ComicKChapterResolver` (hledání + shoda názvu + úplnost + in-memory cache na úrovni titulu) je jádro. Nová obrazovka `SourceResolverScreen`/`SourceResolverViewModel` ji spotřebovává a nahrazuje dnešní snackbar v `MangaDetailScreen.kt`. Po výběru kandidáta appka použije existující `MangaRepository.openPreview()`/`refreshChapters()` (stejná cesta jako dnešní klik na výsledek v Procházet) a naviguje do čtečky — od tohoto bodu appka pracuje úplně normálně, žádný nový kód v `ReaderViewModel` není potřeba (ten zůstává jako poslední pojistka pro `sourceId == "comick"`, beze změny).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation-Compose, kotlinx.coroutines (`async`/`awaitAll`/`Semaphore` pro omezené paralelní hledání).

## Global Constraints

- Appka NIKDY neotevře zdroj potichu — i jediný kandidát se musí zobrazit a potvrdit kliknutím (design doc, klíčové rozhodnutí 3).
- Zúžení kandidátů podle typu obsahu (manga/manhwa/manhua jako jedna skupina, novely/komiksy nikdy) — design doc, klíčové rozhodnutí 2.
- Cache jen in-memory, po dobu běhu appky, na úrovni titulu (ne Room) — design doc, "Cache rozsah".
- Po vyřešení zdroje appka pracuje úplně normálně, žádný trvalý "přišlo z ComicK" stav — design doc, klíčové rozhodnutí 1.
- Design doc: `docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md`, sekce "Sub-projekt 3".

---

### Task 1: `ComicKChapterResolver` — jádro vyhledávacího motoru

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKChapterResolver.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKChapterResolverTest.kt`

**Interfaces:**
- Consumes: `SourceManager.getAll(): List<MangaSource>`, `MangaSource.search(query, page, filter): List<SManga>`, `MangaSource.getChapterList(manga): List<SChapter>`, `normalizeMangaTitle(title: String): String` (`com.haise.jiyu.util`), `SettingsRepository.favoriteSourceIds: Flow<Set<String>>`.
- Produces: `data class ResolvedCandidate(val source: MangaSource, val manga: SManga, val matchedChapterCount: Int, val hasRequestedChapter: Boolean, val isFavorite: Boolean)` a `ComicKChapterResolver.findCandidates(...)` — použije je Task 3 (ViewModel obrazovky).

- [ ] **Step 1: Napsat padající test**

Vytvořit `ComicKChapterResolverTest.kt`:

```kotlin
package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.FakeDataStore
import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.SourceManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSource(
    override val id: String,
    override val name: String,
    override val contentType: String,
    private val searchResults: List<SManga> = emptyList(),
    private val chapters: List<SChapter> = emptyList(),
    private val failSearch: Boolean = false,
) : MangaSource {
    override suspend fun search(query: String, page: Int, filter: MangaFilter) =
        if (failSearch) throw RuntimeException("boom") else searchResults
    override suspend fun getPopular(page: Int, filter: MangaFilter) = emptyList<SManga>()
    override suspend fun getMangaDetails(manga: SManga) = manga
    override suspend fun getChapterList(manga: SManga) = chapters
    override suspend fun getPageList(chapter: SChapter) = emptyList<com.haise.jiyu.source.Page>()
}

class ComicKChapterResolverTest {

    private lateinit var sourceManager: SourceManager
    private lateinit var settings: SettingsRepository
    private lateinit var resolver: ComicKChapterResolver

    @Before
    fun setUp() {
        sourceManager = mockk()
        settings = SettingsRepository(FakeDataStore())
        resolver = ComicKChapterResolver(sourceManager, settings)
    }

    @Test
    fun `only searches sources in the same content-type group as the ComicK title`() = runTest {
        val manhwaMatch = SManga(sourceId = "src-manhwa", url = "u1", title = "Solo Leveling", coverUrl = null)
        val manhwaSource = FakeSource("src-manhwa", "Manhwa Site", "MANHWA", searchResults = listOf(manhwaMatch), chapters = listOf(chapter(1f)))
        val novelSource = FakeSource("src-novel", "Novel Site", "NOVEL", searchResults = listOf(manhwaMatch.copy(sourceId = "src-novel")))
        coEvery { sourceManager.getAll() } returns listOf(manhwaSource, novelSource)

        val result = resolver.findCandidates("comick-id-1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(1, result.size)
        assertEquals("src-manhwa", result[0].source.id)
    }

    @Test
    fun `manga, manhwa and manhua sources are all treated as the same group`() = runTest {
        val match = SManga(sourceId = "src-manga", url = "u1", title = "Solo Leveling", coverUrl = null)
        val mangaSource = FakeSource("src-manga", "Manga Site", "MANGA", searchResults = listOf(match), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(mangaSource)

        // ComicK title is MANHWA, candidate source is generically tagged MANGA - must still match.
        val result = resolver.findCandidates("comick-id-2", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(1, result.size)
    }

    @Test
    fun `only keeps candidates whose normalized title matches`() = runTest {
        val wrongMatch = SManga(sourceId = "src-a", url = "u1", title = "A Completely Different Title", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(wrongMatch))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-3", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a source whose search throws is skipped, not propagated`() = runTest {
        val failing = FakeSource("src-fail", "Broken Site", "MANHWA", failSearch = true)
        coEvery { sourceManager.getAll() } returns listOf(failing)

        val result = resolver.findCandidates("comick-id-4", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `hasRequestedChapter is true when a candidate's chapter list contains a matching chapter number`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f), chapter(5f), chapter(5.5f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-5", "Solo Leveling", "MANHWA", requestedChapterNumber = 5f)

        assertEquals(1, result.size)
        assertTrue(result[0].hasRequestedChapter)
        assertEquals(3, result[0].matchedChapterCount)
    }

    @Test
    fun `hasRequestedChapter is false when no candidate chapter is close enough`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f), chapter(2f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-6", "Solo Leveling", "MANHWA", requestedChapterNumber = 99f)

        assertEquals(1, result.size)
        assertTrue(!result[0].hasRequestedChapter)
    }

    @Test
    fun `requestedChapterNumber null means hasRequestedChapter is always true`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-7", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result[0].hasRequestedChapter)
    }

    @Test
    fun `favorite sources are marked and sorted first`() = runTest {
        settings.toggleFavoriteSource("src-b")
        val matchA = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val matchB = SManga(sourceId = "src-b", url = "u2", title = "Solo Leveling", coverUrl = null)
        val sourceA = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(matchA), chapters = listOf(chapter(1f)))
        val sourceB = FakeSource("src-b", "Site B", "MANHWA", searchResults = listOf(matchB), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(sourceA, sourceB)

        val result = resolver.findCandidates("comick-id-8", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals("src-b", result[0].source.id)
        assertTrue(result[0].isFavorite)
        assertTrue(!result[1].isFavorite)
    }

    @Test
    fun `a second call for the same comicKMangaId does not re-search or re-fetch chapters`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        var searchCalls = 0
        val source = object : MangaSource {
            override val id = "src-a"
            override val name = "Site A"
            override val contentType = "MANHWA"
            override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> {
                searchCalls++
                return listOf(match)
            }
            override suspend fun getPopular(page: Int, filter: MangaFilter) = emptyList<SManga>()
            override suspend fun getMangaDetails(manga: SManga) = manga
            override suspend fun getChapterList(manga: SManga) = listOf(chapter(1f))
            override suspend fun getPageList(chapter: SChapter) = emptyList<com.haise.jiyu.source.Page>()
        }
        coEvery { sourceManager.getAll() } returns listOf(source)

        resolver.findCandidates("comick-id-9", "Solo Leveling", "MANHWA", requestedChapterNumber = 1f)
        resolver.findCandidates("comick-id-9", "Solo Leveling", "MANHWA", requestedChapterNumber = 2f)

        assertEquals(1, searchCalls)
    }

    private fun chapter(number: Float) = SChapter(
        sourceId = "x", mangaUrl = "u", url = "c/$number", name = "Ch.$number",
        chapterNumber = number, dateUpload = 0L,
    )
}
```

- [ ] **Step 2: Spustit test, ověřit že padá (kompilační chyba - `ComicKChapterResolver` neexistuje)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKChapterResolverTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implementovat `ComicKChapterResolver`**

Zjistit nejdřív přesný balíček/import cestu pro `MangaFilter`, `SourceManager`, `SettingsRepository`, `normalizeMangaTitle` (jsou už použité jinde v repu, např. `MangaRepository.kt`/`GlobalSearchViewModel.kt` - zkopírovat přesné importy odtamtud).

Vytvořit `ComicKChapterResolver.kt`:

```kotlin
package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.SourceManager
import com.haise.jiyu.util.normalizeMangaTitle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Jeden nalezený reálný zdroj, který ComicK titul také má. */
data class ResolvedCandidate(
    val source: MangaSource,
    val manga: SManga,
    val matchedChapterCount: Int,
    val hasRequestedChapter: Boolean,
    val isFavorite: Boolean,
)

/**
 * Křížové vyhledání skutečného, čitelného zdroje pro ComicK titul (ComicK sám
 * jen katalogizuje, reálné stránky kapitol nikdy neposkytuje - viz design doc
 * "Sub-projekt 3"). Zužuje kandidáty podle typu obsahu, hledá živě paralelně,
 * porovnává normalizovaný název a cachuje výsledek na úrovni titulu (jen
 * v paměti, po dobu běhu appky - viz design doc "Cache rozsah").
 */
@Singleton
class ComicKChapterResolver @Inject constructor(
    private val sourceManager: SourceManager,
    private val settings: SettingsRepository,
) {
    private data class CachedCandidate(val source: MangaSource, val manga: SManga, val chapters: List<SChapter>)

    private val cache = mutableMapOf<String, List<CachedCandidate>>()

    /**
     * @param comicKMangaId klíč pro cache (Room id ComicK manga entity)
     * @param requestedChapterNumber null = zajímá nás jen "existuje vůbec zdroj", jinak
     *   se navíc spočítá [ResolvedCandidate.hasRequestedChapter] pro tohle konkrétní číslo.
     */
    suspend fun findCandidates(
        comicKMangaId: String,
        comicKTitle: String,
        comicKContentType: String,
        requestedChapterNumber: Float?,
    ): List<ResolvedCandidate> {
        val cached = cache[comicKMangaId] ?: searchAndFetch(comicKTitle, comicKContentType).also {
            cache[comicKMangaId] = it
        }
        val favorites = settings.favoriteSourceIds.first()
        return cached.map { c ->
            ResolvedCandidate(
                source = c.source,
                manga = c.manga,
                matchedChapterCount = c.chapters.size,
                hasRequestedChapter = requestedChapterNumber == null ||
                    c.chapters.any { abs(it.chapterNumber - requestedChapterNumber) < 0.01f },
                isFavorite = c.source.id in favorites,
            )
        }.sortedWith(compareByDescending<ResolvedCandidate> { it.isFavorite }.thenByDescending { it.matchedChapterCount })
    }

    private suspend fun searchAndFetch(comicKTitle: String, comicKContentType: String): List<CachedCandidate> =
        coroutineScope {
            val semaphore = Semaphore(5)
            val normalizedTarget = normalizeMangaTitle(comicKTitle)
            sourceManager.getAll()
                .filter { it.id != "comick" && isSameContentGroup(it.contentType, comicKContentType) }
                .map { source ->
                    async {
                        semaphore.withPermit {
                            try {
                                val results = source.search(comicKTitle, 1, MangaFilter())
                                val match = results.firstOrNull { normalizeMangaTitle(it.title) == normalizedTarget }
                                match?.let { m -> CachedCandidate(source, m, source.getChapterList(m)) }
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
        }

    /**
     * MANGA/MANHWA/MANHUA se pro účely hledání zdroje berou jako jedna skupina (region
     * asijského komiksu) - stejná konvence jako `BrowseViewModel.MANGA_GROUP` pro
     * Procházet, protože spousta zdrojů má title-level typ smíchaný a jen jeden
     * "výchozí" contentType na úrovni celého zdroje. Novely a americké komiksy se
     * nikdy neprohledávají u ComicK titulu (ComicK sám je jen manga/manhwa/manhua tracker).
     */
    private fun isSameContentGroup(sourceType: String, targetType: String): Boolean {
        val asianComicTypes = setOf("MANGA", "MANHWA", "MANHUA")
        return if (targetType in asianComicTypes) sourceType in asianComicTypes else sourceType == targetType
    }
}
```

- [ ] **Step 4: Spustit testy znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKChapterResolverTest"`
Expected: PASS (10 testů).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKChapterResolver.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKChapterResolverTest.kt
git commit -m "feat: ComicKChapterResolver - jadro krizoveho vyhledani zdroje"
```

---

### Task 2: Route `SOURCE_RESOLVER` + `SourceResolverViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/resolver/SourceResolverViewModel.kt`

**Interfaces:**
- Consumes: `ComicKChapterResolver.findCandidates(...)` (Task 1), `MangaRepository.getChapter/getManga/getAllChapters/openPreview/refreshChapters`.
- Produces: `Routes.SOURCE_RESOLVER`, `Routes.sourceResolver(chapterId, incognito)` — použije je Task 3 (Screen) a Task 5 (MangaDetailScreen navigace). `SourceResolverViewModel`'s veřejný stav (`candidates`, `loading`, `comicKTitle`, `openedChapterId`, `selectCandidate(...)`) použije Task 3.

- [ ] **Step 1: Přidat routu**

V `NavGraph.kt` najít `object Routes { ... }` blok s konstantami (`READER`, `QR` apod.) a přidat za `const val QR = "qr/{mangaId}?title={mangaTitle}"`:

```kotlin
const val SOURCE_RESOLVER = "source_resolver/{chapterId}?incognito={incognito}"
```

Najít funkce jako `fun reader(chapterId: String, incognito: Boolean = false) = ...` (o pár řádků níž ve stejném objektu) a přidat vedle:

```kotlin
fun sourceResolver(chapterId: String, incognito: Boolean = false) =
    "source_resolver/${android.net.Uri.encode(chapterId)}?incognito=$incognito"
```

- [ ] **Step 2: Zkompilovat (jen routa, žádný composable ji ještě nepoužívá)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Napsat `SourceResolverViewModel`**

Nejdřív ověřit přesné signatury `MangaRepository.getChapter(id: String): ChapterEntity?`, `getManga(mangaId: String): MangaEntity?`, `getAllChapters(mangaId: String): List<ChapterEntity>`, `openPreview(manga: SManga): String`, `refreshChapters(mangaId: String, manga: SManga): List<ChapterEntity>` (všechny už existují a používá je `ReaderViewModel`/`SourceBrowseViewModel` - zkopírovat přesné použití odtamtud, ne hádat).

Vytvořit `app/src/main/kotlin/com/haise/jiyu/ui/resolver/SourceResolverViewModel.kt`:

```kotlin
package com.haise.jiyu.ui.resolver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ComicKChapterResolver
import com.haise.jiyu.source.comick.ResolvedCandidate
import com.haise.jiyu.util.report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class SourceResolverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resolver: ComicKChapterResolver,
    private val repository: MangaRepository,
) : ViewModel() {

    private val chapterId: String = checkNotNull(savedStateHandle["chapterId"])
    val incognito: Boolean = savedStateHandle.get<String>("incognito")?.toBoolean() ?: false

    private var requestedChapterNumber: Float? = null

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _comicKTitle = MutableStateFlow("")
    val comicKTitle: StateFlow<String> = _comicKTitle.asStateFlow()

    private val _candidates = MutableStateFlow<List<ResolvedCandidate>>(emptyList())
    val candidates: StateFlow<List<ResolvedCandidate>> = _candidates.asStateFlow()

    private val _totalComicKChapters = MutableStateFlow(0)
    val totalComicKChapters: StateFlow<Int> = _totalComicKChapters.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _openedChapterId = MutableStateFlow<String?>(null)
    val openedChapterId: StateFlow<String?> = _openedChapterId.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val chapter = repository.getChapter(chapterId)
                if (chapter == null) { _loading.value = false; return@launch }
                val manga = repository.getManga(chapter.mangaId)
                if (manga == null) { _loading.value = false; return@launch }
                _comicKTitle.value = manga.title
                requestedChapterNumber = chapter.chapterNumber
                _totalComicKChapters.value = repository.getAllChapters(chapter.mangaId).size
                _candidates.value = resolver.findCandidates(
                    comicKMangaId = manga.id,
                    comicKTitle = manga.title,
                    comicKContentType = manga.contentType,
                    requestedChapterNumber = chapter.chapterNumber,
                )
            } catch (e: Exception) {
                e.report("resolver:findCandidates")
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectCandidate(candidate: ResolvedCandidate) {
        val target = requestedChapterNumber ?: return
        _resolving.value = true
        viewModelScope.launch {
            try {
                val mangaId = repository.openPreview(candidate.manga)
                repository.refreshChapters(mangaId, candidate.manga)
                val resolvedChapters = repository.getAllChapters(mangaId)
                val bestMatch = resolvedChapters.minByOrNull { abs(it.chapterNumber - target) }
                _openedChapterId.value = bestMatch?.id
            } catch (e: Exception) {
                e.report("resolver:selectCandidate")
            } finally {
                _resolving.value = false
            }
        }
    }
}
```

- [ ] **Step 4: Zkompilovat**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. Pokud některá z metod `MangaRepository` má jinou signaturu, než plán předpokládal, uprav volání podle skutečné signatury (zdroj pravdy je `MangaRepository.kt`, ne tenhle plán).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt app/src/main/kotlin/com/haise/jiyu/ui/resolver/SourceResolverViewModel.kt
git commit -m "feat: routa a ViewModel pro obrazovku Vyber zdroj"
```

---

### Task 3: `SourceResolverScreen` (Compose UI) + zaregistrování do `NavGraph`

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/resolver/SourceResolverScreen.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-en`, `values-es`, `values-fr`

**Interfaces:**
- Consumes: `SourceResolverViewModel` (Task 2).
- Produces: `SourceResolverScreen(onBack, onOpenChapter, onSearchManually, viewModel)` — `onOpenChapter`/`onSearchManually` zapojí `NavGraph.kt` v tomhle tasku; `onSearchManually` použije Task 4.

Vizuální styl kopíruje `GlobalSearchScreen.kt` (barvy `NightBlue`/`TextPrimary`/`TextSecondary`/`Violet`/`GlowViolet`, `screenGradient`, karty se zaoblenými rohy) - než začneš psát Composable, přečti si celý `GlobalSearchScreen.kt` kvůli přesným importům a stylu karet, ať nová obrazovka vizuálně nevybočuje.

- [ ] **Step 1: Přidat string resources do všech 4 lokalizací**

`app/src/main/res/values/strings.xml`, najít blok s `detail_comick_read_unavailable` (přidaný v Sub-projektu 2) a přidat vedle:

```xml
<string name="resolver_title">Vyber zdroj</string>
<string name="resolver_loading">Hledám zdroje…</string>
<string name="resolver_no_candidates">Žádný zdroj tenhle titul nemá.</string>
<string name="resolver_search_manually">Hledat ručně</string>
<string name="resolver_chapters_ratio">%1$d/%2$d kapitol</string>
<string name="resolver_favorite_badge">Oblíbený</string>
<string name="resolver_missing_chapter">Tuhle kapitolu nemá</string>
<string name="resolver_opening">Otevírám…</string>
```

`app/src/main/res/values-en/strings.xml`:

```xml
<string name="resolver_title">Choose a source</string>
<string name="resolver_loading">Searching sources…</string>
<string name="resolver_no_candidates">No source has this title.</string>
<string name="resolver_search_manually">Search manually</string>
<string name="resolver_chapters_ratio">%1$d/%2$d chapters</string>
<string name="resolver_favorite_badge">Favorite</string>
<string name="resolver_missing_chapter">Doesn\'t have this chapter</string>
<string name="resolver_opening">Opening…</string>
```

`app/src/main/res/values-es/strings.xml`:

```xml
<string name="resolver_title">Elegir fuente</string>
<string name="resolver_loading">Buscando fuentes…</string>
<string name="resolver_no_candidates">Ninguna fuente tiene este título.</string>
<string name="resolver_search_manually">Buscar manualmente</string>
<string name="resolver_chapters_ratio">%1$d/%2$d capítulos</string>
<string name="resolver_favorite_badge">Favorito</string>
<string name="resolver_missing_chapter">No tiene este capítulo</string>
<string name="resolver_opening">Abriendo…</string>
```

`app/src/main/res/values-fr/strings.xml`:

```xml
<string name="resolver_title">Choisir une source</string>
<string name="resolver_loading">Recherche des sources…</string>
<string name="resolver_no_candidates">Aucune source n\'a ce titre.</string>
<string name="resolver_search_manually">Rechercher manuellement</string>
<string name="resolver_chapters_ratio">%1$d/%2$d chapitres</string>
<string name="resolver_favorite_badge">Favori</string>
<string name="resolver_missing_chapter">N\'a pas ce chapitre</string>
<string name="resolver_opening">Ouverture…</string>
```

- [ ] **Step 2: Napsat `SourceResolverScreen.kt`**

Nejdřív si přečíst `GlobalSearchScreen.kt` celý, kvůli přesnému stylu importů/karet/barev. Pak vytvořit:

```kotlin
package com.haise.jiyu.ui.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import com.haise.jiyu.R
import com.haise.jiyu.source.comick.ResolvedCandidate
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.GlowViolet
import com.haise.jiyu.ui.theme.NightBlue
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient

@Composable
fun SourceResolverScreen(
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String, incognito: Boolean) -> Unit,
    onSearchManually: (query: String) -> Unit,
    viewModel: SourceResolverViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.collectAsState()
    val comicKTitle by viewModel.comicKTitle.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val totalComicKChapters by viewModel.totalComicKChapters.collectAsState()
    val resolving by viewModel.resolving.collectAsState()
    val openedChapterId by viewModel.openedChapterId.collectAsState()

    LaunchedEffect(openedChapterId) {
        openedChapterId?.let { onOpenChapter(it, viewModel.incognito) }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    Text(stringResource(R.string.resolver_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (comicKTitle.isNotBlank()) {
                        Text(comicKTitle, color = TextSecondary, fontSize = 13.sp)
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
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.resolver_loading), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                candidates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.resolver_no_candidates), color = TextSecondary, fontSize = 14.sp)
                        Button(
                            onClick = { onSearchManually(comicKTitle) },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Icon(TablerIcons.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.resolver_search_manually))
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, WindowInsets.navigationBars.asBottomDp() + 16.dp),
                ) {
                    item {
                        Button(
                            onClick = { onSearchManually(comicKTitle) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            Icon(TablerIcons.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.resolver_search_manually))
                        }
                    }
                    items(candidates, key = { it.source.id }) { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            totalComicKChapters = totalComicKChapters,
                            enabled = !resolving,
                            onClick = { viewModel.selectCandidate(candidate) },
                        )
                    }
                }
            }
            if (resolving) {
                Box(
                    modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.resolver_opening), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: ResolvedCandidate, totalComicKChapters: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightBlue.copy(alpha = 0.6f))
            .border(1.dp, if (candidate.isFavorite) Violet.copy(alpha = 0.6f) else GlowViolet.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(candidate.source.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    if (candidate.isFavorite) {
                        Icon(TablerIcons.Star, contentDescription = stringResource(R.string.resolver_favorite_badge), tint = Violet, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                    }
                }
                Text(
                    stringResource(R.string.resolver_chapters_ratio, candidate.matchedChapterCount, totalComicKChapters),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                if (!candidate.hasRequestedChapter) {
                    Text(stringResource(R.string.resolver_missing_chapter), color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WindowInsets.asBottomDp() = androidx.compose.foundation.layout.asPaddingValues().calculateBottomPadding()
```

- [ ] **Step 3: Zaregistrovat do `NavGraph.kt`**

Najít `composable(Routes.DETAIL, ...)` blok (viz Task 5 níž pro přesné umístění `MangaDetailScreen`) a přidat NOVÝ `composable` blok pro `Routes.SOURCE_RESOLVER`, po vzoru existujícího `composable(Routes.READER, ...)` bloku (najít ho, zkopírovat styl `navArgument`/`navDeepLink` pokud nějaké má):

```kotlin
composable(
    route = Routes.SOURCE_RESOLVER,
    arguments = listOf(
        navArgument("chapterId") { type = NavType.StringType },
        navArgument("incognito") { type = NavType.StringType; defaultValue = "false" },
    ),
) {
    SourceResolverScreen(
        onBack = { navController.popBackStack() },
        onOpenChapter = { chapterId, incognito ->
            navController.navigate(Routes.reader(chapterId, incognito)) {
                popUpTo(Routes.SOURCE_RESOLVER) { inclusive = true }
            }
        },
        onSearchManually = { query -> navController.navigate(Routes.globalSearch(query)) },
    )
}
```

`Routes.globalSearch(query)` ještě neexistuje - přidá ho Task 4. Než Task 4 doběhne, tenhle řádek se nezkompiluje; je to očekávané, Task 3 a Task 4 spolu úzce souvisí a `Task 3 Step 4` (compile check) proto selže, dokud neproběhne i Task 4. Postupuj v pořadí 3 → 4, nebo (pokud SDD dispatchuje 3 a 4 zvlášť) nech Task 3's finální compile-check/commit počkat, až bude i Task 4 hotový - poznamenej tohle přímo implementátorovi Tasku 3 v dispatch zprávě.

- [ ] **Step 4: Zkompilovat (po dokončení i Tasku 4)**

Run: `./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/resolver/SourceResolverScreen.kt app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "feat: obrazovka Vyber zdroj (SourceResolverScreen)"
```

---

### Task 4: `GlobalSearchScreen` dostane volitelný předvyplněný dotaz

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/search/GlobalSearchScreen.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/search/GlobalSearchViewModel.kt`

**Interfaces:**
- Produces: `Routes.globalSearch(query: String? = null): String` — použije ho Task 3's `onSearchManually`.

- [ ] **Step 1: Rozšířit routu**

V `NavGraph.kt` nahradit `const val GLOBAL_SEARCH = "global_search"` za:

```kotlin
const val GLOBAL_SEARCH = "global_search?q={q}"
```

Přidat vedle ostatních route-builder funkcí (`fun reader(...)`, `fun sourceResolver(...)`):

```kotlin
fun globalSearch(query: String? = null) =
    if (query.isNullOrBlank()) "global_search?q=" else "global_search?q=${android.net.Uri.encode(query)}"
```

Najít existující `composable(Routes.GLOBAL_SEARCH) { GlobalSearchScreen(...) }` a nahradit za verzi s argumentem:

```kotlin
composable(
    route = Routes.GLOBAL_SEARCH,
    arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
) { backStackEntry ->
    val initialQuery = backStackEntry.arguments?.getString("q").orEmpty()
    GlobalSearchScreen(
        // zachovej presne stavajici parametry tehle composable - jen pridej:
        initialQuery = initialQuery,
    )
}
```

Zkontroluj skutečné stávající parametry `GlobalSearchScreen`'s volání v `NavGraph.kt` (mělo by mít `onBack`/`onOpenManga` apod. - viz `GlobalSearchScreen.kt:82-86` z předchozího průzkumu) a zachovej je všechny, jen přidej `initialQuery`.

- [ ] **Step 2: `GlobalSearchViewModel` přijme počáteční dotaz a rovnou vyhledá**

Najít `GlobalSearchViewModel`'s `init` blok (pokud existuje) nebo konstruktor - přidat `SavedStateHandle` závislost (pokud tam ještě není) a v `init` zavolat `search(initialQuery)` pokud není prázdný:

```kotlin
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val repository: MangaRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    init {
        val initialQuery = savedStateHandle.get<String>("q").orEmpty()
        if (initialQuery.isNotBlank()) search(initialQuery)
    }

    // ... zbytek tridy beze zmeny
```

(Zkontroluj přesné jméno existující nav-arg klíče `"q"` sedí s tím, co je v route `"global_search?q={q}"` z Kroku 1 - musí být identické.)

- [ ] **Step 3: `GlobalSearchScreen` přijme `initialQuery` a předvyplní textové pole**

V `GlobalSearchScreen.kt`'s funkci přidat parametr `initialQuery: String = ""` a použít ho jako počáteční hodnotu textového pole (najít, jak se dnes inicializuje `query`/`TextField` stav - pravděpodobně přes `viewModel.query.collectAsState()`, který teď už bude mít správnou hodnotu díky Kroku 2, takže tenhle krok může být jen o přidání parametru bez dalšího zásahu, pokud UI čte `query` state přímo z ViewModelu). Zkontroluj skutečné chování před úpravou - pokud `query` StateFlow z ViewModelu už řídí zobrazený text (pravděpodobné), stačí jen přidat nepoužívaný `initialQuery` parametr do signatury kvůli volání z `NavGraph.kt` a nic dalšího v těle funkce měnit není potřeba.

- [ ] **Step 4: Zkompilovat**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. Tenhle krok taky ověří, že Task 3's `Routes.globalSearch(query)` volání (které čeká na tenhle task) teď sedí.

- [ ] **Step 5: Test**

Zkontrolovat, jestli existuje `GlobalSearchViewModelTest.kt` (`app/src/test/kotlin/com/haise/jiyu/ui/search/`). Pokud ano, přidat test `` `init with a non-blank saved-state query triggers an immediate search` `` podle vzoru existujících testů v tom souboru. Pokud test soubor pro tenhle ViewModel neexistuje vůbec, je v pořádku tenhle krok přeskočit (stejné odůvodnění jako u jiných ViewModelů v týhle sadě tasků bez existující test infrastruktury) - ale napiš to explicitně do reportu.

Run: `./gradlew.bat testDebugUnitTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt app/src/main/kotlin/com/haise/jiyu/ui/search/GlobalSearchScreen.kt app/src/main/kotlin/com/haise/jiyu/ui/search/GlobalSearchViewModel.kt
git commit -m "feat: GlobalSearch prijima predvyplneny dotaz z navigace"
```

---

### Task 5: `MangaDetailScreen` naviguje na resolver místo snackbaru

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `Routes.sourceResolver(chapterId, incognito)` (Task 2).

- [ ] **Step 1: Přidat nový navigační parametr do `MangaDetailScreen`**

V `MangaDetailScreen.kt` najít funkci `fun MangaDetailScreen(onBack, onOpenChapter, onOpenChapterIncognito, onOpenQr, onOpenDetails, viewModel)` a přidat nový parametr:

```kotlin
onResolveChapter: (chapterId: String, incognito: Boolean) -> Unit = { _, _ -> },
```

- [ ] **Step 2: Upravit `openChapter` helper**

Nahradit (viz Sub-projekt 2 Task 7):

```kotlin
fun openChapter(chapter: ChapterEntity, incognito: Boolean = false) {
    if (chapter.sourceId == "comick") {
        coroutineScope.launch { snackbarHostState.showSnackbar(comickReadUnavailableMessage) }
    } else if (incognito) {
        onOpenChapterIncognito(chapter.id)
    } else {
        onOpenChapter(chapter.id)
    }
}
```

za:

```kotlin
fun openChapter(chapter: ChapterEntity, incognito: Boolean = false) {
    if (chapter.sourceId == "comick") {
        onResolveChapter(chapter.id, incognito)
    } else if (incognito) {
        onOpenChapterIncognito(chapter.id)
    } else {
        onOpenChapter(chapter.id)
    }
}
```

Zkontrolovat, jestli `coroutineScope`/`comickReadUnavailableMessage` (proměnné zavedené v Sub-projektu 2 Task 7 přímo kvůli tomuhle snackbaru) jsou po týhle změně v souboru ještě někde použité. Pokud ne, odstranit jejich deklarace (`val coroutineScope = rememberCoroutineScope()`, `val comickReadUnavailableMessage = stringResource(...)`) a nepoužité importy (`rememberCoroutineScope`, `kotlinx.coroutines.launch` - pokud se nepoužívají jinde v souboru).

- [ ] **Step 3: Zaregistrovat nový parametr v `NavGraph.kt`**

Najít `composable(route = Routes.DETAIL, ...) { ... MangaDetailScreen(onBack = ..., onOpenChapter = ..., onOpenChapterIncognito = ..., onOpenQr = ..., onOpenDetails = ...) }` a přidat:

```kotlin
onResolveChapter = { chapterId, incognito -> navController.navigate(Routes.sourceResolver(chapterId, incognito)) },
```

- [ ] **Step 4: Zkompilovat, spustit testy a lint**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest lintDebug -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt
git commit -m "feat: klik na ComicK kapitolu spusti hledani skutecneho zdroje misto hlasky"
```

---

### Task 6: Manuální ověření na zařízení

**Files:** žádné (jen ověření)

- [ ] **Step 1: Sestavit a nainstalovat debug APK**

```bash
cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat assembleDebug -q
```

Nainstalovat na připojené zařízení (`adb install -r app/build/outputs/apk/debug/app-debug.apk` po ověření `adb devices`).

- [ ] **Step 2: Ověřit šťastnou cestu**

Otevřít v appce (ComicK režim) titul se známou dobrou dostupností na jiných zdrojích (např. Solo Leveling), kliknout na libovolnou kapitolu. Ověřit:
- Zobrazí se "Vyber zdroj" s loading stavem, pak seznamem kandidátů.
- U každého kandidáta je vidět `X/Y kapitol`.
- Klik na kandidáta otevře čtečku na odpovídající kapitole (ne na první/poslední, na TÉ, na kterou se kliklo z ComicK detailu).

- [ ] **Step 3: Ověřit cestu bez kandidátů**

Zkusit titul, který pravděpodobně žádný zdroj nemá (velmi obskurní/nedávno přidaný na ComicK). Ověřit hlášku "Žádný zdroj tenhle titul nemá" + funkční tlačítko "Hledat ručně" (otevře GlobalSearch s předvyplněným názvem).

- [ ] **Step 4: Ověřit cache v rámci session**

Otevřít druhou kapitolu STEJNÉHO titulu z Kroku 2 - obrazovka by se měla načíst znatelně rychleji (bez nového loading stavu trvajícího vteřiny), protože kandidáti jsou už v paměti z prvního otevření.

- [ ] **Step 5: Zapsat výsledek manuálního ověření**

Do finální zprávy zaznamenat, co bylo/nebylo možné ověřit (např. pokud není fyzicky dostupný titul bez kandidátů, poznamenat to jako nedokončené ověření, ne jako projeté).
