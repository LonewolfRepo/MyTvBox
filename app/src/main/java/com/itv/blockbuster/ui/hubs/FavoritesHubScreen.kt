package com.itv.blockbuster.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.components.PosterCard
import com.itv.blockbuster.util.VodNavigationCache
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.style.TextAlign
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
import com.itv.blockbuster.ui.theme.BbTextPrimary
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

@Composable
fun FavoritesHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String, String) -> Unit,
    viewModel: FavoritesHubViewModel = hiltViewModel()
) {
    val movieItems by viewModel.movieItems.collectAsState()
    val seriesItems by viewModel.seriesItems.collectAsState()
    val liveChannels by viewModel.liveChannels.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (movieItems.isEmpty() && seriesItems.isEmpty() && liveChannels.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Star, null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No favorites yet", color = BbTextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the star icon on any movie, TV show, or channel to add it here.",
                    color = BbTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Live TV channels
                if (liveChannels.isNotEmpty()) {
                    item {
                        Text(
                            "Live TV",
                            color = BbTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(liveChannels, key = { it.id }) { channel ->
                                ChannelTile(
                                    channel = channel,
                                    isFavorite = favoriteIds.contains(channel.id),
                                    onClick = {
                                        viewModel.getStreamUrl(channel.cmd) { url ->
                                            if (url.isNotEmpty()) onPlayLive(url, channel.id)
                                        }
                                    },
                                    onLongClick = {},
                                    onFavoriteIconClick = { viewModel.toggleLiveFavorite(channel) }
                                )
                            }
                        }
                    }
                }

                // Movies carousel
                if (movieItems.isNotEmpty()) {
                    item {
                        Text(
                            "Movies",
                            color = BbTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(movieItems, key = { it.id }) { item ->
                                PosterCard(
                                    item = item,
                                    isFavorite = favoriteIds.contains(item.id),
                                    onClick = {
                                        VodNavigationCache.currentItem = item
                                        val type = item.contentType.ifEmpty { if (item.isSeries) "series" else "vod" }
                                        onOpenVod(item.id, type)
                                    },
                                    onLongClick = { viewModel.toggleFavorite(item) },
                                    onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                                )
                            }
                        }
                    }
                }

                // TV Shows carousel
                if (seriesItems.isNotEmpty()) {
                    item {
                        Text(
                            "TV Shows",
                            color = BbTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(seriesItems, key = { it.id }) { item ->
                                PosterCard(
                                    item = item,
                                    isFavorite = favoriteIds.contains(item.id),
                                    onClick = {
                                        VodNavigationCache.currentItem = item
                                        val type = item.contentType.ifEmpty { "series" }
                                        onOpenVod(item.id, type)
                                    },
                                    onLongClick = { viewModel.toggleFavorite(item) },
                                    onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
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