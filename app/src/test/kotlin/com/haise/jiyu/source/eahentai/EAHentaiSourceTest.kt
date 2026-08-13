package com.haise.jiyu.source.eahentai

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

class EAHentaiSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: EAHentaiSource

    private fun cardHtml(id: Int, title: String, hash: String) = """
        <a class="group" aria-label="$title" href="/a/$id">
            <img srcset="/_next/image?url=https%3A%2F%2Fi.eahentai.com%2Ffile%2Fea-gallery%2Fgalleries%2F$hash%2Fthumbnail%2Fimage1t.jpg&amp;w=256&amp;q=75 256w" />
        </a>
    """.trimIndent()

    private val homeHtml = "<html><body><div class=\"grid\">${cardHtml(72154, "[Cammy] Friend With Benefit", "hash1")}</div></body></html>"

    private val detailHtml = """
        <html><body>
        <h1>[Cammy] Friend With Benefit With My Friends Mom</h1>
        <span class="text-[#71717a]">Artist</span><div class="flex flex-wrap"><a href="/search?type=artist&q=cammy"><span>cammy</span></a></div>
        <span class="text-[#71717a]">Tags</span><div class="flex flex-wrap"><a href="/search?q=big%20breasts"><span>big breasts</span></a><a href="/search?q=milf"><span>milf</span></a></div>
        <span class="text-[#71717a]">Description</span><details><summary><span>preview text</span></summary><p>The full description text.</p></details>
        </body></html>
    """.trimIndent()

    private fun readerHtml(hash: String, count: Int): String {
        val entries = (1..count).joinToString(",") { "\"i.eahentai.com/file/ea-gallery/galleries/$hash/image$it.webp\"" }
        return "<html><script>self.__next_f.push([1,\"[$entries]\"])</script></html>"
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/" -> MockResponse().setBody(homeHtml)
                    path.startsWith("/search") -> MockResponse().setBody(homeHtml)
                    path == "/a/72154" -> MockResponse().setBody(detailHtml)
                    path == "/a/72154/1" -> MockResponse().setBody(readerHtml("hash1", 5))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = EAHentaiSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses title from aria-label and cover from the Next-js image proxy srcset`() = runTest {
        val result = source.getPopular(1, MangaFilter())
        assertEquals(1, result.size)
        assertEquals("[Cammy] Friend With Benefit", result[0].title)
        assertEquals("https://i.eahentai.com/file/ea-gallery/galleries/hash1/thumbnail/image1t.jpg", result[0].coverUrl)
    }

    @Test
    fun `getPopular returns empty list beyond page 1 since pagination is client-side only`() = runTest {
        val result = source.getPopular(2, MangaFilter())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMangaDetails extracts artist, tags and full description from labelled sibling sections`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val detail = source.getMangaDetails(manga)
        assertEquals("cammy", detail.artist)
        assertEquals(listOf("big breasts", "milf"), detail.genres)
        assertEquals("The full description text.", detail.description)
    }

    @Test
    fun `getPageList extracts every full-resolution page image from one reader request`() = runTest {
        val manga = source.getPopular(1, MangaFilter())[0]
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[0])
        assertEquals(5, pages.size)
        assertEquals("https://i.eahentai.com/file/ea-gallery/galleries/hash1/image1.webp", pages[0].url)
        assertEquals("https://i.eahentai.com/file/ea-gallery/galleries/hash1/image5.webp", pages[4].url)
    }
}
