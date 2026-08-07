package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.SourceManager
import com.haise.jiyu.util.normalizeMangaTitle
import com.haise.jiyu.util.report
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Jeden nalezený reálný zdroj, který ComicK titul také má. */
data class ResolvedCandidate(
    val source: MangaSource,
    val manga: SManga,
    val matchedChapterCount: Int,
    val hasRequestedChapter: Boolean,
    val isFavorite: Boolean,
)

/**
 * Křížové vyhledání skutečného, čitelného zdroje pro ComicK titul (ComicK sám
 * jen katalogizuje, reálné stránky kapitol nikdy neposkytuje - viz design doc
 * "Sub-projekt 3"). Zužuje kandidáty podle typu obsahu, hledá živě paralelně,
 * porovnává normalizovaný název a cachuje výsledek na úrovni titulu (jen
 * v paměti, po dobu běhu appky - viz design doc "Cache rozsah").
 */
@Singleton
class ComicKChapterResolver @Inject constructor(
    private val sourceManager: SourceManager,
    private val settings: SettingsRepository,
) {
    private data class CachedCandidate(val source: MangaSource, val manga: SManga, val chapters: List<SChapter>)

    private val cache = java.util.Collections.synchronizedMap(mutableMapOf<String, List<CachedCandidate>>())

    /**
     * @param comicKMangaId klíč pro cache (Room id ComicK manga entity)
     * @param requestedChapterNumber null = zajímá nás jen "existuje vůbec zdroj", jinak
     *   se navíc spočítá [ResolvedCandidate.hasRequestedChapter] pro tohle konkrétní číslo.
     */
    suspend fun findCandidates(
        comicKMangaId: String,
        comicKTitle: String,
        comicKContentType: String,
        requestedChapterNumber: Float?,
    ): List<ResolvedCandidate> {
        val cached = cache[comicKMangaId]
        val found = cached ?: searchAndFetch(comicKTitle, comicKContentType).also { result ->
            if (result.isNotEmpty()) cache[comicKMangaId] = result
        }
        val favorites = settings.favoriteSourceIds.first()
        return found.map { c ->
            ResolvedCandidate(
                source = c.source,
                manga = c.manga,
                matchedChapterCount = c.chapters.size,
                hasRequestedChapter = requestedChapterNumber == null ||
                    c.chapters.any { abs(it.chapterNumber - requestedChapterNumber) < 0.01f },
                isFavorite = c.source.id in favorites,
            )
        }.sortedWith(compareByDescending<ResolvedCandidate> { it.isFavorite }.thenByDescending { it.matchedChapterCount })
    }

    private suspend fun searchAndFetch(comicKTitle: String, comicKContentType: String): List<CachedCandidate> =
        coroutineScope {
            val semaphore = Semaphore(5)
            val normalizedTarget = normalizeMangaTitle(comicKTitle)
            sourceManager.getAll()
                .filter { it.id != "comick" && isSameContentGroup(it.contentType, comicKContentType) }
                .map { source ->
                    async {
                        semaphore.withPermit {
                            try {
                                withTimeoutOrNull(8_000) {
                                    val results = source.search(comicKTitle, 1, MangaFilter())
                                    val match = results.firstOrNull { normalizeMangaTitle(it.title) == normalizedTarget }
                                    match?.let { m -> CachedCandidate(source, m, source.getChapterList(m)) }
                                }
                            } catch (e: Exception) {
                                e.report("comick:resolver:${source.id}")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
        }

    /**
     * MANGA/MANHWA/MANHUA se pro účely hledání zdroje berou jako jedna skupina (region
     * asijského komiksu) - stejná konvence jako `BrowseViewModel.MANGA_GROUP` pro
     * Procházet, protože spousta zdrojů má title-level typ smíchaný a jen jeden
     * "výchozí" contentType na úrovni celého zdroje. Novely a americké komiksy se
     * nikdy neprohledávají u ComicK titulu (ComicK sám je jen manga/manhwa/manhua tracker).
     */
    private fun isSameContentGroup(sourceType: String, targetType: String): Boolean {
        val asianComicTypes = setOf("MANGA", "MANHWA", "MANHUA")
        return if (targetType in asianComicTypes) sourceType in asianComicTypes else sourceType == targetType
    }
}
