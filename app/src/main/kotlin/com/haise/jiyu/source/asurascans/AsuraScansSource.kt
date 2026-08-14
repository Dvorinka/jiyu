package com.haise.jiyu.source.asurascans

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
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puvodni domena asuracomic.net ted 301-redirectuje na asurascans.com, ktere
 * bezi na kompletne prepsanem Astro frontendu - zmenila se cesta (/series ->
 * /browse, /series/{slug} -> /comics/{slug}-{hash}) i markup (div.series-card
 * misto div.grid > a, obrazky stranek maji atribut data-page-index misto
 * id readerarea).
 */
@Singleton
class AsuraScansSource @Inject constructor(private val client: OkHttpClient) : MangaSource {
    override val id = "asurascans"
    override val name = "Asura Scans"
    override val homepageUrl get() = base
    private val base = "https://asurascans.com"

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", base)
            .build()
        return client.newCall(req).execute().use { it.bodyOrThrow(url) }
    }

    private fun parseList(html: String): List<SManga> {
        val doc = Jsoup.parse(html)
        return doc.select("div.series-card").mapNotNull { card ->
            val link = card.selectFirst("a[href^=/comics/]") ?: return@mapNotNull null
            val href = link.attr("href")
            val img = link.selectFirst("img")
            val title = card.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: img?.attr("alt")?.trim()?.ifBlank { null }
                ?: return@mapNotNull null
            val cover = img?.attr("src")?.takeIf { it.isNotBlank() }
            SManga(sourceId = id, url = href, title = title, coverUrl = cover)
        }
    }

    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try { parseList(get("$base/browse?page=$page")) } catch (_: Exception) { emptyList() }
    }

    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            parseList(get("$base/browse?page=$page&q=$q"))
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            manga.copy(
                title = doc.selectFirst("h1")?.text()?.trim() ?: manga.title,
                coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
                    ?: manga.coverUrl,
                description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim(),
                genres = doc.select("a[href*=genre]").map { it.text().trim() }.filter { it.isNotBlank() },
                author = parseCreator(doc, "Author"),
                artist = parseCreator(doc, "Artist"),
                status = parseInfoCard(doc, "Status")?.lowercase(),
                contentType = parseContentType(doc),
            )
        } catch (_: Exception) { manga }
    }

    /**
     * Info karty ("Status", "Type") maji tvar: label DIV bez potomku, jeho SOUROZENEC je
     * dalsi div, ve kterem je hodnota jako posledni span (prvni span je jen barevna teckova
     * znacka). "Type" byl overeny drive (viz parseContentType) - "Status" ma stejny tvar.
     */
    private fun parseInfoCard(doc: org.jsoup.nodes.Document, label: String): String? {
        val labelDiv = doc.select("div").firstOrNull { it.ownText().trim().equals(label, ignoreCase = true) }
        return labelDiv?.nextElementSibling()?.select("span")?.lastOrNull()?.text()?.trim()
    }

    /**
     * Detailni stranka ma info kartu s labelem "Type" (Manga/Manhwa/Manhua/Novel) - overeno
     * zive na "Return of The Unrivaled Spear Knight" (Manhwa, ne Manga jak appka ukazovala
     * pred timhle fixem, protoze contentType se nikde nenastavoval a ticha vychozi hodnota
     * SManga je "MANGA").
     */
    private fun parseContentType(doc: org.jsoup.nodes.Document): String {
        val raw = parseInfoCard(doc, "Type")?.uppercase()
        return when (raw) {
            "MANHWA", "MANHUA", "NOVEL" -> raw
            else -> "MANGA"
        }
    }

    /**
     * Autor/kresli udaj ma JINY tvar nez info karty vyse - LABEL je span (ne div), jeho
     * rodicovsky div ma jako SOUROZENCE odkaz <a> s hodnotou. Puvodni selektor
     * "div:contains(Author) span" byl chybny - jsoup :contains() hleda text KDEKOLI
     * v potomcich, takze vybral prvni (velky) obalujici div a z nej prvni span na cele
     * strance ("Search", ne jmeno autora) - overeno zive, ze puvodni selektor vraci
     * spatnou hodnotu. Tenhle vyhledava presne label span a bere jeho sourozence.
     */
    private fun parseCreator(doc: org.jsoup.nodes.Document, label: String): String? {
        val labelSpan = doc.select("span").firstOrNull { it.ownText().trim().equals(label, ignoreCase = true) }
        val wrapper = labelSpan?.parent() ?: return null
        return wrapper.nextElementSibling()?.text()?.trim()?.ifBlank { null }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${manga.url}"))
            val chapters = doc.select("a[href^=${manga.url}/chapter/]").distinctBy { it.attr("href") }
            chapters.mapIndexed { i, a ->
                val href = a.attr("href")
                val text = a.selectFirst("span.font-medium")?.text()?.trim()?.ifBlank { null }
                    ?: a.text().trim()
                val num = href.substringAfterLast("/chapter/").toFloatOrNull()
                    ?: (chapters.size - i).toFloat()
                SChapter(sourceId = id, mangaUrl = manga.url, url = href, name = text.ifBlank { "Chapter $num" },
                    chapterNumber = num, dateUpload = 0L)
            }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.parse(get("$base${chapter.url}"))
            doc.select("img[data-page-index]").mapIndexedNotNull { i, img ->
                val url = img.attr("src").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                Page(i, url, url)
            }
        } catch (_: Exception) { emptyList() }
    }
}
