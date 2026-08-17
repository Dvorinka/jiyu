package com.haise.jiyu.ui.resolver

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.R
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ComicKChapterResolver
import com.haise.jiyu.source.comick.ResolvedCandidate
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.floor

@HiltViewModel
class SourceResolverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resolver: ComicKChapterResolver,
    private val repository: MangaRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val chapterId: String = checkNotNull(savedStateHandle["chapterId"])
    val incognito: Boolean = savedStateHandle["incognito"] ?: false

    private var requestedChapterNumber: Float? = null

    // Jmeno/jmena prekladatelske skupiny prave te kapitoly, kterou uzivatel otevrel v seznamu
    // (napr. "Asura" u radku "Ch.5 Asura") - normalizovano (lowercase, jen alfanumericke znaky)
    // pro fuzzy porovnani se jmeny nasich zdroju (viz matchesPreferredGroup). ComicK umi u jedne
    // kapitoly vracet i vic skupin najednou, oddelene carkou (ChapterEntity.scanlationGroup).
    private var preferredGroupTokens: List<String> = emptyList()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _comicKTitle = MutableStateFlow("")
    val comicKTitle: StateFlow<String> = _comicKTitle.asStateFlow()

    private val _candidates = MutableStateFlow<List<ResolvedCandidate>>(emptyList())
    val candidates: StateFlow<List<ResolvedCandidate>> = _candidates.asStateFlow()

    // Zdroje se prohledavaji soubezne a kazdy nalezeny kandidat se do seznamu prida hned,
    // ne az uplne vsechny dohledaji - viz ComicKChapterResolver.findCandidatesFlow. Tenhle
    // flag rika, jestli se jeste na pozadi hleda dal (drobny "Hledam dalsi zdroje..." radek
    // pod uz nalezenymi kandidaty), nezavisle na _loading (ten je jen pro uplne prvni spinner,
    // nez prijde vubec prvni vysledek).
    private val _searchingMore = MutableStateFlow(false)
    val searchingMore: StateFlow<Boolean> = _searchingMore.asStateFlow()

    private val _totalComicKChapters = MutableStateFlow(0)
    val totalComicKChapters: StateFlow<Int> = _totalComicKChapters.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _openedChapterId = MutableStateFlow<String?>(null)
    val openedChapterId: StateFlow<String?> = _openedChapterId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    init {
        viewModelScope.launch {
            try {
                val chapter = repository.getChapter(chapterId)
                if (chapter == null) { _loading.value = false; return@launch }
                val manga = repository.getManga(chapter.mangaId)
                if (manga == null) { _loading.value = false; return@launch }
                _comicKTitle.value = manga.title
                requestedChapterNumber = chapter.chapterNumber
                preferredGroupTokens = (chapter.scanlationGroup ?: "").split(",")
                    .map { normalizeGroupToken(it) }
                    .filter { it.length >= 3 }
                // ComicK eviduje jednu kapitolu vícekrát, jednou za každou skupinu, co ji
                // přeložila - .size by tak počítal "3× Ch.890" jako 3, ne 1. floor() navíc
                // sjednocuje granularitu s ComicKChapterResolver.matchedChapterCount (některé
                // zdroje dělí jeden překlad na X, X.1, X.2 - bez floor() by šel poměr přes
                // 100 %, viz komentář tam).
                _totalComicKChapters.value = repository.getAllChapters(chapter.mangaId)
                    .map { floor(it.chapterNumber).toInt() }.distinct().size
                _searchingMore.value = true
                resolver.findCandidatesFlow(
                    comicKMangaId = manga.id,
                    comicKMangaUrl = manga.url,
                    comicKTitle = manga.title,
                    comicKContentType = manga.contentType,
                    requestedChapterNumber = chapter.chapterNumber,
                )
                    .onCompletion {
                        _searchingMore.value = false
                        // Trideni az na konci hledani, ne po kazdem prubeznem vysledku - uzivatelsky
                        // pozadavek: seznam by se jinak mohl prehazet pod prstem, kdyz uz si nekdo
                        // vybira, zatimco jeste hleda dal na pozadi.
                        //
                        // Priorita (vsechny urovne sestupne dulezite):
                        // 1. oblibeny zdroj
                        // 2. zdroj STEJNE prekladatelske skupiny, jako mela otevrena kapitola
                        //    (matchesPreferredGroup) - uzivatelsky pozadavek: kdyz napr. "Asura"
                        //    prekladala kapitolu, kterou chce cist, a appka Asuru mezi zdroji ma,
                        //    dat ji prednost pred jinym zdrojem, i kdyz ma o par kapitol vic
                        // 3. zdroj, ktery ma presne POZADOVANOU kapitolu
                        // 4. zdroj s nejuplnejsim pokrytim (nejvic kapitol celkem) - NENI to proste
                        //    "nejvic kapitol" samo o sobe (to by mohlo sahnout po zdroji, co uz davno
                        //    skoncil daleko pred cilem, nebo zacal az pozdeji), ale az po bodech 1-3
                        //    uz to jen odlisuje kompletni zdroj od neuplneho
                        // 5. nejmensi vzdalenost nejblizsi dostupne kapitoly od cile (kdyz ani jeden
                        //    kandidat pozadovanou kapitolu nema)
                        val sorted = _candidates.value.sortedWith(
                            compareByDescending<ResolvedCandidate> { it.isFavorite }
                                .thenByDescending { matchesPreferredGroup(it) }
                                .thenByDescending { it.hasRequestedChapter }
                                .thenByDescending { it.matchedChapterCount }
                                .thenBy { it.nearestChapterDistance ?: Float.MAX_VALUE }
                        )
                        _candidates.value = sorted
                        // Uzivatelsky pozadavek: appka ma vzdycky sama vybrat a rovnou otevrit
                        // nejvhodnejsi zdroj podle poradi vyse - rucni seznam (SourceResolverScreen)
                        // se tak realne ukaze jen na kratky okamzik pred prekrytim "resolving"
                        // overlayem, pripadne vubec, kdyz zadny kandidat nebyl nalezen (viz
                        // candidates.isEmpty() stav v SourceResolverScreen).
                        sorted.firstOrNull()?.let { selectCandidate(it) }
                    }
                    .collect { candidate ->
                        _candidates.value = _candidates.value + candidate
                        // Prvni vysledek uz staci na to prestat ukazovat celoobrazovkovy
                        // spinner - dal se hleda na pozadi, viz _searchingMore.
                        _loading.value = false
                    }
            } catch (e: Exception) {
                e.report("resolver:findCandidates")
            } finally {
                _loading.value = false
                _searchingMore.value = false
            }
        }
    }

    /** lowercase + jen alfanumericke znaky, aby "Asura Scans" a "Asura" vysly stejne. */
    private fun normalizeGroupToken(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Fuzzy shoda: normalizovane jmeno zdroje obsahuje normalizovany token skupiny nebo naopak
     * (delsi retezec obvykle obsahuje kratsi - "asurascans" obsahuje "asura", ne naopak). Kratke
     * tokeny (< 3 znaky) uz preferredGroupTokens vyfiltrovalo pri nastaveni, aby se predeslo
     * falesnym shodam u krakich jmen skupin.
     */
    private fun matchesPreferredGroup(candidate: ResolvedCandidate): Boolean {
        if (preferredGroupTokens.isEmpty()) return false
        val sourceName = normalizeGroupToken(candidate.source.name)
        return preferredGroupTokens.any { token -> sourceName.contains(token) || token.contains(sourceName) }
    }

    fun selectCandidate(candidate: ResolvedCandidate) {
        val target = requestedChapterNumber ?: return
        _resolving.value = true
        viewModelScope.launch {
            try {
                val mangaId = repository.openPreview(candidate.manga)
                val resolvedChapters = repository.getAllChapters(mangaId)
                val bestMatch = resolvedChapters.minByOrNull { abs(it.chapterNumber - target) }
                if (bestMatch == null) {
                    _error.value = appContext.getString(R.string.resolver_chapter_missing_after_select)
                } else {
                    _openedChapterId.value = bestMatch.id
                }
            } catch (e: Exception) {
                e.report("resolver:selectCandidate")
                _error.value = e.toFriendlyMessage()
            } finally {
                _resolving.value = false
            }
        }
    }
}
