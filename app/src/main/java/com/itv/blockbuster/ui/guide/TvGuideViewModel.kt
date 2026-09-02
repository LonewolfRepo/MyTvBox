package com.itv.blockbuster.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val previewUrl: String? = null
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager,
    val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    init {
        tick()
        viewModelScope.launch { load() }
    }

    private fun tick() {
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        _uiState.update { it.copy(nowMin = nowMin, clock = clock) }
    }

    private suspend fun load() {
        _uiState.update { it.copy(isLoading = true) }
        val cats = liveTvRepository.getCategories().getOrDefault(emptyList())
        val allChannels = liveTvRepository.getAllChannels().getOrDefault(PortalPage(emptyList(), 0)).items

        // FIX: Properly handle "All" category so channels aren't filtered out
        val default = cats.firstOrNull { it.id == "*" || it.id == "0" || it.id == "all" } ?: cats.firstOrNull()
        val isAll = default == null || default.id == "*" || default.id == "0" || default.id == "all"
        val filteredChannels = if (isAll) allChannels else allChannels.filter { it.genreId == default?.id }

        _uiState.update {
            it.copy(
                isLoading = false,
                categories = cats,
                selectedCategory = default,
                allChannels = allChannels,
                channels = filteredChannels
            )
        }
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