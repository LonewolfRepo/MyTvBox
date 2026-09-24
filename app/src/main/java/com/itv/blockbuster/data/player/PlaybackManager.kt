package com.itv.blockbuster.data.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // UPDATED: was ExoPlayer.Builder(context).build() with 100% stock
    // buffering defaults - specifically DefaultLoadControl's default
    // bufferForPlaybackMs (2500ms) and bufferForPlaybackAfterRebufferMs
    // (5000ms), i.e. ExoPlayer waited for 2.5s of buffered media before
    // ever starting playback, even on a fast connection that could have
    // started sooner. min/max buffer targets (how much it buffers AHEAD
    // once playing) are left at their defaults - only the "how much do I
    // need before I'll START/RESUME playback" thresholds are lowered, so
    // steady-state resilience against network hiccups mid-playback is
    // unaffected; only the initial/resume wait is shorter.
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            1000,  // bufferForPlaybackMs - was the default 2500ms
            2000   // bufferForPlaybackAfterRebufferMs - was the default 5000ms
        )
        .build()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    // NEW: StateFlow mirror of isFullscreenActive so Compose can react to
    // fullscreen enter/exit and re-bind the PIP surface at the right time.
    private val _isFullscreenActive = MutableStateFlow(false)
    val isFullscreenActiveFlow: StateFlow<Boolean> = _isFullscreenActive.asStateFlow()

    // FIX: no @Volatile here — a property with custom getter/setter has no backing
    // field, and @Volatile is not applicable to it. MutableStateFlow.value is
    // already thread-safe for reads and writes.
    var isFullscreenActive: Boolean
        get() = _isFullscreenActive.value
        set(value) {
            _isFullscreenActive.value = value
        }

    // When true, exiting the fullscreen player on a LIVE stream keeps the
    // stream playing so the TV Guide PIP resumes seamlessly.
    @Volatile
    var keepLivePlayingOnExit: Boolean = false

    // ── Level 1 background cleanup ─────────────────────────────────────
    // Optional callback invoked right before background playback is stopped.
    // PlayerViewModel uses this to capture/save VOD progress while the
    // player still has a valid position/duration.
    var onBeforeBackgroundStop: (() -> Unit)? = null

    // ── Live context ──
    var currentChannel: PortalChannel? = null
    var epgPrograms: List<EpgProgram> = emptyList()
    var channelList: List<PortalChannel> = emptyList()

    // ── VOD context ──
    var currentMovieId: String = ""
    var currentSeasonId: String = ""
    var currentSeasonNumber: String = ""
    var currentEpisodeId: String = ""
    var currentEpisodeNumber: String = ""
    // NEW: the episode's own title (e.g. "The Reckoning"), used as the player's
    // title-overlay subtitle. Distinct from currentTitle, which holds the parent
    // series' name.
    var currentEpisodeName: String = ""
    var currentVideoId: String = ""
    var episodeQueue: List<PortalVodItem> = emptyList()

    // Resume target passed from VodDetailViewModel at play-click time.
    // -1 = no resume. Player seeks to this once STATE_READY, then resets to -1.
    var pendingSeekMs: Long = -1L

    var currentItemId: String = ""
    var currentItemType: String = ""
    var currentTitle: String = ""
    var currentLogoUrl: String = ""
    var currentContentType: String = "vod"
    var currentChannelCmd: String = ""
    var currentDescription: String = ""
    var currentDirector: String = ""
    var currentActors: String = ""
    var currentYear: String = ""
    var currentRatingImdb: String = ""
    var currentRatingMpaa: String = ""
    var currentAge: String = ""
    var currentAddedDate: String = ""
    var currentGenres: String = ""
    var currentCountry: String = ""

    fun play(url: String) {
        val currentUrl = player.currentMediaItem?.mediaId
        if (currentUrl != url) {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(url)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
        }
        // If a resume seek is pending, hold auto-play until the seek is applied
        // on STATE_READY (prevents a flash from position 0).
        player.playWhenReady = pendingSeekMs <= 0
    }

    fun setLiveContext(channel: PortalChannel, epg: List<EpgProgram>, allChannels: List<PortalChannel>) {
        currentChannel = channel
        epgPrograms = epg
        channelList = allChannels
        clearVodContext()
    }

    fun clearLiveContext() {
        currentChannel = null
        epgPrograms = emptyList()
    }

    fun clearVodContext() {
        currentMovieId = ""
        currentSeasonId = ""
        currentSeasonNumber = ""
        currentEpisodeId = ""
        currentEpisodeNumber = ""
        currentEpisodeName = ""
        currentVideoId = ""
        episodeQueue = emptyList()
    }

    fun zap(delta: Int): PortalChannel? {
        val list = channelList
        val current = currentChannel ?: return null
        if (list.isEmpty()) return null
        val index = list.indexOfFirst { it.id == current.id }
        val base = if (index == -1) 0 else index
        val nextIndex = (base + delta + list.size) % list.size
        return list.getOrNull(nextIndex)
    }

    fun nextInQueue(): PortalVodItem? {
        val currentId = currentEpisodeId.ifEmpty { currentVideoId }
        if (currentId.isEmpty() || episodeQueue.isEmpty()) return null
        val index = episodeQueue.indexOfFirst { it.id == currentId }
        if (index >= 0 && index + 1 < episodeQueue.size) return episodeQueue[index + 1]
        return null
    }

    fun stopPlayback() {
        player.stop()
        player.clearMediaItems()
        clearLiveContext()
    }

    /**
     * Level 1 background cleanup.
     *
     * This is intentionally stronger than a simple pause, but safer than
     * fully releasing/recreating ExoPlayer.
     *
     * It:
     *  - gives PlayerViewModel a chance to save VOD progress,
     *  - stops playback,
     *  - clears media items so no stream remains active,
     *  - resets pending seek,
     *  - clears the keep-live PIP handoff flag,
     *  - resets fullscreen state,
     *  - clears live channel context.
     *
     * It does NOT kill the app process.
     */
    fun stopBackgroundPlayback() {
        runCatching { onBeforeBackgroundStop?.invoke() }

        player.stop()
        player.clearMediaItems()

        pendingSeekMs = -1L
        keepLivePlayingOnExit = false
        isFullscreenActive = false

        clearLiveContext()
    }
}