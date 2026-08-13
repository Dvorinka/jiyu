package com.haise.jiyu.source.manga18club

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

class Manga18ClubSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: Manga18ClubSource

    private val listingHtml = """
        <html><body>
        <div class="col-md-3 col-sm-3 col-xs-6">
           <div class="story_item">
              <div class="story_images">
                 <a href="/manhwa/beautiful-days-raw" title=""><img src="placeholder.gif" data-src="https://cdn.manga18.club/manga/beautiful-days-raw/cover/cover_thumb_2.webp" alt="" class="img-responsive lazy"></a>
              </div>
              <div class="mg_info">
                 <div class="mg_name">
                    <a style="text-transform: capitalize;" href="/manhwa/beautiful-days-raw">beautiful days raw</a>
                 </div>
                 <div class="mg_chapter">
                    <div class="item"><div class="chapter_count"><a href="/manhwa/beautiful-days-raw/chapter-88">Ch. 88</a></div></div>
                 </div>
              </div>
           </div>
        </div>
        <div class="col-md-3 col-sm-3 col-xs-6">
           <div class="story_item">
              <div class="story_images">
                 <a href="/manhwa/secret-class" title=""><img src="placeholder.gif" data-src="https://cdn.manga18.club/manga/secret-class/cover/cover_thumb_2.webp" alt="" class="img-responsive lazy"></a>
              </div>
              <div class="mg_info">
                 <div class="mg_name">
                    <a style="text-transform: capitalize;" href="/manhwa/secret-class">Secret Class</a>
                 </div>
              </div>
           </div>
        </div>
        </body></html>
    """.trimIndent()

    private val searchJson = """
        {"status":0,"data":[{"id":"30","name":"Love Square","slug":"love-square","otherNames":"N/A","cover_url":"https://cdn.manga18.club/manga/love-square/cover/cover_thumb.jpg"}]}
    """.trimIndent()

    private val detailHtml = """
        <html><body>
        <div class="detail_avatar"><img src="https://cdn.manga18.club/manga/secret-class/cover/cover_250x350.jpg" alt="" class="img-responsive"></div>
        <div class="detail_name"><h1>Secret Class</h1></div>
        <div class="detail_listInfo">
           <div class="item"><div class="info_label">Other name</div><div class="info_value"><span>Secret Classes</span></div></div>
           <div class="item"><div class="info_label">Author</div><div class="info_value"><a href="#">Wang Kang Cheol</a></div></div>
           <div class="item"><div class="info_label">Artist</div><div class="info_value"><a href="#">Mina-Chan</a></div></div>
           <div class="item"><div class="info_label">Status</div><div class="info_value"><span class="label label-success">On Going</span></div></div>
           <div class="item"><div class="info_label">Categories</div><div class="info_value">
              <a href="#">Adult</a> - <a href="#">Romance</a> - <a href="#">Manhwa</a>
           </div></div>
        </div>
        <div class="detail_block detail_review"><div class="detail_reviewContent">Secret Class is about a wife of two cheating on her husband.</div></div>
        <div class="detail_chapterContent">
           <div class="chapter_box">
              <ul>
                 <li class="">
                    <div class="item">
                       <a style="color: white;" href="/manhwa/secret-class/chapter-312" class="chapter_num">Chapter 312</a>
                       <p class="chapter_info">07-08-2026</p>
                       <p class="chapter_info">27,158</p>
                       <p class="chapter_info hide"><a href="#" title="download">DL</a></p>
                    </div>
                 </li>
                 <li class="">
                    <div class="item">
                       <a style="color: white;" href="/manhwa/secret-class/chapter-309-6" class="chapter_num">Chapter 309.6</a>
                       <p class="chapter_info">25-06-2026</p>
                       <p class="chapter_info">78,837</p>
                       <p class="chapter_info hide"><a href="#" title="download">DL</a></p>
                    </div>
                 </li>
              </ul>
           </div>
        </div>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <div class="reading-detail"></div>
        <script type="text/javascript">
          var slides_page;
          var slides_p_path = ["aHR0cHM6Ly9jZG4ubWFuZ2ExOC5jbHViL21hbmdhL3NlY3JldC1jbGFzcy9jaGFwdGVycy9jaGFwdGVyLTMxMi8wMDEuanBn","aHR0cHM6Ly9jZG4ubWFuZ2ExOC5jbHViL21hbmdhL3NlY3JldC1jbGFzcy9jaGFwdGVycy9jaGFwdGVyLTMxMi8wMDIuanBn",];
        </script>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/latest-release/1" -> MockResponse().setBody(listingHtml)
                    path.startsWith("/search?search=") -> MockResponse().setBody(searchJson)
                    path == "/manhwa/secret-class" -> MockResponse().setBody(detailHtml)
                    path == "/manhwa/secret-class/chapter-312" -> MockResponse().setBody(chapterHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        source = Manga18ClubSource(redirectingClient(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getPopular reads title and cover from story_item cards`() = runTest {
        val result = source.getPopular(1)
        assertEquals(2, result.size)
        assertEquals("beautiful days raw", result[0].title)
        assertEquals("https://manga18.club/manhwa/beautiful-days-raw", result[0].url)
        assertEquals("https://cdn.manga18.club/manga/beautiful-days-raw/cover/cover_thumb_2.webp", result[0].coverUrl)
    }

    @Test
    fun `search parses the JSON endpoint`() = runTest {
        val result = source.search("love")
        assertEquals(1, result.size)
        assertEquals("Love Square", result[0].title)
        assertEquals("https://manga18.club/manhwa/love-square", result[0].url)
    }

    @Test
    fun `getMangaDetails reads listInfo fields by label`() = runTest {
        val manga = source.getPopular(1).first { it.url.endsWith("secret-class") }
        val details = source.getMangaDetails(manga)
        assertEquals("Secret Class", details.title)
        assertEquals("Wang Kang Cheol", details.author)
        assertEquals("Mina-Chan", details.artist)
        assertEquals("On Going", details.status)
        assertEquals(listOf("Adult", "Romance", "Manhwa"), details.genres)
        assertEquals("Secret Class is about a wife of two cheating on her husband.", details.description)
    }

    @Test
    fun `getChapterList parses chapter number and DD-MM-YYYY date`() = runTest {
        val manga = source.getPopular(1).first { it.url.endsWith("secret-class") }
        val chapters = source.getChapterList(manga)
        assertEquals(2, chapters.size)
        assertEquals(312f, chapters[0].chapterNumber)
        assertEquals(309.6f, chapters[1].chapterNumber)
        assertTrue(chapters[0].dateUpload > 0L)
    }

    @Test
    fun `getPageList decodes base64 slides_p_path array`() = runTest {
        val manga = source.getPopular(1).first { it.url.endsWith("secret-class") }
        val chapter = source.getChapterList(manga).first { it.chapterNumber == 312f }
        val pages = source.getPageList(chapter)
        assertEquals(2, pages.size)
        assertEquals("https://cdn.manga18.club/manga/secret-class/chapters/chapter-312/001.jpg", pages[0].url)
        assertEquals("https://cdn.manga18.club/manga/secret-class/chapters/chapter-312/002.jpg", pages[1].url)
    }

    @Test
    fun `malformed responses return empty list, not an exception`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("not html")
        }
        server.start()
        val emptySource = Manga18ClubSource(redirectingClient(server))
        assertTrue(emptySource.getPopular(1).isEmpty())
        assertTrue(emptySource.search("x").isEmpty())
    }
}
