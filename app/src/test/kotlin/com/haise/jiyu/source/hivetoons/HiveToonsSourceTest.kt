package com.haise.jiyu.source.hivetoons

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

class HiveToonsSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HiveToonsSource

    private val listHtml = """
        <html><body>
        <a href="/series/test-series" title="Test Series" class="card">
          <img alt="Test Series" src="https://cdn.example.com/cover.webp" />
        </a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1 itemProp="name">Test Series</h1>
        <img itemProp="image" src="https://cdn.example.com/cover.webp" />
        <div itemProp="description">A test summary.</div>
        <a itemProp="genre" href="#">Action</a>
        <a itemProp="genre" href="#">Romance</a>
        <div><h1>Status</h1><div><p>ONGOING</p></div></div>
        <a href="/series/test-series/chapter-1">Chapter 1</a>
        <a href="/series/test-series/chapter-2">Chapter 2</a>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <figure><img data-reader-page-image data-reader-index="0" src="https://cdn.example.com/1/01.webp"/></figure>
        <figure><img data-reader-page-image data-reader-index="1" src="https://cdn.example.com/1/02.webp"/></figure>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/series/test-series/chapter-") -> MockResponse().setBody(chapterHtml)
                    path == "/series/test-series" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/series") -> MockResponse().setBody(listHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = HiveToonsSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses title and cover from series listing`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/cover.webp", result[0].coverUrl)
    }

    @Test
    fun `search filters listing locally by title`() = runTest {
        assertEquals(1, source.search("test", 1).size)
        assertTrue(source.search("nomatch", 1).isEmpty())
        assertTrue(source.search("test", 2).isEmpty())
    }

    @Test
    fun `getMangaDetails parses itemProp metadata and Status label`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A test summary.", details.description)
        assertEquals("ONGOING", details.status)
        assertEquals(listOf("Action", "Romance"), details.genres)
    }

    @Test
    fun `getChapterList and getPageList parse chapter links and reader images`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 1f })
        assertTrue(chapters.any { it.chapterNumber == 2f })

        val pages = source.getPageList(chapters.first { it.chapterNumber == 1f })
        assertEquals(2, pages.size)
        assertEquals("https://cdn.example.com/1/01.webp", pages[0].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = HiveToonsSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
