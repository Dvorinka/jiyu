package com.haise.jiyu.source.fanfox

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

class FanFoxSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: FanFoxSource

    private val directoryHtml = """
        <html><body>
        <a href="/manga/test_series/"><img class="manga-list-1-cover" src="https://cdn.example.com/cover.jpg" alt="Test Series"></a>
        <p class="manga-list-1-item-title"><a href="/manga/test_series/" title="Test Series">Test Series</a></p>
        </body></html>
    """.trimIndent()

    private val searchHtml = """
        <html><body>
        <a href="/manga/test_series/"><img class="manga-list-4-cover" src="https://cdn.example.com/cover.jpg" alt="Test Series"></a>
        <p class="manga-list-4-item-title"><a href="/manga/test_series/" title="Test Series">Test Series</a></p>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <p class="detail-info-right-title"><span class="detail-info-right-title-font">Test Series</span><span class="detail-info-right-title-tip">Ongoing</span></p>
        <p class="detail-info-right-say">Author: <a href="/search/author/Jane+Doe/" title="Jane Doe">Jane Doe</a></p>
        <p class="detail-info-right-tag-list"><a href="/directory/action/" title="Action">Action</a><a href="/directory/drama/" title="Drama">Drama</a></p>
        <p class="detail-info-right-content">A test summary.</p>
        <li>
            <a href="/manga/test_series/c002/1.html">
                <div class="detail-main-list-main"><p class="title3">Ch.002</p><p class="title2">Jun 23,2025 </p></div>
            </a>
        </li>
        <li>
            <a href="/manga/test_series/c001/1.html">
                <div class="detail-main-list-main"><p class="title3">Ch.001</p><p class="title2">Jun 09,2025 </p></div>
            </a>
        </li>
        </body></html>
    """.trimIndent()

    private val readerHtml = """
        <html><body>
        var chapterid =1598287;
        var imagecount=2;
        </body></html>
    """.trimIndent()

    // Skutečná odpověď z chapterfun.ashx (zachyceno živě proti fanfox.net/manga/ao_ashi/c410/).
    private val chapterfunResponse = """eval(function(p,a,c,k,e,d){e=function(c){return(c<a?"":e(parseInt(c/a)))+((c=c%a)>35?String.fromCharCode(c+29):c.toString(36))};if(!''.replace(/^/,String)){while(c--)d[e(c)]=k[c]||e(c);k=[function(e){return d[e]}];e=function(){return'\w+'};c=1;};while(c--)if(k[c])p=p.replace(new RegExp('\b'+e(c)+'\b','g'),k[c]);return p;}('k e(){2 f="//8.b.7/c/3/4/6.0/g";2 1=["/n.h?5=m&9=a","/l.h?5=j&9=a"];o(2 i=0;i<1.u;i++){s(i==0){1[i]="//8.b.7/c/3/4/6.0/g"+1[i];p}1[i]=f+1[i]}q 1}2 d;d=e();r=t;',31,31,'|pvalue|var|manga|29225|token|410|me|zjcdn|ttl|1786291200|mangafox|store||dm5imagefun|pix|compressed|jpg||7121d221352c2e762de1bb012f4e6490f05f70fb|function|b20250623_93556_350|3574dcc3740215b971ee7f6545bac9cc1f75285f|b20250623_93556_349|for|continue|return|currentimageid|if|40804494|length'.split('|'),0,{}))"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/directory/") -> MockResponse().setBody(directoryHtml)
                    path.startsWith("/search") -> MockResponse().setBody(searchHtml)
                    path == "/manga/test_series/" -> MockResponse().setBody(detailHtml)
                    path == "/manga/test_series/c001/1.html" -> MockResponse().setBody(readerHtml)
                    path.startsWith("/manga/test_series/c001/chapterfun.ashx") -> MockResponse().setBody(chapterfunResponse)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = FanFoxSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular parses directory listing cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
        assertEquals("/manga/test_series/", result[0].url)
        assertEquals("https://cdn.example.com/cover.jpg", result[0].coverUrl)
    }

    @Test
    fun `search parses manga-list-4 result cards`() = runTest {
        val result = source.search("test")
        assertEquals(1, result.size)
        assertEquals("Test Series", result[0].title)
    }

    @Test
    fun `getMangaDetails parses status, author, genres and description`() = runTest {
        val manga = source.getPopular(1).first()
        val details = source.getMangaDetails(manga)
        assertEquals("Ongoing", details.status)
        assertEquals("Jane Doe", details.author)
        assertEquals(listOf("Action", "Drama"), details.genres)
        assertEquals("A test summary.", details.description)
    }

    @Test
    fun `getChapterList parses title3 chapter numbers and title2 dates`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(2f, chapters[0].chapterNumber)
        assertEquals(1f, chapters[1].chapterNumber)
        assertTrue(chapters[1].dateUpload > 0L)
    }

    @Test
    fun `getPageList discovers page count and getImageUrl decodes real packer response`() = runTest {
        val manga = source.getPopular(1).first()
        val chapters = source.getChapterList(manga)
        val pages = source.getPageList(chapters[1])
        assertEquals(2, pages.size)
        val resolved = source.getImageUrl(pages[0])
        assertEquals(
            "https://zjcdn.mangafox.me/store/manga/29225/410.0/compressed/b20250623_93556_349.jpg?token=3574dcc3740215b971ee7f6545bac9cc1f75285f&ttl=1786291200",
            resolved,
        )
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = FanFoxSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
        assertTrue(emptySource.search("x").isEmpty())
    }
}
