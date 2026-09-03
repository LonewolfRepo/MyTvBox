package com.itv.blockbuster.ui.hubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.entity.RecentEntity
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.repository.RecentRepository
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
class RecentsHubViewModel @Inject constructor(
    private val recentRepository: RecentRepository,
    private val prefs: UserPreferencesRepository,
    private val session: StalkerSessionManager,
    private val liveTvRepository: LiveTvRepository
) : ViewModel() {

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _liveChannels = MutableStateFlow<List<PortalChannel>>(emptyList())
    val liveChannels: StateFlow<List<PortalChannel>> = _liveChannels.asStateFlow()

    private val _movieItems = MutableStateFlow<List<PortalVodItem>>(emptyList())
    val movieItems: StateFlow<List<PortalVodItem>> = _movieItems.asStateFlow()

    private val _seriesItems = MutableStateFlow<List<PortalVodItem>>(emptyList())
    val seriesItems: StateFlow<List<PortalVodItem>> = _seriesItems.asStateFlow()

    // Keep raw entities for delete operations
    private val _rawRecents = MutableStateFlow<List<RecentEntity>>(emptyList())

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, session.activePortal) { p, sp ->
                Pair(p, sp?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                combine(
                    recentRepository.getRecents(p, s),
                    liveTvRepository.getFavorites(p, s, "LIVE")
                ) { recents, favs -> Pair(recents, favs) }
            }.collect { (recents, favs) ->
                _favoriteIds.value = favs.map { it.itemId }.toSet()
                _rawRecents.value = recents

                _liveChannels.value = recents.filter { it.type == "LIVE" }.map { it.toChannel() }
                _movieItems.value = recents.filter { it.type == "VOD" }.map { it.toPortalItem() }
                _seriesItems.value = recents.filter { it.type == "SERIES" }.map { it.toPortalItem() }
            }
        }
    }

    private fun RecentEntity.toChannel() = PortalChannel(
        id = itemId, name = title, cmd = cmd, logoUrl = logoUrl, number = ""
    )

    private fun RecentEntity.toPortalItem() = PortalVodItem(
        id = itemId, name = title, logoUrl = logoUrl, description = description,
        director = director, actors = actors, year = year, ratingImdb = ratingImdb,
        ratingMpaa = ratingMpaa, age = age, addedDate = addedDate, genres = genres,
        country = country, contentType = contentType, isSeries = type == "SERIES",
        movieId = itemId
    )

    fun deleteRecent(item: PortalVodItem) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = session.activePortal.value?.serverId ?: 0
            val type = if (item.isSeries) "SERIES" else "VOD"
            recentRepository.deleteRecent(p, s, item.id, type)
        }
    }

    fun deleteLiveRecent(channelId: String) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = session.activePortal.value?.serverId ?: 0
            recentRepository.deleteRecent(p, s, channelId, "LIVE")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = session.activePortal.value?.serverId ?: 0
            recentRepository.clearAll(p, s)
        }
    }

    fun toggleLiveFavorite(channel: PortalChannel) {
        viewModelScope.launch {
            val p = prefs.activeProfileIdFlow.first()
            val s = session.activePortal.value?.serverId ?: 0
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