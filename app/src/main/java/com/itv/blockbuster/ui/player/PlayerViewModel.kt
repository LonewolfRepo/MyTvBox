package com.itv.blockbuster.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.repository.RecentRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveBannerData(
    val channel: PortalChannel,
    val now: EpgProgram?,
    val next: EpgProgram?
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playbackManager: PlaybackManager,
    private val liveTvRepository: LiveTvRepository,
    private val vodRepository: VodRepository,
    private val settings: SettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager,
    private val recentRepository: RecentRepository
) : ViewModel() {

    val autoPlayNext: StateFlow<Boolean> = prefs.autoPlayNextFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _rewindMs = MutableStateFlow(15_000L)
    val rewindMs: StateFlow<Long> = _rewindMs.asStateFlow()

    private val _forwardMs = MutableStateFlow(30_000L)
    val forwardMs: StateFlow<Long> = _forwardMs.asStateFlow()

    private val _liveBanner = MutableStateFlow<LiveBannerData?>(null)
    val liveBanner: StateFlow<LiveBannerData?> = _liveBanner.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.activeProfileIdFlow,
                sessionManager.activePortal
            ) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.collect { pair ->
                val p = pair.first
                val s = pair.second
                _rewindMs.value = settings.getInt(p, s, "rewind_interval", 15) * 1000L
                _forwardMs.value = settings.getInt(p, s, "forward_interval", 30) * 1000L
            }
        }
    }

    // =====================================================================
    // LIVE
    // =====================================================================

    suspend fun initLive(channelId: String) {
        if (playbackManager.channelList.isEmpty()) {
            val all = liveTvRepository.getAllChannels()
                .getOrDefault(PortalPage(emptyList(), 0)).items
            playbackManager.channelList = all
        }
        val channel = playbackManager.channelList.firstOrNull { it.id == channelId }
            ?: playbackManager.currentChannel
        if (channel != null) {
            playbackManager.currentChannel = channel
            val epg = liveTvRepository.getShortEpg(channel.id).getOrDefault(emptyList())
            playbackManager.epgPrograms = epg
            _liveBanner.value = LiveBannerData(channel, epg.firstOrNull(), epg.getOrNull(1))

            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            liveTvRepository.addRecent(profileId, serverId, channel)
        }
    }

    fun zap(delta: Int) {
        viewModelScope.launch {
            val next = playbackManager.zap(delta) ?: return@launch
            val url = liveTvRepository.createStreamLink(next.cmd).getOrDefault("")
            if (url.isEmpty()) return@launch
            playbackManager.currentChannel = next
            val epg = liveTvRepository.getShortEpg(next.id).getOrDefault(emptyList())
            playbackManager.epgPrograms = epg
            _liveBanner.value = LiveBannerData(next, epg.firstOrNull(), epg.getOrNull(1))
            playbackManager.play(url)
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            liveTvRepository.addRecent(profileId, serverId, next)
        }
    }

    // =====================================================================
    // VOD PROGRESS
    // =====================================================================

    suspend fun getProgress(videoId: String): PlaybackProgressEntity? =
        vodRepository.getProgress(profileId(), serverId(), videoId)

    fun saveCurrentProgress() {
        viewModelScope.launch {
            val player = playbackManager.player
            val videoId = playbackManager.currentVideoId
            if (videoId.isEmpty() || player.duration <= 0) return@launch
            // Don't save until the video has played for at least 10 seconds.
            // This also protects the saved resume position from being
            // overwritten with ~0 right after a seek is requested.
            if (player.currentPosition < 10_000) return@launch
            vodRepository.saveProgress(
                profileId = profileId(),
                serverId = serverId(),
                movieId = playbackManager.currentMovieId,
                seasonId = playbackManager.currentSeasonId,
                seasonNumber = playbackManager.currentSeasonNumber,
                episodeId = playbackManager.currentEpisodeId,
                episodeNumber = playbackManager.currentEpisodeNumber,
                videoId = videoId,
                positionMs = player.currentPosition,
                durationMs = player.duration
            )
        }
    }

    fun playNextEpisode() {
        viewModelScope.launch {
            val next = playbackManager.nextInQueue() ?: return@launch
            val fileIdResult = vodRepository.getEpisodeFileId(
                movieId = playbackManager.currentMovieId,
                seasonId = playbackManager.currentSeasonId,
                episodeId = next.id
            )
            if (fileIdResult.isFailure) return@launch
            val fileId = fileIdResult.getOrThrow()
            val cmd = "/media/file_$fileId.mpg"
            val urlResult = vodRepository.createStreamLink(cmd, "vod")
            if (urlResult.isFailure) return@launch
            val url = urlResult.getOrThrow()
            if (url.isEmpty()) return@launch

            playbackManager.currentEpisodeId = next.id
            playbackManager.currentEpisodeNumber = next.episodeNumber
            playbackManager.currentVideoId = fileId          // keep progress keyed by file ID
            playbackManager.pendingSeekMs = -1L              // next episode starts at 0
            playbackManager.restartFromBeginning = true
            playbackManager.play(url)
        }
    }

    private suspend fun profileId(): Int = prefs.activeProfileIdFlow.first()
    private fun serverId(): Int = sessionManager.activePortal.value?.serverId ?: 0
}