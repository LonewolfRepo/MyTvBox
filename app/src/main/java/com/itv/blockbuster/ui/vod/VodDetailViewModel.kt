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

// Composite key strictly binds Series ID + Season ID + Episode ID
data class EpisodeProgressKey(val movieId: String, val seasonId: String, val episodeId: String)

data class VodDetailState(
    val isLoading: Boolean = true,
    val item: PortalVodItem? = null,
    val isFavorite: Boolean = false,
    val movieProgress: PlaybackProgressEntity? = null,
    val hasSeasons: Boolean = false,
    val seasons: List<PortalVodItem> = emptyList(),
    val selectedSeason: PortalVodItem? = null,
    val episodes: List<PortalVodItem> = emptyList(),
    val episodeProgressMap: Map<EpisodeProgressKey, PlaybackProgressEntity> = emptyMap(),
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
            if (item.isSeries || contentType == "series") loadSeasons(item)
            else loadMovieProgress(item)
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun loadMetadata(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            vodRepository.isFavorite(p, s, item.id).collect { isFav -> _state.update { it.copy(isFavorite = isFav) } }
        }
    }

    // Movie lookup by exact movieId where episodeId is blank
    private fun loadMovieProgress(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getMovieProgressByMovieId(p, s, item.id)
            _state.update { it.copy(movieProgress = progress) }
        }
    }

    // Episode lookup strictly using Main Item ID as movieId
    private fun loadEpisodesProgress(item: PortalVodItem, season: PortalVodItem, episodes: List<PortalVodItem>) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            val map = mutableMapOf<EpisodeProgressKey, PlaybackProgressEntity>()
            val seriesId = item.id // STRICTLY use main item ID

            episodes.forEach { ep ->
                val progress = vodRepository.getProgressForEpisode(p, s, seriesId, season.id, ep.id)
                if (progress != null) map[EpisodeProgressKey(seriesId, season.id, ep.id)] = progress
            }
            _state.update { it.copy(episodeProgressMap = map) }
        }
    }

    fun selectSeason(season: PortalVodItem) {
        if (_state.value.selectedSeason?.id == season.id) return
        loadEpisodes(season)
    }

    private suspend fun resolveResumePosition(fileId: String): Long {
        if (playbackManager.restartFromBeginning) { playbackManager.restartFromBeginning = false; return -1L }
        val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
        val progress = vodRepository.getProgress(p, s, fileId)
        return if (progress != null && progress.positionMs > 10_000 && progress.positionMs < progress.durationMs - 30_000) progress.positionMs else -1L
    }

    fun playMovie(onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val fileId = vodRepository.getMovieFileId(item.id).getOrThrow()
                val url = vodRepository.createStreamLink("/media/file_$fileId.mpg", "vod").getOrThrow()
                _state.update { it.copy(isLoading = false) }

                playbackManager.clearVodContext()
                playbackManager.currentMovieId = item.id
                playbackManager.currentVideoId = fileId // FIX: Use actual file ID
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

                val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
                vodRepository.addRecent(p, s, item, "VOD")
                if (url.isNotEmpty()) onPlay(url)
            } catch (e: Exception) { _state.update { it.copy(isLoading = false) } }
        }
    }

    private fun loadSeasons(item: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val seasons = vodRepository.getSeasons(item.id).getOrDefault(emptyList())
                _state.update { it.copy(hasSeasons = true, seasons = seasons, selectedSeason = seasons.firstOrNull(), isLoading = false) }
                seasons.firstOrNull()?.let { loadEpisodes(it) }
            } catch (e: Exception) { _state.update { it.copy(isLoading = false) } }
        }
    }

    private fun loadEpisodes(season: PortalVodItem) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeason = season, isLoading = true) }
            try {
                val item = _state.value.item ?: return@launch
                val episodes = vodRepository.getEpisodes(item.id, season.id).getOrDefault(emptyList())
                val sorted = sortEpisodes(episodes, _state.value.episodeSortAscending)
                _state.update { it.copy(episodes = sorted, isLoading = false) }
                loadEpisodesProgress(item, season, sorted)
            } catch (e: Exception) { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun playEpisode(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        val season = _state.value.selectedSeason ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val fileId = vodRepository.getEpisodeFileId(item.id, season.id, episode.id).getOrThrow()
                val url = vodRepository.createStreamLink("/media/file_$fileId.mpg", "vod").getOrThrow()
                _state.update { it.copy(isLoading = false) }

                playbackManager.clearVodContext()
                playbackManager.currentMovieId = item.id // STRICTLY main Series ID
                playbackManager.currentSeasonId = season.id
                playbackManager.currentSeasonNumber = season.seasonNumber
                playbackManager.currentEpisodeId = episode.id
                playbackManager.currentEpisodeNumber = episode.episodeNumber
                playbackManager.currentVideoId = fileId // FIX: Use actual file ID
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
                playbackManager.pendingSeekMs = resolveResumePosition(fileId)

                val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
                vodRepository.addRecent(p, s, item, "SERIES")
                if (url.isNotEmpty()) onPlay(url)
            } catch (e: Exception) { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            if (_state.value.isFavorite) vodRepository.removeFavorite(p, s, item.id)
            else vodRepository.toggleFavorite(p, s, item, item.contentType)
        }
    }

    fun refreshProgress() {
        val item = _state.value.item ?: return
        if (item.isSeries || contentType == "series") {
            val season = _state.value.selectedSeason ?: return
            loadEpisodesProgress(item, season, _state.value.episodes)
        } else {
            loadMovieProgress(item)
        }
    }

    fun playMovieFromBeginning(onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getMovieProgressByMovieId(p, s, item.id)
            if (progress != null) {
                vodRepository.saveProgress(p, s, item.id, "", "", "", "", progress.videoId, 0L, progress.durationMs)
            }
            _state.update { it.copy(movieProgress = null) }
            playbackManager.restartFromBeginning = true
            playMovie(onPlay)
        }
    }

    fun playEpisodeFromBeginning(episode: PortalVodItem, onPlay: (String) -> Unit) {
        val item = _state.value.item ?: return
        val season = _state.value.selectedSeason ?: return
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first(); val s = sessionManager.activePortal.value?.serverId ?: 0
            val progress = vodRepository.getProgressForEpisode(p, s, item.id, season.id, episode.id)
            if (progress != null) {
                vodRepository.saveProgress(p, s, item.id, season.id, season.seasonNumber, episode.id, episode.episodeNumber, progress.videoId, 0L, progress.durationMs)
            }
            val key = EpisodeProgressKey(item.id, season.id, episode.id)
            _state.update { it.copy(episodeProgressMap = it.episodeProgressMap - key) }
            playbackManager.restartFromBeginning = true
            playEpisode(episode, onPlay)
        }
    }

    fun toggleEpisodeSort() {
        val newAsc = !_state.value.episodeSortAscending
        _state.update { it.copy(episodeSortAscending = newAsc, episodes = sortEpisodes(_state.value.episodes, newAsc)) }
    }

    private fun sortEpisodes(episodes: List<PortalVodItem>, asc: Boolean): List<PortalVodItem> =
        if (asc) episodes.sortedBy { it.episodeNumber.toIntOrNull() ?: Int.MAX_VALUE }
        else episodes.sortedByDescending { it.episodeNumber.toIntOrNull() ?: Int.MIN_VALUE }
}