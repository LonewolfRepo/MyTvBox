package com.itv.blockbuster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.repository.StalkerPortalService
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.AdultSessionManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.domain.model.Server
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionError: String? = null,
    val isLoading: Boolean = false,
    val activeServerName: String = "",
    val hero: PortalVodItem? = null,
    val rows: List<HomeRow> = emptyList(),
    val categories: List<PortalCategory> = emptyList(),
    val selectedCategory: PortalCategory? = null,
    val genres: List<PortalCategory> = emptyList(),
    val selectedGenre: PortalCategory? = null,
    // NEW: active search query (combined with genre + category filters)
    val searchQuery: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionRepository: ConnectionRepository,
    private val portalService: StalkerPortalService,
    private val vodRepository: VodRepository,
    private val sessionManager: StalkerSessionManager,
    private val prefs: UserPreferencesRepository,
    private val settings: SettingsRepository,
    private val adultSessionManager: AdultSessionManager
) : ViewModel() {

    companion object {
        // TV typing is slow (remote / sparse keyboard): wait 800ms after the last
        // keystroke before hitting the portal. Clearing the field bypasses this.
        const val SEARCH_DEBOUNCE_MS = 800L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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

    // Signature of the "order_home" setting used by the last successful load.
    // Used to detect Settings changes when the user navigates back to Home.
    private var lastAppliedHomeOrder: String? = null

    // NEW: pending debounced search; cancelled on every new keystroke
    private var searchJob: Job? = null

    /**
     * FIX: Dynamically filter items based on Adult Mode.
     * An item counts as "adult" if EITHER the item itself is flagged censored
     * OR the category it belongs to is flagged censored (some portals only
     * mark the category, some only mark individual items, some mark both).
     * In Adult Mode, ONLY show adult items. In Normal Mode, hide them.
     */
    private fun List<PortalVodItem>.visible(): List<PortalVodItem> {
        val isAdult = adultSessionManager.isAdultMode.value
        return filter { item ->
            val isAdultItem = item.isCensored || adultSessionManager.isCategoryCensored(item.categoryId)
            isAdultItem == isAdult
        }
    }

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                combine(
                    vodRepository.getFavorites(p, s, "VOD"),
                    vodRepository.getFavorites(p, s, "SERIES")
                ) { a, b -> (a + b).map { it.itemId }.toSet() }
            }.collect { _favoriteIds.value = it }
        }
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                vodRepository.getRecentProgress(p, s)
            }.collect { list ->
                _progressMap.value = list.associateBy { it.movieId }
            }
        }
        viewModelScope.launch {
            serverRepository.getActiveServer().collect { server ->
                if (server == null) {
                    _uiState.update {
                        it.copy(
                            connectionError = "No portal configured. Add a portal to start watching",
                            isConnecting = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(activeServerName = server.name, connectionError = null) }
                    connectAndLoad(server)
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val server = serverRepository.getActiveServer().firstOrNull() ?: return@launch
            connectAndLoad(server)
        }
    }

    fun toggleFavorite(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.firstOrNull() ?: return@launch
            val s = sessionManager.activePortal.value?.serverId ?: 0
            vodRepository.toggleFavorite(p, s, item, if (item.isSeries) "SERIES" else "VOD")
        }
    }

    /**
     * Called from HomeScreen ON_RESUME. Reloads Home only when the
     * "Home Categories" sort/visibility setting changed since the last load.
     */
    fun reloadIfHomeOrderChanged() {
        viewModelScope.launch {
            val applied = lastAppliedHomeOrder ?: return@launch
            if (!_uiState.value.isConnected) return@launch
            val p = prefs.activeProfileIdFlow.firstOrNull() ?: return@launch
            val s = sessionManager.activePortal.value?.serverId ?: 0
            val rawOrder = settings.getString(p, s, "order_home", "")
            if (rawOrder != applied) loadHome()
        }
    }

    private fun connectAndLoad(server: Server) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            val needsConnect = sessionManager.ajaxLoader.value.isEmpty() ||
                    sessionManager.activePortal.value?.serverId != server.id
            if (needsConnect) {
                val result = connectionRepository.connectToServer(server)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            connectionError = result.exceptionOrNull()?.message ?: "Connection failed"
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(isConnecting = false, isConnected = true) }
            loadHome()
        }
    }

    private suspend fun loadHome() {
        _uiState.update { it.copy(isLoading = true) }
        searchJob?.cancel()
        val p = prefs.activeProfileIdFlow.firstOrNull() ?: -1
        val s = sessionManager.activePortal.value?.serverId ?: 0

        // Read the Home category order/visibility setting and remember its signature
        val rawOrder = settings.getString(p, s, "order_home", "")
        lastAppliedHomeOrder = rawOrder

        val categories = portalService.fetchVodCategories().getOrDefault(emptyList())
        val genres = portalService.fetchVodGenres().getOrDefault(emptyList())


        // Respect Home Category Settings:
        //  - only categories marked visible are kept
        //  - kept categories stay in the exact configured order
        // NEW: Invert filter for Adult mode
        // FIX: Push censored category IDs to global registry

        // Push censored category IDs to global registry for Player/Detail bypass logic
        val isAdult = adultSessionManager.isAdultMode.value
        val censoredIds = categories.filter { it.isCensored }.map { it.id }.toSet()
        adultSessionManager.updateCensoredCategories(censoredIds)

        // FIX: Invert filter for Adult mode. Show ONLY censored in Adult mode.
        // NEW: A category can hold adult content two ways: the category itself is
        // flagged censored, OR it's a normal category that has some individually
        // censored items mixed in. The row-building step (via .visible()) already
        // knows how to pick out the right items for each mode, so here we must not
        // exclude "not category-level-censored" categories in Adult mode - otherwise
        // any adult item living inside an otherwise-normal category never gets a
        // chance to surface as a row. We only need to exclude the opposite case:
        // fully category-level-censored categories don't belong in Normal mode.
        val filteredCategories = if (isAdult) categories else categories.filter { !it.isCensored }
        val filteredGenres = genres.filter { it.isCensored == isAdult }

        val orderedVisible = CategorySortHelper.applyToCategories(filteredCategories, rawOrder)
            .filter { it.id != "*" && it.id != "0" }

        _allCategories.value = orderedVisible
        val initialBatch = orderedVisible.take(5)
        _visibleCategories.value = initialBatch
        _hasMoreCategories.value = orderedVisible.size > 5

        val allCat = PortalCategory(id = "*", title = if (isAdult) "All Adult" else "All Categories", alias = "all", isCensored = isAdult)
        val categoriesWithAll = listOf(allCat) + orderedVisible

        val allGenre = PortalCategory(id = "*", title = if (isAdult) "All Adult" else "All Genres", alias = "all", isCensored = isAdult)
        val genresWithAll = listOf(allGenre) + filteredGenres

        _uiState.update {
            it.copy(
                categories = categoriesWithAll,
                selectedCategory = allCat,
                genres = genresWithAll,
                selectedGenre = allGenre,
                searchQuery = ""
            )
        }
        loadDefaultHomeRows()
    }

    fun selectCategory(category: PortalCategory) {
        // Category change clears the search and reloads normally
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
        searchJob?.cancel()
        viewModelScope.launch {
            if (category.id == "*" || category.id == "0") {
                loadDefaultHomeRows()
            } else {
                loadCategoryContent(category)
            }
        }
    }

    fun selectGenre(genre: PortalCategory) {
        _uiState.update { it.copy(selectedGenre = genre) }
        viewModelScope.launch {
            // Genre change while a search is active re-runs the search with the new genre
            val q = _uiState.value.searchQuery
            if (q.isNotBlank()) {
                loadSearchResults(q)
                return@launch
            }
            val selectedCat = _uiState.value.selectedCategory
            if (selectedCat?.id == "*" || selectedCat?.id == "0") {
                loadDefaultHomeRows()
            } else if (selectedCat != null) {
                loadCategoryContent(selectedCat)
            }
        }
    }

    /**
     * NEW: debounced search entry point used by the top-bar search field.
     * Blank query restores the normal home rows immediately (no debounce).
     */
    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                reloadCurrentView()
                return@launch
            }
            delay(SEARCH_DEBOUNCE_MS)
            loadSearchResults(query)
        }
    }

    private suspend fun reloadCurrentView() {
        val selectedCat = _uiState.value.selectedCategory
        if (selectedCat == null || selectedCat.id == "*" || selectedCat.id == "0") {
            loadDefaultHomeRows()
        } else {
            loadCategoryContent(selectedCat)
        }
    }

    /**
     * NEW: get_ordered_list&type=vod with &search={q} combined with the active
     * category and genre filters. Results replace the carousel rows with a
     * single "Search Results" row (mixed movies + series, like Home rows).
     * FIX: pagination now continues page-by-page until a page returns 0 rows.
     * FIX: Censored items are stripped from the visible results.
     */
    private suspend fun loadSearchResults(query: String) {
        _uiState.update { it.copy(isLoading = true, hero = null) }
        val genreId = _uiState.value.selectedGenre?.id ?: ""
        val categoryId = _uiState.value.selectedCategory?.id ?: "*"
        val page = portalService.fetchVodSearch(query, categoryId, 1, genreId)
            .getOrDefault(PortalPage(emptyList(), 0))
        val visibleItems = page.items.visible()
        val rows = if (page.items.isNotEmpty()) {
            listOf(
                HomeRow(
                    id = "search",
                    title = "Search Results",
                    items = visibleItems,
                    currentPage = 1,
                    // Keep paginating until a subsequent page returns 0 rows
                    hasMore = true
                )
            )
        } else {
            emptyList()
        }
        _uiState.update { it.copy(isLoading = false, rows = rows) }
    }

    private suspend fun loadDefaultHomeRows() {
        _uiState.update { it.copy(isLoading = true) }
        val genreId = _uiState.value.selectedGenre?.id ?: ""
        val recentPage = portalService.fetchVodList(categoryId = "*", page = 1, pageSize = 15, genreId = genreId)
            .getOrDefault(PortalPage(emptyList(), 0))
        val visibleRecent = recentPage.items.visible()
        val categoryRows = coroutineScope {
            _visibleCategories.value.map { category ->
                async(Dispatchers.IO) {
                    val page = portalService.fetchVodList(category.id, 1, 14, genreId)
                        .getOrDefault(PortalPage(emptyList(), 0))
                    HomeRow(
                        id = category.id,
                        title = category.title,
                        items = page.items.visible(),
                        currentPage = 1,
                        hasMore = page.items.size >= 14
                    )
                }
            }.awaitAll().filter { it.items.isNotEmpty() }
        }
        val allRows = buildList {
            //if (recentPage.items.isNotEmpty()) add(HomeRow("recently_added", "Recently Added", recentPage.items, hasMore = false))
            // FIX: "Recently Added" is no longer capped. It now carries real pagination
            // state so the row keeps loading horizontally until the portal's full
            // recent list is exhausted (limit removed).
            if (visibleRecent.isNotEmpty()) {
                add(
                    HomeRow(
                        id = "*",
                        title = "Recently Added",
                        items = visibleRecent,
                        currentPage = 1,
                        hasMore = recentPage.items.isNotEmpty()
                    )
                )
            }
            addAll(categoryRows)
        }
        _uiState.update { it.copy(isLoading = false, hero = visibleRecent.firstOrNull(), rows = allRows) }
    }

    private suspend fun loadCategoryContent(category: PortalCategory) {
        _uiState.update { it.copy(isLoading = true, hero = null) }
        _hasMoreCategories.value = false
        val genreId = _uiState.value.selectedGenre?.id ?: ""
        val page = portalService.fetchVodList(category.id, 1, 14, genreId)
            .getOrDefault(PortalPage(emptyList(), 0))
        val row = HomeRow(
            id = category.id,
            title = category.title,
            items = page.items.visible(),
            currentPage = 1,
            hasMore = page.items.size >= 14
        )
        _uiState.update { it.copy(isLoading = false, rows = listOf(row)) }
    }

    /**
     * FIX: Keep consuming category batches until at least one non-empty row is
     * produced OR the category list is exhausted. With an active genre filter many
     * categories return zero items; without this loop a single empty batch would
     * leave the bottom spinner spinning forever.
     */
    fun loadMoreCategories() {
        if (_isLoadingMoreCategories.value || !_hasMoreCategories.value) return
        val selectedCat = _uiState.value.selectedCategory
        if (selectedCat == null || (selectedCat.id != "*" && selectedCat.id != "0")) return
        // NEW: never vertically paginate while a search is active
        if (_uiState.value.searchQuery.isNotBlank()) return
        viewModelScope.launch {
            _isLoadingMoreCategories.value = true
            val genreId = _uiState.value.selectedGenre?.id ?: ""
            while (_hasMoreCategories.value) {
                val currentSize = _visibleCategories.value.size
                val nextBatch = _allCategories.value.drop(currentSize).take(5)
                if (nextBatch.isEmpty()) {
                    _hasMoreCategories.value = false
                    break
                }
                val newRows = coroutineScope {
                    nextBatch.map { category ->
                        async(Dispatchers.IO) {
                            val page = portalService.fetchVodList(category.id, 1, 14, genreId)
                                .getOrDefault(PortalPage(emptyList(), 0))
                            HomeRow(
                                id = category.id,
                                title = category.title,
                                items = page.items.visible(),
                                currentPage = 1,
                                hasMore = page.items.size >= 14
                            )
                        }
                    }.awaitAll().filter { it.items.isNotEmpty() }
                }
                _visibleCategories.value = _visibleCategories.value + nextBatch
                _hasMoreCategories.value = _visibleCategories.value.size < _allCategories.value.size
                if (newRows.isNotEmpty()) {
                    _uiState.update { it.copy(rows = it.rows + newRows) }
                    break
                }
            }
            _isLoadingMoreCategories.value = false
        }
    }

    fun loadMoreRowItems(rowId: String) {
        val currentRow = _uiState.value.rows.find { it.id == rowId } ?: return
        if (currentRow.isLoadingPage || !currentRow.hasMore) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(rows = state.rows.map { if (it.id == rowId) it.copy(isLoadingPage = true) else it })
            }
            val nextPage = currentRow.currentPage + 1
            val genreId = _uiState.value.selectedGenre?.id ?: ""

            // FIX: the "search" row must re-run the SEARCH request for the next page
            // (not fetchVodList with category id "search").
            val page = if (rowId == "search") {
                val query = _uiState.value.searchQuery
                val categoryId = _uiState.value.selectedCategory?.id ?: "*"
                portalService.fetchVodSearch(query, categoryId, nextPage, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            } else {
                portalService.fetchVodList(rowId, nextPage, 14, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            }
            _uiState.update { state ->
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
