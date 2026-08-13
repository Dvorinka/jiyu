# ComicK Home Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** V ComicK agregovaném režimu appka nahradí generickou Procházet obrazovku bohatší domovskou obrazovkou se sekcemi (Recently Added/Completed, Popular New Comics, Most Recent Popular, Recent Reviews) + samostatným "Aktualizace" tabem s feedem posledních kapitol.

**Architecture:** Jeden nový `GET /top` (~3 MB, cachovaný in-memory po dobu session) dodá data pro všech 5 sekcí naráz; `parseComicList` se rozdělí na sdílenou `comicFromJson` pomocnou funkci, kterou `getTop`/`getUpdates` znovupoužijí. Nová `ComicKHomeScreen` (Domů/Aktualizace přepínač) + `ComicKSectionScreen` (jedna znovupoužitelná "zobrazit vše" obrazovka pro všech 5 sekcí) nahradí `SourceBrowseScreen` jen pro `appMode == COMICK`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, OkHttp, org.json, Coil, JUnit + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-08-13-comick-home-feed-design.md`.

## Global Constraints

- Práce se dělá přímo na `master`, žádná feature branch.
- Po každém tasku: `compileDebugKotlin` musí projít; testy (pokud task nějaké přidává) musí být zelené; pak commit.
- Žádné nové závislosti.
- `/top` odpověď (~3 MB) se stahuje **jen jednou za session** — cachuje se in-memory v `ComicKSource` (stejný vzor jako `ComicKChapterResolver`'s cache), žádná Room tabulka.
- `/chapter` (Updates feed) se **necachuje** — každé přepnutí Hot/New nebo scroll dolů je nový request.
- Mimo ComicK režim (Klasický režim, ~180 ostatních zdrojů) se nic nemění — `SourceBrowseScreen` zůstává beze změny a beze změny chování.

---

### Task 1: `ComicKSource.getTop()` + `TopFeed`/`ReviewItem` + `comicFromJson` refaktor

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Produces: `data class TopFeed(val recentlyAdded: List<SManga>, val completed: List<SManga>, val popularNew: Map<String, List<SManga>>, val mostRecentPopular: Map<String, List<SManga>>, val recentReviews: List<ReviewItem>)`; `data class ReviewItem(val title: String?, val content: String, val authorName: String?, val comic: SManga)`; `suspend fun ComicKSource.getTop(): TopFeed`.

Živě ověřený tvar `GET https://api.comick.dev/top`:

```json
{
  "news": [ {"title":"...", "slug":"...", "country":"kr", "md_covers":[{"b2key":"..."}]} ],
  "completions": [ /* stejny tvar jako news */ ],
  "topFollowNewComics": { "7": [...], "30": [...], "90": [...] },
  "topFollowComics": { "7": [...], "30": [...], "90": [...] },
  "recentReviews": [ {"id":1, "title":"...", "content":"...", "identities":{"traits":{"username":"..."}}, "md_comics": {"title":"...","slug":"...","country":"kr","md_covers":[...]}} ]
}
```

`news`/`completions`/`topFollowNewComics.{7,30,90}`/`topFollowComics.{7,30,90}` mají identický tvar položek jako `/v1.0/search` — parsují se stávající `parseComicList`. `recentReviews` má JINÝ tvar (recenze, ne komiks) — potřebuje novou `reviewFromJson`.

- [ ] **Step 1: Napsat padající testy**

Přidat do `ComicKSourceTest.kt` (za poslední `@Test` metodu, před uzavírací `}` třídy):

```kotlin
    @Test
    fun `getTop parses all five sections and reuses the search-result comic parser`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/top" -> MockResponse().setBody(
                        """{
                            "news": [{"title": "News Comic", "slug": "news-comic", "country": "kr", "md_covers": [{"b2key": "news.jpg"}]}],
                            "completions": [{"title": "Done Comic", "slug": "done-comic", "country": "jp", "md_covers": [{"b2key": "done.jpg"}]}],
                            "topFollowNewComics": {
                                "7": [{"title": "New7", "slug": "new-7", "country": "cn", "md_covers": []}],
                                "30": [{"title": "New30", "slug": "new-30", "country": "cn", "md_covers": []}],
                                "90": [{"title": "New90", "slug": "new-90", "country": "cn", "md_covers": []}]
                            },
                            "topFollowComics": {
                                "7": [{"title": "Pop7", "slug": "pop-7", "country": "kr", "md_covers": []}],
                                "30": [{"title": "Pop30", "slug": "pop-30", "country": "kr", "md_covers": []}],
                                "90": [{"title": "Pop90", "slug": "pop-90", "country": "kr", "md_covers": []}]
                            },
                            "recentReviews": [{
                                "title": "Great read", "content": "Really enjoyed this one.",
                                "identities": {"traits": {"username": "reader42"}},
                                "md_comics": {"title": "Reviewed Comic", "slug": "reviewed-comic", "country": "jp", "md_covers": [{"b2key": "rev.jpg"}]}
                            }]
                        }"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val feed = source.getTop()
        assertEquals("News Comic", feed.recentlyAdded[0].title)
        assertEquals("Done Comic", feed.completed[0].title)
        assertEquals("MANHUA", feed.completed[0].contentType)
        assertEquals("New7", feed.popularNew["7"]!![0].title)
        assertEquals("New30", feed.popularNew["30"]!![0].title)
        assertEquals("New90", feed.popularNew["90"]!![0].title)
        assertEquals("Pop7", feed.mostRecentPopular["7"]!![0].title)
        assertEquals(1, feed.recentReviews.size)
        assertEquals("Great read", feed.recentReviews[0].title)
        assertEquals("Really enjoyed this one.", feed.recentReviews[0].content)
        assertEquals("reader42", feed.recentReviews[0].authorName)
        assertEquals("Reviewed Comic", feed.recentReviews[0].comic.title)
    }

    @Test
    fun `getTop caches the result - a second call does not hit the network again`() = runTest {
        var requestCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/top" -> {
                        requestCount++
                        MockResponse().setBody(
                            """{"news": [], "completions": [], "topFollowNewComics": {"7":[],"30":[],"90":[]}, "topFollowComics": {"7":[],"30":[],"90":[]}, "recentReviews": []}"""
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        source.getTop()
        source.getTop()
        assertEquals(1, requestCount)
    }

    @Test
    fun `getTop treats a review with no title as null, not blank string`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/top" -> MockResponse().setBody(
                        """{"news": [], "completions": [], "topFollowNewComics": {"7":[],"30":[],"90":[]}, "topFollowComics": {"7":[],"30":[],"90":[]},
                            "recentReviews": [{"content": "No title here.", "identities": {}, "md_comics": {"title": "C", "slug": "c", "country": "kr", "md_covers": []}}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val feed = source.getTop()
        assertEquals(null, feed.recentReviews[0].title)
        assertEquals(null, feed.recentReviews[0].authorName)
    }
```

