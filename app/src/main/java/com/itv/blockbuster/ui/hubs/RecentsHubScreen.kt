package com.itv.blockbuster.ui.hubs

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.PlaybackProgressDao
import com.itv.blockbuster.data.local.dao.RecentDao
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentsHubViewModel @Inject constructor(
    private val recentDao: RecentDao,
    private val progressDao: PlaybackProgressDao,
    private val prefs: UserPreferencesRepository,
    private val session: StalkerSessionManager,
    private val liveTvRepository: LiveTvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.activeProfileIdFlow, session.activePortal) { p, s ->
                Pair(p, s?.serverId ?: 0)
            }.flatMapLatest { (p, s) ->
                combine(
                    recentDao.getRecentLive(p, s, 20),
                    progressDao.getRecentProgress(p, s)
                ) { recents, progress -> Pair(recents, progress) }
            }.collect { (recents, progress) ->
                val rows = buildList {
                    if (recents.isNotEmpty()) {
                        add(
                            HubRow(
                                "live", "Recent Live TV",
                                recents.map {
                                    HubItem(
                                        id = it.channelId, title = it.channelName,
                                        logoUrl = it.logoUrl ?: "", kind = "LIVE",
                                        channelId = it.channelId, cmd = it.cmd
                                    )
                                }
                            )
                        )
                    }
                    val movies = progress.filter { it.episodeId.isEmpty() }
                    if (movies.isNotEmpty()) {
                        add(
                            HubRow(
                                "movies", "Recent Movies",
                                movies.map {
                                    HubItem(
                                        id = it.videoId,
                                        title = "Movie",
                                        kind = "MOVIE",
                                        videoId = it.videoId,
                                        remainingLabel = formatRemaining(it.durationMs - it.positionMs),
                                        progressRatio = if (it.durationMs > 0)
                                            it.positionMs.toFloat() / it.durationMs else 0f
                                    )
                                }
                            )
                        )
                    }
                    val episodes = progress.filter { it.episodeId.isNotEmpty() }
                    if (episodes.isNotEmpty()) {
                        add(
                            HubRow(
                                "episodes", "Recent TV Shows",
                                episodes.map {
                                    HubItem(
                                        id = it.videoId,
                                        title = "S${it.seasonNumber}:E${it.episodeNumber}",
                                        kind = "SERIES",
                                        badge = "S${it.seasonNumber}:E${it.episodeNumber}",
                                        videoId = it.videoId,
                                        remainingLabel = formatRemaining(it.durationMs - it.positionMs),
                                        progressRatio = if (it.durationMs > 0)
                                            it.positionMs.toFloat() / it.durationMs else 0f
                                    )
                                }
                            )
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        rows = rows,
                        profileId = session.activePortal.value?.let { _ ->
                            _uiState.value.profileId
                        } ?: _uiState.value.profileId,
                        serverId = session.activePortal.value?.serverId ?: 0
                    )
                }
                // Capture profileId synchronously from the flow pair
                _uiState.update { st -> st.copy(profileId = prefs.activeProfileIdFlow.let { f ->
                    var v = st.profileId
                    f.collect { x -> v = x }; v
                }) }
            }
        }
    }

    fun setHero(item: HubItem?) {
        _uiState.update { it.copy(hero = item) }
    }

    fun playLive(item: HubItem, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val cmd = item.cmd.ifEmpty {
                // Fallback: resolve fresh cmd from portal channels
                liveTvRepository.getAllChannels().getOrDefault(
                    com.itv.blockbuster.domain.model.PortalPage(emptyList(), 0)
                ).items.firstOrNull { it.id == item.channelId }?.cmd ?: ""
            }
            val url = liveTvRepository.createStreamLink(cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val p = _uiState.value.profileId
            val s = _uiState.value.serverId
            recentDao.clearRecent(p, s)
            progressDao.clearAll(p, s)
        }
    }
}

@Composable
fun RecentsHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String) -> Unit,
    viewModel: RecentsHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isTv = formFactor == FormFactor.TV
    var showClearDialog by remember { mutableStateOf(false) }

    UnifiedHubScreen(
        rows = state.rows,
        hero = if (isTv) state.hero else null,
        emptyIcon = {
            Icon(Icons.Default.History, null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
        },
        emptyText = "Nothing watched yet",
        onItemClicked = { item ->
            when (item.kind) {
                "LIVE" -> viewModel.playLive(item) { url -> onPlayLive(url, item.channelId) }
                else -> onOpenVod(item.videoId.ifEmpty { item.id })
            }
        },
        onItemFocused = { if (isTv) viewModel.setHero(it) },
        onClearAll = { showClearDialog = true }
    )

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = BbBackground,
            title = { Text("Clear all recents?", color = BbTextSecondary) },
            text = { Text("This removes viewing history for this profile on this portal.", color = BbTextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    }
                ) { Text("Clear All", color = BbDestructive, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = BbTextSecondary)
                }
            }
        )
    }
}