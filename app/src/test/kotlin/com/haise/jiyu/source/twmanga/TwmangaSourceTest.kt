package com.haise.jiyu.source.twmanga

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

class TwmangaSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: TwmangaSource

    private val homeHtml = """
        <html><body>
        <a href="/comic/test-series" title="Test Series" class="comics-card__poster"><amp-img src="https://cdn.example.com/cover.jpg"></amp-img></a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1 class="comics-detail__title">Test Series</h1>
        <h2 class="comics-detail__author">Publisher Co</h2>
        <div class="tag-list"><span class="tag">Ongoing</span><span class="tag">Action</span><span class="tag">Fantasy</span></div>
        <a href="/user/page_direct?comic_id=test-series&amp;section_slot=0&amp;chapter_slot=2">2 Second Chapter</a>
        <a href="/user/page_direct?comic_id=test-series&amp;section_slot=0&amp;chapter_slot=1">1 First Chapter</a>
        </body></html>
    """.trimIndent()

    private val readerHtml = """
        <html><body>
        <img src="https://s2.bzcdn.net/scomic/test-series/0/1/1.jpg">
        <img src="https://s2.bzcdn.net/scomic/test-series/0/1/2.jpg">
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/" -> MockResponse().setBody(homeHtml)
                    path.startsWith("/search") -> MockResponse().setBody(homeHtml)
                    path == "/comic/test-series" -> MockResponse().setBody(detailHtml)
                    path == "/comic/chapter/test-series/0_1.html" -> MockResponse().setBody(readerHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = TwmangaSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses AMP card markup`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/comic/test-series", result[0].url)
        assertEquals("MANHUA", result[0].contentType)
    }

    @Test
    fun `getMangaDetails treats first tag as status and rest as genres`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Publisher Co", details.author)
        assertEquals("Ongoing", details.status)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
    }

    @Test
    fun `getChapterList builds direct reader URL from page_direct query params`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals("/comic/chapter/test-series/0_1.html", chapters[1].url)
    }

    @Test
    fun `getPageList reads direct bzcdn image URLs`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://s2.bzcdn.net/scomic/test-series/0/1/1.jpg", pages[0].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = TwmangaSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
