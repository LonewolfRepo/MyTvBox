package com.itv.blockbuster.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.RecentRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.session.AdultSessionManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.EpgProgram
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class GuideUiState(
    val isLoading: Boolean = false,
    val categories: List<PortalCategory> = emptyList(),
    val selectedCategory: PortalCategory? = null,
    val allChannels: List<PortalChannel> = emptyList(), // Cache all channels
    val channels: List<PortalChannel> = emptyList(),    // Filtered channels
    val epg: Map<String, List<EpgProgram>> = emptyMap(),
    val nowMin: Int = 0,
    val clock: String = "",
    val previewChannel: PortalChannel? = null,
    val previewUrl: String? = null,
    // channel the guide should land on (last played live channel)
    val lastPlayedChannelId: String? = null
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    private val recentRepository: RecentRepository,
    private val connectionRepository: ConnectionRepository, // NEW
    private val serverRepository: ServerRepository,         // NEW
    private val settings: SettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager,
    val playbackManager: PlaybackManager,
    private val adultSessionManager: AdultSessionManager // NEW
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    init {
        tick()
        // FIX: Observe profile/portal changes and ensure connection before loading.
        // This prevents the blank screen race condition when the app starts on
        // a different landing page and the user navigates to the TV Guide early.
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.collect {
                connectAndLoad()
            }
        }
    }

    private fun tick() {
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        _uiState.update { it.copy(nowMin = nowMin, clock = clock) }
    }

    private fun connectAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val server = serverRepository.getActiveServer().firstOrNull()
            if (server == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Check if we actually need to run the handshake/connection flow
            val needsConnect = sessionManager.ajaxLoader.value.isEmpty() ||
                    sessionManager.activePortal.value?.serverId != server.id

            if (needsConnect) {
                val result = connectionRepository.connectToServer(server)
                if (result.isFailure) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
            }

            // Session is guaranteed to be connected now
            load()
        }
    }

    private suspend fun load() {
        _uiState.update { it.copy(isLoading = true) }
        val allCats = liveTvRepository.getCategories().getOrDefault(emptyList())
        val isAdult = adultSessionManager.isAdultMode.value
        val allChannelsRaw = liveTvRepository.getAllChannels().getOrDefault(PortalPage(emptyList(), 0)).items
        val p = prefs.activeProfileIdFlow.first()
        val s = sessionManager.activePortal.value?.serverId ?: 0
        val rawOrder = settings.getString(p, s, "order_live", "")

        // FIX: previously the Guide computed its own category list here - filtered
        // by adult/censored only, with no sorting and no respect for categories the
        // user had hidden in Settings -> Content Settings -> Live TV. It now shares
        // the exact same sort/filter logic Live TV uses (LiveTvCategoryFilter, keyed
        // off the same "order_live" setting), so the Guide's category dropdown and
        // channel list always match Live TV exactly.
        val result = LiveTvCategoryFilter.apply(allCats, allChannelsRaw, isAdult, rawOrder)
        adultSessionManager.updateCensoredCategories(result.censoredCategoryIds)

        val default = result.allCategory
        val allChannels = result.channels
        val filteredChannels = allChannels
        // Resolve last played live channel — active playback session first,
        // then the most recent LIVE entry from Recents (survives app restarts).
        val sessionChannelId = playbackManager.currentChannel?.id
        val recentChannelId = recentRepository.getRecents(p, s)
            .first()
            .firstOrNull { it.type == "LIVE" }
            ?.itemId
        val lastPlayedId = sessionChannelId ?: recentChannelId

        _uiState.update {
            it.copy(
                isLoading = false,
                categories = result.categories,
                selectedCategory = default,
                allChannels = allChannels,
                channels = filteredChannels,
                lastPlayedChannelId = lastPlayedId
            )
        }

        // Auto-preview the last played channel so the top-left player resumes it
        val lastChannel = filteredChannels.firstOrNull { it.id == lastPlayedId }
            ?: allChannels.firstOrNull { it.id == lastPlayedId }
        if (lastChannel != null) selectForPreview(lastChannel)
    }

    fun selectCategory(category: PortalCategory) {
        val isAll = category.id == "*" || category.id == "0" || category.id == "all"
        val filtered = if (isAll) _uiState.value.allChannels else _uiState.value.allChannels.filter { it.genreId == category.id }
        _uiState.update { it.copy(selectedCategory = category, channels = filtered) }
    }

    fun ensureEpg(channelId: String) {
        viewModelScope.launch {
            if (_uiState.value.epg.containsKey(channelId)) return@launch
            val epg = liveTvRepository.getShortEpgCached(channelId)
            _uiState.update { it.copy(epg = it.epg + (channelId to epg)) }
        }
    }

    fun selectForPreview(channel: PortalChannel) {
        viewModelScope.launch {
            // FIX: only skip restart when the player STILL HOLDS the media item.
            // If a lifecycle race cleared the media, we must re-create the stream link.
            if (_uiState.value.previewChannel?.id == channel.id &&
                !_uiState.value.previewUrl.isNullOrEmpty() &&
                playbackManager.player.currentMediaItem != null
            ) return@launch

            _uiState.update { it.copy(previewChannel = channel, previewUrl = null) }
            val url = liveTvRepository.createStreamLink(channel.cmd).getOrDefault("")
            val epg = liveTvRepository.getShortEpgCached(channel.id)
            playbackManager.setLiveContext(channel, epg, _uiState.value.channels)
            playbackManager.play(url)
            _uiState.update { it.copy(previewUrl = url, epg = it.epg + (channel.id to epg)) }
        }
    }

    fun playArchive(program: EpgProgram, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val cmd = program.cmd ?: return@launch
            val url = liveTvRepository.createStreamLink(cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }
}
