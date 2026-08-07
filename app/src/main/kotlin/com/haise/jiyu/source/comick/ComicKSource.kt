package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.LanguageMap
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.Page
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SGroup
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.interceptor.CloudflareInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zdroj napojený na veřejné REST API ComicK (https://api.comick.dev - dřívější
 * api.comick.fun je mrtvá doména, endpoint pro detail/kapitoly je teď pod
 * "/comic/" místo "/manga/").
 *
 * ComicK poskytuje veřejné API bez nutnosti klíče a explicitně povoluje
 * jeho využití třetími stranami. Pokrývá přes 100 000 titulů (manga,
 * manhwa, manhua) s překlady do desítek jazyků.
 *
 * Klíčové entity v API:
 *  - slug  = URL-friendly název ("one-piece"), používá se v adrese mangy
 *  - hid   = hash ID ("abc123"), používá se pro kapitoly a stránky
 */
@Singleton
class ComicKSource @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) : MangaSource {

    override val id   = "comick"
    override val name = "ComicK"
    override val homepageUrl get() = "https://comick.io"

    private val apiBase   = "https://api.comick.dev"
    private val coverBase = "https://meo.comick.pictures"

    // ─── Vyhledávání & browse ────────────────────────────────────────────────

    override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            parseComicList(getArray("$apiBase/v1.0/search?q=$q&limit=20&page=$page"))
        }

    override suspend fun getPopular(page: Int, filter: MangaFilter): List<SManga> =
        withContext(Dispatchers.IO) {
            val sort = when (filter.sortBy) {
                "latest" -> "uploaded"
                "rating" -> "rating"
                "title"  -> "title"
                else     -> "follow"
            }
            parseComicList(getArray("$apiBase/v1.0/search?sort=$sort&limit=20&page=$page"))
        }

    // ─── Detail mangy ────────────────────────────────────────────────────────

    /**
     * Doplní popis a stav vydávání.
     * manga.url je ve formátu "$apiBase/manga/{slug}".
     */
    override suspend fun getMangaDetails(manga: SManga): SManga =
        withContext(Dispatchers.IO) {
            val slug = manga.url.substringAfterLast("/")
            val json = getObject("$apiBase/comic/$slug")
            val comic = json.getJSONObject("comic")

            val desc = comic.optString("desc").ifBlank { null }
            val status = when (comic.optInt("status", -1)) {
                1    -> "Vychází"
                2    -> "Dokončeno"
                3    -> "Zrušeno"
                4    -> "Přerušeno"
                else -> null
            }
            val year = comic.optInt("year", 0).takeIf { it > 0 }

            val authors = json.optJSONArray("authors")
            val author = if (authors != null && authors.length() > 0)
                authors.getJSONObject(0).optString("name").ifBlank { null }
            else null

            val genres = mutableListOf<String>()
            val tagsArr = json.optJSONArray("genres") ?: json.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val name = tagsArr.optJSONObject(i)?.optString("name")
                        ?: tagsArr.optString(i)
                    if (!name.isNullOrBlank()) genres.add(name)
                }
            }

            manga.copy(
                description = desc,
                status      = status,
                author      = author,
                genres      = genres,
                year        = year,
                contentType = contentTypeFromCountry(comic.optString("country")),
            )
        }

    // ─── Kapitoly ────────────────────────────────────────────────────────────

    /**
     * Stáhne kompletní seznam kapitol v angličtině.
     * API vyžaduje hid (ne slug) pro endpoint /manga/{hid}/chapters,
     * proto nejdřív načteme detail mangy abychom hid získali.
     * Prochází stránky po 60 dokud API nevrátí méně výsledků.
     */
    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        withContext(Dispatchers.IO) {
            // Krok 1: získat hid z detailu mangy
            val slug = manga.url.substringAfterLast("/")
            val detailJson = getObject("$apiBase/comic/$slug")
            val hid = detailJson.getJSONObject("comic").getString("hid")

            // Krok 2: stránkovat přes všechny kapitoly
            val chapters = mutableListOf<SChapter>()
            var page = 1
            val pageSize = 60

            val langCode = LanguageMap.toMangaDexCode(settings.sourceLanguage.first())
            while (true) {
                val url = "$apiBase/comic/$hid/chapters?lang=$langCode&page=$page&limit=$pageSize"
                val json = getObject(url)
                val arr = json.optJSONArray("chapters") ?: break

                for (i in 0 until arr.length()) {
                    chapterFromJson(arr.getJSONObject(i), manga.url)
                        ?.let { chapters.add(it) }
                }

                // Méně výsledků než pageSize = poslední stránka
                if (arr.length() < pageSize) break
                page++
            }

            chapters
        }

    // ─── Stránky kapitoly ────────────────────────────────────────────────────

    /**
     * Stáhne seznam stránek kapitoly.
     * chapter.url je ve formátu "$apiBase/chapter/{hid}".
     * Obrázky jsou hostované na meo.comick.pictures/{b2key}.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> =
        withContext(Dispatchers.IO) {
            val chHid = chapter.url.substringAfterLast("/")
            val json = getObject("$apiBase/chapter/$chHid")
            val images = json.getJSONObject("chapter").getJSONArray("md_images")

            (0 until images.length()).map { i ->
                val img = images.getJSONObject(i)
                val b2key = img.getString("b2key")
                val imageUrl = "$coverBase/$b2key"
                Page(index = i, url = imageUrl, imageUrl = imageUrl)
            }
        }

    // ─── Privátní pomocné funkce ─────────────────────────────────────────────

    /**
     * api.comick.dev je za Cloudflare a bez prohlížečového User-Agentu vrací 403
     * "Just a moment..." (WAF pravidlo na základě UA, ne skutečná JS výzva) - zjištěno
     * auditem 2026-07-27. S touhle hlavičkou requesty prochází bez CloudflareInterceptor
     * (rychlejší a spolehlivější než čekat na jeho WebView-based řešení výzvy).
     */
    private fun requestBuilder(url: String) = Request.Builder().url(url)
        .header("User-Agent", CloudflareInterceptor.CHROME_UA)

    private fun getArray(url: String): JSONArray {
        val request = requestBuilder(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "ComicK API chyba ${response.code}: $url" }
            return JSONArray(body)
        }
    }

    private fun getObject(url: String): JSONObject {
        val request = requestBuilder(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "ComicK API chyba ${response.code}: $url" }
            return JSONObject(body)
        }
    }

    /** Převede jeden objekt z výsledků hledání na SManga. */
    private fun parseComicList(arr: JSONArray): List<SManga> =
        (0 until arr.length()).mapNotNull { i ->
            val comic = arr.getJSONObject(i)
            val title = comic.optString("title").ifBlank { return@mapNotNull null }
            val slug  = comic.optString("slug").ifBlank { return@mapNotNull null }

            // Titulní obrázek: první položka md_covers s neprázdným b2key
            val coverUrl = comic.optJSONArray("md_covers")
                ?.let { covers ->
                    (0 until covers.length()).firstNotNullOfOrNull { j ->
                        covers.getJSONObject(j).optString("b2key").ifBlank { null }
                    }
                }
                ?.let { b2key -> "$coverBase/$b2key" }

            SManga(
                sourceId    = id,
                url         = "$apiBase/comic/$slug",
                title       = title,
                coverUrl    = coverUrl,
                contentType = contentTypeFromCountry(comic.optString("country")),
            )
        }

    /** ComicK nema vlastni "contentType" pole - odvozujeme ho z puvodu (jp/kr/cn). internal kvuli testu. */
    internal fun contentTypeFromCountry(country: String): String = when (country) {
        "jp"  -> "MANGA"
        "kr"  -> "MANHWA"
        "cn"  -> "MANHUA"
        else  -> "MANGA"
    }

    /** Převede jeden objekt kapitoly na SChapter, nebo null pokud chybí hid. */
    private fun chapterFromJson(json: JSONObject, mangaUrl: String): SChapter? {
        val chHid = json.optString("hid").ifBlank { return null }
        val chap  = json.optString("chap", "0")
        // ComicK API vraci "vol"/"title" jako JSON null (ne jako chybejici klic) -
        // org.json.optString() na JSONObject.NULL vraci doslovny retezec "null",
        // proto je nutne nejdriv zkontrolovat isNull(), ne az .ifBlank {}.
        val vol   = if (json.isNull("vol")) null else json.optString("vol").ifBlank { null }
        val title = if (json.isNull("title")) null else json.optString("title").ifBlank { null }

        val chapterNum = chap.toFloatOrNull() ?: 0f
        val name = buildString {
            if (vol != null) append("Vol.$vol ")
            append("Ch.$chap")
            if (!title.isNullOrBlank()) append(" – $title")
        }

        val groups = parseGroups(json)

        return SChapter(
            sourceId        = id,
            mangaUrl        = mangaUrl,
            url             = "$apiBase/chapter/$chHid",
            name            = name,
            chapterNumber   = chapterNum,
            dateUpload      = parseIso(json.optString("created_at")),
            volume          = vol,
            scanlationGroup = groups.joinToString(", ") { it.name }.ifBlank { null },
            groups          = groups,
        )
    }

    /**
     * "group_name" je autoritativní seznam jmen/pořadí skupin u kapitoly.
     * "md_chapters_groups" ho jen doplňuje o slug a hezčí zobrazovací jméno, ale
     * ověřeno živě na API: může být kratší, nebo úplně `[]`, i když group_name
     * prázdné není (např. smazaná/anonymizovaná skupina u starší kapitoly).
     * Párujeme podle indexu; chybějící index = jen jméno z group_name bez slugu.
     *
     * Stejný bug jako u vol/title v [chapterFromJson]: "group_name[i]" i
     * "md_groups.title"/"md_groups.slug" umí ComicK vracet jako JSON null, proto
     * isNull() guard před optString() i tady. internal kvůli testu.
     */
    internal fun parseGroups(json: JSONObject): List<SGroup> {
        val names = json.optJSONArray("group_name") ?: return emptyList()
        val mdGroups = json.optJSONArray("md_chapters_groups")
        return (0 until names.length()).map { i ->
            val rawName = if (names.isNull(i)) "" else names.optString(i)
            val mdGroup = mdGroups?.optJSONObject(i)?.optJSONObject("md_groups")
            SGroup(
                name = mdGroup?.takeIf { !it.isNull("title") }?.optString("title")?.ifBlank { null } ?: rawName,
                slug = mdGroup?.takeIf { !it.isNull("slug") }?.optString("slug")?.ifBlank { null },
            )
        }
    }

    private fun parseIso(iso: String): Long = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
