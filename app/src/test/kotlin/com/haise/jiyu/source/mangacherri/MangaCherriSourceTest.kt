package com.haise.jiyu.source.mangacherri

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

class MangaCherriSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangaCherriSource

    private val homeHtml = """
        <html><body>
        <a href="/test-series" title="Test Series" class="link no-decoration horizontal manga-cover-link">
            <div class="manga-live-cover"><img src="https://cdn.example.com/cover.jpg" alt="Test Series" class="manga-live-img"></div>
        </a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <span class="text grey small">Status</span><span class="text default small">Ongoing</span>
        <a href="/author/jane-doe.1">Jane Doe</a>
        <a href="/genre.php?genre=Comedy">Comedy</a>
        <a href="/genre.php?genre=Drama">Drama</a>
        <div class="chapters-container">
            <a href="test-series/2">2</a>
            <a href="test-series/1">1</a>
        </div>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <img src="https://mangacherri.com/mangas/1/1/0.webp" alt="page">
        <img src="https://mangacherri.com/mangas/1/1/1.webp" alt="page">
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/home.php") -> MockResponse().setBody(homeHtml)
                    path == "/test-series" -> MockResponse().setBody(detailHtml)
                    path == "/test-series/1" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangaCherriSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses manga-cover-link cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/test-series", result[0].url)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `getMangaDetails parses status, author and genre links`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Ongoing", details.status)
        assertEquals("Jane Doe", details.author)
        assertEquals(listOf("Comedy", "Drama"), details.genres)
    }

    @Test
    fun `getChapterList resolves relative hrefs without leading slash via base URI`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals("/test-series/2", chapters[0].url)
        assertEquals("/test-series/1", chapters[1].url)
    }

    @Test
    fun `getPageList reads direct mangas-CDN image URLs`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://mangacherri.com/mangas/1/1/0.webp", pages[0].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = MangaCherriSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
