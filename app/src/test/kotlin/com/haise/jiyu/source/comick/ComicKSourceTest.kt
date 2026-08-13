package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.FakeDataStore
import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.redirectingClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ComicK pouziva verejne REST JSON API (viz komentar v ComicKSource.kt). */
class ComicKSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: ComicKSource

    private val searchArrayJson = """
        [ {"title": "Test Series", "slug": "test-series", "country": "kr", "md_covers": [{"b2key": "cover.jpg"}]} ]
    """.trimIndent()

    private val mangaDetailJson = """
        {"demographic": "Shounen", "comic": {"hid": "abcd", "desc": "A summary.", "status": 1, "year": 2020, "country": "kr", "translation_completed": true, "has_anime": true, "final_chapter": "200", "final_volume": "3"}, "authors": [{"name": "Jane"}], "genres": [{"name": "Action"}]}
    """.trimIndent()

    private val chaptersJson = """
        {"chapters": [{"hid": "ch1", "chap": "1", "vol": null, "title": "", "created_at": "2026-01-01T00:00:00Z"}]}
    """.trimIndent()

    private val pagesJson = """
        {"chapter": {"md_images": [{"b2key": "test/1/01.jpg"}]}}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(mangaDetailJson)
                    path.startsWith("/comic/abcd/chapters") -> MockResponse().setBody(chaptersJson)
                    path.startsWith("/chapter/ch1") -> MockResponse().setBody(pagesJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val settings = SettingsRepository(FakeDataStore())
        source = ComicKSource(redirectingClient(server), settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses title and cover from md_covers`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertTrue(result[0].coverUrl!!.endsWith("/cover.jpg"))
    }

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

    @Test
    fun `getPopular maps latest sort to a sort value the ComicK API actually accepts`() = runTest {
        // ComicK's /v1.0/search schema only allows sort in {view, created_at,
        // uploaded, rating, average_rating, follow, user_follow_count, ""} -
        // "date" (used until 2026-08-07) is rejected with HTTP 400.
        source.getPopular(1, MangaFilter(sortBy = "latest"))
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("sort=uploaded"))
    }

    @Test
    fun `getMangaDetails maps numeric status to Czech label`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A summary.", details.description)
        assertEquals("Vychází", details.status)
        assertEquals("Jane", details.author)
        assertEquals(2020, details.year)
    }

    @Test
    fun `getMangaDetails reads demographic directly as a resolved label string`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Shounen", details.demographic)
    }

    @Test
    fun `getMangaDetails reads translation_completed and has_anime booleans`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals(true, details.translationCompleted)
        assertEquals(true, details.hasAnime)
    }

    @Test
    fun `getMangaDetails combines final_chapter and final_volume into one label`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Svazek 3, kapitola 200", details.finalChapter)
    }

    @Test
    fun `getMangaDetails leaves finalChapter null when the API provides no final_chapter`() = runTest {
        // ComicK vraci final_chapter/final_volume/translation_completed/has_anime casto jako
        // explicitni JSON null (ne jako chybejici klic) - overeno zive na API pro vic titulu.
        // Fixtura proto musi obsahovat tyhle klice s hodnotou null, ne je vynechat, jinak by
        // netestovala presne tenhle bug (org.json optString() na JSON null vraci na realnem
        // Android org.json doslovny retezec "null", ne "").
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setBody(
                        """{"demographic": null, "comic": {"hid": "abcd", "desc": "A summary.", "status": 1, "year": 2020, "country": "kr", "translation_completed": null, "has_anime": null, "final_chapter": null, "final_volume": null}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals(null, details.finalChapter)
        assertEquals(null, details.translationCompleted)
        assertEquals(null, details.hasAnime)
    }

    @Test
    fun `getMangaDetails throws a friendly message when the ComicK detail endpoint 404s`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v1.0/search") -> MockResponse().setBody(searchArrayJson)
                    path == "/comic/test-series" -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val manga = source.getPopular(1).first()
        var message: String? = null
        try {
            source.getMangaDetails(manga)
        } catch (e: Exception) {
            message = e.message
        }
        assertEquals("ComicK tenhle titul přes veřejné API neposkytuje (časté u 18+ obsahu)", message)
    }

    @Test
    fun `getTitleInfo puts the md_titles entry flagged is_default first`() = runTest {
        // Realny pripad, ktery zpusoboval "zadny zdroj to nema" v resolveru (Sub-projekt 3):
        // ComicK.comic.title muze byt uplne jiny nazev, nez ten oznaceny is_default=true.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/comic/test-series" -> MockResponse().setBody(
                        """{"comic": {"hid": "abcd", "title": "I am the only the one who levels up", "md_titles": [
                            {"title": "I Alone Level-Up", "lang": "en", "is_default": false},
                            {"title": "Solo Leveling", "lang": "en", "is_default": true},
                            {"title": "سولو ليفيلنغ", "lang": "ar", "is_default": false}
                        ]}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val info = source.getTitleInfo("https://api.comick.dev/comic/test-series")
        assertEquals("Solo Leveling", info.alternateTitles[0])
        assertTrue(info.alternateTitles.contains("I Alone Level-Up"))
        assertTrue(info.alternateTitles.none { it.contains("سولو") })
    }

    @Test
    fun `getTitleInfo falls back to comic title when md_titles is absent`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/comic/test-series" -> MockResponse().setBody(
                        """{"comic": {"hid": "abcd", "title": "Plain Title"}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val info = source.getTitleInfo("https://api.comick.dev/comic/test-series")
        assertEquals(listOf("Plain Title"), info.alternateTitles)
        assertEquals(null, info.contentRating)
    }

    @Test
    fun `getTitleInfo reads content_rating from the comic object`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/comic/test-series" -> MockResponse().setBody(
                        """{"comic": {"hid": "abcd", "title": "Plain Title", "content_rating": "erotica"}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val info = source.getTitleInfo("https://api.comick.dev/comic/test-series")
        assertEquals("erotica", info.contentRating)
    }

    @Test
    fun `getChapterList resolves hid first, then pages`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)

        val pages = source.getPageList(chapters[0])
        assertEquals(1, pages.size)
        assertTrue(pages[0].url.endsWith("/test/1/01.jpg"))
    }

    @Test
    fun `getChapterList treats explicit JSON null vol and title as missing, not string literal null`() = runTest {
        // ComicK API vraci vol a title jako JSON null (ne jako chybejici klic) -
        // overeno zive na /comic/{hid}/chapters. Android org.json.optString() na
        // JSONObject.NULL vraci doslovny retezec null, ne "" - bez isNull() kontroly
        // se v UI zobrazi Vol.null Ch.3 - null.
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

    @Test
    fun `parseGroups guards against ComicK's JSON-null-becomes-string-null bug`() {
        // Stejny bug jako u vol/title (viz komentar u chapterFromJson), ale v parseGroups:
        // group_name[i] muze byt JSON null, a md_groups.title/md_groups.slug taky - bez
        // isNull() kontroly by se do SGroup.name/slug dostal doslovny retezec "null".
        //
        // POZNAMKA: knihovna org.json:json (pouzita na testovacim JVM classpath - viz
        // app/build.gradle.kts) tenhle bug na rozdil od realneho Androidu (AOSP libcore
        // org.json) nereprodukuje - jeji optString() uz sama vraci "" pro JSON null,
        // takze test postaveny na skutecnem parsovani JSON textu (pres MockWebServer)
        // by prosel i bez isNull() guardu. AndroidBuggyJsonObject/-Array proto simuluji
        // primo chovani AOSP optString(), aby test isNull() guardy skutecne overil.
        val raw = JSONObject(
            """{"group_name": [null, "Flame Scans"],
                "md_chapters_groups": [
                    {"md_groups": {"title": null, "slug": null}},
                    {"md_groups": {"title": null, "slug": "flame-scans"}}
                ]}"""
        )
        val buggyJson = AndroidBuggyJsonObject(raw)

        val groups = source.parseGroups(buggyJson)

        assertEquals(2, groups.size)
        for (group in groups) {
            assertTrue(group.name != "null")
            assertTrue(group.slug != "null")
        }
        assertEquals("Flame Scans", groups[1].name)
        assertEquals("flame-scans", groups[1].slug)
    }

    @Test
    fun `server error throws (no try-catch around ComicK network calls)`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
        }
        server.start()
        val settings = SettingsRepository(FakeDataStore())
        val failingSource = ComicKSource(redirectingClient(server), settings)

        var threw = false
        try { failingSource.getPopular(1) } catch (_: Exception) { threw = true }
        assertTrue(threw)
    }

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

    @Test
    fun `getTop parses all five sections and reuses the search-result comic parser`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/top" -> MockResponse().setBody(
                        """{
                            "news": [{"title": "News Comic", "slug": "news-comic", "country": "kr", "md_covers": [{"b2key": "news.jpg"}]}],
                            "completions": [{"title": "Done Comic", "slug": "done-comic", "country": "cn", "md_covers": [{"b2key": "done.jpg"}]}],
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
}

/**
 * Simuluje AOSP org.json chovani (optString() na JSON null vraci doslovny retezec "null" -
 * viz komentar u ComicKSource.chapterFromJson/parseGroups), ktere referencni org.json:json
 * knihovna pouzita na testovacim JVM classpath nema (ta uz sama vraci "" pro null). Slouzi
 * jen k tomu, aby test parseGroups mel co realne overit - jinak by prosel i bez isNull() guardu.
 */
private class AndroidBuggyJsonObject(source: JSONObject) : JSONObject(source.toString()) {
    override fun optString(key: String): String =
        if (isNull(key)) "null" else super.optString(key)

    override fun optJSONArray(key: String): JSONArray? =
        super.optJSONArray(key)?.let { AndroidBuggyJsonArray(it) }

    override fun optJSONObject(key: String): JSONObject? =
        super.optJSONObject(key)?.let { AndroidBuggyJsonObject(it) }
}

private class AndroidBuggyJsonArray(source: JSONArray) : JSONArray(source.toString()) {
    override fun optString(index: Int): String =
        if (isNull(index)) "null" else super.optString(index)

    override fun optJSONObject(index: Int): JSONObject? =
        super.optJSONObject(index)?.let { AndroidBuggyJsonObject(it) }
}
