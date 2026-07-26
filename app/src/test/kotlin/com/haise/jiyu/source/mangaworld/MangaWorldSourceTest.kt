package com.haise.jiyu.source.mangaworld

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

class MangaWorldSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangaWorldSource

    private val listHtml = """
        <html><body>
        <div class="entry">
          <a class="thumb" href="https://www.mangaworld.mx/manga/1/test-series" title="Test Series">
            <img src="https://cdn.example.com/cover.jpg"/>
          </a>
          <a class="manga-title" href="https://www.mangaworld.mx/manga/1/test-series" title="Test Series">Test Series</a>
        </div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><head><meta name="description" content="A test summary."/></head><body>
        <div class="thumb"><img src="https://cdn.example.com/cover.jpg"/></div>
        <h1 class="name bigger">Test Series</h1>
        <a href="https://www.mangaworld.mx/archive?genre=azione">Azione</a>
        <a href="https://www.mangaworld.mx/archive?genre=commedia">Commedia</a>
        <a href="https://www.mangaworld.mx/archive?status=ongoing">In corso</a>
        <a href="https://www.mangaworld.mx/archive?author=Test%20Author">Test Author</a>
        <div class="chapter"><a class="chap" href="https://www.mangaworld.mx/manga/1/test-series/read/abc1" title="Test Series Capitolo 01"><span>Capitolo 01</span><i class="chap-date">08 Ottobre 2020</i></a></div>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <img class="img-fluid" src="https://cdn.example.com/chapters/test/1.jpg"/>
        <select class="page"><option value=0>1/3</option><option value=1>2/3</option><option value=2>3/3</option></select>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/manga/1/test-series/read/") -> MockResponse().setBody(chapterHtml)
                    path == "/manga/1/test-series" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/archive") -> MockResponse().setBody(listHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangaWorldSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses entry cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `search parses filtered archive results`() = runTest {
        val result = source.search("test", 1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getMangaDetails parses description, genres, status and author`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A test summary.", details.description)
        assertEquals(listOf("Azione", "Commedia"), details.genres)
        assertEquals("In corso", details.status)
        assertEquals("Test Author", details.author)
    }

    @Test
    fun `getChapterList parses chap links with Italian date`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)
        assertTrue(chapters[0].dateUpload > 0L)
    }

    @Test
    fun `getPageList derives sequential page URLs from first page and select total`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[0])
        assertEquals(3, pages.size)
        assertEquals("https://cdn.example.com/chapters/test/1.jpg", pages[0].url)
        assertEquals("https://cdn.example.com/chapters/test/2.jpg", pages[1].url)
        assertEquals("https://cdn.example.com/chapters/test/3.jpg", pages[2].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = MangaWorldSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
