package com.itv.blockbuster.data.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val player: ExoPlayer by lazy { ExoPlayer.Builder(context).build() }

    @Volatile
    var isFullscreenActive: Boolean = false

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
    var currentVideoId: String = ""
    var episodeQueue: List<PortalVodItem> = emptyList()

    // Skip resume and play from beginning
    var restartFromBeginning: Boolean = false

    // NEW: resume target passed from VodDetailViewModel at play-click time.
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
}