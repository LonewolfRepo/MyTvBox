package com.itv.blockbuster.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.util.VodNavigationCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodDetailState(
    val isLoading: Boolean = true,
    val item: PortalVodItem? = null,
    val isFavorite: Boolean = false,
    val movieProgress: PlaybackProgressEntity? = null,
    val hasSeasons: Boolean = false,
    val seasons: List<PortalVodItem> = emptyList(),
    val selectedSeason: PortalVodItem? = null,
    val episodes: List<PortalVodItem> = emptyList(),
    val episodeProgressMap: Map<String, PlaybackProgressEntity> = emptyMap()
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val sessionManager: StalkerSessionManager,
    private val prefs: UserPreferencesRepository,
    private val playbackManager: PlaybackManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val contentType: String = savedStateHandle.get<String>("contentType") ?: "vod"

    private val _state = MutableStateFlow(VodDetailState())
    val state: StateFlow<VodDetailState> = _state.asStateFlow()

    init {
        // FIX: Access the global object directly instead of an injected instance
        val item = VodNavigationCache.currentItem
        if (item != null) {
            _state.update { it.copy(item = item, isLoading = false) }
            loadMetadata(item)
            if (contentType == "series") {
                loadSeasons(item)
            } else {
                loadMovieProgress(item)
            }
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun loadMetadata(item: PortalVodItem) {
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            vodRepository.isFavorite(profileId, serverId, item.id).collect { isFav ->
                _state.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    private fun loadMovieProgress(item: PortalVodItem) {
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getProgress(profileId, serverId, item.id)
            _state.update { it.copy(movieProgress = progress) }
        }
    }

    private fun loadSeasons(item: PortalVodItem) {
        viewModelScope.launch {
            // TODO: replace with real getMovieSeasons() once added to VodRepository
            val seasons = listOf(
                PortalVodItem(id = "${item.id}_s1", name = "Season 1", seasonNumber = "1", movieId = item.id),
                PortalVodItem(id = "${item.id}_s2", name = "Season 2", seasonNumber = "2", movieId = item.id)
            )
            _state.update {
                it.copy(hasSeasons = true, seasons = seasons, selectedSeason = seasons.firstOrNull())
            }
            seasons.firstOrNull()?.let { season -> loadEpisodes(season) }
        }
    }

    private fun loadEpisodes(season: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season) }
            // TODO: replace with real getSeasonEpisodes() once added to VodRepository
            val episodes = (1..10).map {
                PortalVodItem(
                    id = "e${season.seasonNumber}_$it",
                    name = "Episode $it",
                    episodeNumber = it.toString(),
                    seasonNumber = season.seasonNumber,
                    movieId = season.movieId
                )
            }
            _state.update { it.copy(episodes = episodes) }
            loadEpisodesProgress(season.movieId)
        }
    }

    private fun loadEpisodesProgress(movieId: String) {
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            val progressList = vodRepository.getProgressForMovie(profileId, serverId, movieId)
            _state.update { it.copy(episodeProgressMap = progressList.associateBy { p -> p.videoId }) }
        }
    }

    fun selectSeason(season: PortalVodItem) {
        if (_state.value.selectedSeason?.id == season.id) return
        loadEpisodes(season)
    }

    fun playMovie(onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            playbackManager.clearLiveContext()
            playbackManager.currentMovieId = item.movieId.ifEmpty { item.id }
            playbackManager.currentSeasonId = ""
            playbackManager.currentSeasonNumber = ""
            playbackManager.currentEpisodeId = ""
            playbackManager.currentEpisodeNumber = ""
            playbackManager.currentVideoId = item.id

            val cmd = item.cmd.ifEmpty { "/media/file_${item.id}.mpg" }
            val url = vodRepository.createStreamLink(cmd, "vod").getOrDefault("")
            if (url.isNotEmpty()) onPlay(url)
        }
    }

    fun playEpisode(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        val season = _state.value.selectedSeason
        viewModelScope.launch {
            playbackManager.clearLiveContext()
            playbackManager.currentMovieId = item.id
            playbackManager.currentSeasonId = season?.id ?: ""
            playbackManager.currentSeasonNumber = season?.seasonNumber ?: ""
            playbackManager.currentEpisodeId = episode.id
            playbackManager.currentEpisodeNumber = episode.episodeNumber
            playbackManager.currentVideoId = episode.id

            val cmd = episode.cmd.ifEmpty { "/media/file_${episode.id}.mpg" }
            val url = vodRepository.createStreamLink(cmd, "vod", episode.episodeNumber).getOrDefault("")
            if (url.isNotEmpty()) onPlay(url)
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            if (_state.value.isFavorite) {
                vodRepository.removeFavorite(profileId, serverId, item.id)
            } else {
                vodRepository.toggleFavorite(profileId, serverId, item, item.contentType)
            }
        }
    }

    fun refreshProgress() {
        val item = _state.value.item ?: return
        loadMovieProgress(item)
        if (_state.value.hasSeasons) loadEpisodesProgress(item.id)
    }
}