package com.haise.jiyu.source.mangago

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.haise.jiyu.source.interceptor.CloudflareInterceptor
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Mangago pouziva CloudflareInterceptor (potrebuje Context), proto Robolectric.
 * Na uspesnou 200 odpoved z MockWebServer se interceptor chova jako no-op
 * (jen kdyz je 403/503 s Cloudflare markery, spusti se WebView reseni).
 */
@RunWith(RobolectricTestRunner::class)
class MangagoSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MangagoSource

    // Skutecna struktura "/list/?page=N" - overeno zivym stazenim. "src" je vzdy jen
    // sdileny base64 placeholder pro lazy-load, realna obalka je v "data-src".
    private val listHtml = """
        <html><body>
        <div class="flex1 listitem ">
          <div class="updatesli"><div class="left">
            <a href="/read-manga/test-series" title="ignored-placeholder-title">
              <img class="showdesc" src="data:image/gif;base64,ignored" data-src="https://cdn.example.com/test.jpg">
            </a>
          </div></div>
          <span class="title"><a href="/read-manga/test-series" title="Test Series">Test Series</a></span>
        </div>
        </body></html>
    """.trimIndent()

    // Skutecna struktura "/r/l_search/?name=..." - jiny layout nez popularni vypis,
    // obalka v primem "src" a nadpis se shodou hledaneho vyrazu obalenou v <span class="hilight">.
    private val searchHtml = """
        <html><body>
        <ul id="search_list" class="pic_list">
          <li>
            <div class="box">
              <div class="left"><a href="/read-manga/test-series"><img src="https://cdn.example.com/test.jpg" alt="Test Series"></a></div>
              <div class="left">
                <span class="tit"><h2><a href="/read-manga/test-series"><span class="hilight">Test</span> Series</a></h2></span>
              </div>
            </div>
          </li>
        </ul>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <div class="w-title"><h1>Test Series</h1></div>
        <div id="content"><p>A summary.</p></div>
        <table class="table-ellipsis"><tr><td>Author</td><td>Jane</td></tr></table>
        <table id="chapter_table"><tr><td><a href="/read-manga/test-series/chapter-1">Chapter 1</a></td></tr></table>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body><script>var newImglist = ["https://cdn.example.com/test/1/01.jpg"]</script></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/list/") -> MockResponse().setBody(listHtml)
                    path.startsWith("/r/l_search/") -> MockResponse().setBody(searchHtml)
                    path == "/read-manga/test-series" -> MockResponse().setBody(detailHtml)
                    path == "/read-manga/test-series/chapter-1" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = MangagoSource(redirectingClient(server), CloudflareInterceptor(context, com.haise.jiyu.settings.FakeDataStore()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses title and cover`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getPopular reads the cover from data-src, not the lazy-load placeholder in src`() = runTest {
        val result = source.getPopular(1)
        assertEquals("https://cdn.example.com/test.jpg", result[0].coverUrl)
    }

    @Test
    fun `search parses title and cover from the search-only markup, joining the highlighted match span`() = runTest {
        val result = source.search("test", 1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("https://cdn.example.com/test.jpg", result[0].coverUrl)
    }

    @Test
    fun `getMangaDetails and getChapterList parse detail page`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Jane", details.author)

        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
    }

    @Test
    fun `getPageList extracts JS array of image URLs`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[0])
        assertEquals(1, pages.size)
        assertEquals("https://cdn.example.com/test/1/01.jpg", pages[0].url)
    }

    @Test
    fun `malformed HTML returns empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("<html></html>")
        }
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emptySource = MangagoSource(redirectingClient(server), CloudflareInterceptor(context, com.haise.jiyu.settings.FakeDataStore()))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
