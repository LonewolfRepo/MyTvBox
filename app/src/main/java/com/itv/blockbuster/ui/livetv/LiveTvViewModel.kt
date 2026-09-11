package com.itv.blockbuster.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.session.AdultSessionManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.util.LiveTvCategoryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTvUiState(
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val categories: List<PortalCategory> = emptyList(),
    val selectedCategory: PortalCategory? = null,
    val allChannels: List<PortalChannel> = emptyList(),
    val channels: List<PortalChannel> = emptyList(),
    val favoriteIds: Set<String> = emptySet()
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    private val connectionRepository: ConnectionRepository,
    private val serverRepository: ServerRepository,
    private val settings: SettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager,
    val playbackManager: PlaybackManager,
    private val adultSessionManager: AdultSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.collect { connectAndLoad() }
        }
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                liveTvRepository.getFavorites(p, s, "LIVE")
            }.collect { favs ->
                _favoriteIds.value = favs.map { it.itemId }.toSet()
            }
        }
    }

    private fun connectAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            val server = serverRepository.getActiveServer().firstOrNull()
            if (server == null) {
                _uiState.update { it.copy(isConnecting = false, connectionError = "No portal configured.") }
                return@launch
            }
            val needsConnect = sessionManager.ajaxLoader.value.isEmpty() || sessionManager.activePortal.value?.serverId != server.id
            if (needsConnect) {
                val result = connectionRepository.connectToServer(server)
                if (result.isFailure) {
                    _uiState.update { it.copy(isConnecting = false, connectionError = result.exceptionOrNull()?.message ?: "Connection failed") }
                    return@launch
                }
            }
            _uiState.update { it.copy(isConnecting = false) }
            load()
        }
    }

    private suspend fun load() {
        _uiState.update { it.copy(isLoading = true) }
        val allCats = liveTvRepository.getCategories().getOrDefault(emptyList())
        val isAdult = adultSessionManager.isAdultMode.value
        val allRaw = liveTvRepository.getAllChannels().getOrDefault(PortalPage(emptyList(), 0)).items
        val p = prefs.activeProfileIdFlow.first()
        val s = sessionManager.activePortal.value?.serverId ?: 0
        val rawOrder = settings.getString(p, s, "order_live", "")

        // Shared with TvGuideViewModel so Live TV and the TV Guide's category
        // filter/sort always match the Live TV Content Settings exactly.
        val result = LiveTvCategoryFilter.apply(allCats, allRaw, isAdult, rawOrder)

        // FIX: Push censored category IDs to global registry
        adultSessionManager.updateCensoredCategories(result.censoredCategoryIds)

        _uiState.update {
            it.copy(
                isLoading = false,
                categories = result.categories,
                selectedCategory = result.allCategory,
                allChannels = result.channels,
                channels = result.channels
            )
        }
    }

    private fun filterChannels(all: List<PortalChannel>, cat: PortalCategory?): List<PortalChannel> {
        if (cat == null || cat.id == "*" || cat.id == "0" || cat.id == "all") return all
        return all.filter { it.genreId == cat.id }
    }

    fun selectCategory(category: PortalCategory) {
        _uiState.update { it.copy(selectedCategory = category, channels = filterChannels(it.allChannels, category)) }
    }

    fun getStreamUrl(cmd: String, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val url = liveTvRepository.createStreamLink(cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }

    fun toggleFavorite(channel: PortalChannel) {
        if (adultSessionManager.isAdultMode.value) return
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = sessionManager.activePortal.value?.serverId ?: 0
            liveTvRepository.toggleFavorite(p, s, channel)
        }
    }
}