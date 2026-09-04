package com.itv.blockbuster.ui.hubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.local.entity.FavoriteEntity
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.VodRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesHubViewModel @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val vodRepository: VodRepository,
    private val liveTvRepository: LiveTvRepository,
    private val prefs: UserPreferencesRepository,
    private val sessionManager: StalkerSessionManager
) : ViewModel() {

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, PlaybackProgressEntity>>(emptyMap())
    val progressMap: StateFlow<Map<String, PlaybackProgressEntity>> = _progressMap.asStateFlow()

    private val _liveChannels = MutableStateFlow<List<PortalChannel>>(emptyList())
    val liveChannels: StateFlow<List<PortalChannel>> = _liveChannels.asStateFlow()

    private val _movieItems = MutableStateFlow<List<PortalVodItem>>(emptyList())
    val movieItems: StateFlow<List<PortalVodItem>> = _movieItems.asStateFlow()

    private val _seriesItems = MutableStateFlow<List<PortalVodItem>>(emptyList())
    val seriesItems: StateFlow<List<PortalVodItem>> = _seriesItems.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                combine(
                    favoriteDao.getFavorites(p, s, "VOD"),
                    favoriteDao.getFavorites(p, s, "SERIES"),
                    favoriteDao.getFavorites(p, s, "LIVE")
                ) { vodFavs, seriesFavs, liveFavs ->
                    Triple(vodFavs, seriesFavs, liveFavs)
                }
            }.collect { (vodFavs, seriesFavs, liveFavs) ->
                _favoriteIds.value = (vodFavs + seriesFavs + liveFavs).map { it.itemId }.toSet()
                _movieItems.value = vodFavs.map { it.toPortalItem(isSeries = false) }
                _seriesItems.value = seriesFavs.map { it.toPortalItem(isSeries = true) }

                // FIX: Build channels straight from DB records — instant, no network
                _liveChannels.value = liveFavs.map { it.toChannel() }
            }
        }

        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, sessionManager.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                vodRepository.getRecentProgress(p, s)
            }.collect { list ->
                _progressMap.value = list.associateBy { it.videoId }
            }
        }
    }

    private fun FavoriteEntity.toChannel() = PortalChannel(
        id = itemId, name = title, number = "", cmd = cmd, logoUrl = logoUrl,
        genreId = categoryId, nowPlaying = "", hasArchive = false, archiveDuration = 0, isCensored = false
    )

    private fun FavoriteEntity.toPortalItem(isSeries: Boolean) = PortalVodItem(
        id = itemId, name = title, cmd = cmd, logoUrl = logoUrl, description = description,
        director = director, actors = actors, year = year, duration = duration,
        ratingImdb = ratingImdb, ratingMpaa = ratingMpaa, age = age, addedDate = addedDate,
        categoryId = categoryId, contentType = if (isSeries) "series" else "vod",
        genres = genres, country = country, movieId = itemId, isSeries = isSeries
    )

    fun toggleFavorite(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = sessionManager.activePortal.value?.serverId ?: 0
            vodRepository.toggleFavorite(p, s, item, if (item.isSeries) "SERIES" else "VOD")
        }
    }

    fun toggleLiveFavorite(channel: PortalChannel) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = sessionManager.activePortal.value?.serverId ?: 0
            liveTvRepository.toggleFavorite(p, s, channel)
        }
    }

    fun getStreamUrl(cmd: String, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val url = liveTvRepository.createStreamLink(cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }
}