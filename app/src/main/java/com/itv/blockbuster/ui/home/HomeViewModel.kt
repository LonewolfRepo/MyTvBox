package com.itv.blockbuster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.repository.StalkerPortalService
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.domain.model.Server
import com.itv.blockbuster.ui.components.HomeRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val rows: List<HomeRow> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionRepository: ConnectionRepository,
    private val portalService: StalkerPortalService,
    private val vodRepository: VodRepository,
    private val sessionManager: StalkerSessionManager,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

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
        val recentPage = portalService.fetchVodList(categoryId = "*", page = 1, pageSize = 15)
            .getOrDefault(PortalPage(emptyList(), 0))
        val categories = portalService.fetchVodCategories().getOrDefault(emptyList())
        val rowCategories = categories.filter { it.id != "*" && it.id != "0" }

        _allCategories.value = rowCategories
        val initialBatch = rowCategories.take(5)
        _visibleCategories.value = initialBatch
        _hasMoreCategories.value = rowCategories.size > 5

        val categoryRows = coroutineScope {
            initialBatch.map { category ->
                async(Dispatchers.IO) {
                    val page = portalService.fetchVodList(category.id, 1, 14)
                        .getOrDefault(PortalPage(emptyList(), 0))
                    HomeRow(
                        id = category.id,
                        title = category.title,
                        items = page.items,
                        currentPage = 1,
                        hasMore = page.items.size >= 14
                    )
                }
            }.awaitAll().filter { it.items.isNotEmpty() }
        }
        val allRows = buildList {
            if (recentPage.items.isNotEmpty()) add(HomeRow("recently_added", "Recently Added", recentPage.items, hasMore = false))
            addAll(categoryRows)
        }
        _uiState.update { it.copy(isLoading = false, hero = recentPage.items.firstOrNull(), rows = allRows) }
    }

    fun loadMoreCategories() {
        if (_isLoadingMoreCategories.value || !_hasMoreCategories.value) return
        viewModelScope.launch {
            _isLoadingMoreCategories.value = true
            val currentSize = _visibleCategories.value.size
            val nextBatch = _allCategories.value.drop(currentSize).take(5)

            val newRows = coroutineScope {
                nextBatch.map { category ->
                    async(Dispatchers.IO) {
                        val page = portalService.fetchVodList(category.id, 1, 14)
                            .getOrDefault(PortalPage(emptyList(), 0))
                        HomeRow(
                            id = category.id,
                            title = category.title,
                            items = page.items,
                            currentPage = 1,
                            hasMore = page.items.size >= 14
                        )
                    }
                }.awaitAll().filter { it.items.isNotEmpty() }
            }

            _visibleCategories.value = _visibleCategories.value + nextBatch
            _hasMoreCategories.value = _visibleCategories.value.size < _allCategories.value.size
            _uiState.update { it.copy(rows = it.rows + newRows) }
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
            val page = portalService.fetchVodList(rowId, nextPage, 14)
                .getOrDefault(PortalPage(emptyList(), 0))

            _uiState.update { state ->
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