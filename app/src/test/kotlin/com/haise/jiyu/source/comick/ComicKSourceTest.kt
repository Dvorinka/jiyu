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
        {"comic": {"hid": "abcd", "desc": "A summary.", "status": 1, "year": 2020, "country": "kr"}, "authors": [{"name": "Jane"}], "genres": [{"name": "Action"}]}
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
}
