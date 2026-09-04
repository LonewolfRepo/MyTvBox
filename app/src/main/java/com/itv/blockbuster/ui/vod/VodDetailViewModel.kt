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
    val episodeProgressMap: Map<String, PlaybackProgressEntity> = emptyMap(),
    val episodeSortAscending: Boolean = true
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
        val item = VodNavigationCache.currentItem
        if (item != null) {
            _state.update { it.copy(item = item, isLoading = false) }
            loadMetadata(item)
            if (item.isSeries || contentType == "series") {
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

    // =====================================================================
    // RESUME HELPER — fetches progress for the exact video file ID at
    // play-click time and stores the resume target in PlaybackManager.
    // =====================================================================
    private suspend fun resolveResumePosition(fileId: String): Long {
        // "Play from beginning" explicitly skips resume
        if (playbackManager.restartFromBeginning) {
            playbackManager.restartFromBeginning = false
            return -1L
        }
        val profileId = prefs.activeProfileIdFlow.first()
        val serverId = sessionManager.activePortal.value?.serverId ?: 0
        val progress = vodRepository.getProgress(profileId, serverId, fileId)
        return if (progress != null &&
            progress.positionMs > 10_000 &&                       // only resume past 10s
            progress.positionMs < progress.durationMs - 30_000    // not near the end
        ) progress.positionMs else -1L
    }

    fun playMovie(onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val fileIdResult = vodRepository.getMovieFileId(item.id)
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

                playbackManager.clearVodContext()
                playbackManager.currentMovieId = item.id
                playbackManager.currentVideoId = fileId            // KEY: progress keyed by file ID
                playbackManager.currentItemId = item.id
                playbackManager.currentItemType = "VOD"
                playbackManager.currentTitle = item.name
                playbackManager.currentLogoUrl = item.logoUrl
                playbackManager.currentContentType = "vod"
                playbackManager.currentDescription = item.description
                playbackManager.currentDirector = item.director
                playbackManager.currentActors = item.actors
                playbackManager.currentYear = item.year
                playbackManager.currentRatingImdb = item.ratingImdb
                playbackManager.currentRatingMpaa = item.ratingMpaa
                playbackManager.currentAge = item.age
                playbackManager.currentAddedDate = item.addedDate
                playbackManager.currentGenres = item.genres
                playbackManager.currentCountry = item.country
                playbackManager.episodeQueue = emptyList()

                // Fetch resume position for THIS video file ID, pass to player
                playbackManager.pendingSeekMs = resolveResumePosition(fileId)

                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                vodRepository.addRecent(profileId, serverId, item, "VOD")

                if (url.isNotEmpty()) onPlay(url)
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
                val episodesResult = vodRepository.getEpisodes(
                    movieId = season.movieId,
                    seasonId = season.id
                )
                if (episodesResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val rawEpisodes = episodesResult.getOrDefault(emptyList())
                val sortedEpisodes = sortEpisodes(rawEpisodes, _state.value.episodeSortAscending)
                _state.update { it.copy(episodes = sortedEpisodes, isLoading = false) }
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
                val fileIdResult = vodRepository.getEpisodeFileId(
                    movieId = item.id,
                    seasonId = season.id,
                    episodeId = episode.id
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

                val episodes = _state.value.episodes
                playbackManager.clearVodContext()
                playbackManager.currentMovieId = item.id
                playbackManager.currentSeasonId = season.id
                playbackManager.currentSeasonNumber = season.seasonNumber
                playbackManager.currentEpisodeId = episode.id       // for nextInQueue
                playbackManager.currentEpisodeNumber = episode.episodeNumber
                playbackManager.currentVideoId = fileId             // KEY: progress keyed by file ID
                playbackManager.currentItemId = item.id
                playbackManager.currentItemType = "SERIES"
                playbackManager.currentTitle = item.name
                playbackManager.currentLogoUrl = item.logoUrl
                playbackManager.currentContentType = "series"
                playbackManager.currentDescription = item.description
                playbackManager.currentDirector = item.director
                playbackManager.currentActors = item.actors
                playbackManager.currentYear = item.year
                playbackManager.currentRatingImdb = item.ratingImdb
                playbackManager.currentRatingMpaa = item.ratingMpaa
                playbackManager.currentAge = item.age
                playbackManager.currentAddedDate = item.addedDate
                playbackManager.currentGenres = item.genres
                playbackManager.currentCountry = item.country
                playbackManager.episodeQueue = episodes

                // Fetch resume position for THIS video file ID, pass to player
                playbackManager.pendingSeekMs = resolveResumePosition(fileId)

                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                vodRepository.addRecent(profileId, serverId, item, "SERIES")

                if (url.isNotEmpty()) onPlay(url)
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

    fun playMovieFromBeginning(onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getProgress(profileId, serverId, item.id)
            if (progress != null) {
                vodRepository.saveProgress(
                    profileId = profileId, serverId = serverId,
                    movieId = item.movieId.ifEmpty { item.id },
                    seasonId = "", seasonNumber = "",
                    episodeId = "", episodeNumber = "",
                    videoId = item.id,
                    positionMs = 0L,
                    durationMs = progress.durationMs
                )
            }
            _state.update { it.copy(movieProgress = null) }
            playbackManager.restartFromBeginning = true
            playMovie(onPlay)
        }
    }

    fun playEpisodeFromBeginning(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getProgress(profileId, serverId, episode.id)
            if (progress != null) {
                vodRepository.saveProgress(
                    profileId = profileId, serverId = serverId,
                    movieId = item.id,
                    seasonId = _state.value.selectedSeason?.id ?: "",
                    seasonNumber = _state.value.selectedSeason?.seasonNumber ?: "",
                    episodeId = episode.id,
                    episodeNumber = episode.episodeNumber,
                    videoId = episode.id,
                    positionMs = 0L,
                    durationMs = progress.durationMs
                )
            }
            _state.update {
                it.copy(episodeProgressMap = it.episodeProgressMap - episode.id)
            }
            playbackManager.restartFromBeginning = true
            playEpisode(episode, onPlay)
        }
    }

    fun toggleEpisodeSort() {
        val current = _state.value
        val newAscending = !current.episodeSortAscending
        val sortedEpisodes = sortEpisodes(current.episodes, newAscending)
        _state.update { it.copy(episodeSortAscending = newAscending, episodes = sortedEpisodes) }
    }

    private fun sortEpisodes(episodes: List<PortalVodItem>, ascending: Boolean): List<PortalVodItem> {
        return if (ascending) {
            episodes.sortedBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }
        } else {
            episodes.sortedByDescending { it.episodeNumber.toIntOrNull() ?: Int.MIN_VALUE }
        }
    }
}