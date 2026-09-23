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
    // FIX ("landing page starts on no items then goes to loading"): the
    // screen's loading overlay is gated on (isLoading || isConnecting) &&
    // rows.isEmpty() - with both defaulting false, the very first
    // composition (before init{}'s coroutine gets a dispatcher turn to
    // flip this) rendered with neither flag set and an empty row list, so
    // it fell through to whatever renders when nothing is loading - an
    // empty page - for a frame or two before the overlay appeared.
    // Defaulting isConnecting to true (matching the real first step of the
    // connect flow) means the loading overlay is already what's shown on
    // the very first frame.
    val isConnecting: Boolean = true,
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
        const val SEARCH_DEBOUNCE_MS = 3000L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // NEW: which "section" this ViewModel instance is serving - "home" (no
    // filter, mixed movies+series, the original behavior), "vod" (Movies -
    // items where isSeries is false), or "series" (TV Shows - isSeries
    // true). null means the screen hasn't called initialize() yet; the
    // connection/load flow below waits for it before doing anything, so a
    // Movies/TV Shows screen can never briefly flash unfiltered Home
    // content before its real contentType is known.
    private val _contentType = MutableStateFlow<String?>(null)

    /**
     * Called once by HomeScreen (mirroring VodBrowserScreen's own
     * initialize() pattern) so this single ViewModel/screen pair can serve
     * Home, Movies, or TV Shows depending on which route it was opened for.
     * Safe to call repeatedly with the same value (no-op) - HomeScreen calls
     * this from a LaunchedEffect(contentType), which re-fires on
     * recomposition but only actually changes value on a genuine route
     * switch.
     */
    fun initialize(contentType: String) {
        if (_contentType.value == contentType) return
        _contentType.value = contentType
    }

    // Convenience accessor for the functions below (selectCategory,
    // loadMoreCategories, etc.) that run well after initialize() has always
    // already been called - defaults to "home" only as a last-resort guard,
    // never actually expected to be hit in practice.
    private fun contentType(): String = _contentType.value ?: "home"

    // NEW: the category sort/visibility setting is stored per-section, same
    // convention VodBrowserViewModel already used for Movies/TV Shows
    // ("order_vod"/"order_series") - reusing those exact keys here means a
    // user's existing Movies/TV Shows category customization carries over
    // unchanged now that those sections are served by this ViewModel too.
    private fun orderSettingKey(): String = when (contentType()) {
        "series" -> "order_series"
        "vod" -> "order_vod"
        else -> "order_home"
    }

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
            // NEW: also waits on _contentType - Home/Movies/TV Shows all
            // share this one connect-and-load flow, but it must not fire
            // with the wrong (or a not-yet-known) content type. HomeScreen
            // calls initialize() from a LaunchedEffect essentially
            // immediately on first composition, so in practice this only
            // ever delays a single frame for Home's own default ("home")
            // case - Movies/TV Shows behave exactly like VodBrowserScreen's
            // own initialize()-gated pattern.
            combine(serverRepository.getActiveServer(), _contentType) { server, type -> server to type }
                .collect { (server, type) ->
                    if (type == null) return@collect
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
            val rawOrder = settings.getString(p, s, orderSettingKey(), "")
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

        // Read the category order/visibility setting (per-section key - see
        // orderSettingKey()) and remember its signature.
        val rawOrder = settings.getString(p, s, orderSettingKey(), "")
        lastAppliedHomeOrder = rawOrder

        // UPDATED: these two were sequential awaits (fetch categories, THEN
        // fetch genres) despite being fully independent requests - each one
        // now pays for its own round trip back to back for no reason.
        val (categories, genres) = coroutineScope {
            val categoriesDeferred = async { portalService.fetchVodCategories().getOrDefault(emptyList()) }
            val genresDeferred = async { portalService.fetchVodGenres().getOrDefault(emptyList()) }
            Pair(categoriesDeferred.await(), genresDeferred.await())
        }


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
        // NEW: unlike loadCategoryContent, deliberately does NOT null out
        // hero here - Home now keeps using the same hero-overlay layout
        // while searching (see the guard in HomeScreen), so nulling hero
        // out would blank the persistent hero banner the instant a search
        // starts, which is exactly the "carousel jumping" this was meant to
        // avoid. Whatever hero was already showing just stays as-is.
        _uiState.update { it.copy(isLoading = true) }
        // Searching never triggers "load more categories" (guarded in
        // loadMoreCategories() too), so make sure a stale hasMoreCategories
        // from browsing "All Categories" doesn't leave a load-more spinner
        // sentinel visible under the search results that can never resolve.
        _hasMoreCategories.value = false
        val genreId = _uiState.value.selectedGenre?.id ?: ""
        val categoryId = _uiState.value.selectedCategory?.id ?: "*"
        val page = vodRepository.search(contentType(), query, categoryId, 1, genreId)
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
        // FIX (confirmed via device logcat: vertical pagination on the main
        // carousels page permanently stuck at hasMoreCategories=false after
        // visiting an individual category or a search, then switching back
        // to All Categories): loadCategoryContent() and loadSearchResults()
        // both correctly set this false (neither of them uses category-row
        // pagination), but this function - the one that DOES use it - never
        // reset it back, so it stayed false for the rest of the session
        // once either of those had run, even though there were genuinely
        // more categories to page through. Same "how many more remain"
        // check loadMoreCategories() itself uses after appending a batch
        // (see its own _hasMoreCategories update) - _visibleCategories may
        // already have grown past its initial 5 from earlier pagination
        // this session, so recomputing from current sizes here is correct
        // where the original loadHome()-only startup check (orderedVisible
        // .size > 5) would not be.
        _hasMoreCategories.value = _visibleCategories.value.size < _allCategories.value.size
        val genreId = _uiState.value.selectedGenre?.id ?: ""

        // UPDATED (initial ~1s frame-skip on cold start, confirmed via
        // device logcat - Choreographer reported "Skipped 64 frames!" at
        // the exact moment isLoading flipped to false with 6 rows): this
        // used to fetch every row's data in parallel (good - that's why the
        // FETCH itself is fast) but wait for ALL of them via awaitAll()
        // before a single _uiState.update - so Compose had to compose/
        // layout 6 fully-populated carousel rows, each triggering several
        // image loads, in one burst.
        //
        // Fetches still fire in parallel via async below - nothing here
        // makes the network side any slower. What changes is the REVEAL:
        // rows are awaited and added to state ONE AT A TIME, in the same
        // fixed order they'll display in (Recently Added first, then each
        // category in _visibleCategories' order) - never in "whichever
        // network call happened to finish first" order. Awaiting an
        // already-completed Deferred returns immediately, so by the time
        // row 3 is revealed its fetch may well have finished minutes ago
        // while rows 1 and 2 were being revealed - this costs nothing, it
        // just guarantees the order on screen always matches category
        // order regardless of network timing. Compose now only has to lay
        // out one new row per update, spread naturally across several
        // frames instead of six at once.
        coroutineScope {
            val recentDeferred = async(Dispatchers.IO) {
                vodRepository.getList(contentType(), categoryId = "*", page = 1, pageSize = 15, genreId = genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            }
            val categoryDeferreds = _visibleCategories.value.map { category ->
                async(Dispatchers.IO) {
                    val page = vodRepository.getList(contentType(), category.id, 1, 14, genreId)
                        .getOrDefault(PortalPage(emptyList(), 0))
                    HomeRow(
                        id = category.id,
                        title = category.title,
                        items = page.items.visible(),
                        currentPage = 1,
                        hasMore = page.items.size >= 14
                    )
                }
            }

            val revealedRows = mutableListOf<HomeRow>()
            val recentPage = recentDeferred.await()
            val visibleRecent = recentPage.items.visible()
            if (visibleRecent.isNotEmpty()) {
                revealedRows.add(
                    HomeRow(
                        id = "*",
                        title = "Recently Added",
                        items = visibleRecent,
                        currentPage = 1,
                        hasMore = recentPage.items.isNotEmpty()
                    )
                )
                _uiState.update {
                    it.copy(isLoading = false, hero = visibleRecent.firstOrNull(), rows = revealedRows.toList())
                }
            }
            for (deferred in categoryDeferreds) {
                val row = deferred.await()
                if (row.items.isEmpty()) continue
                revealedRows.add(row)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hero = it.hero ?: visibleRecent.firstOrNull(),
                        rows = revealedRows.toList()
                    )
                }
            }
            // Covers the edge case where every single row - Recently Added
            // included - came back empty: isLoading needs to clear even
            // though no row-reveal update above ever ran.
            if (revealedRows.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadCategoryContent(category: PortalCategory) {
        // NEW: no longer nulls hero out - the grid view now keeps the same
        // persistent hero banner the carousel view uses (see HomeScreen's
        // CategoryGridWithHeroLayout), so blanking it here would cause the
        // same jarring "hero disappears" jump that loadSearchResults used
        // to cause before its own equivalent fix. Whatever hero was already
        // showing just stays as-is.
        _uiState.update { it.copy(isLoading = true) }
        _hasMoreCategories.value = false
        val genreId = _uiState.value.selectedGenre?.id ?: ""
        val page = vodRepository.getList(contentType(), category.id, 1, 14, genreId)
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
        // FIX: set this SYNCHRONOUSLY, before launching the coroutine below -
        // onFocus deliberately calls this repeatedly as focus moves across
        // the last few rows (see the pagination-reachability fix elsewhere
        // in this file), so two calls landing back-to-back before the first
        // coroutine actually gets scheduled would BOTH pass the guard above
        // (which was reading a value only written INSIDE that not-yet-run
        // coroutine), both fetch the same batch of categories, and both
        // append it - producing duplicate category rows with duplicate item
        // IDs, which crashes LazyRow/LazyColumn's unique-key requirement.
        // Writing the flag here, before returning control to the caller,
        // closes that window.
        _isLoadingMoreCategories.value = true
        viewModelScope.launch {
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
                            val page = vodRepository.getList(contentType(), category.id, 1, 14, genreId)
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
                    // FIX: distinctBy as a defensive safety net, same
                    // reasoning as loadMoreRowItems above.
                    _uiState.update { it.copy(rows = (it.rows + newRows).distinctBy { row -> row.id }) }
                    break
                }
            }
            _isLoadingMoreCategories.value = false
        }
    }

    fun loadMoreRowItems(rowId: String) {
        val currentRow = _uiState.value.rows.find { it.id == rowId } ?: return
        if (currentRow.isLoadingPage || !currentRow.hasMore) return
        // FIX: written SYNCHRONOUSLY here, before launching the coroutine -
        // see the matching comment on loadMoreCategories above for the full
        // explanation. onFocus calls this repeatedly as focus moves across
        // the last few items in a row; without this, two calls landing
        // before the first coroutine actually runs would both pass the
        // isLoadingPage guard, both fetch the same next page, and both
        // append it - producing duplicate item IDs within the same row,
        // which crashes LazyRow's unique-key requirement.
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.id == rowId) it.copy(isLoadingPage = true) else it })
        }
        viewModelScope.launch {
            val nextPage = currentRow.currentPage + 1
            val genreId = _uiState.value.selectedGenre?.id ?: ""

            // FIX: the "search" row must re-run the SEARCH request for the next page
            // (not fetchVodList with category id "search").
            val page = if (rowId == "search") {
                val query = _uiState.value.searchQuery
                val categoryId = _uiState.value.selectedCategory?.id ?: "*"
                vodRepository.search(contentType(), query, categoryId, nextPage, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            } else {
                vodRepository.getList(contentType(), rowId, nextPage, 14, genreId)
                    .getOrDefault(PortalPage(emptyList(), 0))
            }
            _uiState.update { state ->
                state.copy(rows = state.rows.map {
                    if (it.id == rowId) {
                        it.copy(
                            // Censored items stripped from appended pages as well
                            // FIX: distinctBy as a defensive safety net on top
                            // of the isLoadingPage race fix above - guarantees
                            // LazyRow's unique-key requirement can never be
                            // violated here even if a duplicate ever slipped
                            // through some other path (e.g. a server-side
                            // pagination quirk returning an item twice).
                            items = (it.items + page.items.visible()).distinctBy { item -> item.id },
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