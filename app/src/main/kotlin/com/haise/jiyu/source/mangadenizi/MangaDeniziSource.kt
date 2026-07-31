package com.haise.jiyu.source.mangadenizi

import com.haise.jiyu.source.bodyOrThrow

import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.Page
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.util.ScrambledImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MangaDenizi (mangadenizi.net, TR) - na první pohled čistě klientsky
 * renderované Nuxt SPA (žádná data ve statickém HTML), ale reverzováním
 * jejich JS chunků ve složce _nuxt (2026-07-26) se našlo kompletní interní
 * REST API, které appka volá přímo:
 *  - `GET /api/v1/web/manga?page=N` - stránkovaný seznam
 *  - `GET /api/v1/web/manga/{slug}` - detail včetně ÚPLNÉHO seznamu kapitol
 *  - `GET /api/v1/reader/{slug}/{chapterSlug}` - stránky kapitoly
 *
 * Stránky jsou navíc rozřezané na dlaždice a zpřeházené (pole `scramble` v
 * odpovědi readeru, `"method":"tiled-v1"`) - grid/seed se zakódují do URL
 * obrázku přes [ScrambledImageUrl] a rozskládají zpátky až při zobrazení/
 * stažení (viz [com.haise.jiyu.util.TileScramble] pro algoritmus a
 * `com.haise.jiyu.ui.reader.TileDescrambleTransformation` /
 * `ChapterDownloadWorker` pro obě místa, kde se to aplikuje).
 *
 * `?search=`/`?q=`/`?query=` parametry na listing endpointu nic nefiltrují
 * (server je ignoruje) - search proto stahne první stránku a filtruje
 * lokálně, stejný vzor jako [com.haise.jiyu.source.hachirumi.HachirumiSource].
 */
@Singleton
class MangaDeniziSource @Inject constructor(private val client: OkHttpClient) : MangaSource {

    override val id = "mangadenizi"
    override val name = "MangaDenizi (TR)"
    override val language = "tr"
    override val homepageUrl get() = base
    private val base = "https://mangadenizi.net"

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(req).execute().use { it.bodyOrThrow(url) }
        return JSONObject(body)
    }

    private fun contentTypeOf(item: JSONObject): String =
        when (item.optJSONObject("type")?.optString("slug")) {
            "manhwa" -> "MANHWA"
            "manhua" -> "MANHUA"
            "novel" -> "NOVEL"
            else -> "MANGA"
        }

    private fun itemToManga(item: JSONObject): SManga {
        val slug = item.optString("slug")
        return SManga(
            sourceId = id,
            url = "$base/api/v1/web/manga/$slug",
            title = item.optString("title"),
            coverUrl = item.optString("cover_url").takeIf { it.isNotBlank() },
            description = item.optString("description").takeIf { it.isNotBlank() },
            status = item.optString("status").takeIf { it.isNotBlank() },
            contentType = contentTypeOf(item),
        )
    }

    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        try {
            val root = getJson("$base/api/v1/web/manga?page=$page")
            val data = root.getJSONObject("data").getJSONObject("manga").getJSONArray("data")
            (0 until data.length()).map { itemToManga(data.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        try {
            getPopular(1, filter).filter { it.title.contains(query, ignoreCase = true) }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        try {
            val item = getJson(manga.url).getJSONObject("data").getJSONObject("manga")
            val genres = item.optJSONArray("categories")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf(String::isNotBlank) }
            } ?: emptyList()
            val author = item.optJSONArray("authors")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf(String::isNotBlank) }
                    .joinToString(", ").takeIf { it.isNotBlank() }
            }
            manga.copy(
                title = item.optString("title", manga.title),
                coverUrl = item.optString("cover_url").takeIf { it.isNotBlank() } ?: manga.coverUrl,
                description = item.optString("description").takeIf { it.isNotBlank() },
                status = item.optString("status").takeIf { it.isNotBlank() },
                author = author,
                genres = genres,
                contentType = contentTypeOf(item),
            )
        } catch (_: Exception) { manga }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            val item = getJson(manga.url).getJSONObject("data").getJSONObject("manga")
            val slug = item.optString("slug")
            val chapters = item.optJSONArray("chapters") ?: return@withContext emptyList()
            (0 until chapters.length()).mapNotNull { i ->
                val ch = chapters.getJSONObject(i)
                val chapterSlug = ch.optString("slug").ifBlank { return@mapNotNull null }
                val number = ch.optDouble("number", 0.0)
                val title = ch.optString("title").takeIf { it.isNotBlank() && it != "null" }
                SChapter(
                    sourceId = id,
                    mangaUrl = manga.url,
                    url = "$base/api/v1/reader/$slug/${URLEncoder.encode(chapterSlug, "UTF-8")}",
                    name = title ?: "Bölüm ${formatChapterNumber(number)}",
                    chapterNumber = number.toFloat(),
                    dateUpload = parseDate(ch.optString("published_at")),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun formatChapterNumber(number: Double): String =
        if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()

    private fun parseDate(text: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(text)?.time ?: 0L
    } catch (_: Exception) { 0L }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        try {
            val root = getJson(chapter.url)
            val pages = root.optJSONArray("pages") ?: return@withContext emptyList()
            (0 until pages.length()).mapNotNull { i ->
                val p = pages.getJSONObject(i)
                val imageUrl = p.optString("image_url").ifBlank { return@mapNotNull null }
                val scramble = p.optJSONObject("scramble")
                val finalUrl = if (scramble?.optString("method") == "tiled-v1") {
                    val grid = scramble.optInt("grid", 0)
                    val seed = scramble.optLong("seed", 0L)
                    if (grid > 0) ScrambledImageUrl.encode(imageUrl, grid, seed) else imageUrl
                } else imageUrl
                Page(i, finalUrl, finalUrl)
            }
        } catch (_: Exception) { emptyList() }
    }
}
