package com.haise.jiyu.source.i18n

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

/**
 * AnimeSamaSource (2026-07-26 redesign anime-sama.fr -> anime-sama.to):
 * kapitoly/stranky se nezjistuji z HTML, ale z interniho JSON API
 * (get_nb_chap_et_img.php), ktere web sam pouziva pro JS reader - viz
 * komentar u tridy v FrenchSources.kt.
 */
class AnimeSamaSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: AnimeSamaSource

    private val listHtml = """
        <html><body>
        <div class="catalog-card"><a href="https://anime-sama.to/catalogue/test-series"><h2 class="card-title">Test Series</h2><img class="card-image" src="https://cdn.example.com/test.jpg" /></a></div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1>Test Series</h1>
        <p id="synopsisText">A synopsis.</p>
        <span class="genre-pill">Action</span>
        </body></html>
    """.trimIndent()

    private val scanPageHtml = """
        <html><body><span id="titreOeuvre">Test Series</span></body></html>
    """.trimIndent()

    private val chapterCountsJson = """{"1":2,"2":3}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/s2/scans/get_nb_chap_et_img.php") -> MockResponse().setBody(chapterCountsJson)
                    path == "/catalogue/test-series/scan/vf/" -> MockResponse().setBody(scanPageHtml)
                    path == "/catalogue/test-series" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/catalogue/?page=") -> MockResponse().setBody(listHtml)
                    path.startsWith("/catalogue/?type=") -> MockResponse().setBody(listHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = AnimeSamaSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses catalog-card title and cover, language tag is fr`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/test.jpg", result[0].coverUrl)
        assertEquals("fr", source.language)
    }

    @Test
    fun `search filters listing locally by title`() = runTest {
        assertEquals(1, source.search("test", 1).size)
        assertTrue(source.search("nomatch", 1).isEmpty())
        assertTrue(source.search("test", 2).isEmpty())
    }

    @Test
    fun `getMangaDetails parses synopsisText and genre-pill`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A synopsis.", details.description)
        assertEquals(listOf("Action"), details.genres)
    }

    @Test
    fun `getChapterList reads titreOeuvre then calls chapter-count API`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 1f })
        assertTrue(chapters.any { it.chapterNumber == 2f })
    }

    @Test
    fun `getPageList builds sequential image URLs from page count`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters.first { it.chapterNumber == 1f })
        assertEquals(2, pages.size)
        assertTrue(pages[0].url.endsWith("/1/1.jpg"))
        assertTrue(pages[1].url.endsWith("/1/2.jpg"))
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = AnimeSamaSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
