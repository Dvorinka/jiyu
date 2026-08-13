package com.haise.jiyu.source.omegascans

import com.haise.jiyu.source.MangaFilter
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

class OmegaScansSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: OmegaScansSource

    private val queryJson = """
        {"meta":{"total":1,"per_page":20,"current_page":1,"last_page":1},"data":[
            {"id":716,"title":"A Theme For Every Building","series_slug":"a-theme-for-every-building","thumbnail":"https://media.example.com/cover.webp"}
        ]}
    """.trimIndent()

    private val seriesJson = """
        {"id":716,"title":"A Theme For Every Building","series_slug":"a-theme-for-every-building",
         "thumbnail":"https://media.example.com/cover.webp","description":"<p>Some &ldquo;summary&rdquo;.</p>",
         "status":"Ongoing","author":"-","tags":[{"id":2,"name":"Drama"},{"id":3,"name":"Fantasy"}]}
    """.trimIndent()

    private val chapterQueryJson = """
        {"meta":{"total":2,"per_page":999,"current_page":1,"last_page":1},"data":[
            {"id":14022,"chapter_name":"Chapter 2","chapter_slug":"chapter-2","index":"2.0","created_at":"2026-01-02T00:00:00Z"},
            {"id":10673,"chapter_name":"Chapter 1","chapter_slug":"chapter-1","index":"1.0","created_at":"2026-01-01T00:00:00Z"}
        ]}
    """.trimIndent()

    private val chapterFreeJson = """
        {"chapter":{"id":10673,"chapter_name":"Chapter 1","chapter_data":{"images":["https://media.example.com/1/01.jpg","https://media.example.com/1/02.jpg"]}}}
    """.trimIndent()

    private val chapterPaywalledJson = """
        {"paywall":true,"chapter":{"id":14022,"chapter_name":"Chapter 2","price":12}}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/query") -> MockResponse().setBody(queryJson)
                    path == "/series/a-theme-for-every-building" -> MockResponse().setBody(seriesJson)
                    path.startsWith("/chapter/query") -> MockResponse().setBody(chapterQueryJson)
                    path == "/chapter/a-theme-for-every-building/chapter-1" -> MockResponse().setBody(chapterFreeJson)
                    path == "/chapter/a-theme-for-every-building/chapter-2" -> MockResponse().setBody(chapterPaywalledJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = OmegaScansSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses series list`() = runTest {
        val result = source.getPopular(1, MangaFilter())
        assertEquals(1, result.size)
        assertEquals("A Theme For Every Building", result[0].title)
        assertTrue(result[0].url.endsWith("/series/a-theme-for-every-building"))
    }

    @Test
    fun `getMangaDetails strips HTML from description and treats dash author as blank`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val detail = source.getMangaDetails(manga)
        assertEquals("Some “summary”.", detail.description)
        assertEquals(null, detail.author)
        assertEquals(listOf("Drama", "Fantasy"), detail.genres)
    }

    @Test
    fun `getChapterList resolves series id first, then reads chapter index as chapter number`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals(1f, chapters[1].chapterNumber)
    }

    @Test
    fun `getPageList returns images for a free chapter`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val chapters = source.getChapterList(manga)
        val freeChapter = chapters.first { it.chapterNumber == 1f }
        val pages = source.getPageList(freeChapter)
        assertEquals(2, pages.size)
        assertTrue(pages[0].url.endsWith("01.jpg"))
    }

    @Test
    fun `getPageList returns empty list for a paywalled chapter`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val chapters = source.getChapterList(manga)
        val paywalled = chapters.first { it.chapterNumber == 2f }
        val pages = source.getPageList(paywalled)
        assertTrue(pages.isEmpty())
    }
}
