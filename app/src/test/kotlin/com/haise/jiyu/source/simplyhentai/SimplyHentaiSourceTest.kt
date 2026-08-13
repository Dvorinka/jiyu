package com.haise.jiyu.source.simplyhentai

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

class SimplyHentaiSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: SimplyHentaiSource

    private fun nextDataHtml(pagePropsJson: String) = """
        <html><body><div id="__next"></div><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":$pagePropsJson}}</script></body></html>
    """.trimIndent()

    private val listingHtml = nextDataHtml(
        """
        {"mangas":[
            {"id":207580,"slug":"sensei-rentaru-talk-sensei-rental-talk","title":"Sensei Rentaru Talk",
             "series":{"id":3357,"slug":"1-blue-archive","title":"Blue Archive"},
             "preview":{"sizes":{"full":"https://images.sh-cdn.com/x/full.jpg"}}}
        ],"pagination":{"current":1,"pages":415}}
        """.trimIndent()
    )

    private val detailHtml = nextDataHtml(
        """
        {"manga":{"id":363517,"slug":"sensei-rentaru-talk-sensei-rental-talk","title":"Sensei Rentaru Talk | Sensei Rental Talk",
          "series":{"id":3357,"slug":"1-blue-archive","title":"Blue Archive"},
          "description":"<p>A &ldquo;short&rdquo; summary.</p>",
          "artists":[{"id":26874,"slug":"yanje","title":"Yanje"}],
          "tags":[{"id":12142,"slug":"halo","title":"halo"}],
          "images":[
            {"id":1,"page_num":1,"sizes":{"full":"https://images.sh-cdn.com/x/0c325282.jpg"}},
            {"id":2,"page_num":2,"sizes":{"full":"https://images.sh-cdn.com/x/500d7bb7.jpg"}}
          ]}}
        """.trimIndent()
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/2-mangas/sort-most-viewed") -> MockResponse().setBody(listingHtml)
                    path == "/search/sensei-rentaru-talk" -> MockResponse().setBody(detailHtml)
                    path == "/search/nonexistent-title" -> MockResponse().setResponseCode(404)
                    path == "/1-blue-archive/sensei-rentaru-talk-sensei-rental-talk" -> MockResponse().setBody(detailHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = SimplyHentaiSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses the mangas array from Next-js pageProps`() = runTest {
        val result = source.getPopular(1, MangaFilter())
        assertEquals(1, result.size)
        assertEquals("Sensei Rentaru Talk", result[0].title)
        assertEquals("https://www.simply-hentai.com/1-blue-archive/sensei-rentaru-talk-sensei-rental-talk", result[0].url)
        assertEquals("https://images.sh-cdn.com/x/full.jpg", result[0].coverUrl)
    }

    @Test
    fun `search resolves an exact slug match via the redirect trick`() = runTest {
        val result = source.search("Sensei Rentaru Talk", 1, MangaFilter())
        assertEquals(1, result.size)
        assertEquals("Sensei Rentaru Talk | Sensei Rental Talk", result[0].title)
    }

    @Test
    fun `search returns empty list when no gallery slug matches exactly`() = runTest {
        val result = source.search("nonexistent title", 1, MangaFilter())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMangaDetails strips HTML from description and reads artist and tags`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val detail = source.getMangaDetails(manga)
        assertEquals("A “short” summary.", detail.description)
        assertEquals("Yanje", detail.artist)
        assertEquals(listOf("halo"), detail.genres)
    }

    @Test
    fun `getPageList reads full-resolution image URLs already embedded in the detail page`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[0])
        assertEquals(2, pages.size)
        assertEquals("https://images.sh-cdn.com/x/0c325282.jpg", pages[0].url)
    }
}