Ověřit, že `ComicKSourceTest.kt` už má potřebné importy (`assertEquals`, `runTest`, `Dispatcher`, `MockResponse`, `RecordedRequest`) — ano, používají je existující testy v souboru.

- [ ] **Step 2: Spustit testy a ověřit, že padají na "unresolved reference: getTop"**

PowerShell, `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` nejdřív:
```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: FAIL, kompilace testu spadne na `Unresolved reference: getTop`.

- [ ] **Step 3: Refaktorovat `parseComicList` na sdílenou `comicFromJson` + přidat `getTop()`**

V `ComicKSource.kt` nahradit stávající `parseComicList` (viz `// ─── Privátní pomocné funkce ───` sekce):

```kotlin
    /** Převede jeden objekt z výsledků hledání na SManga. */
    private fun parseComicList(arr: JSONArray): List<SManga> =
        (0 until arr.length()).mapNotNull { i -> comicFromJson(arr.getJSONObject(i)) }

    /**
     * Jeden komiks z `/v1.0/search`, `/group/{slug}`'s `comics[]`, i `/top`'s
     * `news`/`completions`/`topFollowNewComics`/`topFollowComics` - všechny mají
     * stejný tvar položky, proto jedna sdílená funkce.
     */
    private fun comicFromJson(comic: JSONObject): SManga? {
        val title = comic.optString("title").ifBlank { return null }
        val slug  = comic.optString("slug").ifBlank { return null }

        // Titulní obrázek: první položka md_covers s neprázdným b2key
        val coverUrl = comic.optJSONArray("md_covers")
            ?.let { covers ->
                (0 until covers.length()).firstNotNullOfOrNull { j ->
                    covers.getJSONObject(j).optString("b2key").ifBlank { null }
                }
            }
            ?.let { b2key -> "$coverBase/$b2key" }

        return SManga(
            sourceId    = id,
            url         = "$apiBase/comic/$slug",
            title       = title,
            coverUrl    = coverUrl,
            contentType = contentTypeFromCountry(comic.optString("country")),
        )
    }
```

Přidat novou metodu do třídy `ComicKSource`, hned za `getGroup()`:

```kotlin
    private var cachedTop: TopFeed? = null

    /**
     * ComicK domovská data (Sub-projekt: Home Feed) - jeden request vrátí data
     * pro všech 5 sekcí naráz (~3 MB), proto se cachuje po dobu běhu appky
     * (stejný vzor jako [ComicKChapterResolver]'s cache) - Home i "zobrazit vše"
     * obrazovky sdílí jedno stažení, ne request na sekci.
     */
    suspend fun getTop(): TopFeed =
        withContext(Dispatchers.IO) {
            cachedTop?.let { return@withContext it }
            val json = getObject("$apiBase/top")
            val windows = listOf("7", "30", "90")
            fun windowMap(key: String): Map<String, List<SManga>> {
                val obj = json.optJSONObject(key) ?: JSONObject()
                return windows.associateWith { w -> parseComicList(obj.optJSONArray(w) ?: JSONArray()) }
            }
            val feed = TopFeed(
                recentlyAdded     = parseComicList(json.optJSONArray("news") ?: JSONArray()),
                completed         = parseComicList(json.optJSONArray("completions") ?: JSONArray()),
                popularNew        = windowMap("topFollowNewComics"),
                mostRecentPopular = windowMap("topFollowComics"),
                recentReviews     = parseReviewList(json.optJSONArray("recentReviews") ?: JSONArray()),
            )
            cachedTop = feed
            feed
        }

    private fun parseReviewList(arr: JSONArray): List<ReviewItem> =
        (0 until arr.length()).mapNotNull { i -> reviewFromJson(arr.getJSONObject(i)) }

    private fun reviewFromJson(json: JSONObject): ReviewItem? {
        val content = json.optString("content").ifBlank { return null }
        val comicJson = json.optJSONObject("md_comics") ?: return null
        val comic = comicFromJson(comicJson) ?: return null
        val title = if (json.isNull("title")) null else json.optString("title").ifBlank { null }
        val authorName = json.optJSONObject("identities")
            ?.optJSONObject("traits")
            ?.optString("username")
            ?.ifBlank { null }
        return ReviewItem(title = title, content = content, authorName = authorName, comic = comic)
    }
```

Na konec souboru (za `data class GroupInfo`) přidat:

```kotlin
/** Výsledek [ComicKSource.getTop] - data pro ComicK domovskou obrazovku (5 sekcí). Klíče map jsou "7"/"30"/"90" (dny). */
data class TopFeed(
    val recentlyAdded: List<SManga>,
    val completed: List<SManga>,
    val popularNew: Map<String, List<SManga>>,
    val mostRecentPopular: Map<String, List<SManga>>,
    val recentReviews: List<ReviewItem>,
)

/** Jedna recenze z `/top`'s `recentReviews[]` - `title` může chybět (recenze bez nadpisu). */
data class ReviewItem(
    val title: String?,
    val content: String,
    val authorName: String?,
    val comic: SManga,
)
```

- [ ] **Step 4: Spustit testy znovu a ověřit, že projdou**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: PASS, VŠECHNY testy v souboru zelené (existující i 3 nové) - refaktor `parseComicList`/`comicFromJson` nesmí rozbít stávající `getPopular`/`search`/`getGroup` testy.

- [ ] **Step 5: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "feat: pridat ComicKSource.getTop() pro ComicK domovskou obrazovku"
```

---

### Task 2: `ComicKSource.getUpdates()` + `ChapterUpdate`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Consumes: `comicFromJson(JSONObject): SManga?` a `chapterFromJson(JSONObject, mangaUrl: String): SChapter?` (Task 1 a existující, oba private v `ComicKSource`, volatelné odsud jako členské funkce).
- Produces: `data class ChapterUpdate(val chapter: SChapter, val comic: SManga, val upCount: Int, val commentCount: Int)`; `suspend fun ComicKSource.getUpdates(order: String, page: Int): List<ChapterUpdate>`.

Živě ověřený tvar `GET https://api.comick.dev/chapter?lang={kod}&order=hot|new&page={n}` (pole objektů, ne obalené):

```json
[{
  "chap": "177", "vol": null, "hid": "7hslA3Hk", "created_at": "2026-08-13T11:44:48.626Z",
  "up_count": 10, "comment_count": 2, "group_name": ["asurascans"],
  "md_chapters_groups": [{"md_groups": {"title": "Asura", "slug": "asura"}}],
  "md_comics": {"title": "...", "slug": "...", "country": "kr", "md_covers": [{"b2key": "..."}]}
}]
```

