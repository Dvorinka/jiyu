package com.haise.jiyu.source.flamecomics

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

class FlameComicsSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: FlameComicsSource

    private fun nextDataHtml(pageProps: String) = """
        <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":$pageProps}}</script></body></html>
    """.trimIndent()

    private val browseHtml = nextDataHtml(
        """{"series":[{"series_id":165,"title":"Test Series","cover":"thumbnail.webp"}]}"""
    )

    private val detailHtml = nextDataHtml(
        """{"series":{"series_id":165,"title":"Test Series","description":"<p>A summary.</p>","tags":["Action"],"author":["Someone"],"status":"Ongoing","cover":"thumbnail.webp"},"chapters":[{"chapter_id":1,"series_id":165,"chapter":"1.00","title":"Beginning","token":"abc123","release_date":1700000000}]}"""
    )

    private val pagesHtml = nextDataHtml(
        """{"chapter":{"series_id":165,"chapter_id":1,"images":{"0":{"name":"page-00.jpg"},"1":{"name":"page-01.jpg"}}}}"""
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/browse" -> MockResponse().setBody(browseHtml)
                    path == "/series/165" -> MockResponse().setBody(detailHtml)
                    path == "/series/165/abc123" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = FlameComicsSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses title and cover from NEXT_DATA`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/series/165", result[0].url)
    }

    @Test
    fun `full flow parses details, chapters and pages`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("A summary.", details.description)
        assertEquals("Someone", details.author)

        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)
        assertEquals("/series/165/abc123", chapters[0].url)

        val pages = source.getPageList(chapters[0])
        assertEquals(2, pages.size)
        assertTrue(pages[0].imageUrl!!.endsWith("/165/abc123/page-00.jpg"))
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val emptySource = FlameComicsSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
