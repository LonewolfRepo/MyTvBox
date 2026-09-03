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
            _state.update { it.copy(isLoading = true) }

            try {
                // Step 1: Get the actual video file ID
                val fileIdResult = vodRepository.getMovieFileId(item.id)
                if (fileIdResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val fileId = fileIdResult.getOrThrow()

                // Step 2: Construct the stream command
                val cmd = "/media/file_$fileId.mpg"

                // Step 3: Create the stream link
                val urlResult = vodRepository.createStreamLink(cmd, "vod")
                if (urlResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val url = urlResult.getOrThrow()

                _state.update { it.copy(isLoading = false) }

                // Step 4: Play the stream
                if (url.isNotEmpty()) {
                    onPlay(url)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadSeasons(item: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val seasonsResult = vodRepository.getSeasons(item.id)
                if (seasonsResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }

                val seasons = seasonsResult.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        hasSeasons = true,
                        seasons = seasons,
                        selectedSeason = seasons.firstOrNull(),
                        isLoading = false
                    )
                }

                // Load episodes for the first season
                seasons.firstOrNull()?.let { season -> loadEpisodes(season) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadEpisodes(season: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season, isLoading = true) }

            try {
                // FIX: Use season.id instead of season.seasonId
                val episodesResult = vodRepository.getEpisodes(
                    movieId = season.movieId,
                    seasonId = season.id  // CHANGED from season.seasonId
                )

                if (episodesResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }

                val episodes = episodesResult.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        episodes = episodes,
                        isLoading = false
                    )
                }

                loadEpisodesProgress(season.movieId)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun playEpisode(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        val season = _state.value.selectedSeason ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // FIX: Use season.id for seasonId, and episode.id for episodeId
                val fileIdResult = vodRepository.getEpisodeFileId(
                    movieId = item.id,
                    seasonId = season.id,      // CHANGED from season.seasonId
                    episodeId = episode.id     // CHANGED from episode.episodeId
                )

                if (fileIdResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val fileId = fileIdResult.getOrThrow()

                val cmd = "/media/file_$fileId.mpg"

                val urlResult = vodRepository.createStreamLink(cmd, "vod")
                if (urlResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val url = urlResult.getOrThrow()

                _state.update { it.copy(isLoading = false) }

                if (url.isNotEmpty()) {
                    onPlay(url)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
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