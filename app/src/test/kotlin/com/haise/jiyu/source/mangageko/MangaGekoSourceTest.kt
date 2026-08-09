package com.haise.jiyu.source.mangageko

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

class MangaGekoSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangaGekoSource

    private val popularHtml = """
        <html><body>
        <a href="/manga/test-series/" title="Test Series" class="list-body">
            <img class="lazy" src="placeholder.png" data-src="https://cdn.example.com/cover.jpg" alt="Test Series" />
        </a>
        </body></html>
    """.trimIndent()

    private val searchHtml = """
        <html><body>
        <a href="/manga/test-series/" title="Test Series" class="list-body">
            <img class="lazy" src="placeholder.png" data-src="https://cdn.example.com/cover.jpg" />
        </a>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1 class="novel-title">Test Series</h1>
        <a href='#' class="property-item"><span itemprop="author">Jane Doe</span></a>
        <span><strong class="ongoing">Ongoing</strong><small>Status</small></span>
        <div class="categories">
            <ul>
                <li><a href="/browse-comics/?genre_included=Action" class="property-item">Action</a></li>
                <li><a href="/browse-comics/?genre_included=Manhwa" class="property-item">Manhwa</a></li>
            </ul>
        </div>
        <p class="description">Test Series is a Manga/Manhwa/Manhua in english language, Action series. The Summary is A test summary.</p>
        <ul class="chapter-list">
            <li data-chapterno="1" class="chapter-list-item"><a href="/reader/en/test-series-chapter-2-eng-li/"><div class="chapter-number">2-eng-li</div></a></li>
            <li data-chapterno="1" class="chapter-list-item"><a href="/reader/en/test-series-chapter-1-eng-li/"><div class="chapter-number">1-eng-li</div></a></li>
        </ul>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <img src="https://imgsrv5.com/sv2/comic/test-series/chapter-1/0.webp" onerror="tryAgain(this);">
        <img src="https://imgsrv5.com/sv2/comic/test-series/chapter-1/1.webp" onerror="tryAgain(this);">
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/jumbo/manga/") -> MockResponse().setBody(popularHtml)
                    path.startsWith("/search/") -> MockResponse().setBody(searchHtml)
                    path == "/manga/test-series/" -> MockResponse().setBody(detailHtml)
                    path == "/reader/en/test-series-chapter-1-eng-li/" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangaGekoSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses jumbo listing cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/manga/test-series/", result[0].url)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `search parses result cards`() = runTest {
        val result = source.search("test")
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getMangaDetails infers MANHWA and strips boilerplate description`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Jane Doe", details.author)
        assertEquals("Ongoing", details.status)
        assertEquals("MANHWA", details.contentType)
        assertEquals(listOf("Action"), details.genres)
        assertEquals("A test summary.", details.description)
    }

    @Test
    fun `getChapterList extracts chapter numbers from reader href`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals("Chapter 1", chapters[1].name)
    }

    @Test
    fun `getPageList reads direct sv2 comic image URLs`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://imgsrv5.com/sv2/comic/test-series/chapter-1/0.webp", pages[0].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = MangaGekoSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
        assertTrue(emptySource.search("x").isEmpty())
    }
}
