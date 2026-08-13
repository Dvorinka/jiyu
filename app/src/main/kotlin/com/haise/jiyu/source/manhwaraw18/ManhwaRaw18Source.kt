package com.haise.jiyu.source.manhwaraw18

import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.Page
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.bodyOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * manhwaraw18.com - vlastni (ne-Madara) sablona s vlastnim CDN
 * (cdn.manhwaraw18.com pro obalky, cdn2.manhwaraw18.com pro stranky kapitol).
 * Hledani z hlavicky je ciste JSON API (`/api/search?q=...`), takze pro
 * search() se nepouziva HTML parsovani. Filtr "type=manhwa" na `/search`
 * endpointu je nefunkcni (vzdy 0 vysledku, overeno zive) - getPopular proto
 * jede bez type filtru, jen s razenim (`sort=-views` apod.).
 */
@Singleton
class ManhwaRaw18Source @Inject constructor(private val client: OkHttpClient) : MangaSource {

    override val id = "manhwaraw18"
    override val name = "ManhwaRaw18"
    override val contentType = "MANHWA"
    override val isAdult = true
    override val homepageUrl get() = base

    private val base = "https://manhwaraw18.com"

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()
        return client.newCall(req).execute().use { it.bodyOrThrow(url) }
    }

    private fun parseDocument(url: String): Document = Jsoup.parse(get(url), url)

    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> =
        withContext(Dispatchers.IO) {
            try {
                val sort = when (filter.sortBy) {
                    "latest" -> "-updated_at"
                    "title"  -> "name"
                    else     -> "-views"
                }
                val doc = parseDocument("$base/search?type=&sort=$sort&page=$page")
                doc.select("a.result-card").mapNotNull { a ->
                    val url = a.absUrl("href").ifBlank { return@mapNotNull null }
                    val title = (a.selectFirst("div.result-card-title")?.text()?.trim()?.ifBlank { null }
                        ?: a.selectFirst("img")?.attr("alt")?.trim()?.ifBlank { null })
                        ?: return@mapNotNull null
                    val cover = a.selectFirst("img")?.attr("src")?.trim()?.ifBlank { null }
                    SManga(sourceId = id, url = url, title = title, coverUrl = cover, contentType = "MANHWA")
                }
            } catch (_: Exception) { emptyList() }
        }

    // Header-vyhledavani na webu jede pres cisty JSON endpoint - zadne HTML
    // parsovani, zadna pagination (server ji sam neomezuje na malo vysledku).
    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> =
        withContext(Dispatchers.IO) {
            if (page > 1) return@withContext emptyList()
            try {
                val q = URLEncoder.encode(query, "UTF-8")
                val json = JSONObject(get("$base/api/search?q=$q"))
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val o = results.optJSONObject(i) ?: return@mapNotNull null
                    val slug = o.optString("slug").ifBlank { return@mapNotNull null }
                    val title = o.optString("name").ifBlank { return@mapNotNull null }
                    val cover = o.optString("cover_full_url").takeIf { it.isNotBlank() }
                    SManga(sourceId = id, url = "$base/manga/$slug", title = title, coverUrl = cover, contentType = "MANHWA")
                }
            } catch (_: Exception) { emptyList() }
        }

    override suspend fun getMangaDetails(manga: SManga): SManga =
        withContext(Dispatchers.IO) {
            try {
                val doc = parseDocument(manga.url)
                val detectedType = doc.selectFirst("span.detail-tag-number")?.text()?.let(::normalizeContentType)
                val status = doc.selectFirst("span.detail-tag-year")?.text()?.trim()?.ifBlank { null }
                // Synopse je "korejsky text\n------\nanglicky text/poznamky" - "------"
                // je oddelovac jako obycejny text, ne HTML znacka, takze po nem lze
                // rozdelit primo na vyparsovanem .text().
                val description = doc.selectFirst("div.detail-synopsis, #synopsisBox")
                    ?.text()?.trim()?.substringBefore("------")?.trim()?.ifBlank { null }

                var author: String? = null
                var genres: List<String> = emptyList()
                doc.select("div.detail-stat-row").forEach { row ->
                    val label = row.selectFirst(".detail-stat-label")?.text()?.trim().orEmpty()
                    val valueEl = row.selectFirst(".detail-stat-value") ?: return@forEach
                    when {
                        label.startsWith("Authors") -> author = valueEl.select("a").joinToString(", ") { it.text().trim() }.ifBlank { null }
                        label.startsWith("Genres")  -> genres = valueEl.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
                    }
                }

                manga.copy(
                    description = description,
                    status = status,
                    author = author,
                    genres = genres,
                    contentType = detectedType ?: "MANHWA",
                )
            } catch (_: Exception) { manga }
        }

    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        withContext(Dispatchers.IO) {
            try {
                val doc = parseDocument(manga.url)
                doc.select("div.detail-chapter-row").mapNotNull { row ->
                    val link = row.selectFirst("a") ?: return@mapNotNull null
                    val url = link.absUrl("href").ifBlank { return@mapNotNull null }
                    val name = link.text().trim().ifBlank { return@mapNotNull null }
                    // "data-chapter-number" je presne cislo kapitoly primo v atributu
                    // (Madara-styl weby ho musi hadat z nazvu) - spolehlivejsi nez regex.
                    val chapterNumber = row.attr("data-chapter-number").toFloatOrNull()
                        ?: Regex("""[\d.]+""").find(name)?.value?.toFloatOrNull() ?: 0f
                    val dateText = row.selectFirst("span.detail-col-updated")?.text()?.trim()
                    SChapter(
                        sourceId = id,
                        mangaUrl = manga.url,
                        url = url,
                        name = name,
                        chapterNumber = chapterNumber,
                        dateUpload = parseDate(dateText),
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    private fun parseDate(text: String?): Long {
        if (text.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            // "29/05/23"
            SimpleDateFormat("d/M/yy", Locale.ENGLISH).parse(text)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        withContext(Dispatchers.IO) {
            try {
                val doc = parseDocument(chapter.url)
                doc.select("#readerPages img, div.reader-pages img").mapIndexedNotNull { i, img ->
                    val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
                    if (src.isBlank() || src.startsWith("data:")) return@mapIndexedNotNull null
                    Page(index = i, url = src, imageUrl = src)
                }
            } catch (_: Exception) { emptyList() }
        }

    private fun normalizeContentType(text: String): String? = when (text.trim().lowercase()) {
        "manga" -> "MANGA"
        "manhwa" -> "MANHWA"
        "manhua" -> "MANHUA"
        "webtoon" -> "MANHWA"
        "novel", "light novel" -> "NOVEL"
        else -> null
    }
}
