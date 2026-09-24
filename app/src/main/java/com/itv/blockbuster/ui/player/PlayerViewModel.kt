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
import com.itv.blockbuster.data.session.AdultSessionManager
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
    private val recentRepository: RecentRepository,
    private val adultSessionManager: AdultSessionManager // NEW
) : ViewModel() {

    val autoPlayNext: StateFlow<Boolean> = prefs.autoPlayNextFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _rewindMs = MutableStateFlow(15_000L)
    val rewindMs: StateFlow<Long> = _rewindMs.asStateFlow()

    private val _forwardMs = MutableStateFlow(30_000L)
    val forwardMs: StateFlow<Long> = _forwardMs.asStateFlow()

    private val _liveBanner = MutableStateFlow<LiveBannerData?>(null)
    val liveBanner: StateFlow<LiveBannerData?> = _liveBanner.asStateFlow()

    // Level 1 background cleanup:
    // Cache the latest profile/server IDs so progress can be captured
    // synchronously before PlaybackManager stops the player.
    private var currentProfileId: Int = 0
    private var currentServerId: Int = 0

    // Level 1 background cleanup:
    // Stable callback identity so onCleared() only clears this ViewModel's
    // callback and does not accidentally clear a callback installed by a
    // newer PlayerViewModel instance.
    private val backgroundStopCallback: () -> Unit = {
        captureProgressBeforeBackgroundStop()
    }

    init {
        // Level 1 background cleanup:
        // If the app goes to the background while this PlayerViewModel is
        // active, save VOD progress before PlaybackManager stops playback.
        playbackManager.onBeforeBackgroundStop = backgroundStopCallback

        viewModelScope.launch {
            combine(
                prefs.activeProfileIdFlow,
                sessionManager.activePortal
            ) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.collect { pair ->
                val p = pair.first
                val s = pair.second

                currentProfileId = p
                currentServerId = s

                _rewindMs.value = settings.getInt(p, s, "rewind_interval", 15) * 1000L
                _forwardMs.value = settings.getInt(p, s, "forward_interval", 30) * 1000L
            }
        }
    }

    override fun onCleared() {
        // Level 1 background cleanup:
        // Remove this ViewModel's callback only if it is still the active one.
        if (playbackManager.onBeforeBackgroundStop === backgroundStopCallback) {
            playbackManager.onBeforeBackgroundStop = null
        }
        super.onCleared()
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

            // FIX: Dual-layer check (Item + Category)
            val isAdultContent = channel.isCensored || adultSessionManager.isCategoryCensored(channel.genreId)
            if (!isAdultContent) {
                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                liveTvRepository.addRecent(profileId, serverId, channel)
            }
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

            // FIX: Dual-layer check (Item + Category)
            val isAdultContent = next.isCensored || adultSessionManager.isCategoryCensored(next.genreId)
            if (!isAdultContent) {
                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                liveTvRepository.addRecent(profileId, serverId, next)
            }
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

            // Note: Progress saving is generally acceptable for adult content to resume playback,
            // but if you want to block it entirely, add: if (playbackManager.currentIsCensored) return@launch
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

    /**
     * Level 1 background cleanup:
     *
     * Captures the current playback position synchronously while the player
     * is still valid, then saves it asynchronously.
     *
     * This is used by PlaybackManager.stopBackgroundPlayback() before the
     * player is stopped/cleared.
     */
    private fun captureProgressBeforeBackgroundStop() {
        runCatching {
            val player = playbackManager.player
            val videoId = playbackManager.currentVideoId

            if (videoId.isEmpty() || player.duration <= 0) return@runCatching

            // Don't save until the video has played for at least 10 seconds.
            // This also protects the saved resume position from being
            // overwritten with ~0 right after a seek is requested.
            if (player.currentPosition < 10_000) return@runCatching

            val position = player.currentPosition
            val duration = player.duration

            val movieId = playbackManager.currentMovieId
            val seasonId = playbackManager.currentSeasonId
            val seasonNumber = playbackManager.currentSeasonNumber
            val episodeId = playbackManager.currentEpisodeId
            val episodeNumber = playbackManager.currentEpisodeNumber

            val profileId = currentProfileId
            val serverId = currentServerId

            viewModelScope.launch {
                vodRepository.saveProgress(
                    profileId = profileId,
                    serverId = serverId,
                    movieId = movieId,
                    seasonId = seasonId,
                    seasonNumber = seasonNumber,
                    episodeId = episodeId,
                    episodeNumber = episodeNumber,
                    videoId = videoId,
                    positionMs = position,
                    durationMs = duration
                )
            }
        }
    }

    fun playNextEpisode() {
        viewModelScope.launch {
            val next = playbackManager.nextInQueue() ?: return@launch
            // FIX - ROOT CAUSE CORRECTED: next.id IS the right value for
            // this lookup's episode_id filter (an earlier fix attempt
            // swapped this for next.episodeId, which was wrong - reverted;
            // see VodDetailViewModel's matching comment). The actual bug is
            // the create_link call below missing the "series" param
            // (next.episodeNumber, mapped from the raw series_number
            // field) needed to resolve the right episode within the file.
            val fileIdResult = vodRepository.getEpisodeFileId(
                movieId = playbackManager.currentMovieId,
                seasonId = playbackManager.currentSeasonId,
                episodeId = next.id
            )
            if (fileIdResult.isFailure) return@launch
            val fileId = fileIdResult.getOrThrow()
            val cmd = "/media/file_$fileId.mpg"
            val urlResult = vodRepository.createStreamLink(cmd, "vod", series = next.episodeNumber)
            if (urlResult.isFailure) return@launch
            val url = urlResult.getOrThrow()
            if (url.isEmpty()) return@launch

            playbackManager.currentEpisodeId = next.id
            playbackManager.currentEpisodeNumber = next.episodeNumber
            playbackManager.currentEpisodeName = next.name
            playbackManager.currentVideoId = fileId          // keep progress keyed by file ID
            // FIX: was also setting playbackManager.restartFromBeginning =
            // true here - redundant (pendingSeekMs above already explicitly
            // starts the next episode at 0) and the actual source of a
            // cross-screen bug: that flag was a shared/global mutable flag
            // VodDetailViewModel's playMovie() checked to decide resume-vs-
            // restart for a COMPLETELY UNRELATED later movie. Since nothing
            // in THIS autoplay-next-episode flow ever consumed/reset it,
            // playing the next episode here would silently force the next
            // movie's own "Play" button to restart from 0 instead of
            // resuming, whenever the user next visited one. See
            // VodDetailViewModel.playMovie's own comment for the full
            // picture - it now takes an explicit forceRestart parameter
            // instead, so there's no shared flag left to leak.
            playbackManager.pendingSeekMs = -1L              // next episode starts at 0
            playbackManager.play(url)
        }
    }

    private suspend fun profileId(): Int = prefs.activeProfileIdFlow.first()
    private fun serverId(): Int = sessionManager.activePortal.value?.serverId ?: 0
}