package com.itv.blockbuster.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.components.PosterCard
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.VodNavigationCache

private sealed class MenuTarget {
    data class Vod(val item: PortalVodItem) : MenuTarget()
    data class Live(val channel: PortalChannel) : MenuTarget()
}

@Composable
fun RecentsHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String, String) -> Unit,
    viewModel: RecentsHubViewModel = hiltViewModel()
) {
    val movieItems by viewModel.movieItems.collectAsState()
    val seriesItems by viewModel.seriesItems.collectAsState()
    val liveChannels by viewModel.liveChannels.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (movieItems.isEmpty() && seriesItems.isEmpty() && liveChannels.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.History, null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Nothing watched yet", color = BbTextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Items you watch will automatically appear here.",
                    color = BbTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (liveChannels.isNotEmpty()) {
                    item { Text("Recent Live TV", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(liveChannels, key = { it.id }) { channel ->
                                Box {
                                    ChannelTile(
                                        channel = channel,
                                        isFavorite = favoriteIds.contains(channel.id),
                                        onClick = { viewModel.getStreamUrl(channel.cmd) { url -> if (url.isNotEmpty()) onPlayLive(url, channel.id) } },
                                        onLongClick = { menuTarget = MenuTarget.Live(channel) },
                                        onFavoriteIconClick = { viewModel.toggleLiveFavorite(channel) }
                                    )
                                    DropdownMenu(
                                        expanded = menuTarget is MenuTarget.Live && (menuTarget as MenuTarget.Live).channel.id == channel.id,
                                        onDismissRequest = { menuTarget = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (favoriteIds.contains(channel.id)) "Remove from Favorites" else "Add to Favorites") },
                                            onClick = {
                                                viewModel.toggleLiveFavorite(channel)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (favoriteIds.contains(channel.id)) Icons.Default.StarBorder else Icons.Default.Star,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete from Recent") },
                                            onClick = {
                                                viewModel.deleteLiveRecent(channel.id)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = BbDestructive)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (movieItems.isNotEmpty()) {
                    item { Text("Recent Movies", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(movieItems, key = { it.id }) { item ->
                                Box {
                                    PosterCard(
                                        item = item,
                                        isFavorite = favoriteIds.contains(item.id),
                                        onClick = { VodNavigationCache.currentItem = item;
                                            val type = item.contentType.ifEmpty { if (item.isSeries) "series" else "vod" }
                                            onOpenVod(item.id, type) },
                                        onLongClick = { menuTarget = MenuTarget.Vod(item) },
                                        onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                                    )
                                    DropdownMenu(
                                        expanded = menuTarget is MenuTarget.Vod && (menuTarget as MenuTarget.Vod).item.id == item.id,
                                        onDismissRequest = { menuTarget = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (favoriteIds.contains(item.id)) "Remove from Favorites" else "Add to Favorites") },
                                            onClick = {
                                                viewModel.toggleFavorite(item)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (favoriteIds.contains(item.id)) Icons.Default.StarBorder else Icons.Default.Star,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete from Recent") },
                                            onClick = {
                                                viewModel.deleteRecent(item)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = BbDestructive)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (seriesItems.isNotEmpty()) {
                    item { Text("Recent TV Shows", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(seriesItems, key = { it.id }) { item ->
                                Box {
                                    PosterCard(
                                        item = item,
                                        isFavorite = favoriteIds.contains(item.id),
                                        onClick = { VodNavigationCache.currentItem = item;
                                            val type = item.contentType.ifEmpty { if (item.isSeries) "series" else "vod" }
                                            onOpenVod(item.id, type) },
                                        onLongClick = { menuTarget = MenuTarget.Vod(item) },
                                        onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                                    )
                                    DropdownMenu(
                                        expanded = menuTarget is MenuTarget.Vod && (menuTarget as MenuTarget.Vod).item.id == item.id,
                                        onDismissRequest = { menuTarget = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (favoriteIds.contains(item.id)) "Remove from Favorites" else "Add to Favorites") },
                                            onClick = {
                                                viewModel.toggleFavorite(item)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (favoriteIds.contains(item.id)) Icons.Default.StarBorder else Icons.Default.Star,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete from Recent") },
                                            onClick = {
                                                viewModel.deleteRecent(item)
                                                menuTarget = null
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = BbDestructive)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
        IconButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Delete, "Clear all", tint = BbDestructive)
        }
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = BbBackground,
                title = { Text("Clear all recents?", color = BbTextSecondary) },
                text = { Text("This removes viewing history. Playback progress for resume will be kept.", color = BbTextMuted) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                        Text("Clear All", color = BbDestructive, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = BbTextSecondary) }
                }
            )
        }
    }
}