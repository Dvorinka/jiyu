package com.haise.jiyu.source.hdoujin

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

class HDoujinSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HDoujinSource

    // Zkraceny, ale strukturalne realny vyrez z zive odpovedi https://hdoujin.com/?page=1
    private val homeHtml = """
        <html><body>
        <div class="story-grid">
            <div class="story-card" title="sutorongubide 1) ] Strong Bidet 1" data-story-id="593380">
                <a href="/en/1332/sutorongubide-1-strong-bidet-1" class="card-image-wrapper">
                    <img src="https://s1.hentaithai.net/thumb/?img=english/2026/2026-08-09/01332/1.webp" alt="Strong Bidet 1" class="cover-img">
                </a>
                <div class="card-info">
                    <a href="/en/1332/sutorongubide-1-strong-bidet-1" class="card-title" title="[Sunaba suzume] Strong Bidet 1">
                        [Sunaba suzume] Strong Bidet 1
                    </a>
                </div>
            </div>
        </div>
        </body></html>
    """.trimIndent()

    // Zkraceny vyrez z zive odpovedi https://hdoujin.com/en/1332/sutorongubide-1-strong-bidet-1
    private val galleryHtml = """
        <html><body>
        <h1 class="title-thai" itemprop="name"><span class="title-before">[Sunaba suzume]</span> <span class="title-pretty">Strong Bidet 1</span></h1>
        <div class="metadata-table">
            <div class="meta-row">
                <span class="meta-label">Artists:</span>
                <div class="tag-list"><a href="/artist/50043/sunaba-suzume" class="tag-badge" data-tax="artist"><span>sunaba suzume</span></a></div>
            </div>
            <div class="meta-row">
                <span class="meta-label">Tags:</span>
                <div class="tag-list">
                    <a href="/tag/1/full-color" class="tag-badge" data-tax="tag"><span>full color</span></a>
                    <a href="/tag/15/big-breasts" class="tag-badge" data-tax="tag"><span>big breasts</span></a>
                </div>
            </div>
        </div>
        <div class="reader-images-col">
            <div class="reader-image-wrapper" id="page-1">
                <img src="https://s1.hentaithai.net/english/2026/2026-08-09/01332/1.webp" alt="page 1" loading="lazy">
            </div>
            <div class="reader-image-wrapper" id="page-2">
                <img src="https://s1.hentaithai.net/english/2026/2026-08-09/01332/2.webp" alt="page 2" loading="lazy">
            </div>
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
                    path.startsWith("/?page=") || path == "/" -> MockResponse().setBody(homeHtml)
                    path.startsWith("/en/1332") -> MockResponse().setBody(galleryHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = HDoujinSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular reads title and cover from the story-card wrapper`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("[Sunaba suzume] Strong Bidet 1", result[0].title)
        assertEquals("https://s1.hentaithai.net/thumb/?img=english/2026/2026-08-09/01332/1.webp", result[0].coverUrl)
        assertEquals("https://hdoujin.com/en/1332/sutorongubide-1-strong-bidet-1", result[0].url)
    }

    @Test
    fun `getMangaDetails reads artist and tags via data-tax attribute`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("sunaba suzume", details.artist)
        assertEquals(listOf("full color", "big breasts"), details.genres)
    }

    @Test
    fun `getChapterList returns a single synthetic chapter`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(1, chapters.size)
        assertEquals(1f, chapters[0].chapterNumber)
    }

    @Test
    fun `getPageList reads images directly from the detail page reader`() = runTest {
        val manga = source.getPopular(1).first()
        val chapter = source.getChapterList(manga).first()
        val pages = source.getPageList(chapter)
        assertEquals(2, pages.size)
        assertEquals("https://s1.hentaithai.net/english/2026/2026-08-09/01332/1.webp", pages[0].url)
        assertEquals("https://s1.hentaithai.net/english/2026/2026-08-09/01332/2.webp", pages[1].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = HDoujinSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
    }
}
