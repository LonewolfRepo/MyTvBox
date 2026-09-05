package com.itv.blockbuster.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.components.HomeRow
import com.itv.blockbuster.util.CategorySortHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodBrowserState(
    val isLoading: Boolean = false,
    val categories: List<PortalCategory> = emptyList(),
    val selectedCategory: PortalCategory? = null,
    val searchQuery: String = "",
    val rows: List<HomeRow> = emptyList()
)

@HiltViewModel
class VodBrowserViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val settings: SettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager
) : ViewModel() {

    private var _contentType: String = ""
    val contentType: String get() = _contentType

    private val _state = MutableStateFlow(VodBrowserState())
    val state: StateFlow<VodBrowserState> = _state.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, PlaybackProgressEntity>>(emptyMap())
    val progressMap: StateFlow<Map<String, PlaybackProgressEntity>> = _progressMap.asStateFlow()

    // Vertical Pagination State
    private val _allCategories = MutableStateFlow<List<PortalCategory>>(emptyList())
    private val _visibleCategories = MutableStateFlow<List<PortalCategory>>(emptyList())
    private val _hasMoreCategories = MutableStateFlow(true)
    private val _isLoadingMoreCategories = MutableStateFlow(false)

    val hasMoreCategories: StateFlow<Boolean> = _hasMoreCategories.asStateFlow()

    private var isInitialized = false

    fun initialize(type: String) {
        if (isInitialized && _contentType == type) return
        _contentType = type
        isInitialized = true
        observeFavorites()
        observeProgress()
        viewModelScope.launch {
            try {
                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                loadInitialData(profileId, serverId)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                vodRepository.getFavorites(p, s, if (_contentType == "series") "SERIES" else "VOD")
            }.collect { favs ->
                _favoriteIds.value = favs.map { it.itemId }.toSet()
            }
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                vodRepository.getRecentProgress(p, s)
            }.collect { list ->
                _progressMap.value = list.associateBy { it.movieId }
            }
        }
    }

    fun toggleFavorite(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = sessionManager.activePortal.value?.serverId ?: 0
            vodRepository.toggleFavorite(p, s, item, if (_contentType == "series") "SERIES" else "VOD")
        }
    }

    private suspend fun loadInitialData(profileId: Int, serverId: Int) {
        _state.update { it.copy(isLoading = true) }
        try {
            val masterCats = vodRepository.getCategories().getOrDefault(emptyList())
            val orderKey = if (_contentType == "series") "order_series" else "order_vod"
            val rawOrder = settings.getString(profileId, serverId, orderKey, "")
            val ordered = CategorySortHelper.applyToCategories(masterCats, rawOrder)
            val filteredOrdered = ordered.filter { it.id != "*" && it.id != "0" }

            _allCategories.value = filteredOrdered
            val initialBatch = filteredOrdered.take(5)
            _visibleCategories.value = initialBatch
            _hasMoreCategories.value = filteredOrdered.size > 5

            val defaultCat = ordered.firstOrNull { it.id == "*" || it.id == "0" } ?: ordered.firstOrNull()
            _state.update { it.copy(categories = ordered, selectedCategory = defaultCat) }

            if (defaultCat != null && (defaultCat.id == "*" || defaultCat.id == "0")) {
                loadInitialRows(initialBatch)
            } else if (defaultCat != null) {
                loadContent(defaultCat)
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadInitialRows(cats: List<PortalCategory>) {
        val rows = coroutineScope {
            cats.map { cat ->
                async(Dispatchers.IO) {
                    val page = vodRepository.getList(_contentType, cat.id, 1, 14)
                        .getOrDefault(PortalPage(emptyList(), 0))
                    HomeRow(cat.id, cat.title, page.items, currentPage = 1, hasMore = page.items.size >= 14)
                }
            }.awaitAll().filter { it.items.isNotEmpty() }
        }
        _state.update { it.copy(isLoading = false, rows = rows) }
    }

    fun selectCategory(category: PortalCategory) {
        _state.update { it.copy(selectedCategory = category, searchQuery = "") }
        viewModelScope.launch { loadContent(category) }
    }

    fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val cat = _state.value.selectedCategory ?: return@launch
            loadContent(cat, query)
        }
    }

    private suspend fun loadContent(category: PortalCategory, search: String = "") {
        _state.update { it.copy(isLoading = true) }
        try {
            if (search.isNotBlank()) {
                val result = vodRepository.search(_contentType, search, category.id, 1)
                val page = result.getOrDefault(PortalPage(emptyList(), 0))
                _state.update {
                    it.copy(isLoading = false, rows = listOf(HomeRow("search", "Search Results", page.items, hasMore = false)))
                }
                return
            }
            val catsToLoad = if (category.id == "*" || category.id == "0") {
                _visibleCategories.value
            } else {
                listOf(category)
            }
            val rows = coroutineScope {
                catsToLoad.map { cat ->
                    async(Dispatchers.IO) {
                        val page = vodRepository.getList(_contentType, cat.id, 1, 14)
                            .getOrDefault(PortalPage(emptyList(), 0))
                        HomeRow(cat.id, cat.title, page.items, currentPage = 1, hasMore = page.items.size >= 14)
                    }
                }.awaitAll().filter { it.items.isNotEmpty() }
            }
            _state.update { it.copy(isLoading = false, rows = rows) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun loadMoreCategories() {
        if (_isLoadingMoreCategories.value || !_hasMoreCategories.value) return
        viewModelScope.launch {
            _isLoadingMoreCategories.value = true
            val currentSize = _visibleCategories.value.size
            val nextBatch = _allCategories.value.drop(currentSize).take(5)

            val newRows = coroutineScope {
                nextBatch.map { cat ->
                    async(Dispatchers.IO) {
                        val page = vodRepository.getList(_contentType, cat.id, 1, 14)
                            .getOrDefault(PortalPage(emptyList(), 0))
                        HomeRow(cat.id, cat.title, page.items, currentPage = 1, hasMore = page.items.size >= 14)
                    }
                }.awaitAll().filter { it.items.isNotEmpty() }
            }

            _visibleCategories.value = _visibleCategories.value + nextBatch
            _hasMoreCategories.value = _visibleCategories.value.size < _allCategories.value.size
            _state.update { it.copy(rows = it.rows + newRows) }
            _isLoadingMoreCategories.value = false
        }
    }

    fun loadMoreRowItems(rowId: String) {
        val currentRow = _state.value.rows.find { it.id == rowId } ?: return
        if (currentRow.isLoadingPage || !currentRow.hasMore) return

        viewModelScope.launch {
            _state.update { state ->
                state.copy(rows = state.rows.map { if (it.id == rowId) it.copy(isLoadingPage = true) else it })
            }

            val nextPage = currentRow.currentPage + 1
            val page = vodRepository.getList(_contentType, rowId, nextPage, 14)
                .getOrDefault(PortalPage(emptyList(), 0))

            _state.update { state ->
                state.copy(rows = state.rows.map {
                    if (it.id == rowId) {
                        it.copy(
                            items = it.items + page.items,
                            currentPage = nextPage,
                            hasMore = page.items.size >= 14,
                            isLoadingPage = false
                        )
                    } else it
                })
            }
        }
    }
}