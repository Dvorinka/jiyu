package com.haise.jiyu.source.hachiraw

import com.haise.jiyu.source.bodyOrThrow
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.Page
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HachirawSource @Inject constructor(private val client: OkHttpClient) : MangaSource {

    override val id = "hachiraw"
    override val name = "Hachiraw"
    override val homepageUrl get() = base
    private val base = "https://hachiraw.win"

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", base)
            .build()
        return client.newCall(req).execute().use { it.bodyOrThrow(url) }
    }

    private fun parseCard(article: Element): SManga? {
        val link = article.selectFirst("h3.entry-title a") ?: return null
        val href = link.attr("href").ifBlank { return null }
        val title = link.text().trim().ifBlank { return null }
        val cover = article.selectFirst(".featured-thumb img")?.attr("data-src")?.trim()
            ?.takeIf { it.isNotBlank() }
        return SManga(sourceId = id, url = href, title = title, coverUrl = cover, contentType = "MANGA")
    }

    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "$base/" else "$base/page/$page/"
            val doc = Jsoup.parse(get(url))
            doc.select("article.post.manga").mapNotNull(::parseCard)
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = if (page <= 1) "$base/?s=$q" else "$base/page/$page/?s=$q"
            val doc = Jsoup.parse(get(url))
            doc.select("article.post.manga").mapNotNull(::parseCard)
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            val genres = doc.select("a[href^=/category/]").map { it.text().trim() }.filter { it.isNotBlank() }
            val authorText = doc.select("p").firstOrNull { it.text().trim().startsWith("Author:") }
                ?.text()?.removePrefix("Author:")?.trim()
            manga.copy(
                title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: manga.title,
                author = authorText?.takeIf { it.isNotBlank() && !it.equals("Updating", ignoreCase = true) },
                genres = genres,
            )
        } catch (_: Exception) { manga }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            doc.select("table.table-hover a[href^=/chapter/]").mapNotNull { a ->
                val href = a.attr("href").ifBlank { return@mapNotNull null }
                val name = a.text().trim().ifBlank { return@mapNotNull null }
                val num = Regex("""[\d.]+""").find(name)?.value?.toFloatOrNull() ?: 0f
                SChapter(sourceId = id, mangaUrl = manga.url, url = href, name = name, chapterNumber = num, dateUpload = 0L)
            }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${chapter.url}"))
            doc.select("img.aligncenter[data-src]").mapIndexedNotNull { i, img ->
                val url = img.attr("data-src").takeIf { it.startsWith("http") } ?: return@mapIndexedNotNull null
                Page(i, url, url)
            }
        } catch (_: Exception) { emptyList() }
    }
}
