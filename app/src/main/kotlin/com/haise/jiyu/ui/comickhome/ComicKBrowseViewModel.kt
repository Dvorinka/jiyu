package com.haise.jiyu.ui.comickhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ComicKGenreOption
import com.haise.jiyu.source.comick.ComicKSearchFilters
import com.haise.jiyu.source.comick.ComicKSource
import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ComicK "Procházet" s plnou sadou filtrů (žánry/tagy/demografie/typ/status/
 * content rating/min. kapitol/rok) - viz [ComicKSource.searchAdvanced], kde je
 * seznam ověřených parametrů. Znovupoužívá [ComicKSectionViewModel]'s openManga
 * vzor pro otevírání titulu do knihovny/detailu.
 */
@HiltViewModel
class ComicKBrowseViewModel @Inject constructor(
    private val comicKSource: ComicKSource,
    private val repository: MangaRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** Skryje Suggestive/Erotica možnosti ve filtru content ratingu, když má appka vypnuté 18+ zdroje. */
    val showAdultContent: StateFlow<Boolean> = settings.showAdultSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(ComicKSearchFilters())
    val filters: StateFlow<ComicKSearchFilters> = _filters.asStateFlow()

    private val _genreOptions = MutableStateFlow<List<ComicKGenreOption>>(emptyList())
    val genreOptions: StateFlow<List<ComicKGenreOption>> = _genreOptions.asStateFlow()

    private val _results = MutableStateFlow<List<SManga>>(emptyList())
    val results: StateFlow<List<SManga>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openingManga = MutableStateFlow<SManga?>(null)
    val openingManga: StateFlow<SManga?> = _openingManga.asStateFlow()

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    private var page = 1

    init {
        loadFirstPage()
        viewModelScope.launch {
            try {
                _genreOptions.value = comicKSource.getGenreList()
            } catch (e: Exception) {
                e.report("comickbrowse:getGenreList")
            }
        }
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun search() = loadFirstPage()

    fun applyFilters(newFilters: ComicKSearchFilters) {
        _filters.value = newFilters
        loadFirstPage()
    }

    fun clearFilters() {
        _filters.value = ComicKSearchFilters(sortBy = _filters.value.sortBy)
        loadFirstPage()
    }

    private fun loadFirstPage() {
        page = 1
        _results.value = emptyList()
        _error.value = null
        loadMore()
    }

    fun loadMore() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val f = _filters.value.copy(query = _query.value)
                val next = comicKSource.searchAdvanced(page, f)
                _results.value = (_results.value + next).distinctBy { it.sourceId + it.url }
                if (next.isNotEmpty()) page++
            } catch (e: Exception) {
                e.report("comickbrowse:searchAdvanced")
                if (_results.value.isEmpty()) _error.value = e.toFriendlyMessage()
            } finally {
                _loading.value = false
            }
        }
    }

    fun openManga(manga: SManga, onOpened: (String) -> Unit) {
        if (_openingManga.value != null) return
        _openingManga.value = manga
        viewModelScope.launch {
            try {
                val id = repository.openPreview(manga)
                onOpened(id)
            } catch (e: Exception) {
                e.report("comickbrowse:openManga")
                _openError.value = e.toFriendlyMessage()
            } finally {
                _openingManga.value = null
            }
        }
    }

    fun clearOpenError() { _openError.value = null }
}
