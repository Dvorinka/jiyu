package com.haise.jiyu.source.hentaihand

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

class HentaiHandSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HentaiHandSource

    // Zkraceny, ale strukturalne realny vyrez z zive odpovedi https://hentaihand.com/api/comics?page=1
    private val listJson = """
        {"current_page":1,"data":[{"id":672205,"linkcode":"671554","title":"[Dhibi] Nee Oshiri","alternative_title":"Nee Oshiri","slug":"dhibi","description":null,"rewritten":false,"translated":false,"speechless":false,"uploaded_at":"2026-08-09","pages":9,"favorites":0,"status":null,"chapters_count":0,"thumb_url":"https://cdn.hentaihand.com/nhentai/storage/comics/thumbs/672205.webp","image_url":"https://cdn.hentaihand.com/nhentai/storage/comics/672205.webp","short_url":"https://hentaihand.com/g/672205","premium":false,
            "category":{"id":1,"name":"Doujinshi","slug":"doujinshi"},
            "language":{"id":2,"name":"English","slug":"english"},
            "tags":[{"id":1,"name":"Big Breasts","slug":"big-breasts"},{"id":2,"name":"Group","slug":"group"}]
        }],"first_page_url":"...","from":1,"last_page":34551,"last_page_url":"...","next_page_url":"...","path":"...","per_page":18,"prev_page_url":null,"to":18,"total":621914}
    """.trimIndent()

    // Zkraceny vyrez z zive odpovedi https://hentaihand.com/api/comics/dhibi/images
    private val imagesJson = """
        {"comic":{"id":672205,"linkcode":"671554","title":"[Dhibi] Nee Oshiri","alternative_title":"Nee Oshiri","slug":"dhibi","description":null,"pages":2,"tags":[{"slug":"big-breasts"},{"slug":"group"}]},
        "chapter":null,"next_chapter":null,
        "images":[
            {"id":1,"page":1,"source_url":"https://cdn.hentaihand.com/nhentai/storage/images/672205/1.jpg","thumbnail_url":"https://cdn.hentaihand.com/nhentai/storage/thumbnails/672205/1.jpg"},
            {"id":2,"page":2,"source_url":"https://cdn.hentaihand.com/nhentai/storage/images/672205/2.jpg","thumbnail_url":"https://cdn.hentaihand.com/nhentai/storage/thumbnails/672205/2.jpg"}
        ]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/comics/dhibi/images") -> MockResponse().setBody(imagesJson)
                    path.startsWith("/api/comics") -> MockResponse().setBody(listJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = HentaiHandSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses the Laravel-style paginated JSON`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("dhibi", result[0].url)
        assertEquals("[Dhibi] Nee Oshiri", result[0].title)
        assertEquals(listOf("Big Breasts", "Group"), result[0].genres)
    }

    @Test
    fun `search falls back to getPopular for a blank query`() = runTest {
        val result = source.search("", 1)
        assertEquals(1, result.size)
    }

    @Test
    fun `getMangaDetails keeps genres already known from the listing`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals(listOf("Big Breasts", "Group"), details.genres)
    }

    @Test
    fun `getChapterList returns a single synthetic chapter`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)
    }

    @Test
    fun `getPageList reads source_url from the per-slug images endpoint`() = runTest {
        val manga = source.getPopular(1).first()
        val chapter = source.getChapterList(manga).first()
        val pages = source.getPageList(chapter)
        assertEquals(2, pages.size)
        assertEquals("https://cdn.hentaihand.com/nhentai/storage/images/672205/1.jpg", pages[0].url)
        assertEquals("https://cdn.hentaihand.com/nhentai/storage/images/672205/2.jpg", pages[1].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not json")
        }
        server.start()
        val emptySource = HentaiHandSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
