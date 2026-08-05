package com.haise.jiyu.source.voidscans

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

class VoidScansSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: VoidScansSource

    private fun homeHtml(host: String) = """
        <html><body>
        <div class="card shadow-sm h-100">
          <a href="$host/library/6"><img src="https://cdn.example.com/cover.jpg"/></a>
          <div class="card-body"><p class="card-text">Test Series</p></div>
        </div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <img id="manga-img" src="https://cdn.example.com/cover.jpg"/>
        <h1>Test Series</h1>
        <p>A test summary.</p>
        <ul class="list-group">
          <a href="https://voidscans.net/read/6/57" class="list-group-item">Chapter 57</a>
          <a href="https://voidscans.net/read/6/57.5" class="list-group-item">Chapter 57.5</a>
        </ul>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <div class="mySlides imageHolder"><img class="img-fluid" data-elem="pinchzoomer" src="https://cdn.example.com/1/001.png"/></div>
        <div class="mySlides imageHolder"><img class="img-fluid" data-elem="pinchzoomer" src="https://cdn.example.com/1/002.png"/></div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/read/6/") -> MockResponse().setBody(chapterHtml)
                    path == "/library/6" -> MockResponse().setBody(detailHtml)
                    path == "/" -> MockResponse().setBody(homeHtml(server.url("/").toString().trimEnd('/')))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = VoidScansSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses homepage cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
        assertEquals("MANHWA", result[0].contentType)
    }

    @Test
    fun `getPopular returns empty for page beyond 1`() = runTest {
        assertTrue(source.getPopular(2).isEmpty())
    }

    @Test
    fun `search filters homepage locally by title`() = runTest {
        assertEquals(1, source.search("test", 1).size)
        assertTrue(source.search("nomatch", 1).isEmpty())
    }

    @Test
    fun `getMangaDetails parses title, cover and description`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Test Series", details.title)
        assertEquals("A test summary.", details.description)
        assertEquals("MANHWA", details.contentType)
    }

    @Test
    fun `getChapterList and getPageList parse chapters and reader images`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 57f })
        assertTrue(chapters.any { it.chapterNumber == 57.5f })

        val pages = source.getPageList(chapters.first { it.chapterNumber == 57f })
        assertEquals(2, pages.size)
        assertEquals("https://cdn.example.com/1/001.png", pages[0].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = VoidScansSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
