package com.haise.jiyu.ui.comickhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haise.jiyu.data.repository.MangaRepository
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.comick.ChapterUpdate
import com.haise.jiyu.source.comick.ComicKSource
import com.haise.jiyu.source.comick.TopFeed
import com.haise.jiyu.util.report
import com.haise.jiyu.util.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ComicK domovská obrazovka - 5 sekcí z /top + Aktualizace (chapter feed z
 * /chapter) jako poslední, nekonečně scrollovatelná sekce v témže seznamu.
 * Dřív měla vlastní záložku ("Aktualizace" nahoře vedle "Domů") - zrušeno,
 * protože feed je teď celý rovnou tady (uživatelský požadavek); to místo
 * v horní liště zabralo tlačítko "Procházet" (viz [ComicKHomeScreen]).
 */
@HiltViewModel
class ComicKHomeViewModel @Inject constructor(
    private val comicKSource: ComicKSource,
    private val repository: MangaRepository,
) : ViewModel() {

    private val _topFeed = MutableStateFlow<TopFeed?>(null)
    val topFeed: StateFlow<TopFeed?> = _topFeed.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _updatesError = MutableStateFlow<String?>(null)
    val updatesError: StateFlow<String?> = _updatesError.asStateFlow()

    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    private val _popularNewWindow = MutableStateFlow("7")
    val popularNewWindow: StateFlow<String> = _popularNewWindow.asStateFlow()

    private val _mostRecentPopularWindow = MutableStateFlow("7")
    val mostRecentPopularWindow: StateFlow<String> = _mostRecentPopularWindow.asStateFlow()

    private val _updatesOrder = MutableStateFlow("hot")
    val updatesOrder: StateFlow<String> = _updatesOrder.asStateFlow()

    private val _updates = MutableStateFlow<List<ChapterUpdate>>(emptyList())
    val updates: StateFlow<List<ChapterUpdate>> = _updates.asStateFlow()

    private val _updatesLoading = MutableStateFlow(false)
    val updatesLoading: StateFlow<Boolean> = _updatesLoading.asStateFlow()

    private var updatesPage = 1

    private val _openingManga = MutableStateFlow<SManga?>(null)
    val openingManga: StateFlow<SManga?> = _openingManga.asStateFlow()

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    init {
        loadTop()
        // Nacte se hned, ne az pri prepnuti na zalozku Aktualizace - Domu ted
        // ukazuje kratky nahled Hot/New aktualizaci rovnou na sobe (uzivatelsky
        // pozadavek, "stylem jak to ma comic").
        loadUpdatesFirstPage()
    }

    fun retry() {
        loadTop()
        if (_updates.value.isEmpty()) loadUpdatesFirstPage()
    }

    /** Retry jen pro Aktualizace feed (dole na Domů) - nemusi znovu tahat /top. */
    fun retryUpdates() = loadUpdatesFirstPage()

    private fun loadTop() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _topFeed.value = comicKSource.getTop()
            } catch (e: Exception) {
                e.report("comickhome:getTop")
                _error.value = e.toFriendlyMessage()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setShowCompleted(completed: Boolean) { _showCompleted.value = completed }
    fun setPopularNewWindow(window: String) { _popularNewWindow.value = window }
    fun setMostRecentPopularWindow(window: String) { _mostRecentPopularWindow.value = window }

    fun setUpdatesOrder(order: String) {
        _updatesOrder.value = order
        loadUpdatesFirstPage()
    }

    private fun loadUpdatesFirstPage() {
        updatesPage = 1
        _updates.value = emptyList()
        _updatesError.value = null
        loadMoreUpdates()
    }

    fun loadMoreUpdates() {
        if (_updatesLoading.value) return
        viewModelScope.launch {
            _updatesLoading.value = true
            try {
                val page = comicKSource.getUpdates(_updatesOrder.value, updatesPage)
                _updates.value = (_updates.value + page).distinctBy { it.chapter.sourceId + it.chapter.url }
                if (page.isNotEmpty()) updatesPage++
            } catch (e: Exception) {
                e.report("comickhome:getUpdates")
                if (_updates.value.isEmpty()) _updatesError.value = e.toFriendlyMessage()
            } finally {
                _updatesLoading.value = false
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
                e.report("comickhome:openManga")
                _openError.value = e.toFriendlyMessage()
            } finally {
                _openingManga.value = null
            }
        }
    }

    fun clearOpenError() { _openError.value = null }
}
