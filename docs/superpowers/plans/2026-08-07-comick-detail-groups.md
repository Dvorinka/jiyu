# ComicK detail obohacený o skupiny — Implementační plán (Sub-projekt 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Opravit dva bugy v `ComicKSource` (chybné "Vol.null"/"– null" v názvu kapitoly, chybějící `contentType`), doplnit strukturovaná data o překladatelských skupinách (`SChapter.groups`, nový Room sloupec `groupsJson`), schovat stahovací ikonku u ComicK kapitol a nahradit pád do čtečky srozumitelnou hláškou.

**Architecture:** Změny se soustředí do tří vrstev: zdrojová (`ComicKSource.kt` — opravy parsování JSON), datová (`ChapterEntity`/`AppDatabase`/`MangaRepository` — nový sloupec a jeho naplnění), a UI (`MangaDetailScreen.kt` — schování ikonky, přesměrování kliku na kapitolu). Každá vrstva má vlastní task a vlastní test, žádná mezikroková vrstva se nemusí měnit dvakrát.

**Tech Stack:** Kotlin, Jetpack Compose, Room (SQLite migrace), OkHttp + `org.json` (žádná nová závislost), JUnit + Robolectric (migrace) + MockWebServer (zdroj).

## Global Constraints

- Commit po každém tasku (stejná konvence jako Sub-projekt 1: `docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md`).
- Žádná nová externí knihovna — serializace skupin jde přes `org.json`, který už `ComicKSource.kt` používá.
- Room verze se zvedá z 29 na 30 (`MIGRATION_29_30`), stejný SQL vzor jako `MIGRATION_27_28` (`ALTER TABLE ... ADD COLUMN`).
- Design doc: `docs/superpowers/specs/2026-08-05-comick-aggregated-mode-design.md`, sekce "Sub-projekt 2".

---

