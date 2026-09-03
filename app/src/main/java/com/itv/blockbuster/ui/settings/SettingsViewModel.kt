package com.itv.blockbuster.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.repository.RecentRepository
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.util.CategorySortHelper
import com.itv.blockbuster.util.CategorySortItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profileId: Int = -1,
    val serverId: Int = 0,
    val rememberLastProfile: Boolean = false,
    val appAnimations: Boolean = true,
    val autoPlayNext: Boolean = true,
    val autoStartLive: Boolean = false,
    val playerEngineLive: String = "EXO",
    val playerEngineVod: String = "EXO",
    val rewindInterval: Int = 15,
    val forwardInterval: Int = 30,
    val timezone: String = "",
    val defaultHomePage: String = "LIVE_TV",
    val defaultMyListPage: String = "ALL",
    val vodSortItems: List<CategorySortItem> = emptyList(),
    val seriesSortItems: List<CategorySortItem> = emptyList(),
    val liveSortItems: List<CategorySortItem> = emptyList(),
    val diagHost: String = "",
    val diagPath: String = "",
    val diagConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val session: StalkerSessionManager,
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao,
    private val progressDao: PlaybackProgressDao,
    private val vodRepository: VodRepository,
    private val liveTvRepository: LiveTvRepository,
    private val recentRepository: RecentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    // In-memory caches to prevent re-fetching from server during sorting
    private var cachedVodCats: List<PortalCategory> = emptyList()
    private var cachedLiveCats: List<PortalCategory> = emptyList()
    private var lastServerId: Int = -1

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, session.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.collect { pair ->
                val p = pair.first
                val s = pair.second
                _state.update { it.copy(profileId = p, serverId = s) }
                refresh()
                loadCategories()
            }
        }
        viewModelScope.launch { prefs.rememberLastProfileFlow.collect { v -> _state.update { it.copy(rememberLastProfile = v) } } }
        viewModelScope.launch { prefs.appAnimationsFlow.collect { v -> _state.update { it.copy(appAnimations = v) } } }
    }

    private suspend fun refresh() {
        val p = _state.value.profileId; val s = _state.value.serverId
        _state.update {
            it.copy(
                autoPlayNext = settings.getBool(p, s, "auto_play_next", true),
                autoStartLive = settings.getBool(p, s, "auto_start_live", false),
                playerEngineLive = settings.getString(p, s, "player_live", "EXO"),
                playerEngineVod = settings.getString(p, s, "player_vod", "EXO"),
                rewindInterval = settings.getInt(p, s, "rewind_interval", 15),
                forwardInterval = settings.getInt(p, s, "forward_interval", 30),
                timezone = settings.getString(p, s, "timezone", ""),
                defaultHomePage = settings.getString(p, s, "default_home", "LIVE_TV"),
                defaultMyListPage = settings.getString(p, s, "default_mylist", "ALL"),
                diagHost = session.activePortal.value?.host ?: "",
                diagPath = session.portalDir.value,
                diagConnected = session.ajaxLoader.value.isNotEmpty()
            )
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val p = _state.value.profileId; val s = _state.value.serverId

            // Clear cache if portal changed
            if (s != lastServerId) {
                cachedVodCats = emptyList()
                cachedLiveCats = emptyList()
                lastServerId = s
            }

            // Only fetch from server if cache is empty
            if (cachedVodCats.isEmpty()) {
                cachedVodCats = vodRepository.getCategories().getOrDefault(emptyList())
            }
            if (cachedLiveCats.isEmpty()) {
                cachedLiveCats = liveTvRepository.getCategories().getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    vodSortItems = CategorySortHelper.parse(cachedVodCats, settings.getString(p, s, "order_vod", "")),
                    seriesSortItems = CategorySortHelper.parse(cachedVodCats, settings.getString(p, s, "order_series", "")),
                    liveSortItems = CategorySortHelper.parse(cachedLiveCats, settings.getString(p, s, "order_live", ""))
                )
            }
        }
    }

    private fun setB(name: String, v: Boolean) = viewModelScope.launch { settings.setBool(_state.value.profileId, _state.value.serverId, name, v); refresh() }
    private fun setI(name: String, v: Int) = viewModelScope.launch { settings.setInt(_state.value.profileId, _state.value.serverId, name, v); refresh() }
    private fun setS(name: String, v: String) = viewModelScope.launch { settings.setString(_state.value.profileId, _state.value.serverId, name, v); refresh() }

    fun setRememberLastProfile(v: Boolean) = viewModelScope.launch { prefs.setRememberLastProfile(v) }
    fun setAppAnimations(v: Boolean) = viewModelScope.launch { prefs.setAppAnimations(v) }
    fun setAutoPlayNext(v: Boolean) = setB("auto_play_next", v)
    fun setAutoStartLive(v: Boolean) = setB("auto_start_live", v)
    fun setPlayerEngineLive(v: String) = setS("player_live", v)
    fun setPlayerEngineVod(v: String) = setS("player_vod", v)
    fun setRewindInterval(v: Int) = setI("rewind_interval", v)
    fun setForwardInterval(v: Int) = setI("forward_interval", v)
    fun setTimezone(v: String) = setS("timezone", v)
    fun setDefaultHomePage(v: String) = setS("default_home", v)
    fun setDefaultMyListPage(v: String) = setS("default_mylist", v)

    // Save final list to DataStore and State (called ONLY on dialog dismiss)
    fun saveVodSort(items: List<CategorySortItem>) = viewModelScope.launch {
        val p = _state.value.profileId; val s = _state.value.serverId
        settings.setString(p, s, "order_vod", CategorySortHelper.serialize(items))
        _state.update { it.copy(vodSortItems = items) }
    }

    fun saveSeriesSort(items: List<CategorySortItem>) = viewModelScope.launch {
        val p = _state.value.profileId; val s = _state.value.serverId
        settings.setString(p, s, "order_series", CategorySortHelper.serialize(items))
        _state.update { it.copy(seriesSortItems = items) }
    }

    fun saveLiveSort(items: List<CategorySortItem>) = viewModelScope.launch {
        val p = _state.value.profileId; val s = _state.value.serverId
        settings.setString(p, s, "order_live", CategorySortHelper.serialize(items))
        _state.update { it.copy(liveSortItems = items) }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                _notice.value = "Cache cleared"
            } catch (e: Exception) { _notice.value = "Cache clear failed" }
        }
    }

    fun clearSearchHistory() { _notice.value = "Search history cleared" }

    fun clearAllUserData() {
        viewModelScope.launch {
            val p = _state.value.profileId
            val s = _state.value.serverId
            try {
                favoriteDao.clearAll(p, s)
                recentRepository.clearAll(p, s) // NEW
                progressDao.clearAll(p, s)
                _notice.value = "All user data cleared"
            } catch (e: Exception) {
                _notice.value = "Failed to clear data: ${e.message}"
            }
        }
    }
}