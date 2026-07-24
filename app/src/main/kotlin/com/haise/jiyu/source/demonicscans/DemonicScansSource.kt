package com.haise.jiyu.source.demonicscans

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
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Náhrada za mrtvou doménu "Demon Scans" (demonscans.net - DNS už nerozresolvuje
 * vůbec, doména zanikla). Tohle je JINÝ tým/branding ("Manga Demon" / demonicscans.org),
 * ne přímý nástupce - jen podobně znějící jméno. Vlastní (ne Madara) šablona webu:
 * seznamy i detail jsou plně server-side renderované HTML (žádné nutné AJAX volání),
 * kapitoly taky - `<img class="imgholder">` s přímou URL na CDN, bez potřeby Refereru.
 *
 * `/chaptered.php?manga={id}&chapter={n}` dělá jen 302 redirect na skutečnou čtecí
 * stránku `/title/{slug}/chapter/{n}/1` - necháváme na tom, že OkHttpClient
 * (viz AppModule.kt) sleduje redirecty defaultně, takže stačí posílat tenhle
 * jednodušší odkaz a nemusíme si sami skládat slug.
 */
@Singleton
class DemonicScansSource @Inject constructor(
    private val client: OkHttpClient,
) : MangaSource {

    override val id = "demonicscans"
    override val name = "DemonicScans"
    override val contentType = "MANHWA"
    override val homepageUrl get() = base
    private val base = "https://demonicscans.org"

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    /** Karty v seznamech (translationlist.php/lastupdates.php) mají shodnou strukturu. */
    private fun parseCards(html: String): List<SManga> {
        val doc = Jsoup.parse(html)
        return doc.select("#updates-container > div.updates-element").mapNotNull { card ->
            val a = card.selectFirst("h2 a[href^=/manga/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val cover = card.selectFirst(".thumb img")?.attr("src")
            SManga(sourceId = id, url = href, title = title, coverUrl = cover, contentType = contentType)
        }.distinctBy { it.url }
    }

    /**
     * Žádná dedikovaná "populární" stránka - "Populární" tab proto mapujeme na
     * translationlist.php (kompletní katalog jejich vlastních překladů, nejbližší
     * ekvivalent "výchozího procházení"), "Nejnovější" na lastupdates.php (feed
     * aktualizací kapitol, odpovídá skutečnému významu "latest").
     */
    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try {
            val path = if (filter.sortBy == "latest") "lastupdates.php" else "translationlist.php"
            val query = if (page > 1) "?list=$page" else ""
            parseCards(get("$base/$path$query"))
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        // /search.php nemá stránkování (je to živý autocomplete endpoint) - druhá a
        // další stránka by jen zopakovala stejný výsledek, radši ukončit scrollování.
        if (page > 1) return@withContext emptyList()
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.parse(get("$base/search.php?manga=$q"))
            doc.select("a[href^=/manga/]").mapNotNull { a ->
                val href = a.attr("href")
                val title = a.selectFirst(".seach-right > div")?.text()?.trim()
                    ?: a.selectFirst("img")?.attr("title")?.trim()
                    ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                val cover = a.selectFirst("img")?.attr("src")
                SManga(sourceId = id, url = href, title = title, coverUrl = cover, contentType = contentType)
            }.distinctBy { it.url }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            val genres = doc.select(".genres-list li").map { it.text().trim() }.filter { it.isNotBlank() }

            var author: String? = null
            var status: String? = null
            doc.select("#manga-info-stats > div.flex.flex-row").forEach { row ->
                val cells = row.select("li")
                when (cells.getOrNull(0)?.text()?.trim()) {
                    "Author" -> author = cells.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }
                    "Status" -> status = cells.getOrNull(1)?.text()?.trim()
                }
            }
            val normalizedStatus = when {
                status.equals("Ongoing", ignoreCase = true) -> "Ongoing"
                status.equals("Completed", ignoreCase = true) -> "Completed"
                else -> status
            }

            manga.copy(
                title = doc.selectFirst("h1.big-fat-titles")?.text()?.trim() ?: manga.title,
                coverUrl = doc.selectFirst("#manga-page img")?.attr("src") ?: manga.coverUrl,
                genres = genres,
                author = author,
                status = normalizedStatus,
            )
        } catch (_: Exception) { manga }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            doc.select("#chapters-list a.chplinks").mapNotNull { a ->
                val href = a.attr("href")
                val num = Regex("""chapter=(\d+(?:\.\d+)?)""").find(href)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: return@mapNotNull null
                val name = a.ownText().trim().ifBlank { "Chapter ${num.toChapterLabel()}" }
                val dateText = a.selectFirst("span")?.text()?.trim().orEmpty()
                SChapter(
                    sourceId = id,
                    mangaUrl = manga.url,
                    url = href,
                    name = name,
                    chapterNumber = num,
                    dateUpload = parseDate(dateText),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${chapter.url}"))
            doc.select("img.imgholder").mapIndexedNotNull { i, img ->
                val src = img.attr("src")
                // Vyfiltrovat reklamní banner ("free_ads.jpg"), který má taky class="imgholder".
                if (!src.contains("demoniclibs.com")) return@mapIndexedNotNull null
                Page(index = i, url = src, imageUrl = src)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun Float.toChapterLabel(): String =
        if (this == this.toInt().toFloat()) this.toInt().toString() else this.toString()

    private fun parseDate(text: String): Long = try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(text)?.time ?: 0L
    } catch (_: Exception) { 0L }
}