### Task 1: Oprava "Vol.null"/"– null" a chybějícího `volume` u ComicK kapitol

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt:220-242` (`chapterFromJson`)
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Consumes: nic nového, jen opravuje existující privátní `chapterFromJson(json: JSONObject, mangaUrl: String): SChapter?`.
- Produces: `SChapter.volume` je nyní u ComicK kapitol skutečně naplněné (dřív se `vol` spočítalo, ale nikdy se nedostalo do konstruktoru `SChapter` — beze změny by "seskupit podle volume" v `MangaDetailScreen.kt:756` u ComicK titulů nikdy nic neseskupilo).

- [ ] **Step 1: Napsat padající test**

Do `ComicKSourceTest.kt` přidat za test `getChapterList resolves hid first, then pages` (za řádek 92, před `server error throws`):

```kotlin
    @Test
    fun `getChapterList treats explicit JSON null vol and title as missing, not the literal string "null"`() = runTest {
        // ComicK API vraci "vol" a "title" jako JSON null (ne jako chybejici klic) -
        // overeno zive na /comic/{hid}/chapters. Android org.json.optString() na
        // JSONObject.NULL vraci doslovny retezec "null", ne "" - bez isNull() kontroly
        // se v UI zobrazi "Vol.null Ch.3 - null".
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(
                        """{"chapters": [{"hid": "ch1", "chap": "3", "vol": null, "title": null, "created_at": "2026-01-01T00:00:00Z"}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals("Ch.3", chapters[0].name)
        assertEquals(null, chapters[0].volume)
    }

    @Test
    fun `getChapterList carries a real vol value into SChapter#volume`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(
                        """{"chapters": [{"hid": "ch1", "chap": "3", "vol": "2", "title": "", "created_at": "2026-01-01T00:00:00Z"}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals("Vol.2 Ch.3", chapters[0].name)
        assertEquals("2", chapters[0].volume)
    }
```

- [ ] **Step 2: Spustit testy a ověřit, že první nový test padá**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: `getChapterList treats explicit JSON null vol and title...` FAIL (name obsahuje "Vol.null"/"– null"), druhý test může projít náhodou (volume se nikdy neposílá) nebo padnout na `assertEquals("2", ...)` vs `null` — obojí je v pořádku, oba testy musí po Step 3 projít.

- [ ] **Step 3: Opravit `chapterFromJson`**

V `ComicKSource.kt` nahradit:

```kotlin
    private fun chapterFromJson(json: JSONObject, mangaUrl: String): SChapter? {
        val chHid = json.optString("hid").ifBlank { return null }
        val chap  = json.optString("chap", "0")
        val vol   = json.optString("vol").ifBlank { null }
        val title = json.optString("title").ifBlank { null }

        val chapterNum = chap.toFloatOrNull() ?: 0f
        val name = buildString {
            if (vol != null) append("Vol.$vol ")
            append("Ch.$chap")
            if (!title.isNullOrBlank()) append(" – $title")
        }

        return SChapter(
            sourceId      = id,
            mangaUrl      = mangaUrl,
            url           = "$apiBase/chapter/$chHid",
            name          = name,
            chapterNumber = chapterNum,
            dateUpload    = parseIso(json.optString("created_at")),
        )
    }
```

za:

```kotlin
    private fun chapterFromJson(json: JSONObject, mangaUrl: String): SChapter? {
        val chHid = json.optString("hid").ifBlank { return null }
        val chap  = json.optString("chap", "0")
        // ComicK API vraci "vol"/"title" jako JSON null (ne jako chybejici klic) -
        // org.json.optString() na JSONObject.NULL vraci doslovny retezec "null",
        // proto je nutne nejdriv zkontrolovat isNull(), ne az .ifBlank {}.
        val vol   = if (json.isNull("vol")) null else json.optString("vol").ifBlank { null }
        val title = if (json.isNull("title")) null else json.optString("title").ifBlank { null }

        val chapterNum = chap.toFloatOrNull() ?: 0f
        val name = buildString {
            if (vol != null) append("Vol.$vol ")
            append("Ch.$chap")
            if (!title.isNullOrBlank()) append(" – $title")
        }

        return SChapter(
            sourceId      = id,
            mangaUrl      = mangaUrl,
            url           = "$apiBase/chapter/$chHid",
            name          = name,
            chapterNumber = chapterNum,
            dateUpload    = parseIso(json.optString("created_at")),
            volume        = vol,
        )
    }
```

- [ ] **Step 4: Spustit testy znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: PASS (všech 7 testů v souboru).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "fix: ComicK kapitoly ukazovaly Vol.null/- null misto skutecnych hodnot"
```

---

### Task 2: `contentType` u ComicK podle pole `country`

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt` (`parseComicList`, `getMangaDetails`)
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Consumes: nic nového.
- Produces: nová privátní funkce `contentTypeFromCountry(country: String): String` — použije ji i Sub-projekt 3 (`SManga.contentType` slouží k zúžení kandidátních zdrojů, viz design doc bod 2 "Klíčová rozhodnutí").

- [ ] **Step 1: Napsat padající testy**

Do `ComicKSourceTest.kt` upravit `searchArrayJson` a `mangaDetailJson` fixtures tak, aby obsahovaly `country`, a přidat nové testy. Nahradit:

```kotlin
    private val searchArrayJson = """
        [ {"title": "Test Series", "slug": "test-series", "md_covers": [{"b2key": "cover.jpg"}]} ]
    """.trimIndent()

    private val mangaDetailJson = """
        {"comic": {"hid": "abcd", "desc": "A summary.", "status": 1, "year": 2020}, "authors": [{"name": "Jane"}], "genres": [{"name": "Action"}]}
    """.trimIndent()
```

za:

```kotlin
    private val searchArrayJson = """
        [ {"title": "Test Series", "slug": "test-series", "country": "kr", "md_covers": [{"b2key": "cover.jpg"}]} ]
    """.trimIndent()

    private val mangaDetailJson = """
        {"comic": {"hid": "abcd", "desc": "A summary.", "status": 1, "year": 2020, "country": "kr"}, "authors": [{"name": "Jane"}], "genres": [{"name": "Action"}]}
    """.trimIndent()
```

Přidat za test `getPopular parses title and cover from md_covers`:

```kotlin
    @Test
    fun `getPopular maps country to contentType (kr to MANHWA)`() = runTest {
        val result = source.getPopular(1)
        assertEquals("MANHWA", result[0].contentType)
    }

    @Test
    fun `getMangaDetails re-maps contentType from the detail endpoint's country field too`() = runTest {
        val manga = source.getPopular(1).first().copy(contentType = "MANGA")
        val details = source.getMangaDetails(manga)
        assertEquals("MANHWA", details.contentType)
    }

    @Test
    fun `contentTypeFromCountry maps jp to MANGA, cn to MANHUA, and anything unknown to MANGA`() {
        assertEquals("MANGA", source.contentTypeFromCountry("jp"))
        assertEquals("MANHUA", source.contentTypeFromCountry("cn"))
        assertEquals("MANGA", source.contentTypeFromCountry("xx"))
        assertEquals("MANGA", source.contentTypeFromCountry(""))
    }
```

`contentTypeFromCountry` bude `internal` (ne `private`) — stejný vzor jako jinde v repu (viz `internal fun` v `LibraryScreen.kt`, `TranslateRepository.kt` apod.), `internal` je v rámci modulu viditelné i z `src/test`, takže test může volat `source.contentTypeFromCountry(...)` přímo bez zvláštního wrapperu.

- [ ] **Step 2: Spustit testy, ověřit že padají**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: FAIL na `contentTypeFromCountryForTest` (metoda neexistuje) — build error je v pořádku, potvrzuje že test byl napsán před implementací.

- [ ] **Step 3: Implementovat**

V `parseComicList` nahradit:

```kotlin
    private fun parseComicList(arr: JSONArray): List<SManga> =
        (0 until arr.length()).mapNotNull { i ->
            val comic = arr.getJSONObject(i)
            val title = comic.optString("title").ifBlank { return@mapNotNull null }
            val slug  = comic.optString("slug").ifBlank { return@mapNotNull null }

            // Titulní obrázek: první položka md_covers s neprázdným b2key
            val coverUrl = comic.optJSONArray("md_covers")
                ?.let { covers ->
                    (0 until covers.length()).firstNotNullOfOrNull { j ->
                        covers.getJSONObject(j).optString("b2key").ifBlank { null }
                    }
                }
                ?.let { b2key -> "$coverBase/$b2key" }

            SManga(
                sourceId = id,
                url      = "$apiBase/comic/$slug",
                title    = title,
                coverUrl = coverUrl,
            )
        }
```

za:

```kotlin
    private fun parseComicList(arr: JSONArray): List<SManga> =
        (0 until arr.length()).mapNotNull { i ->
            val comic = arr.getJSONObject(i)
            val title = comic.optString("title").ifBlank { return@mapNotNull null }
            val slug  = comic.optString("slug").ifBlank { return@mapNotNull null }

            // Titulní obrázek: první položka md_covers s neprázdným b2key
            val coverUrl = comic.optJSONArray("md_covers")
                ?.let { covers ->
                    (0 until covers.length()).firstNotNullOfOrNull { j ->
                        covers.getJSONObject(j).optString("b2key").ifBlank { null }
                    }
                }
                ?.let { b2key -> "$coverBase/$b2key" }

            SManga(
                sourceId    = id,
                url         = "$apiBase/comic/$slug",
                title       = title,
                coverUrl    = coverUrl,
                contentType = contentTypeFromCountry(comic.optString("country")),
            )
        }

    /** ComicK nema vlastni "contentType" pole - odvozujeme ho z puvodu (jp/kr/cn). internal kvuli testu. */
    internal fun contentTypeFromCountry(country: String): String = when (country) {
        "jp"  -> "MANGA"
        "kr"  -> "MANHWA"
        "cn"  -> "MANHUA"
        else  -> "MANGA"
    }
```

V `getMangaDetails` nahradit poslední řádek funkce:

```kotlin
            manga.copy(description = desc, status = status, author = author, genres = genres, year = year)
```

za:

```kotlin
            manga.copy(
                description = desc,
                status      = status,
                author      = author,
                genres      = genres,
                year        = year,
                contentType = contentTypeFromCountry(comic.optString("country")),
            )
```

- [ ] **Step 4: Spustit testy znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: PASS (všech 10 testů v souboru).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "fix: ComicK nikdy nenastavoval contentType, vsechno vypadalo jako MANGA"
```

---

### Task 3: `SGroup` model a `SChapter.groups` — ComicK parsuje překladatelské skupiny

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/MangaSource.kt` (nová `SGroup`, `SChapter.groups`)
- Modify: `app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt` (`chapterFromJson`, nová `parseGroups`)
- Test: `app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt`

**Interfaces:**
- Consumes: `chapterFromJson` z Tasku 1 (`vol`, `title`, `chap` už opravené).
- Produces: `data class SGroup(val name: String, val slug: String? = null)` a `SChapter.groups: List<SGroup>` — použije je Task 5 (perzistence) a v budoucnu Sub-projekt 4 (klikací stránka skupiny, `slug` je pro to připravený).

- [ ] **Step 1: Napsat padající testy**

Do `ComicKSourceTest.kt` přidat za testy z Tasku 1:

```kotlin
    @Test
    fun `getChapterList prefers md_chapters_groups title over the raw group_name string, and keeps the slug`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(
                        """{"chapters": [{"hid": "ch1", "chap": "1", "vol": null, "title": null,
                            "created_at": "2026-01-01T00:00:00Z", "group_name": ["asurascans"],
                            "md_chapters_groups": [{"md_groups": {"title": "Asura", "slug": "asura"}}]}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters[0].groups.size)
        assertEquals("Asura", chapters[0].groups[0].name)
        assertEquals("asura", chapters[0].groups[0].slug)
        assertEquals("Asura", chapters[0].scanlationGroup)
    }

    @Test
    fun `getChapterList falls back to the raw group_name string when md_chapters_groups is empty`() = runTest {
        // Overeno zive na API - md_chapters_groups muze byt [] i kdyz group_name neni
        // prazdne (napr. kdyz skupinu ComicK smaze/anonymizuje, ale historicka kapitola zustane).
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(
                        """{"chapters": [{"hid": "ch1", "chap": "1", "vol": null, "title": null,
                            "created_at": "2026-01-01T00:00:00Z", "group_name": ["Official"],
                            "md_chapters_groups": []}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters[0].groups.size)
        assertEquals("Official", chapters[0].groups[0].name)
        assertEquals(null, chapters[0].groups[0].slug)
    }

    @Test
    fun `getChapterList handles multiple groups on one chapter`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(
                        """{"chapters": [{"hid": "ch1", "chap": "1", "vol": null, "title": null,
                            "created_at": "2026-01-01T00:00:00Z", "group_name": ["Asura", "Flame Scans"],
                            "md_chapters_groups": [
                                {"md_groups": {"title": "Asura", "slug": "asura"}},
                                {"md_groups": {"title": "Flame Scans", "slug": "flame-scans-kft5oueu"}}
                            ]}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters[0].groups.size)
        assertEquals("Asura, Flame Scans", chapters[0].scanlationGroup)
    }
```

- [ ] **Step 2: Spustit testy, ověřit že padají (kompilační chyba - `groups` na `SChapter` ještě neexistuje)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: FAIL (compile error - `Unresolved reference: groups`).

- [ ] **Step 3: Přidat `SGroup` a `SChapter.groups` do `MangaSource.kt`**

Nahradit:

```kotlin
/**
 * Kapitola tak, jak ji vrací konkrétní zdroj.
 */
data class SChapter(
    val sourceId: String,
    val mangaUrl: String,
    val url: String,
    val name: String,
    val chapterNumber: Float,
    val dateUpload: Long,
    val scanlationGroup: String? = null,
    val volume: String? = null,
)
```

za:

```kotlin
/** Překladatelská/scan skupina u konkrétní kapitoly - `slug` je nepovinný (ne každý zdroj ho má). */
data class SGroup(val name: String, val slug: String? = null)

/**
 * Kapitola tak, jak ji vrací konkrétní zdroj.
 */
data class SChapter(
    val sourceId: String,
    val mangaUrl: String,
    val url: String,
    val name: String,
    val chapterNumber: Float,
    val dateUpload: Long,
    val scanlationGroup: String? = null,
    val volume: String? = null,
    val groups: List<SGroup> = emptyList(),
)
```

- [ ] **Step 4: Naplnit `groups` v `ComicKSource.chapterFromJson`**

Nahradit (výsledek Tasku 1):

```kotlin
    private fun chapterFromJson(json: JSONObject, mangaUrl: String): SChapter? {
        val chHid = json.optString("hid").ifBlank { return null }
        val chap  = json.optString("chap", "0")
        // ComicK API vraci "vol"/"title" jako JSON null (ne jako chybejici klic) -
        // org.json.optString() na JSONObject.NULL vraci doslovny retezec "null",
        // proto je nutne nejdriv zkontrolovat isNull(), ne az .ifBlank {}.
        val vol   = if (json.isNull("vol")) null else json.optString("vol").ifBlank { null }
        val title = if (json.isNull("title")) null else json.optString("title").ifBlank { null }

        val chapterNum = chap.toFloatOrNull() ?: 0f
        val name = buildString {
            if (vol != null) append("Vol.$vol ")
            append("Ch.$chap")
            if (!title.isNullOrBlank()) append(" – $title")
        }

        return SChapter(
            sourceId      = id,
            mangaUrl      = mangaUrl,
            url           = "$apiBase/chapter/$chHid",
            name          = name,
            chapterNumber = chapterNum,
            dateUpload    = parseIso(json.optString("created_at")),
            volume        = vol,
        )
    }
```

za:

```kotlin
    private fun chapterFromJson(json: JSONObject, mangaUrl: String): SChapter? {
        val chHid = json.optString("hid").ifBlank { return null }
        val chap  = json.optString("chap", "0")
        // ComicK API vraci "vol"/"title" jako JSON null (ne jako chybejici klic) -
        // org.json.optString() na JSONObject.NULL vraci doslovny retezec "null",
        // proto je nutne nejdriv zkontrolovat isNull(), ne az .ifBlank {}.
        val vol   = if (json.isNull("vol")) null else json.optString("vol").ifBlank { null }
        val title = if (json.isNull("title")) null else json.optString("title").ifBlank { null }

        val chapterNum = chap.toFloatOrNull() ?: 0f
        val name = buildString {
            if (vol != null) append("Vol.$vol ")
            append("Ch.$chap")
            if (!title.isNullOrBlank()) append(" – $title")
        }

        val groups = parseGroups(json)

        return SChapter(
            sourceId        = id,
            mangaUrl        = mangaUrl,
            url             = "$apiBase/chapter/$chHid",
            name            = name,
            chapterNumber   = chapterNum,
            dateUpload      = parseIso(json.optString("created_at")),
            volume          = vol,
            scanlationGroup = groups.joinToString(", ") { it.name }.ifBlank { null },
            groups          = groups,
        )
    }

    /**
     * "group_name" je autoritativní seznam jmen/pořadí skupin u kapitoly.
     * "md_chapters_groups" ho jen doplňuje o slug a hezčí zobrazovací jméno, ale
     * ověřeno živě na API: může být kratší, nebo úplně `[]`, i když group_name
     * prázdné není (např. smazaná/anonymizovaná skupina u starší kapitoly).
     * Párujeme podle indexu; chybějící index = jen jméno z group_name bez slugu.
     */
    private fun parseGroups(json: JSONObject): List<SGroup> {
        val names = json.optJSONArray("group_name") ?: return emptyList()
        val mdGroups = json.optJSONArray("md_chapters_groups")
        return (0 until names.length()).map { i ->
            val rawName = names.optString(i)
            val mdGroup = mdGroups?.optJSONObject(i)?.optJSONObject("md_groups")
            SGroup(
                name = mdGroup?.optString("title")?.ifBlank { null } ?: rawName,
                slug = mdGroup?.optString("slug")?.ifBlank { null },
            )
        }
    }
```

- [ ] **Step 5: Spustit testy znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.source.comick.ComicKSourceTest"`
Expected: PASS (všech 13 testů v souboru).

- [ ] **Step 6: Ověřit, že build celého projektu (jiné zdroje používající `SChapter`) pořád projde**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest -q`
Expected: PASS — `groups` má výchozí hodnotu `emptyList()`, takže žádný z ostatních ~180 zdrojů, co staví `SChapter(...)` bez `groups`, se nerozbije.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/source/MangaSource.kt app/src/main/kotlin/com/haise/jiyu/source/comick/ComicKSource.kt app/src/test/kotlin/com/haise/jiyu/source/comick/ComicKSourceTest.kt
git commit -m "feat: ComicK kapitoly nesou strukturovana data o prekladatelskych skupinach"
```

---

### Task 4: Room migrace 29→30 (`ChapterEntity.groupsJson`)

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/data/db/entity/ChapterEntity.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/data/db/AppDatabase.kt`
- Modify: `app/src/main/kotlin/com/haise/jiyu/di/AppModule.kt`
- Test: `app/src/test/kotlin/com/haise/jiyu/data/db/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: nic.
- Produces: `ChapterEntity.groupsJson: String?` — nový sloupec, který Task 5 naplní (JSON pole `[{"name":...,"slug":...}]` serializované přes `org.json`).

- [ ] **Step 1: Napsat padající test (rozšíření existujícího migračního testu)**

V `AppDatabaseMigrationTest.kt` nahradit blok `.addMigrations(...)`:

```kotlin
            .addMigrations(
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
            )
```

za (přidán jen poslední řádek):

```kotlin
            .addMigrations(
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
            )
```

A přidat na konec testovací metody (před `db.close()`):

```kotlin
        // MIGRATION_29_30: groupsJson pridany na chapter, musi byt citelny (nullable, default null)
        // a musi prezit round-trip pres Room.
        val ch = com.haise.jiyu.data.db.entity.ChapterEntity(
            id = "ch1", mangaId = "m1", sourceId = "comick", url = "https://example.com/ch1",
            name = "Ch.1", chapterNumber = 1f, dateUpload = 0L,
            groupsJson = """[{"name":"Asura","slug":"asura"}]""",
        )
        chapters.upsertAll(listOf(ch))
        assertEquals("""[{"name":"Asura","slug":"asura"}]""", chapters.getById("ch1")?.groupsJson)
```

- [ ] **Step 2: Spustit test, ověřit že padá (kompilační chyba - `MIGRATION_29_30`/`groupsJson` ještě neexistují)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.db.AppDatabaseMigrationTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Přidat sloupec do entity**

V `ChapterEntity.kt` nahradit:

```kotlin
    val scanlationGroup: String? = null,
    val volume: String? = null,
)
```

za:

```kotlin
    val scanlationGroup: String? = null,
    val volume: String? = null,
    /** JSON pole [{"name":...,"slug":...}] - viz SGroup. Zatim se nikde nectete zpet do UI (pripraveno pro budouci klikaci stranku skupiny). */
    val groupsJson: String? = null,
)
```

- [ ] **Step 4: Přidat migraci a zvednout verzi**

V `AppDatabase.kt` nahradit `version = 29,` za `version = 30,`.

Přidat za `MIGRATION_28_29` (před uzavírací `}` companion objectu):

```kotlin
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapter ADD COLUMN groupsJson TEXT")
            }
        }
```

- [ ] **Step 5: Zaregistrovat migraci v `AppModule.kt`**

V `AppModule.kt` za řádek `AppDatabase.MIGRATION_28_29,` (řádek 225) přidat:

```kotlin
                AppDatabase.MIGRATION_29_30,
```

- [ ] **Step 6: Spustit test znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.db.AppDatabaseMigrationTest"`
Expected: PASS.

- [ ] **Step 7: Spustit celou test suite (Room schema export, ostatní testy)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest -q`
Expected: PASS. `exportSchema = true` znamená, že Room při buildu vygeneruje nový schema JSON soubor (`app/schemas/.../30.json`) - to je očekávané a patří do commitu.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/data/db/entity/ChapterEntity.kt app/src/main/kotlin/com/haise/jiyu/data/db/AppDatabase.kt app/src/main/kotlin/com/haise/jiyu/di/AppModule.kt app/src/test/kotlin/com/haise/jiyu/data/db/AppDatabaseMigrationTest.kt app/schemas
git commit -m "feat: Room migrace 29-30, novy sloupec chapter.groupsJson"
```

---

### Task 5: `MangaRepository` naplní `groupsJson` při ukládání kapitol

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/data/repository/MangaRepository.kt:254-273` (`refreshChapters`) a konec souboru (nová top-level funkce)
- Test: nový soubor `app/src/test/kotlin/com/haise/jiyu/data/repository/SerializeChapterGroupsTest.kt`

**Interfaces:**
- Consumes: `SChapter.groups: List<SGroup>` (Task 3), `ChapterEntity.groupsJson: String?` (Task 4).
- Produces: `internal fun serializeChapterGroups(groups: List<SGroup>): String?` — top-level funkce v `MangaRepository.kt` (mimo třídu, takže ji test volá přímo bez nutnosti sestavovat celý DI graf `MangaRepository`).

- [ ] **Step 1: Napsat padající test**

Vytvořit `SerializeChapterGroupsTest.kt`:

```kotlin
package com.haise.jiyu.data.repository

import com.haise.jiyu.source.SGroup
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializeChapterGroupsTest {

    @Test
    fun `empty groups list serializes to null, not an empty JSON array`() {
        assertNull(serializeChapterGroups(emptyList()))
    }

    @Test
    fun `groups with a slug round-trip through JSON`() {
        val json = serializeChapterGroups(listOf(SGroup(name = "Asura", slug = "asura")))!!
        val parsed = JSONArray(json)
        assertEquals(1, parsed.length())
        assertEquals("Asura", parsed.getJSONObject(0).getString("name"))
        assertEquals("asura", parsed.getJSONObject(0).getString("slug"))
    }

    @Test
    fun `a group without a slug serializes slug as JSON null`() {
        val json = serializeChapterGroups(listOf(SGroup(name = "Official", slug = null)))!!
        val parsed = JSONArray(json)
        assertEquals(true, parsed.getJSONObject(0).isNull("slug"))
    }
}
```

- [ ] **Step 2: Spustit test, ověřit že padá (kompilační chyba - `serializeChapterGroups` ještě neexistuje)**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.repository.SerializeChapterGroupsTest"`
Expected: FAIL (compile error - `Unresolved reference: serializeChapterGroups`).

- [ ] **Step 3: Implementovat v `MangaRepository.kt`**

Přidat import (za `import kotlinx.coroutines.flow.Flow` na řádku 23):

```kotlin
import org.json.JSONArray
import org.json.JSONObject
```

Nahradit (`refreshChapters`, řádky 259-269):

```kotlin
            ChapterEntity(
                id = chapterId(chapter),
                mangaId = mangaId,
                sourceId = chapter.sourceId,
                url = chapter.url,
                name = chapter.name,
                chapterNumber = chapter.chapterNumber,
                dateUpload = chapter.dateUpload,
                scanlationGroup = chapter.scanlationGroup,
                volume = chapter.volume,
            )
```

za:

```kotlin
            ChapterEntity(
                id = chapterId(chapter),
                mangaId = mangaId,
                sourceId = chapter.sourceId,
                url = chapter.url,
                name = chapter.name,
                chapterNumber = chapter.chapterNumber,
                dateUpload = chapter.dateUpload,
                scanlationGroup = chapter.scanlationGroup,
                volume = chapter.volume,
                groupsJson = serializeChapterGroups(chapter.groups),
            )
```

Na konec souboru (za poslední uzavírací `}` třídy `MangaRepository`) přidat top-level funkci:

```kotlin

/** JSON pole [{"name":...,"slug":...}] pro uložení SChapter.groups do ChapterEntity.groupsJson. */
internal fun serializeChapterGroups(groups: List<SGroup>): String? =
    groups.takeIf { it.isNotEmpty() }?.let { list ->
        JSONArray(list.map { JSONObject().apply { put("name", it.name); put("slug", it.slug) } }).toString()
    }
```

- [ ] **Step 4: Spustit test znovu, ověřit že prochází**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest --tests "com.haise.jiyu.data.repository.SerializeChapterGroupsTest"`
Expected: PASS.

- [ ] **Step 5: Spustit celou test suite**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/data/repository/MangaRepository.kt app/src/test/kotlin/com/haise/jiyu/data/repository/SerializeChapterGroupsTest.kt
git commit -m "feat: MangaRepository uklada SChapter.groups do noveho sloupce groupsJson"
```

---

### Task 6: Schovat stahovací ikonku u ComicK kapitol

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt:909-915` (`GlassChapterRow`)

**Interfaces:**
- Consumes: `ChapterEntity.sourceId` (existující pole, nic nového).
- Produces: nic navenek — čistě UI.

Bez automatického testu — Compose UI se v tomhle souboru netestuje jednotkově (stejná poznámka jako u Sub-projektu 1, `composable(...)` větvení). Ověří se manuálně v Step 3.

- [ ] **Step 1: Upravit `GlassChapterRow`**

V `MangaDetailScreen.kt` nahradit:

```kotlin
            when (chapter.downloadStatus) {
                DownloadStatus.DOWNLOADED  -> Icon(TablerIcons.CircleCheck, contentDescription = stringResource(R.string.detail_chapter_downloaded), tint = Cyan, modifier = Modifier.size(18.dp))
                DownloadStatus.DOWNLOADING -> Text("↓", color = Violet, fontSize = 16.sp)
                DownloadStatus.QUEUED      -> Text("⏳", fontSize = 14.sp)
                DownloadStatus.ERROR       -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) { Icon(TablerIcons.Download, contentDescription = stringResource(R.string.common_retry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                else                       -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) { Icon(TablerIcons.Download, contentDescription = stringResource(R.string.common_download), tint = TextSecondary, modifier = Modifier.size(18.dp)) }
            }
```

za:

```kotlin
            // ComicK je jen katalog/metadata (viz design doc) - stahovani dava smysl
            // az po vyreseni skutecneho zdroje (Sub-projekt 3), do te doby se ikonka
            // u ComicK kapitol vubec nevykresluje.
            if (chapter.sourceId != "comick") {
                when (chapter.downloadStatus) {
                    DownloadStatus.DOWNLOADED  -> Icon(TablerIcons.CircleCheck, contentDescription = stringResource(R.string.detail_chapter_downloaded), tint = Cyan, modifier = Modifier.size(18.dp))
                    DownloadStatus.DOWNLOADING -> Text("↓", color = Violet, fontSize = 16.sp)
                    DownloadStatus.QUEUED      -> Text("⏳", fontSize = 14.sp)
                    DownloadStatus.ERROR       -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) { Icon(TablerIcons.Download, contentDescription = stringResource(R.string.common_retry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                    else                       -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) { Icon(TablerIcons.Download, contentDescription = stringResource(R.string.common_download), tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                }
            }
```

- [ ] **Step 2: Zkompilovat**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manuální ověření (odloženo na konec plánu, Task 7 zasahuje do stejné obrazovky)**

Ověří se společně s Task 7 v jednom instalačním kole - viz Task 7 Step 4.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt
git commit -m "feat: schovat stahovaci ikonku u ComicK kapitol"
```

---

### Task 7: Srozumitelná hláška místo pádu čtečky u ComicK kapitol

**Files:**
- Modify: `app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt` (6 volání `onOpenChapter`/`onOpenChapterIncognito`)
- Modify: `app/src/main/res/values/strings.xml`, `values-en`, `values-es`, `values-fr` (nový string)

**Interfaces:**
- Consumes: `ChapterEntity.sourceId`, existující `snackbarHostState`/`Scaffold` infrastruktura (`MangaDetailScreen.kt:126,153`).
- Produces: nic navenek — čistě UI.

Bez automatického testu (stejný důvod jako Task 6). Ověří se manuálně v Step 6.

- [ ] **Step 1: Přidat string do všech 4 lokalizací**

`app/src/main/res/values/strings.xml`, nahradit:

```xml
    <string name="detail_no_volume">Bez volumu</string>
    <string name="detail_volume_label">Volume %1$s</string>
```

za:

```xml
    <string name="detail_no_volume">Bez volumu</string>
    <string name="detail_volume_label">Volume %1$s</string>
    <string name="detail_comick_read_unavailable">Čtení přes ComicK ještě nefunguje – hledání skutečného zdroje přidáme v příští aktualizaci.</string>
```

`app/src/main/res/values-en/strings.xml`, nahradit:

```xml
    <string name="detail_no_volume">No volume</string>
    <string name="detail_volume_label">Volume %1$s</string>
```

za:

```xml
    <string name="detail_no_volume">No volume</string>
    <string name="detail_volume_label">Volume %1$s</string>
    <string name="detail_comick_read_unavailable">Reading through ComicK doesn\'t work yet – source lookup is coming in a future update.</string>
```

`app/src/main/res/values-es/strings.xml`, nahradit:

```xml
    <string name="detail_no_volume">Sin volumen</string>
    <string name="detail_volume_label">Volumen %1$s</string>
```

za:

```xml
    <string name="detail_no_volume">Sin volumen</string>
    <string name="detail_volume_label">Volumen %1$s</string>
    <string name="detail_comick_read_unavailable">La lectura a través de ComicK todavía no funciona – la búsqueda de la fuente real llegará en una próxima actualización.</string>
```

`app/src/main/res/values-fr/strings.xml`, nahradit:

```xml
    <string name="detail_no_volume">Sans volume</string>
    <string name="detail_volume_label">Volume %1$s</string>
```

za:

```xml
    <string name="detail_no_volume">Sans volume</string>
    <string name="detail_volume_label">Volume %1$s</string>
    <string name="detail_comick_read_unavailable">La lecture via ComicK ne fonctionne pas encore – la recherche de la source réelle arrivera dans une prochaine mise à jour.</string>
```

- [ ] **Step 2: Přidat importy do `MangaDetailScreen.kt`**

Nahradit (řádky 62-63):

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

za:

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
```

(`ChapterEntity` je v souboru už naimportovaná — řádek 85, `import com.haise.jiyu.data.db.entity.ChapterEntity` — žádný další import navíc není potřeba.)

- [ ] **Step 3: Přidat `openChapter` helper funkci a nahradit volání**

Za řádek `val snackbarHostState = remember { SnackbarHostState() }` (řádek 126) přidat:

```kotlin
    val coroutineScope = rememberCoroutineScope()
    val comickReadUnavailableMessage = stringResource(R.string.detail_comick_read_unavailable)
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

Pak nahradit šest volání:

1. Řádek 426: `.clickable { onOpenChapter(chapter.id) }` → `.clickable { openChapter(chapter) }`
2. Řádek 448: `onClick = { showReadMenu = false; onOpenChapter(chapter.id) },` → `onClick = { showReadMenu = false; openChapter(chapter) },`
3. Řádek 453: `onClick = { showReadMenu = false; onOpenChapterIncognito(chapter.id) },` → `onClick = { showReadMenu = false; openChapter(chapter, incognito = true) },`
4. Řádek 541: `onClick = { showChapterOverflowMenu = false; firstUnread?.let { onOpenChapter(it.id) } },` → `onClick = { showChapterOverflowMenu = false; firstUnread?.let { openChapter(it) } },`
5. Řádek 740: `.clickable { onOpenChapter(chapter.id) }` → `.clickable { openChapter(chapter) }`
6. Řádky 779 a 792 (oba stejné): `onOpen = { onOpenChapter(chapter.id) },` → `onOpen = { openChapter(chapter) },`

- [ ] **Step 4: Zkompilovat**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Spustit celou test suite a lint**

Run: `cd "C:\Users\ilekr\Desktop\jiyu" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew.bat testDebugUnitTest lintDebug -q`
Expected: PASS.

- [ ] **Step 6: Manuální ověření na zařízení (pokrývá i Task 6)**

Sestavit a nainstalovat debug APK (`./gradlew.bat assembleDebug`, `adb install -r app/build/outputs/apk/debug/app-debug.apk`), na zařízení:
1. Přepnout Nastavení → Zdroje → ComicK agregovaný režim.
2. Otevřít libovolný titul, ověřit že se u kapitol zobrazují jména skupin a datum bez „null" (Task 1-3).
3. Ověřit, že chybí stahovací ikonka u všech kapitol (Task 6).
4. Kliknout na libovolnou kapitolu (hlavní tlačítko i položku v seznamu) — ověřit, že se zobrazí snackbar se srozumitelnou hláškou, ne pád do čtečky (Task 7).
5. Zkusit i „Přeskočit na první nepřečtenou" v přetečeném menu a „Číst inkognito" — obojí musí taky ukázat snackbar, ne otevřít čtečku.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/haise/jiyu/ui/detail/MangaDetailScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "feat: srozumitelna hlaska misto padu ctecky pri kliku na ComicK kapitolu"
```
