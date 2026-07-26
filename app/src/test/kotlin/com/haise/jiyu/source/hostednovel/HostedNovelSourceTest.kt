package com.haise.jiyu.source.hostednovel

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

class HostedNovelSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HostedNovelSource

    private val listHtml = """
        <html><body>
        <li>
          <a href="https://hostednovel.com/novel/test-novel">
            <img data-src="https://cdn.example.com/cover.jpg"/>
          </a>
          <p class="mt-2 truncate">Test Novel</p>
        </li>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1>Test Novel</h1>
        <div class="prose"><div>A test summary.</div></div>
        <dl>
          <dt>Author:</dt><dd>Test Author</dd>
          <dt>Status:</dt><dd>Ongoing</dd>
        </dl>
        <div id="chapters">
          <ul role="list">
            <li><a href="https://hostednovel.com/novel/test-novel/chapter-1">Chapter 0001: The Beginning</a></li>
            <li><a href="https://hostednovel.com/novel/test-novel/chapter-2">Chapter 0002: Continuation</a></li>
          </ul>
        </div>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body><div id="chapter-content"><p>Some chapter text here.</p></div></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/novel/test-novel/chapter-") -> MockResponse().setBody(chapterHtml)
                    path == "/novel/test-novel" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/novels") -> MockResponse().setBody(listHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = HostedNovelSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses novel cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Novel", result[0].title)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `search filters listing locally by title`() = runTest {
        assertEquals(1, source.search("test", 1).size)
        assertTrue(source.search("nomatch", 1).isEmpty())
    }

    @Test
    fun `getMangaDetails parses description, author and status`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A test summary.", details.description)
        assertEquals("Test Author", details.author)
        assertEquals("Ongoing", details.status)
    }

    @Test
    fun `getChapterList parses scoped chapter links`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 1f })
        assertTrue(chapters.any { it.chapterNumber == 2f })
    }

    @Test
    fun `getPageList returns chapter text as a single novel text page`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters.first { it.chapterNumber == 1f })
        assertEquals(1, pages.size)
        assertEquals("novel://text", pages[0].imageUrl)
        assertEquals("Some chapter text here.", pages[0].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = HostedNovelSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
