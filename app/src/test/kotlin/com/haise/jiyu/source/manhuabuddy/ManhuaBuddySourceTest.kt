package com.haise.jiyu.source.manhuabuddy

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

class ManhuaBuddySourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: ManhuaBuddySource

    private val listHtml = """
        <html><body>
        <li><div class="item"><div class="visual">
          <div class="manga-cover">
            <a href="https://manhuabuddy.com/manhwa/test-series">
              <img src="loading.svg" data-original="https://cdn.example.com/cover.jpg" class="lazy"/>
            </a>
          </div>
        </div>
        <div class="main_text"><h3 class="title"><a href="https://manhuabuddy.com/manhwa/test-series">Test Series</a></h3></div>
        </div></li>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><head>
        <meta name="twitter:description" content="A test summary.">
        </head><body>
        <h1>Test Series</h1>
        <div class="line"><span class="line-text">Status:</span> <span class="line-content"> Ongoing</span></div>
        <div class="line"><span class="line-text">Genres</span>
          <span class="line-content"><a class="item-tag" href="/genre/action">Action</a><a class="item-tag" href="/genre/fantasy">Fantasy</a></span>
        </div>
        <script type="application/ld+json">
        {"@context":"https://schema.org","@graph":[
          {"@type":"WebPage","@id":"https://manhuabuddy.com/manhwa/test-series"},
          {"@type":"ItemList","name":"Test Series","numberOfItems":2,"itemListElement":[
            {"@type":"ListItem","position":1,"item":{"@type":"WebPage","url":"https://manhuabuddy.com/manhwa/Test Series/chapter-2","name":"Chapter 2","datePublished":"2026-07-24T23:10:23-05:00"}},
            {"@type":"ListItem","position":2,"item":{"@type":"WebPage","url":"https://manhuabuddy.com/manhwa/Test Series/chapter-1","name":"Chapter 1","datePublished":"2026-07-20T10:00:00-05:00"}}
          ]}
        ]}
        </script>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <div class="chapter-content">
          <div class="item-photo"><img src="https://cdn.example.com/1/0.jpg" alt="p1"></div>
          <div class="item-photo"><img src="https://cdn.example.com/1/1.jpg" alt="p2"></div>
        </div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/chapter-") -> MockResponse().setBody(chapterHtml)
                    path == "/manhwa/test-series" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/popular") || path.startsWith("/search") -> MockResponse().setBody(listHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = ManhuaBuddySource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses card grid`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
        assertEquals("MANHWA", result[0].contentType)
    }

    @Test
    fun `search parses results using the same card structure`() = runTest {
        val result = source.search("test", 1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getMangaDetails parses description, status and genres`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A test summary.", details.description)
        assertEquals("Ongoing", details.status)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
        assertEquals("MANHWA", details.contentType)
    }

    @Test
    fun `getChapterList parses JSON-LD ItemList`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 1f })
        assertTrue(chapters.any { it.chapterNumber == 2f })
        assertTrue(chapters.all { it.dateUpload > 0L })
    }

    @Test
    fun `getPageList parses chapter-content item-photo images`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters.first { it.chapterNumber == 1f })
        assertEquals(2, pages.size)
        assertEquals("https://cdn.example.com/1/0.jpg", pages[0].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = ManhuaBuddySource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
