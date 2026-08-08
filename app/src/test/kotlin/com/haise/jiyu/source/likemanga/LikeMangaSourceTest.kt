package com.haise.jiyu.source.likemanga

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

class LikeMangaSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: LikeMangaSource

    private val popularHtml = """
        <html><body>
        <div class="card">
            <a href="/test-series-2172/" class="jtip card-img-top"><img src="upload/pages/test.jpg" class="jtip card-img-top"></a>
            <div class="card-body list-left-8-manga">
                <p class="card-text title-manga"><a href="/test-series-2172/" class="jtip text-body">Test Series</a></p>
            </div>
        </div>
        </body></html>
    """.trimIndent()

    private val searchJson = """
        <li><a href="/test-series-2172/">
            <img src="upload/pages/test.jpg" alt="Test Series">
            <h3>Test Series</h3>
            <h4><i>Alt Title</i></h4>
        </a></li>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <h1 class="title-detail" data-manga="2172">Test Series</h1>
        <div class="col-image"><img src="upload/pages/cover.jpg"></div>
        <ul>
            <li class="author row"><p class="name col-4">Author</p><p class="col-8">Jane Doe</p></li>
            <li class="status row"><p class="name col-4">Status</p><p class="col-8">Ongoing</p></li>
            <li class="kind row"><p class="name col-4">Genres</p><p class="col-8"><a href="/genres/action/">Action</a> - <a href="/genres/manhwa/">Manhwa</a></p></li>
        </ul>
        <div id="summary_shortened">A test summary.</div>
        <div class="list-chapter" id="nt_listchapter"></div>
        </body></html>
    """.trimIndent()

    private val chapterListPage1 = """
        {"list_chap":"<li class=\"wp-manga-chapter\"><a href=\"/test-series-2172/chapter-2-222/\">Chapter 2</a><span class=\"chapter-release-date\"><i>Aug 1, 2026</i></span></li><li class=\"wp-manga-chapter\"><a href=\"/test-series-2172/chapter-1-111/\">Chapter 1</a><span class=\"chapter-release-date\"><i>Jul 1, 2026</i></span></li>","nav":""}
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <div class="reading-detail box_doc">
        <div id="page_1" class="page-chapter p1"><img data-index="1" src="https://like.mgread.io/manga/2172/1.jpg"></div>
        <div id="page_2" class="page-chapter p2"><img data-index="2" src="https://like.mgread.io/manga/2172/2.jpg"></div>
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
                    path.startsWith("/search/top-all/") -> MockResponse().setBody(popularHtml)
                    path.startsWith("/?act=ajax&code=search_manga") -> MockResponse().setBody(searchJson)
                    path == "/test-series-2172/" -> MockResponse().setBody(detailHtml)
                    path.startsWith("/?act=ajax&code=load_list_chapter") && path.contains("page_num=1") ->
                        MockResponse().setBody(chapterListPage1)
                    path.startsWith("/?act=ajax&code=load_list_chapter") ->
                        MockResponse().setBody("""{"list_chap":"","nav":""}""")
                    path == "/test-series-2172/chapter-1-111/" -> MockResponse().setBody(pagesHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = LikeMangaSource(redirectingClient(server))
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
        assertEquals("/test-series-2172/", result[0].url)
        assertTrue(result[0].coverUrl!!.endsWith("/upload/pages/test.jpg"))
        assertEquals("MANGA", result[0].contentType)
    }

    @Test
    fun `search parses AJAX suggest results`() = runTest {
        val result = source.search("test")
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/test-series-2172/", result[0].url)
    }

    @Test
    fun `getMangaDetails infers MANHWA content type from genre tag`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Jane Doe", details.author)
        assertEquals("Ongoing", details.status)
        assertEquals("A test summary.", details.description)
        assertEquals(listOf("Action", "Manhwa"), details.genres)
        assertEquals("MANHWA", details.contentType)
    }

    @Test
    fun `getChapterList follows AJAX pagination until an empty page`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals(1f, chapters[1].chapterNumber)
    }

    @Test
    fun `getPageList reads real image src directly, no lazy-load attribute`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        assertEquals("https://like.mgread.io/manga/2172/1.jpg", pages[0].url)
        assertEquals("https://like.mgread.io/manga/2172/2.jpg", pages[1].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = LikeMangaSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
        assertTrue(emptySource.search("x").isEmpty())
    }
}
