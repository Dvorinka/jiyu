package com.haise.jiyu.source.hentaizap

import com.haise.jiyu.source.redirectingClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HentaiZapSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: HentaiZapSource

    // Zkraceny, ale strukturalne realny vyrez z zive odpovedi https://hentaizap.com/popular/?page=1
    private val popularHtml = """
        <html><body>
        <div class="thumb" data-categories="6">
            <div class="t_inf"><span class="th_ct"><h3><a href="/category/artist-cg/">Artist CG</a></h3></span></div>
            <div class="inner_thumb"><a href="/gallery/1610961/"><img class="lazy" src="placeholder.svg" data-src="https://m11.hentaizap.com/032/k5bi98t4z0/thumb.jpg" alt="cover"></a></div>
            <div class="caption"><h2><a href="/gallery/1610961/">Childbirth Island 2&amp;3</a></h2></div>
        </div>
        </body></html>
    """.trimIndent()

    // Zkraceny vyrez z zive odpovedi https://hentaizap.com/gallery/1610961/
    private val detailHtml = """
        <html><body>
        <div class="gp_top_left"><div class="gp_cover"><img class="lazy" src="placeholder.svg" data-src="https://m11.hentaizap.com/032/k5bi98t4z0/cover.jpg" alt="cover"/></div></div>
        <div class="gp_top_right"><h1>Childbirth Island 2&amp;3</h1></div>
        <ul><span class='info_txt'>Tags:</span>
            <li><a class='gp_btn_tag' href='/tag/big-breasts/'>big breasts<span class='tag_badge'>448068</span></a></li>
            <li><a class='gp_btn_tag' href='/tag/futanari/'>futanari<span class='tag_badge'>80807</span></a></li>
        </ul>
        <ul><span class='info_txt'>Artists:</span>
            <li><a class='gp_btn_tag' href='/artist/niyasuke/'>niyasuke<span class='tag_badge'>12</span></a></li>
        </ul>
        <div class="thumbstrip">
            <img src="https://m11.hentaizap.com/032/k5bi98t4z0/1t.jpg"/>
            <img src="https://m11.hentaizap.com/032/k5bi98t4z0/2t.jpg"/>
            <img src="https://m11.hentaizap.com/032/k5bi98t4z0/10t.jpg"/>
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
                    path.startsWith("/popular/") -> MockResponse().setBody(popularHtml)
                    path.startsWith("/gallery/1610961") -> MockResponse().setBody(detailHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = HentaiZapSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular reads title and cover from data-src`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Childbirth Island 2&3", result[0].title)
        assertEquals("https://m11.hentaizap.com/032/k5bi98t4z0/thumb.jpg", result[0].coverUrl)
        assertEquals("https://hentaizap.com/gallery/1610961/", result[0].url)
    }

    @Test
    fun `getMangaDetails reads artist and tags without the trailing badge count`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("niyasuke", details.artist)
        assertEquals(listOf("big breasts", "futanari"), details.genres)
    }

    @Test
    fun `getPageList derives full-res webp urls from the thumbstrip, sorted numerically`() = runTest {
        val manga = source.getPopular(1).first()
        val chapter = source.getChapterList(manga).first()
        val pages = source.getPageList(chapter)
        assertEquals(3, pages.size)
        assertEquals("https://m11.hentaizap.com/032/k5bi98t4z0/1.webp", pages[0].url)
        assertEquals("https://m11.hentaizap.com/032/k5bi98t4z0/2.webp", pages[1].url)
        assertEquals("https://m11.hentaizap.com/032/k5bi98t4z0/10.webp", pages[2].url)
    }
}
