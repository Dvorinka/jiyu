package com.haise.jiyu.source.projectsuki

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

class ProjectSukiSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: ProjectSukiSource

    private val browseHtml = """
        <html><body>
        <div class="col-6 col-sm-6 col-md-6 col-lg-6 browse">
            <a class="inherit-color p-1" href="/book/12345" aria-label="Test Series">
                <img loading="auto" class="browse" src="https://projectsuki.com/images/gallery/12345/thumb.jpg" alt="Test Series">
            </a>
        </div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <div class="col-4 col-md-3 strong" itemprop="author">Author:</div>
        <div class="col-8 col-md-9 comma-sep"><a href="#" class="inherit-color">Golden Dove</a></div>
        <div class="col-4 col-md-3 strong">Status:</div>
        <div class="col-8 col-md-9"><a href="#" class="inherit-color">Ongoing</a></div>
        <div class="col-4 col-md-3 strong">Genre(s):</div>
        <div class="col-8 col-md-9" itemprop="genre"><a href="/genre/action" class="inherit-color">Action</a>, <a href="/genre/drama" class="inherit-color">Drama</a></div>
        <a href="/read/12345/999/1" class="inherit-color d-flex" title="">Chapter 2</a>
        <a href="/read/12345/998/1" class="inherit-color d-flex" title="">Chapter 1</a>
        </body></html>
    """.trimIndent()

    private val readerPage1Html = """
        <html><body>
        <img class="img-fluid center-block" src="https://projectsuki.com/images/gallery/12345/abc/001?" alt="Test Series - Chapter 998 - Image 1">
        </body></html>
    """.trimIndent()

    private val readerPage2Html = """
        <html><body>
        <img class="img-fluid center-block" src="https://projectsuki.com/images/gallery/12345/abc/002?" alt="Test Series - Chapter 998 - Image 2">
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/browse/1" -> MockResponse().setBody(browseHtml)
                    path.startsWith("/search") -> MockResponse().setBody(browseHtml)
                    path == "/book/12345" -> MockResponse().setBody(detailHtml)
                    path == "/read/12345/998/1" -> MockResponse().setBody(readerPage1Html)
                    path == "/read/12345/998/2" -> MockResponse().setBody(readerPage2Html)
                    path == "/read/12345/998/9999" -> MockResponse().setResponseCode(302)
                        .setHeader("Location", "/read/12345/998/2")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = ProjectSukiSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses browse cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/book/12345", result[0].url)
    }

    @Test
    fun `search reuses browse card parsing`() = runTest {
        val result = source.search("test")
        assertEquals(1, result.size)
    }

    @Test
    fun `getMangaDetails parses author, status and itemprop genre`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Golden Dove", details.author)
        assertEquals("Ongoing", details.status)
        assertEquals(listOf("Action", "Drama"), details.genres)
    }

    @Test
    fun `getChapterList strips page number, keeping base chapter URL`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals("/read/12345/999", chapters[0].url)
        assertEquals("/read/12345/998", chapters[1].url)
    }

    @Test
    fun `getPageList discovers page count via 9999 redirect, getImageUrl resolves real image`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://projectsuki.com/images/gallery/12345/abc/001?", source.getImageUrl(pages[0]))
        assertEquals("https://projectsuki.com/images/gallery/12345/abc/002?", source.getImageUrl(pages[1]))
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = ProjectSukiSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