Položka NEMÁ vlastní `title` klíč (jen `chap`/`vol`/`hid`/`created_at`/`group_name`/`md_chapters_groups`) - existující `chapterFromJson` s tím počítá (`isNull("title")` vrací `true` i pro chybějící klíč, ne jen pro JSON `null`), jde tedy znovupoužít beze změny.

**`limit` parametr NENÍ spolehlivý** - živě ověřeno, že `limit=2` i `limit=5` vrátily desítky položek (API ho zjevně ignoruje nebo počítá jinak, než by se čekalo). Neimplementovat žádnou logiku závislou na přesném počtu položek na stránku - konec seznamu se pozná jen podle PRÁZDNÉHO pole (stejná konvence jako `SourceBrowseViewModel.loadMore()` - "hasMore" podle prázdné stránky, ne magického čísla).

- [ ] **Step 1: Napsat padající testy**

Přidat do `ComicKSourceTest.kt` (za testy z Tasku 1):

```kotlin
    @Test
    fun `getUpdates parses chapter feed items reusing chapterFromJson and comicFromJson`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/chapter") && !path.startsWith("/chapter/") -> MockResponse().setBody(
                        """[{
                            "chap": "177", "vol": null, "hid": "abc123", "created_at": "2026-08-13T11:44:48.626Z",
                            "up_count": 10, "comment_count": 2, "group_name": ["asurascans"],
                            "md_chapters_groups": [{"md_groups": {"title": "Asura", "slug": "asura"}}],
                            "md_comics": {"title": "Knight King", "slug": "knight-king", "country": "kr", "md_covers": [{"b2key": "cover.jpg"}]}
                        }]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val updates = source.getUpdates(order = "hot", page = 1)
        assertEquals(1, updates.size)
        assertEquals("Knight King", updates[0].comic.title)
        assertEquals("MANHWA", updates[0].comic.contentType)
        assertEquals(177f, updates[0].chapter.chapterNumber)
        assertEquals("Asura", updates[0].chapter.groups[0].name)
        assertEquals(10, updates[0].upCount)
        assertEquals(2, updates[0].commentCount)
        assertTrue(request().path!!.contains("order=hot"))
        assertTrue(request().path!!.contains("page=1"))
    }

    @Test
    fun `getUpdates skips items whose md_comics is missing instead of throwing`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/chapter") && !path.startsWith("/chapter/") -> MockResponse().setBody(
                        """[{"chap": "1", "hid": "x", "created_at": "2026-01-01T00:00:00Z", "up_count": 0, "comment_count": 0, "group_name": []}]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val updates = source.getUpdates(order = "new", page = 1)
        assertTrue(updates.isEmpty())
    }

    private fun request() = server.takeRequest()
```

- [ ] **Step 2: Spustit testy a ověřit, že padají na "unresolved reference: getUpdates"**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: FAIL.

- [ ] **Step 3: Přidat `getUpdates()` a `ChapterUpdate` do `ComicKSource.kt`**

Přidat metodu hned za `getTop()`:

```kotlin
    /**
     * Feed posledních nahraných kapitol napříč VŠEMI tituly (Updates tab) -
     * na rozdíl od [getTop] se nekešuje, každé přepnutí `order` nebo scroll
     * dolů je nový request. `limit` parametr API spolehlivě neomezuje počet
     * položek (ověřeno živě) - konec seznamu pozná appka jen podle prázdné
     * odpovědi, ne podle magického čísla.
     */
    suspend fun getUpdates(order: String, page: Int): List<ChapterUpdate> =
        withContext(Dispatchers.IO) {
            val langCode = LanguageMap.toMangaDexCode(settings.sourceLanguage.first())
            val arr = getArray("$apiBase/chapter?lang=$langCode&order=$order&page=$page")
            (0 until arr.length()).mapNotNull { i ->
                val json = arr.getJSONObject(i)
                val comicJson = json.optJSONObject("md_comics") ?: return@mapNotNull null
                val comic = comicFromJson(comicJson) ?: return@mapNotNull null
                val chapter = chapterFromJson(json, comic.url) ?: return@mapNotNull null
                ChapterUpdate(
                    chapter = chapter,
                    comic = comic,
                    upCount = json.optInt("up_count", 0),
                    commentCount = json.optInt("comment_count", 0),
                )
            }
        }
```

Na konec souboru (za `data class ReviewItem`) přidat:

```kotlin
/** Jedna položka z [ComicKSource.getUpdates] - kapitola + komiks, ke kterému patří, + počty lajků/komentářů. */
data class ChapterUpdate(
    val chapter: SChapter,
    val comic: SManga,
    val upCount: Int,
    val commentCount: Int,
)
```

- [ ] **Step 4: Spustit testy znovu a ověřit, že projdou**

```
.\gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest" --console=plain
```
Expected: PASS.

- [ ] **Step 5: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "feat: pridat ComicKSource.getUpdates() pro Aktualizace feed"
```

---

### Task 3: Route + string zdroje

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`

**Interfaces:**
- Produces: `Routes.COMICK_HOME = "comick_home"`, `Routes.COMICK_SECTION = "comick_section/{section}?window={window}&title={title}"`, `fun Routes.comickSection(section: String, window: String?, title: String): String`. String resources `comick_home_tab_home`, `comick_home_tab_updates`, `comick_home_recently_added`, `comick_home_completed`, `comick_home_popular_new`, `comick_home_most_recent_popular`, `comick_home_recent_reviews`, `comick_home_window_7d`, `comick_home_window_30d`, `comick_home_window_90d`, `comick_home_view_all`, `comick_home_loading`, `comick_home_load_failed`, `comick_home_empty`, `comick_home_review_by`.

**Zavedená znalost projektu (Sub-projekt 4, Task 3 fix round)**: KAŽDÝ nový string musí mít překlad ve VŠECH 4 locale souborech (`values`, `values-en`, `values-es`, `values-fr`), jinak `lintDebug` spadne na `MissingTranslation`. Tenhle task proto rovnou přidává překlady do všech čtyř, ne jen do base.

**Section key hodnoty** (used jako `section` nav argument i v `ComicKSectionViewModel` v Tasku 7): `"recently_added"`, `"completed"`, `"popular_new"`, `"most_recent_popular"`, `"recent_reviews"`.

- [ ] **Step 1: Přidat route konstanty a builder do `Routes` objektu**

V `NavGraph.kt`, do `object Routes` přidat za `const val GROUP = ...`:

```kotlin
    const val COMICK_HOME = "comick_home"
    const val COMICK_SECTION = "comick_section/{section}?window={window}&title={title}"
```

A do sekce s funkcemi, za `fun group(...)`, přidat:

```kotlin
    fun comickSection(section: String, window: String?, title: String) =
        "comick_section/${android.net.Uri.encode(section)}?window=${android.net.Uri.encode(window ?: "")}&title=${android.net.Uri.encode(title)}"
```

- [ ] **Step 2: Přesměrovat `browseRoute` na novou domovskou obrazovku**

Upravit stávající funkci (nahradit `sourceBrowse("comick")` za `COMICK_HOME`):

```kotlin
    fun browseRoute(appMode: String): String =
        if (appMode == com.haise.jiyu.settings.AppMode.COMICK) COMICK_HOME else BROWSE
```

- [ ] **Step 3: Přidat string zdroje do všech 4 locale souborů**

Do `app/src/main/res/values/strings.xml`, za blok `group_screen_*`:

```xml
    <string name="comick_home_tab_home">Domů</string>
    <string name="comick_home_tab_updates">Aktualizace</string>
    <string name="comick_home_recently_added">Nově přidané</string>
    <string name="comick_home_completed">Dokončené</string>
    <string name="comick_home_popular_new">Nové oblíbené</string>
    <string name="comick_home_most_recent_popular">Aktuálně oblíbené</string>
    <string name="comick_home_recent_reviews">Nedávné recenze</string>
    <string name="comick_home_window_7d">7d</string>
    <string name="comick_home_window_30d">1m</string>
    <string name="comick_home_window_90d">3m</string>
    <string name="comick_home_view_all">Zobrazit vše</string>
    <string name="comick_home_loading">Načítám…</string>
    <string name="comick_home_load_failed">Nepodařilo se načíst ComicK.</string>
    <string name="comick_home_empty">Nic tu není.</string>
    <string name="comick_home_review_by">od %1$s</string>
```

Do `app/src/main/res/values-en/strings.xml`:

```xml
    <string name="comick_home_tab_home">Home</string>
    <string name="comick_home_tab_updates">Updates</string>
    <string name="comick_home_recently_added">Recently Added</string>
    <string name="comick_home_completed">Completed</string>
    <string name="comick_home_popular_new">Popular New Comics</string>
    <string name="comick_home_most_recent_popular">Most Recent Popular</string>
    <string name="comick_home_recent_reviews">Recent Reviews</string>
    <string name="comick_home_window_7d">7d</string>
    <string name="comick_home_window_30d">1m</string>
    <string name="comick_home_window_90d">3m</string>
    <string name="comick_home_view_all">View All</string>
    <string name="comick_home_loading">Loading…</string>
    <string name="comick_home_load_failed">Couldn\'t load ComicK.</string>
    <string name="comick_home_empty">Nothing here.</string>
    <string name="comick_home_review_by">by %1$s</string>
```

Do `app/src/main/res/values-es/strings.xml`:

```xml
    <string name="comick_home_tab_home">Inicio</string>
    <string name="comick_home_tab_updates">Actualizaciones</string>
    <string name="comick_home_recently_added">Añadido recientemente</string>
    <string name="comick_home_completed">Completado</string>
    <string name="comick_home_popular_new">Nuevos populares</string>
    <string name="comick_home_most_recent_popular">Populares recientes</string>
    <string name="comick_home_recent_reviews">Reseñas recientes</string>
    <string name="comick_home_window_7d">7d</string>
    <string name="comick_home_window_30d">1m</string>
    <string name="comick_home_window_90d">3m</string>
    <string name="comick_home_view_all">Ver todo</string>
    <string name="comick_home_loading">Cargando…</string>
    <string name="comick_home_load_failed">No se pudo cargar ComicK.</string>
    <string name="comick_home_empty">No hay nada aquí.</string>
    <string name="comick_home_review_by">por %1$s</string>
```

Do `app/src/main/res/values-fr/strings.xml`:

```xml
    <string name="comick_home_tab_home">Accueil</string>
    <string name="comick_home_tab_updates">Mises à jour</string>
    <string name="comick_home_recently_added">Ajoutés récemment</string>
    <string name="comick_home_completed">Terminés</string>
    <string name="comick_home_popular_new">Nouveaux populaires</string>
    <string name="comick_home_most_recent_popular">Populaires récemment</string>
    <string name="comick_home_recent_reviews">Avis récents</string>
    <string name="comick_home_window_7d">7j</string>
    <string name="comick_home_window_30d">1m</string>
    <string name="comick_home_window_90d">3m</string>
    <string name="comick_home_view_all">Tout afficher</string>
    <string name="comick_home_loading">Chargement…</string>
    <string name="comick_home_load_failed">Échec du chargement de ComicK.</string>
    <string name="comick_home_empty">Rien ici.</string>
    <string name="comick_home_review_by">par %1$s</string>
```

- [ ] **Step 4: Zkompilovat a spustit lint**

```
.\gradlew.bat compileDebugKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
```
Expected: obě BUILD SUCCESSFUL (route/stringy zatím nikde nepoužité, ale platný Kotlin/XML; lint ověří, že žádný string nechybí v některém ze 4 souborů).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "feat: pridat route a string zdroje pro ComicK domovskou obrazovku"
```

---

### Task 4: `ComicKHomeViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeViewModel.kt`

**Interfaces:**
- Consumes: `ComicKSource.getTop(): TopFeed` a `ComicKSource.getUpdates(order: String, page: Int): List<ChapterUpdate>` (Task 1, 2), `MangaRepository.openPreview(manga: SManga): String` (existuje).
- Produces: `enum class HomeTab { HOME, UPDATES }`; `class ComicKHomeViewModel` s `StateFlow`y: `tab`, `topFeed: StateFlow<TopFeed?>`, `loading`, `error`, `showCompleted: StateFlow<Boolean>`, `popularNewWindow: StateFlow<String>`, `mostRecentPopularWindow: StateFlow<String>`, `updatesOrder: StateFlow<String>`, `updates: StateFlow<List<ChapterUpdate>>`, `updatesLoading`, `openingManga`, `openError`; metody `setTab`, `retry`, `setShowCompleted`, `setPopularNewWindow`, `setMostRecentPopularWindow`, `setUpdatesOrder`, `loadMoreUpdates`, `openManga`, `clearOpenError`.

Vzor: `SourceResolverViewModel`/`GroupViewModel` (init fetch, `StateFlow`y) + `SourceBrowseViewModel.openManga`/`_openingManga`/`_openError` (identická logika). Žádný dedikovaný unit test - stejný precedens jako `SourceResolverViewModel`/`SourceBrowseViewModel`/`GroupViewModel` (viz "Testování" sekce spec dokumentu - ověřuje se manuálně kvůli závislosti na Compose navigaci).

- [ ] **Step 1: Napsat `ComicKHomeViewModel.kt`**

```kotlin
package com.haise.jiyu.ui.comickhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ChapterUpdate
import com.haise.jiyu.source.comick.ComicKSource
import com.haise.jiyu.source.comick.TopFeed
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { HOME, UPDATES }

