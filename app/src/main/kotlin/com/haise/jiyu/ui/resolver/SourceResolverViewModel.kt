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
                    .onCompletion { _searchingMore.value = false }
                    .collect { candidate ->
                        _candidates.value = (_candidates.value + candidate)
                            .sortedWith(compareByDescending<ResolvedCandidate> { it.isFavorite }.thenByDescending { it.matchedChapterCount })
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
