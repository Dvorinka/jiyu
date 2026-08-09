package com.haise.jiyu.source.mangamikan

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

class MangaMikanSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangaMikanSource

    private val browseHtml = """
        <html><body>
        <a class="card-manga d-block" href="/manga/test-series" title="Test Series">
            <div class="cover-wrap"><img class="cover" src="https://cdn.example.com/cover.jpg"></div>
        </a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <span class="me-2">Author: <b>Jane Doe</b></span>
        <a class="genre-pill" href="/genre.php?genre=Action">Action</a>
        <a class="genre-pill" href="/genre.php?genre=Fantasy">Fantasy</a>
        <a href="/read/test-series/1">Chapter 1</a>
        <a href="/read/test-series/2">Chapter 2</a>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <img class="page-img js-lazy" src="data:image/gif;base64,x" data-src="/i.php?c=1&amp;f=0001.webp&amp;exp=1&amp;t=abc">
        <img class="page-img js-lazy" src="data:image/gif;base64,x" data-src="/i.php?c=1&amp;f=0002.webp&amp;exp=1&amp;t=def">
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/browse") -> MockResponse().setBody(browseHtml)
                    path == "/manga/test-series" -> MockResponse().setBody(detailHtml)
                    path == "/read/test-series/1" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangaMikanSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses card-manga listing`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/manga/test-series", result[0].url)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `search reuses card-manga parsing`() = runTest {
        val result = source.search("test")
        assertEquals(1, result.size)
    }

    @Test
    fun `getMangaDetails parses author and genre-pill list`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Jane Doe", details.author)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
    }

    @Test
    fun `getChapterList parses read links`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)
        assertEquals(2f, chapters[1].chapterNumber)
    }

    @Test
    fun `getPageList reads pre-signed i-php URLs from data-src, unescaping entities`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[0])
        assertEquals(2, pages.size)
        assertEquals("https://mangamikan.com/i.php?c=1&f=0001.webp&exp=1&t=abc", pages[0].url)
        assertEquals("https://mangamikan.com/i.php?c=1&f=0002.webp&exp=1&t=def", pages[1].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = MangaMikanSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
