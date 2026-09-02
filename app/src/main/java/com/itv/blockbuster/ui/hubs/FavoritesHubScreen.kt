package com.itv.blockbuster.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.FavoriteDao
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

data class HubUiState(
    val rows: List<HubRow> = emptyList(),
    val hero: HubItem? = null,
    val profileId: Int = -1,
    val serverId: Int = 0
)

@HiltViewModel
class FavoritesHubViewModel @Inject constructor(
    private val favoriteDao: FavoriteDao,
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
                    favoriteDao.getFavorites(p, s, "LIVE"),
                    favoriteDao.getFavorites(p, s, "VOD"),
                    favoriteDao.getFavorites(p, s, "SERIES")
                ) { live, vod, series -> Triple(live, vod, series) }
            }.collect { (live, vod, series) ->
                val rows = buildList {
                    if (live.isNotEmpty()) {
                        add(
                            HubRow(
                                "live", "Favorite Live TV",
                                live.map {
                                    HubItem(
                                        id = it.itemId, title = it.title, logoUrl = it.logoUrl,
                                        kind = "LIVE", channelId = it.itemId, cmd = it.cmd
                                    )
                                }
                            )
                        )
                    }
                    if (vod.isNotEmpty()) {
                        add(
                            HubRow(
                                "vod", "Favorite Movies",
                                vod.map {
                                    HubItem(
                                        id = it.itemId, title = it.title, logoUrl = it.logoUrl,
                                        kind = "MOVIE", videoId = it.itemId, year = it.year
                                    )
                                }
                            )
                        )
                    }
                    if (series.isNotEmpty()) {
                        add(
                            HubRow(
                                "series", "Favorite TV Shows",
                                series.map {
                                    HubItem(
                                        id = it.itemId, title = it.title, logoUrl = it.logoUrl,
                                        kind = "SERIES", videoId = it.itemId, year = it.year
                                    )
                                }
                            )
                        )
                    }
                }
                _uiState.update {
                    it.copy(rows = rows, profileId = prefs.activeProfileIdFlow.let { f ->
                        var v = it.profileId; f.collect { x -> v = x; }; v
                    }, serverId = session.activePortal.value?.serverId ?: 0)
                }
            }
        }
    }

    fun setHero(item: HubItem?) {
        _uiState.update { it.copy(hero = item) }
    }

    fun playLive(item: HubItem, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val url = liveTvRepository.createStreamLink(item.cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val p = _uiState.value.profileId
            val s = _uiState.value.serverId
            favoriteDao.clearFavorites(p, s, "LIVE")
            favoriteDao.clearFavorites(p, s, "VOD")
            favoriteDao.clearFavorites(p, s, "SERIES")
        }
    }
}

@Composable
fun FavoritesHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String) -> Unit,
    viewModel: FavoritesHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isTv = formFactor == FormFactor.TV
    var showClearDialog by remember { mutableStateOf(false) }

    UnifiedHubScreen(
        rows = state.rows,
        hero = if (isTv) state.hero else null,
        emptyIcon = {
            Icon(Icons.Default.StarBorder, null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
        },
        emptyText = "No favorites yet",
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
            title = { Text("Clear all favorites?", color = BbTextSecondary) },
            text = { Text("This removes every favorite for this profile on this portal.", color = BbTextMuted) },
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

// =====================================================================
// SHARED HUB LAYOUT
// =====================================================================

@Composable
fun UnifiedHubScreen(
    rows: List<HubRow>,
    hero: HubItem?,
    emptyIcon: @Composable () -> Unit,
    emptyText: String,
    onItemClicked: (HubItem) -> Unit,
    onItemFocused: (HubItem) -> Unit,
    onClearAll: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                emptyIcon()
                Text(emptyText, color = BbTextMuted, fontSize = 15.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (hero != null || rows.isNotEmpty()) {
                    item(key = "hero") { HubHero(hero) }
                }
                items(rows, key = { it.id }) { row ->
                    HubRowComposable(
                        row = row,
                        onItemClicked = onItemClicked,
                        onItemFocused = onItemFocused
                    )
                }
                item { Box(Modifier.padding(32.dp)) }
            }
        }

        IconButton(
            onClick = onClearAll,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Delete, "Clear all", tint = BbDestructive)
        }
    }
}