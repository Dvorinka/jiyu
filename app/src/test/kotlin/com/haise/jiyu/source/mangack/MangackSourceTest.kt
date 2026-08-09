package com.haise.jiyu.source.mangack

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

class MangackSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangackSource

    private val listHtml = """
        <html><body>
        <a href="https://mangack.com/manga/test-series/"><img src="https://cdn.example.com/cover.jpg" alt="Test Series"></a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1 class="entry-title">Test Series</h1>
        <table>
            <tr><td><b>Author</b></td><td><a href="https://mangack.com/authors/jane-doe/">Jane Doe</a></td></tr>
            <tr><td><b>Genres</b></td><td><a href="https://mangack.com/genres/action/">Action</a><a href="https://mangack.com/genres/comedy/">Comedy</a></td></tr>
            <tr><td><strong>Status</strong></td><td><a href="https://mangack.com/manga-status/ongoing/">Ongoing</a></td></tr>
        </table>
        <ul class="chapterslist">
            <li><a href="https://mangack.com/chapter/test-series-chapter-2/" class="title">CHAPTER 2<span class="badge">NEW</span></a></li>
            <li><a href="https://mangack.com/chapter/test-series-chapter-1/" class="title">CHAPTER 1</a></li>
        </ul>
        </body></html>
    """.trimIndent()

    private val readerHtml = """
        <html><body>
        <div class="wp-block-image"><figure class="aligncenter"><img decoding="async" src="https://i.imgur.com/abc123.jpeg" alt=""/></figure></div>
        <div class="wp-block-image"><figure class="aligncenter"><img decoding="async" src="https://i.imgur.com/def456.jpeg" alt=""/></figure></div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/newest/page/") -> MockResponse().setBody(listHtml)
                    path.startsWith("/?s=") || path.startsWith("/page/") -> MockResponse().setBody(listHtml)
                    path == "/manga/test-series/" -> MockResponse().setBody(detailHtml)
                    path == "/chapter/test-series-chapter-1/" -> MockResponse().setBody(readerHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangackSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular filters cards by manga path`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getMangaDetails parses table-based author, genres and status`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Jane Doe", details.author)
        assertEquals(listOf("Action", "Comedy"), details.genres)
        assertEquals("Ongoing", details.status)
    }

    @Test
    fun `getChapterList uses ownText to skip the NEW badge`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals("CHAPTER 2", chapters[0].name)
    }

    @Test
    fun `getPageList reads direct imgur URLs`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://i.imgur.com/abc123.jpeg", pages[0].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = MangackSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