/** ComicK domovská obrazovka - Domů (5 sekcí z /top) + Aktualizace (chapter feed z /chapter). */
@HiltViewModel
class ComicKHomeViewModel @Inject constructor(
    private val comicKSource: ComicKSource,
    private val repository: MangaRepository,
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.HOME)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    private val _topFeed = MutableStateFlow<TopFeed?>(null)
    val topFeed: StateFlow<TopFeed?> = _topFeed.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    private val _popularNewWindow = MutableStateFlow("7")
    val popularNewWindow: StateFlow<String> = _popularNewWindow.asStateFlow()

    private val _mostRecentPopularWindow = MutableStateFlow("7")
    val mostRecentPopularWindow: StateFlow<String> = _mostRecentPopularWindow.asStateFlow()

    private val _updatesOrder = MutableStateFlow("hot")
    val updatesOrder: StateFlow<String> = _updatesOrder.asStateFlow()

    private val _updates = MutableStateFlow<List<ChapterUpdate>>(emptyList())
    val updates: StateFlow<List<ChapterUpdate>> = _updates.asStateFlow()

    private val _updatesLoading = MutableStateFlow(false)
    val updatesLoading: StateFlow<Boolean> = _updatesLoading.asStateFlow()

    private var updatesPage = 1

    private val _openingManga = MutableStateFlow<SManga?>(null)
    val openingManga: StateFlow<SManga?> = _openingManga.asStateFlow()

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    init {
        loadTop()
    }

    fun retry() {
        if (_tab.value == HomeTab.HOME) loadTop() else loadUpdatesFirstPage()
    }

    private fun loadTop() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _topFeed.value = comicKSource.getTop()
            } catch (e: Exception) {
                e.report("comickhome:getTop")
                _error.value = e.toFriendlyMessage()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setTab(newTab: HomeTab) {
        _tab.value = newTab
        if (newTab == HomeTab.UPDATES && _updates.value.isEmpty()) loadUpdatesFirstPage()
    }

    fun setShowCompleted(completed: Boolean) { _showCompleted.value = completed }
    fun setPopularNewWindow(window: String) { _popularNewWindow.value = window }
    fun setMostRecentPopularWindow(window: String) { _mostRecentPopularWindow.value = window }

    fun setUpdatesOrder(order: String) {
        _updatesOrder.value = order
        loadUpdatesFirstPage()
    }

    private fun loadUpdatesFirstPage() {
        updatesPage = 1
        _updates.value = emptyList()
        _error.value = null
        loadMoreUpdates()
    }

    fun loadMoreUpdates() {
        if (_updatesLoading.value) return
        viewModelScope.launch {
            _updatesLoading.value = true
            try {
                val page = comicKSource.getUpdates(_updatesOrder.value, updatesPage)
                _updates.value = _updates.value + page
                if (page.isNotEmpty()) updatesPage++
            } catch (e: Exception) {
                e.report("comickhome:getUpdates")
                if (_updates.value.isEmpty()) _error.value = e.toFriendlyMessage()
            } finally {
                _updatesLoading.value = false
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
                e.report("comickhome:openManga")
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
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeViewModel.kt
git commit -m "feat: pridat ComicKHomeViewModel pro ComicK domovskou obrazovku"
```

---

### Task 5: `ComicKHomeScreen` — Domů sekce (UI)

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeScreen.kt`

**Interfaces:**
- Consumes: `ComicKHomeViewModel` (Task 4) - `tab`/`topFeed`/`loading`/`error`/`showCompleted`/`popularNewWindow`/`mostRecentPopularWindow`/`openingManga`/`openError` + `setTab`/`retry`/`setShowCompleted`/`setPopularNewWindow`/`setMostRecentPopularWindow`/`openManga`/`clearOpenError`. `TopFeed`/`ReviewItem` (Task 1). String zdroje z Tasku 3.
- Produces: `@Composable fun ComicKHomeScreen(onOpenManga: (String) -> Unit, onOpenSearch: () -> Unit, onOpenSection: (section: String, window: String?, title: String) -> Unit, viewModel: ComicKHomeViewModel = hiltViewModel())`. Tenhle task pokrývá jen `HomeTab.HOME` větev - `HomeTab.UPDATES` větev (Aktualizace) přidá Task 6 do STEJNÉHO souboru.

Vzor: `GroupScreen.kt` (top bar layout, loading/error+retry/empty stavy, `GroupTitleCard`-styl karty) - v tomhle tasku navíc s vodorovnou `LazyRow` místo `LazyVerticalGrid` pro náhled sekce.

- [ ] **Step 1: Napsat `ComicKHomeScreen.kt` (Domů větev + top bar + tab přepínač)**

```kotlin
package com.haise.jiyu.ui.comickhome

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.haise.jiyu.source.comick.ReviewItem
import com.haise.jiyu.source.comick.TopFeed
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.GlowCyan
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.Search

@Composable
fun ComicKHomeScreen(
    onOpenManga: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSection: (section: String, window: String?, title: String) -> Unit,
    viewModel: ComicKHomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsState()
    val topFeed by viewModel.topFeed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val popularNewWindow by viewModel.popularNewWindow.collectAsState()
    val mostRecentPopularWindow by viewModel.mostRecentPopularWindow.collectAsState()
    val openingManga by viewModel.openingManga.collectAsState()
    val openError by viewModel.openError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openError) {
        openError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOpenError()
        }
    }

    fun openManga(manga: SManga) {
        viewModel.openManga(manga, onOpenManga)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ComicK", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSearch) {
                        Icon(TablerIcons.Search, contentDescription = stringResource(R.string.common_search), tint = TextSecondary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(HomeTab.HOME to stringResource(R.string.comick_home_tab_home), HomeTab.UPDATES to stringResource(R.string.comick_home_tab_updates)).forEach { (t, label) ->
                        val selected = tab == t
                        Button(
                            onClick = { if (!selected) viewModel.setTab(t) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Violet.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (selected) Violet else TextSecondary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.15f)),
                            elevation = null,
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().background(screenGradient).padding(innerPadding),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.comick_home_loading), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(stringResource(R.string.comick_home_load_failed), color = TextSecondary, fontSize = 14.sp)
                        Text(error ?: "", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        OutlinedButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry), color = Violet) }
                    }
                }
                tab == HomeTab.HOME -> {
                    val feed = topFeed
                    if (feed == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.comick_home_empty), color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + navBottom)) {
                            item {
                                val recentlyAddedLabel = stringResource(R.string.comick_home_recently_added)
                                val completedLabel = stringResource(R.string.comick_home_completed)
                                ToggleSection(
                                    leftLabel = recentlyAddedLabel,
                                    rightLabel = completedLabel,
                                    rightSelected = showCompleted,
                                    onToggle = { viewModel.setShowCompleted(it) },
                                    comics = if (showCompleted) feed.completed else feed.recentlyAdded,
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection(if (showCompleted) "completed" else "recently_added", null, if (showCompleted) completedLabel else recentlyAddedLabel) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_popular_new)
                                WindowSection(
                                    title = label,
                                    window = popularNewWindow,
                                    onWindowChange = { viewModel.setPopularNewWindow(it) },
                                    comics = feed.popularNew[popularNewWindow].orEmpty(),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("popular_new", popularNewWindow, label) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_most_recent_popular)
                                WindowSection(
                                    title = label,
                                    window = mostRecentPopularWindow,
                                    onWindowChange = { viewModel.setMostRecentPopularWindow(it) },
                                    comics = feed.mostRecentPopular[mostRecentPopularWindow].orEmpty(),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("most_recent_popular", mostRecentPopularWindow, label) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_recent_reviews)
                                ReviewSection(
                                    reviews = feed.recentReviews,
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("recent_reviews", null, label) },
                                )
                            }
                        }
                    }
                }
                // HomeTab.UPDATES vetev pridava Task 6
            }
            if (openingManga != null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    JiyuLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.comick_home_view_all),
            color = Violet,
            fontSize = 12.sp,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onViewAll() }) },
        )
    }
}

@Composable
private fun ToggleSection(
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    comics: List<SManga>,
    onOpenManga: (SManga) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(
                    leftLabel, color = if (!rightSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggle(false) }) },
                )
                Text("/", color = TextSecondary, fontSize = 16.sp)
                Text(
                    rightLabel, color = if (rightSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggle(true) }) },
                )
            }
            Text(
                stringResource(R.string.comick_home_view_all), color = Violet, fontSize = 12.sp,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onViewAll() }) },
            )
        }
        MangaRow(comics, onOpenManga)
    }
}

@Composable
private fun WindowSection(
    title: String,
    window: String,
    onWindowChange: (String) -> Unit,
    comics: List<SManga>,
    onOpenManga: (SManga) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        SectionHeader(title, onViewAll)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("7" to R.string.comick_home_window_7d, "30" to R.string.comick_home_window_30d, "90" to R.string.comick_home_window_90d).forEach { (value, labelRes) ->
                val selected = window == value
                Text(
                    stringResource(labelRes),
                    color = if (selected) Violet else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Violet.copy(alpha = 0.15f) else Color.Transparent)
                        .pointerInput(value) { detectTapGestures(onTap = { onWindowChange(value) }) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        MangaRow(comics, onOpenManga)
    }
}

@Composable
private fun MangaRow(comics: List<SManga>, onOpenManga: (SManga) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(comics, key = { it.sourceId + it.url }) { manga ->
            ComicKMangaCard(manga = manga, onClick = { onOpenManga(manga) })
        }
    }
}

@Composable
private fun ReviewSection(reviews: List<ReviewItem>, onOpenManga: (SManga) -> Unit, onViewAll: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.comick_home_recent_reviews), onViewAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(reviews, key = { it.comic.sourceId + it.comic.url + it.content.hashCode() }) { review ->
                ReviewCard(review = review, onClick = { onOpenManga(review.comic) })
            }
        }
    }
}

@Composable
internal fun ReviewCard(review: ReviewItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                ) {
                    SubcomposeAsyncImage(
                        model = review.comic.coverUrl,
                        contentDescription = review.comic.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val state = painter.state
                        if (review.comic.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)))
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
                Text(
                    review.comic.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (review.title != null) {
                Text(review.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            Text(review.content, color = TextSecondary, fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (review.authorName != null) {
                Text(stringResource(R.string.comick_home_review_by, review.authorName), color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Stejný vizuální střih jako `GroupScreen.GroupTitleCard`/`SourceBrowseScreen.BrowseMangaCard`, jen fixní šířka pro LazyRow místo mřížky (karty se v kódu nesdílí mezi soubory, zavedená konvence). */
@Composable
internal fun ComicKMangaCard(manga: SManga, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "comick_manga_card_scale",
    )

    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(0.68f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
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
                    Icon(TablerIcons.Book, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                }
            } else {
                SubcomposeAsyncImageContent()
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xEA070B14)))),
        )
        Text(
            text = manga.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}
```

Poznámka: `stringResource()` je `@Composable` funkce, jde volat jen během kompozice - proto se v každém `item { }` bloku nejdřív spočítá `val label = stringResource(...)` (běží v kompozici) a teprve TEN `label` (obyčejný `String`) se pak zachytí uvnitř `onViewAll = { ... }` lambdy (ta NENÍ `@Composable` kontext, spouští se až při kliknutí). Nikdy nevolat `stringResource()` přímo uvnitř `onViewAll`/`onTap`/jiné plain lambdy - nezkompiluje se.

- [ ] **Step 2: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeScreen.kt
git commit -m "feat: pridat Domu sekce ComicKHomeScreen (5 sekci)"
```

---

### Task 6: `ComicKHomeScreen` — Aktualizace (Updates feed UI)

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeScreen.kt`

**Interfaces:**
- Consumes: `ComicKHomeViewModel.updatesOrder`/`updates`/`updatesLoading` + `setUpdatesOrder`/`loadMoreUpdates` (Task 4). `ChapterUpdate` (Task 2).
- Produces: Doplní `when` větev `tab == HomeTab.UPDATES` do `ComicKHomeScreen` (nahrazuje komentář `// HomeTab.UPDATES vetev pridava Task 6` z Tasku 5).

- [ ] **Step 1: Přidat importy**

Do `ComicKHomeScreen.kt` přidat k existujícím importům:

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.haise.jiyu.source.comick.ChapterUpdate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

- [ ] **Step 2: Nahradit komentář `// HomeTab.UPDATES vetev pridava Task 6` skutečnou větví**

V `ComicKHomeScreen`'s `when` bloku (uvnitř hlavního `Box`), nahradit řádek `// HomeTab.UPDATES vetev pridava Task 6` za:

```kotlin
                tab == HomeTab.UPDATES -> {
                    val updates by viewModel.updates.collectAsState()
                    val updatesOrder by viewModel.updatesOrder.collectAsState()
                    val updatesLoading by viewModel.updatesLoading.collectAsState()
                    val listState = rememberLazyListState()

                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 5 && totalItems > 0
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !updatesLoading) viewModel.loadMoreUpdates()
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("hot" to stringResource(R.string.source_browse_popular), "new" to stringResource(R.string.source_browse_latest)).forEach { (value, label) ->
                                val selected = updatesOrder == value
                                Button(
                                    onClick = { if (!selected) viewModel.setUpdatesOrder(value) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Violet.copy(alpha = 0.2f) else Color.Transparent,
                                        contentColor = if (selected) Violet else TextSecondary,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.15f)),
                                    elevation = null,
                                ) { Text(label, fontSize = 13.sp) }
                            }
                        }
                        if (updates.isEmpty() && !updatesLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.comick_home_empty), color = TextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp).let {
                                    PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBottom)
                                },
                            ) {
                                items(updates, key = { it.chapter.sourceId + it.chapter.url }) { update ->
                                    UpdateRow(update = update, onClick = { openManga(update.comic) })
                                }
                                if (updatesLoading) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            JiyuLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
```

- [ ] **Step 3: Přidat `UpdateRow` composable**

Za `ComicKMangaCard` (na konec souboru) přidat:

```kotlin
@Composable
private fun UpdateRow(update: ChapterUpdate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
            SubcomposeAsyncImage(
                model = update.comic.coverUrl,
                contentDescription = update.comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val state = painter.state
                if (update.comic.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)))
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(update.comic.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(update.chapter.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val groupName = update.chapter.scanlationGroup
            if (!groupName.isNullOrBlank()) {
                Text(groupName, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (update.chapter.dateUpload > 0L) {
                Text(
                    SimpleDateFormat("d. M.", Locale.getDefault()).format(Date(update.chapter.dateUpload)),
                    color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${update.upCount}▲", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                Text("${update.commentCount}💬", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}
```

- [ ] **Step 4: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKHomeScreen.kt
git commit -m "feat: pridat Aktualizace feed do ComicKHomeScreen"
```

---

### Task 7: `ComicKSectionScreen` — "zobrazit vše"

**Files:**
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKSectionViewModel.kt`
- Create: `app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKSectionScreen.kt`

**Interfaces:**
- Consumes: `ComicKSource.getTop(): TopFeed` (Task 1, cache hit - žádný nový network request), `MangaRepository.openPreview` (existuje), `ComicKMangaCard`/`ReviewCard` composables (Task 5, oba `internal` ve `ComicKHomeScreen.kt`, tedy volatelné odsud - stejný soubor/package `com.haise.jiyu.ui.comickhome`).
- Produces: `class ComicKSectionViewModel` s `StateFlow`y `title`, `comics: StateFlow<List<SManga>>`, `reviews: StateFlow<List<ReviewItem>>`, `loading`, `error`, `openingManga`, `openError`, metody `retry`/`openManga`/`clearOpenError`. `@Composable fun ComicKSectionScreen(onBack: () -> Unit, onOpenManga: (String) -> Unit, viewModel: ComicKSectionViewModel = hiltViewModel())`.

Section klíče (z Tasku 3): `"recently_added"`, `"completed"`, `"popular_new"`, `"most_recent_popular"`, `"recent_reviews"`. Poslední z nich renderuje `reviews`, ostatní čtyři `comics`.

- [ ] **Step 1: Napsat `ComicKSectionViewModel.kt`**

```kotlin
package com.haise.jiyu.ui.comickhome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ComicKSource
import com.haise.jiyu.source.comick.ReviewItem
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Zobrazit vše" na jednu sekci ComicK domovské obrazovky - znovupoužívá [ComicKSource.getTop]'s cache, žádný nový network request. */
@HiltViewModel
class ComicKSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicKSource: ComicKSource,
    private val repository: MangaRepository,
) : ViewModel() {

    private val section: String = checkNotNull(savedStateHandle["section"])
    private val window: String = savedStateHandle.get<String>("window")?.ifBlank { "7" } ?: "7"

    val title: String = savedStateHandle.get<String>("title").orEmpty()

    private val _comics = MutableStateFlow<List<SManga>>(emptyList())
    val comics: StateFlow<List<SManga>> = _comics.asStateFlow()

    private val _reviews = MutableStateFlow<List<ReviewItem>>(emptyList())
    val reviews: StateFlow<List<ReviewItem>> = _reviews.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openingManga = MutableStateFlow<SManga?>(null)
    val openingManga: StateFlow<SManga?> = _openingManga.asStateFlow()

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val feed = comicKSource.getTop()
                when (section) {
                    "recently_added"      -> _comics.value = feed.recentlyAdded
                    "completed"           -> _comics.value = feed.completed
                    "popular_new"         -> _comics.value = feed.popularNew[window].orEmpty()
                    "most_recent_popular" -> _comics.value = feed.mostRecentPopular[window].orEmpty()
                    "recent_reviews"      -> _reviews.value = feed.recentReviews
                }
            } catch (e: Exception) {
                e.report("comicksection:$section")
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
                e.report("comicksection:openManga")
                _openError.value = e.toFriendlyMessage()
            } finally {
                _openingManga.value = null
            }
        }
    }

    fun clearOpenError() { _openError.value = null }
}
```

- [ ] **Step 2: Napsat `ComicKSectionScreen.kt`**

```kotlin
package com.haise.jiyu.ui.comickhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.haise.jiyu.R
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft

@Composable
fun ComicKSectionScreen(
    onBack: () -> Unit,
    onOpenManga: (String) -> Unit,
    viewModel: ComicKSectionViewModel = hiltViewModel(),
) {
    val comics by viewModel.comics.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = null, tint = TextPrimary)
                }
                Text(viewModel.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(screenGradient).padding(innerPadding)) {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { JiyuLoadingIndicator() }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(stringResource(R.string.comick_home_load_failed), color = TextSecondary, fontSize = 14.sp)
                        Text(error ?: "", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        OutlinedButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry), color = Violet) }
                    }
                }
                reviews.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, bottom = 16.dp + navBottom)) {
                    items(reviews) { review ->
                        Box(modifier = Modifier.padding(vertical = 6.dp)) {
                            ReviewCard(review = review, onClick = { viewModel.openManga(review.comic, onOpenManga) })
                        }
                    }
                }
                comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.comick_home_empty), color = TextSecondary, fontSize = 14.sp)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp + navBottom),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(comics, key = { it.sourceId + it.url }) { manga ->
                        ComicKMangaCard(manga = manga, onClick = { viewModel.openManga(manga, onOpenManga) })
                    }
                }
            }
            if (openingManga != null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    JiyuLoadingIndicator()
                }
            }
        }
    }
}
```

Poznámka: `ComicKMangaCard` má v Tasku 5 fixní `Modifier.width(110.dp)` navrženou pro `LazyRow` - v `LazyVerticalGrid` s `GridCells.Adaptive(minSize = 110.dp)` fixní šířka kartu jen omezí na minimum buňky, vizuálně to bude fungovat (stejný princip jako `GroupTitleCard`, jen bez `aspectRatio`-first přístupu) - pokud by karty v mřížce vypadaly nesouměrně, zvážit v tomhle tasku úpravu `ComicKMangaCard` na `fillMaxWidth()` uvnitř grid kontextu (např. přidáním volitelného `Modifier` parametru, který volající předá).

- [ ] **Step 3: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKSectionViewModel.kt app/src/main/kotlin/com/haise/jiyu/ui/comickhome/ComicKSectionScreen.kt
git commit -m "feat: pridat ComicKSectionScreen (zobrazit vse na sekci)"
```

---

### Task 8: Navigační wiring + manuální ověření

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `ComicKHomeScreen` (Task 5+6), `ComicKSectionScreen` (Task 7), `Routes.COMICK_HOME`/`Routes.COMICK_SECTION`/`Routes.comickSection()` (Task 3).

- [ ] **Step 1: Přidat importy**

Do `NavGraph.kt`, abecedně mezi existující `com.haise.jiyu.ui.community.CommunityScreen` a `com.haise.jiyu.ui.css.CustomCssScreen`:

```kotlin
import com.haise.jiyu.ui.comickhome.ComicKHomeScreen
import com.haise.jiyu.ui.comickhome.ComicKSectionScreen
```

- [ ] **Step 2: Zaregistrovat obě nové routy**

Za blok `composable(route = Routes.GROUP, ...)` (končí před `composable(Routes.SETTINGS)`), přidat:

```kotlin
        composable(Routes.COMICK_HOME) {
            ComicKHomeScreen(
                onOpenManga = { mangaId -> navController.navigate(Routes.detail(mangaId)) },
                onOpenSearch = { navController.navigate(Routes.globalSearch()) },
                onOpenSection = { section, window, title -> navController.navigate(Routes.comickSection(section, window, title)) },
            )
        }

        composable(
            route = Routes.COMICK_SECTION,
            arguments = listOf(
                navArgument("section") { type = NavType.StringType },
                navArgument("window") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ComicKSectionScreen(
                onBack = { navController.popBackStack() },
                onOpenManga = { mangaId -> navController.navigate(Routes.detail(mangaId)) },
            )
        }
```

`Routes.browseRoute(appMode)` už na `COMICK_HOME` ukazuje od Tasku 3 - žádná další úprava zde netřeba, tenhle task jen registruje composable bloky, které dřív chyběly.

- [ ] **Step 3: Zkompilovat celý projekt**

```
.\gradlew.bat compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Spustit celou testovací sadu a lint**

```
.\gradlew.bat testDebugUnitTest lintDebug --console=plain
```
Expected: BUILD SUCCESSFUL, všechny testy zelené (Task 1 a 2 nové testy včetně).

- [ ] **Step 5: Manuální ověření na zařízení/emulátoru**

1. Přepnout appku do ComicK režimu (Nastavení).
2. Otevřít záložku Procházet → ověřit, že se místo staré mřížky zobrazí nová domovská obrazovka s 5 sekcemi (Recently Added/Completed přepínač, Popular New Comics + 7d/1m/3m, Most Recent Popular + 7d/1m/3m, Recent Reviews).
3. Přepnout 7d/1m/3m u obou časových sekcí → ověřit, že se obsah řady mění (žádný nový network request - ověřit v logu/network inspektoru, že se `/top` nevolá znovu).
4. Kliknout "Zobrazit vše" na libovolné ze 4 mřížkových sekcí → ověřit plnou mřížku, žádný nový `/top` request.
5. Kliknout "Zobrazit vše" na Recent Reviews → ověřit seznam recenzí (ne mřížku obálek).
6. Přepnout na "Aktualizace" tab → ověřit chapter feed, přepnout Hot/New → ověřit že se obsah mění, scrollovat dolů → ověřit že se dotahují další stránky (network log ukáže rostoucí `page=` parametr).
7. Kliknout na libovolný komiks kdekoliv (sekce, zobrazit vše, updates řádek) → ověřit že se otevře jeho ComicK detail.
8. Ikonka hledání v top baru → ověřit že otevře GlobalSearch.
9. Přepnout appku zpět do Klasického režimu → ověřit, že Procházet ukazuje beze změny starou mřížku zdrojů (regrese na ~180 ostatních zdrojů).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/navigation/NavGraph.kt
git commit -m "feat: zaregistrovat ComicK domovskou obrazovku a sekce v navigaci"
```

---

## Self-Review (proveden při psaní plánu)

**Pokrytí spec:** Datová vrstva `/top`+`/chapter` (Task 1, 2), 5 sekcí (Task 5), přepínač Domů/Aktualizace na téže obrazovce (Task 5, 6), "zobrazit vše" na všech 5 sekcích včetně Recent Reviews (Task 7), nahrazení `browseRoute` (Task 3, 8), in-memory cache `/top` sdílená mezi Home a "zobrazit vše" (Task 1 `cachedTop`, Task 7 znovupoužívá `getTop()`) - všechny body spec dokumentu mají odpovídající task.

**Typová konzistence:** `TopFeed`/`ReviewItem`/`ChapterUpdate` (Task 1, 2) používají stejná jména polí, jaká `ComicKHomeViewModel`/`ComicKSectionViewModel`/obrazovky (Task 4-7) čtou (`recentlyAdded`, `completed`, `popularNew`, `mostRecentPopular`, `recentReviews`, `upCount`, `commentCount`). `Routes.comickSection(section, window, title)` (Task 3) bere stejné 3 parametry, jaké `ComicKHomeScreen`'s `onOpenSection` (Task 5) posílá a `ComicKSectionViewModel` (Task 7) čte ze `SavedStateHandle`. Section klíčové řetězce (`"recently_added"` atd.) jsou identické napříč Task 3 (dokumentace), Task 5 (volání `onOpenSection`) a Task 7 (`when (section)`).

**Mimo rozsah** (shoda se spec dokumentem): `/top`'s `trending`/`rank`/`recentRank`/`follows`/`comicsByCurrentSeason`/`recentCustomLists`/`extendedNews` pole se nikde neparsují ani nepoužívají - jen `news`/`completions`/`topFollowNewComics`/`topFollowComics`/`recentReviews`.
