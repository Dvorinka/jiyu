package com.haise.jiyu.source.mangadenizi

import com.haise.jiyu.source.redirectingClient
import com.haise.jiyu.util.ScrambledImageUrl
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

class MangaDeniziSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangaDeniziSource

    private val listJson = """
        {"data":{"manga":{"current_page":1,"data":[
          {"id":1,"title":"Test Series","slug":"test-series","cover_url":"https://cdn.example.com/cover.jpg",
           "description":"A test summary.","status":"ongoing","type":{"id":1,"name":"Manga","slug":"manhwa"}}
        ],"last_page":1,"per_page":15,"total":1}}}
    """.trimIndent()

    private val detailJson = """
        {"data":{"manga":{"id":1,"title":"Test Series","slug":"test-series","cover_url":"https://cdn.example.com/cover.jpg",
          "description":"A test summary.","status":"ongoing","type":{"id":1,"name":"Manga","slug":"manhwa"},
          "categories":[{"name":"Action","slug":"action"},{"name":"Fantasy","slug":"fantasy"}],
          "authors":[{"id":1,"name":"Test Author","role":"author"}],
          "chapters":[
            {"id":10,"manga_id":1,"number":2,"title":"Chapter Two","slug":"2","pages_count":3,"published_at":"2026-05-02T10:00:00.000000Z","is_new":true},
            {"id":9,"manga_id":1,"number":1,"title":null,"slug":"1","pages_count":2,"published_at":"2026-05-01T10:00:00.000000Z","is_new":false}
          ]}}}
    """.trimIndent()

    private val readerJson = """
        {"manga":{"id":1,"title":"Test Series","slug":"test-series"},
         "chapter":{"id":9,"slug":"1","number":1,"title":null},
         "pages":[
           {"id":100,"page_number":1,"width":1100,"height":1463,"image_url":"https://img.example.com/1/001.webp","variant_hash":"a","scramble":{"method":"tiled-v1","grid":10,"seed":3849681284}},
           {"id":101,"page_number":2,"width":1100,"height":1463,"image_url":"https://img.example.com/1/002.webp","variant_hash":"b","scramble":{"method":"tiled-v1","grid":10,"seed":3849681284}}
         ]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/v1/reader/test-series/") -> MockResponse().setBody(readerJson)
                    path == "/api/v1/web/manga/test-series" -> MockResponse().setBody(detailJson)
                    path.startsWith("/api/v1/web/manga") -> MockResponse().setBody(listJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = MangaDeniziSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses listing and maps type slug to contentType`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
        assertEquals("MANHWA", result[0].contentType)
    }

    @Test
    fun `search filters listing locally by title (server ignores query params)`() = runTest {
        assertEquals(1, source.search("test", 1).size)
        assertTrue(source.search("nomatch", 1).isEmpty())
        assertTrue(source.search("test", 2).isEmpty())
    }

    @Test
    fun `getMangaDetails parses categories as genres and joins author names`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A test summary.", details.description)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
        assertEquals("Test Author", details.author)
        assertEquals("ongoing", details.status)
    }

    @Test
    fun `getChapterList parses full chapters array with null-title fallback`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertTrue(chapters.any { it.chapterNumber == 2f && it.name == "Chapter Two" })
        assertTrue(chapters.any { it.chapterNumber == 1f && it.name == "Bölüm 1" })
    }

    @Test
    fun `getPageList encodes tiled-v1 scramble params into the page URL`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters.first { it.chapterNumber == 1f })
        assertEquals(2, pages.size)
        val params = ScrambledImageUrl.parse(pages[0].url)
        assertEquals(ScrambledImageUrl.Params(10, 3849681284L), params)
        assertTrue(pages[0].url.startsWith("https://img.example.com/1/001.webp"))
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not json")
        }
        server.start()
        val emptySource = MangaDeniziSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
