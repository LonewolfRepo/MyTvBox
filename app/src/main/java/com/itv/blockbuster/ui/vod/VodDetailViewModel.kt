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

// NEW: Smart play target computed after seasons load
data class PlayTarget(
    val season: PortalVodItem?,
    val episode: PortalVodItem?,
    val isResume: Boolean,
    val seekMs: Long,
    val label: String
)

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
    val episodeSortAscending: Boolean = true,
    val playTarget: PlayTarget? = null
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
    // SEASONS + SMART PLAY TARGET
    // =====================================================================

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
                if (seasons.isEmpty()) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                _state.update {
                    it.copy(hasSeasons = true, seasons = seasons, isLoading = false)
                }
                computePlayTarget(item, seasons)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    // =====================================================================
    // SMART PLAY TARGET COMPUTATION
    // =====================================================================

    private fun computePlayTarget(item: PortalVodItem, seasons: List<PortalVodItem>) {
        viewModelScope.launch {
            val profileId = prefs.activeProfileIdFlow.first()
            val serverId = sessionManager.activePortal.value?.serverId ?: 0

            // Sort seasons ascending by seasonNumber
            val sortedSeasons = seasons.sortedBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }

            // STEP 1: Fetch most recent progress for the movie
            val progressList = vodRepository.getProgressForMovie(profileId, serverId, item.id)
            val latestProgress = progressList.maxByOrNull { it.timestamp }

            // STEP 2: No progress → play least season / least episode
            if (latestProgress == null) {
                applyFirstSeasonTarget(item, sortedSeasons, progressList)
                return@launch
            }

            // STEP 3: Found progress → locate season + episode
            val targetSeason = sortedSeasons.find { it.id == latestProgress.seasonId }
            if (targetSeason == null) {
                applyFirstSeasonTarget(item, sortedSeasons, progressList)
                return@launch
            }

            val episodesResult = vodRepository.getEpisodes(item.id, targetSeason.id)
            val sortedEps = episodesResult.getOrDefault(emptyList())
                .sortedBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }

            val seasonProgress = progressList.filter { it.seasonId == targetSeason.id }
            val progressByEpisode = seasonProgress.associateBy { it.episodeId }

            val targetEpisode = sortedEps.find { it.id == latestProgress.episodeId }
            if (targetEpisode == null) {
                val firstEp = sortedEps.firstOrNull()
                if (firstEp != null) {
                    _state.update {
                        it.copy(
                            selectedSeason = targetSeason,
                            episodes = sortedEps,
                            episodeProgressMap = progressByEpisode,
                            playTarget = PlayTarget(
                                season = targetSeason, episode = firstEp,
                                isResume = false, seekMs = 0L,
                                label = "Play S${targetSeason.seasonNumber}E${firstEp.episodeNumber}"
                            )
                        )
                    }
                }
                return@launch
            }

            val epProgress = progressByEpisode[targetEpisode.id]
            if (epProgress == null) {
                // No progress for this episode → play from beginning
                _state.update {
                    it.copy(
                        selectedSeason = targetSeason, episodes = sortedEps,
                        episodeProgressMap = progressByEpisode,
                        playTarget = PlayTarget(
                            season = targetSeason, episode = targetEpisode,
                            isResume = false, seekMs = 0L,
                            label = "Play S${targetSeason.seasonNumber}E${targetEpisode.episodeNumber}"
                        )
                    )
                }
                return@launch
            }

            val posMs = epProgress.positionMs
            val durMs = epProgress.durationMs
            val isNearlyFinished = durMs > 0 && (durMs - posMs) <= 30_000
            val isPartiallyPlayed = posMs > 5_000 && !isNearlyFinished

            // STEP 4: Partially played → Resume
            if (isPartiallyPlayed) {
                _state.update {
                    it.copy(
                        selectedSeason = targetSeason, episodes = sortedEps,
                        episodeProgressMap = progressByEpisode,
                        playTarget = PlayTarget(
                            season = targetSeason, episode = targetEpisode,
                            isResume = true, seekMs = posMs,
                            label = "Resume S${targetSeason.seasonNumber}E${targetEpisode.episodeNumber}"
                        )
                    )
                }
                return@launch
            }

            // STEP 5: Fully/nearly played → next episode in same season?
            val epIndex = sortedEps.indexOf(targetEpisode)
            if (epIndex + 1 < sortedEps.size) {
                val nextEp = sortedEps[epIndex + 1]
                _state.update {
                    it.copy(
                        selectedSeason = targetSeason, episodes = sortedEps,
                        episodeProgressMap = progressByEpisode,
                        playTarget = PlayTarget(
                            season = targetSeason, episode = nextEp,
                            isResume = false, seekMs = 0L,
                            label = "Play S${targetSeason.seasonNumber}E${nextEp.episodeNumber}"
                        )
                    )
                }
                return@launch
            }

            // STEP 6: Last episode of season → advance to next season
            val seasonIndex = sortedSeasons.indexOf(targetSeason)
            if (seasonIndex + 1 < sortedSeasons.size) {
                val nextSeason = sortedSeasons[seasonIndex + 1]
                val nextEpsResult = vodRepository.getEpisodes(item.id, nextSeason.id)
                val sortedNextEps = nextEpsResult.getOrDefault(emptyList())
                    .sortedBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }
                val firstNextEp = sortedNextEps.firstOrNull()

                if (firstNextEp != null) {
                    val nextSeasonProgress = progressList.filter { it.seasonId == nextSeason.id }
                    val nextProgressByEp = nextSeasonProgress.associateBy { it.episodeId }
                    _state.update {
                        it.copy(
                            selectedSeason = nextSeason, episodes = sortedNextEps,
                            episodeProgressMap = nextProgressByEp,
                            playTarget = PlayTarget(
                                season = nextSeason, episode = firstNextEp,
                                isResume = false, seekMs = 0L,
                                label = "Play S${nextSeason.seasonNumber}E${firstNextEp.episodeNumber}"
                            )
                        )
                    }
                    return@launch
                }
            }

            // No next season → play least season / least episode
            applyFirstSeasonTarget(item, sortedSeasons, progressList)
        }
    }

    // Helper: play least season / least episode
    private suspend fun applyFirstSeasonTarget(
        item: PortalVodItem,
        sortedSeasons: List<PortalVodItem>,
        allProgress: List<PlaybackProgressEntity>
    ) {
        val firstSeason = sortedSeasons.firstOrNull() ?: return
        val episodesResult = vodRepository.getEpisodes(item.id, firstSeason.id)
        val sortedEps = episodesResult.getOrDefault(emptyList())
            .sortedBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }
        val firstEp = sortedEps.firstOrNull() ?: return

        val seasonProgress = allProgress.filter { it.seasonId == firstSeason.id }
        val progressByEp = seasonProgress.associateBy { it.episodeId }

        _state.update {
            it.copy(
                selectedSeason = firstSeason, episodes = sortedEps,
                episodeProgressMap = progressByEp,
                playTarget = PlayTarget(
                    season = firstSeason, episode = firstEp,
                    isResume = false, seekMs = 0L,
                    label = "Play S${firstSeason.seasonNumber}E${firstEp.episodeNumber}"
                )
            )
        }
    }

    // =====================================================================
    // PLAY ACTIONS
    // =====================================================================

    fun playTargetEpisode(onPlay: (String) -> Unit) {
        val target = _state.value.playTarget ?: return
        val episode = target.episode ?: return
        val season = target.season ?: return
        playEpisodeInternal(episode, season, target.seekMs, onPlay)
    }

    fun playTargetFromBeginning(onPlay: (String) -> Unit) {
        val target = _state.value.playTarget ?: return
        val episode = target.episode ?: return
        val season = target.season ?: return
        playEpisodeInternal(episode, season, 0L, onPlay)
    }

    private fun playEpisodeInternal(
        episode: PortalVodItem,
        season: PortalVodItem,
        seekMs: Long,
        onPlay: (String) -> Unit
    ) {
        val item = _state.value.item ?: return
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

                playbackManager.clearVodContext()
                playbackManager.currentMovieId = item.id
                playbackManager.currentSeasonId = season.id
                playbackManager.currentSeasonNumber = season.seasonNumber
                playbackManager.currentEpisodeId = episode.id
                playbackManager.currentEpisodeNumber = episode.episodeNumber
                playbackManager.currentVideoId = fileId
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
                playbackManager.episodeQueue = _state.value.episodes
                playbackManager.pendingSeekMs = seekMs

                val profileId = prefs.activeProfileIdFlow.first()
                val serverId = sessionManager.activePortal.value?.serverId ?: 0
                vodRepository.addRecent(profileId, serverId, item, "SERIES")

                if (url.isNotEmpty()) onPlay(url)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
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
                playbackManager.currentVideoId = fileId
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

    fun playEpisode(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val season = _state.value.selectedSeason ?: return
        playEpisodeInternal(episode, season, 0L, onPlay)
    }

    fun playEpisodeFromBeginning(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val season = _state.value.selectedSeason ?: return
        playEpisodeInternal(episode, season, 0L, onPlay)
    }

    private fun loadEpisodes(season: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season, isLoading = true) }
            try {
                val movieId = _state.value.item?.id ?: season.movieId
                val episodesResult = vodRepository.getEpisodes(movieId, season.id)
                if (episodesResult.isFailure) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val rawEpisodes = episodesResult.getOrDefault(emptyList())
                val sortedEpisodes = sortEpisodes(rawEpisodes, _state.value.episodeSortAscending)
                _state.update { it.copy(episodes = sortedEpisodes, isLoading = false) }
                loadEpisodesProgress(movieId)
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
        if (_state.value.hasSeasons) {
            loadEpisodesProgress(item.id)
            val seasons = _state.value.seasons
            if (seasons.isNotEmpty()) computePlayTarget(item, seasons)
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

    private suspend fun resolveResumePosition(fileId: String): Long {
        if (playbackManager.restartFromBeginning) {
            playbackManager.restartFromBeginning = false
            return -1L
        }
        val profileId = prefs.activeProfileIdFlow.first()
        val serverId = sessionManager.activePortal.value?.serverId ?: 0
        val progress = vodRepository.getProgress(profileId, serverId, fileId)
        return if (progress != null &&
            progress.positionMs > 10_000 &&
            progress.positionMs < progress.durationMs - 30_000
        ) progress.positionMs else -1L
    }
}