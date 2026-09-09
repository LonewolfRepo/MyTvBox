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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val genres: List<PortalCategory> = emptyList(),
    val selectedGenre: PortalCategory? = null,
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

    companion object {
        // TV typing is slow (remote / sparse keyboard): wait 800ms after the last
        // keystroke before hitting the portal. Clearing the field bypasses this.
        const val SEARCH_DEBOUNCE_MS = 800L
    }

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

    // NEW: pending debounced search; cancelled on every new keystroke
    private var searchJob: Job? = null

    /**
     * NEW: Censored (adult) items are stripped HERE, at the Movies / TV Shows page
     * level, instead of in VodRepository. The repository stays raw so a dedicated
     * Censored page can reuse the same data later.
     */
    private fun List<PortalVodItem>.visible(): List<PortalVodItem> = filter { !it.isCensored }

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
            // Censored categories stay hidden on Movies / TV Shows (dedicated page later)
            val masterCats = vodRepository.getCategories().getOrDefault(emptyList())
                .filter { !it.isCensored }
            val masterGenres = vodRepository.getGenres().getOrDefault(emptyList())

            val orderKey = if (_contentType == "series") "order_series" else "order_vod"
            val rawOrder = settings.getString(profileId, serverId, orderKey, "")
            val ordered = CategorySortHelper.applyToCategories(masterCats, rawOrder)
            val filteredOrdered = ordered.filter { it.id != "*" && it.id != "0" }

            val uncensoredGenres = masterGenres.filter { !it.isCensored }

            _allCategories.value = filteredOrdered
            val initialBatch = filteredOrdered.take(5)
            _visibleCategories.value = initialBatch
            _hasMoreCategories.value = filteredOrdered.size > 5

            val defaultCat = ordered.firstOrNull { it.id == "*" || it.id == "0" } ?: ordered.firstOrNull()
            val allGenre = PortalCategory(id = "*", title = "All Genres", alias = "all", isCensored = false)
            val genresWithAll = listOf(allGenre) + uncensoredGenres

            _state.update {
                it.copy(
                    categories = ordered,
                    selectedCategory = defaultCat,
                    genres = genresWithAll,
                    selectedGenre = allGenre,
                    searchQuery = ""
                )
            }

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
        val genreId = _state.value.selectedGenre?.id ?: ""
        val rows = coroutineScope {
            cats.map { cat ->
                async(Dispatchers.IO) {
                    val page = vodRepository.getList(_contentType, cat.id, 1, 14, genreId)
                        .getOrDefault(PortalPage(emptyList(), 0))
                    HomeRow(cat.id, cat.title, page.items.visible(), currentPage = 1, hasMore = page.items.size >= 14)
                }
            }.awaitAll().filter { it.items.isNotEmpty() }
        }
        _state.update { it.copy(isLoading = false, rows = rows) }
    }

    fun selectCategory(category: PortalCategory) {
        // Category change clears the search and reloads normally
        _state.update { it.copy(selectedCategory = category, searchQuery = "") }
        searchJob?.cancel()
        if (category.id != "*" && category.id != "0") {
            _hasMoreCategories.value = false
        }
        viewModelScope.launch { loadContent(category) }
    }

    fun selectGenre(genre: PortalCategory) {
        _state.update { it.copy(selectedGenre = genre) }
        viewModelScope.launch {
            // Genre change while a search is active re-runs the search with the new genre
            val cat = _state.value.selectedCategory ?: return@launch
            loadContent(cat, _state.value.searchQuery)
        }
    }

    /**
     * NEW: debounced search entry point used by the top-bar search field.
     * Blank query restores the normal category/genre rows immediately (no debounce).
     */
    fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val cat = _state.value.selectedCategory ?: return@launch
            if (query.isBlank()) {
                loadContent(cat, "")
                return@launch
            }
            delay(SEARCH_DEBOUNCE_MS)
            loadContent(cat, query)
        }
    }

    // ── FIX 2: Derive isAllCategories and assign to _hasMoreCategories ──
    private suspend fun loadContent(category: PortalCategory, search: String = "") {
        _state.update { it.copy(isLoading = true) }
        try {
            val genreId = _state.value.selectedGenre?.id ?: ""
            if (search.isNotBlank()) {
                // get_ordered_list&type=vod with &search= + &category= + &genre=
                val result = vodRepository.search(_contentType, search, category.id, 1, genreId)
                val page = result.getOrDefault(PortalPage(emptyList(), 0))
                _hasMoreCategories.value = false
                // FIX: search row paginates page-by-page until a page returns 0 rows;
                // censored items are stripped from the visible results.
                _state.update {
                    it.copy(
                        isLoading = false,
                        rows = if (page.items.isNotEmpty()) {
                            listOf(
                                HomeRow(
                                    id = "search",
                                    title = "Search Results",
                                    items = page.items.visible(),
                                    currentPage = 1,
                                    hasMore = true
                                )
                            )
                        } else {
                            emptyList()
                        }
                    )
                }
                return
            }

            val isAllCategories = category.id == "*" || category.id == "0"
            _hasMoreCategories.value = isAllCategories

            val catsToLoad = if (isAllCategories) {
                _visibleCategories.value
            } else {
                listOf(category)
            }
            val rows = coroutineScope {
                catsToLoad.map { cat ->
                    async(Dispatchers.IO) {
                        val page = vodRepository.getList(_contentType, cat.id, 1, 14, genreId)
                            .getOrDefault(PortalPage(emptyList(), 0))
                        HomeRow(cat.id, cat.title, page.items.visible(), currentPage = 1, hasMore = page.items.size >= 14)
                    }
                }.awaitAll().filter { it.items.isNotEmpty() }
            }
            _state.update { it.copy(isLoading = false, rows = rows) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * FIX: Keep consuming category batches until at least one non-empty row is
     * produced OR the category list is exhausted. With an active genre filter many
     * categories return zero items; without this loop a single empty batch would
     * leave the bottom spinner spinning forever.
     */
    fun loadMoreCategories() {
        if (_isLoadingMoreCategories.value || !_hasMoreCategories.value) return
        val selectedCat = _state.value.selectedCategory
        if (selectedCat == null || (selectedCat.id != "*" && selectedCat.id != "0")) return
        // NEW: never vertically paginate while a search is active
        if (_state.value.searchQuery.isNotBlank()) return

        viewModelScope.launch {
            _isLoadingMoreCategories.value = true
            val genreId = _state.value.selectedGenre?.id ?: ""

            while (_hasMoreCategories.value) {
                val currentSize = _visibleCategories.value.size
                val nextBatch = _allCategories.value.drop(currentSize).take(5)
                if (nextBatch.isEmpty()) {
                    _hasMoreCategories.value = false
                    break
                }
                val newRows = coroutineScope {
                    nextBatch.map { cat ->
                        async(Dispatchers.IO) {
                            val page = vodRepository.getList(_contentType, cat.id, 1, 14, genreId)
                                .getOrDefault(PortalPage(emptyList(), 0))
                            HomeRow(cat.id, cat.title, page.items.visible(), currentPage = 1, hasMore = page.items.size >= 14)
                        }
                    }.awaitAll().filter { it.items.isNotEmpty() }
                }
                _visibleCategories.value = _visibleCategories.value + nextBatch
                _hasMoreCategories.value = _visibleCategories.value.size < _allCategories.value.size
                if (newRows.isNotEmpty()) {
                    _state.update { it.copy(rows = it.rows + newRows) }
                    break
                }
            }
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
            val genreId = _state.value.selectedGenre?.id ?: ""

            // FIX: the "search" row must re-run the SEARCH request for the next page
            // (not getList with category id "search").
            val page = if (rowId == "search") {
                val query = _state.value.searchQuery
                val categoryId = _state.value.selectedCategory?.id ?: "*"
                vodRepository.search(_contentType, query, categoryId, nextPage, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            } else {
                vodRepository.getList(_contentType, rowId, nextPage, 14, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            }

            _state.update { state ->
                state.copy(rows = state.rows.map {
                    if (it.id == rowId) {
                        it.copy(
                            // Censored items stripped from appended pages as well
                            items = it.items + page.items.visible(),
                            currentPage = nextPage,
                            // FIX: search rows keep paginating until a page returns 0 rows;
                            // category rows stop on a partial page as before.
                            hasMore = if (rowId == "search") page.items.isNotEmpty() else page.items.size >= 14,
                            isLoadingPage = false
                        )
                    } else it
                })
            }
        }
    }
}
