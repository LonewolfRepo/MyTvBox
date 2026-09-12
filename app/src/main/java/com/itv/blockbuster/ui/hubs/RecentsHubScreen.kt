package com.itv.blockbuster.ui.hubs

import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.components.PosterCard
import com.itv.blockbuster.ui.components.NetflixStyleCarousel
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.VodNavigationCache
import kotlinx.coroutines.launch

private sealed class MenuTarget {
    data class Vod(val item: PortalVodItem) : MenuTarget()
    data class Live(val channel: PortalChannel) : MenuTarget()
}

@Composable
fun RecentsHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String, String) -> Unit,
    viewModel: RecentsHubViewModel = hiltViewModel(),
    // D-pad focus: this screen's route, used to hand focus off from the rail
    // to its first item once content has loaded (see AppShell/FocusRegistry).
    route: String = Routes.RECENT
) {
    val movieItems by viewModel.movieItems.collectAsState()
    val seriesItems by viewModel.seriesItems.collectAsState()
    val liveChannels by viewModel.liveChannels.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }

    val formFactor = rememberFormFactor()
    val collapsedMenuWidth = if (formFactor == FormFactor.MOBILE_PORTRAIT) 0.dp else 84.dp

    // FIX: D-pad focus - the LazyColumn's scroll position survives navigating
    // away and back (rememberLazyListState is rememberSaveable-backed, and
    // Navigation Compose's restoreState=true preserves that across tab
    // switches). If the user had scrolled down before leaving, the first
    // section's row can be scrolled off-screen and not actually laid out on
    // return, so its FocusRequester can never accept focus - hoisting the
    // state here lets the effect below scroll back to the top before
    // attempting to focus it.
    val listState = rememberLazyListState()

    // D-pad focus: hoisted per-SECTION scroll states (each section's own
    // horizontal NetflixStyleCarousel), separate from the outer LazyColumn's
    // listState above. Needed specifically because Recents re-sorts an item
    // to the FRONT of its section the moment it's played - if that row's
    // own horizontal scroll position was left showing later items (e.g. the
    // user had scrolled right before clicking something further along),
    // the promoted item at index 0 falls outside the LazyRow's composed
    // range and never gets laid out, so restoreClickedItemFocus below can
    // never find a live FocusRequester for it no matter how long it
    // retries. Scrolling each row back to index 0 before attempting the
    // restore guarantees the front item - which recently-played items
    // always become - is actually composed.
    val liveRowState = rememberLazyListState()
    val moviesRowState = rememberLazyListState()
    val seriesRowState = rememberLazyListState()

    // Sections render in this order (Live TV, Movies, TV Shows) - whichever
    // is non-empty first is the one whose first item receives initial focus.
    val firstSection = when {
        liveChannels.isNotEmpty() -> "live"
        movieItems.isNotEmpty() -> "movies"
        seriesItems.isNotEmpty() -> "series"
        else -> null
    }
    // D-pad focus: whichever non-empty section renders LAST - its items
    // block D-pad Down from escaping past the bottom of the content into
    // the rail (see CarouselRow's isLastRow for the same fix elsewhere).
    val lastSection = when {
        seriesItems.isNotEmpty() -> "series"
        movieItems.isNotEmpty() -> "movies"
        liveChannels.isNotEmpty() -> "live"
        else -> null
    }
    val firstItemRequester = remember(route) { FocusRequester() }
    if (firstSection != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
    }
    LaunchedEffect(firstSection) {
        Log.d("DpadFocus", "RecentsHubScreen(route=$route): firstSection=$firstSection")
        if (firstSection != null) FocusRegistry.notifyContentReady(route)
    }

    // D-pad focus: on resume (e.g. Back popping a VOD detail screen or the
    // player pushed from this one), restore focus onto the exact item that
    // was clicked - no-ops unless RailShell's route-change handling armed
    // this route for restoration (see FocusRegistry.armRestoreFocus/
    // restoreClickedItemFocus and AppShell.kt).
    //
    // FIX: scroll all three section rows back to index 0 FIRST, before
    // attempting the restore. Recents re-sorts whatever was just played to
    // the FRONT of its section - if that row's own horizontal scroll
    // position was left showing later items, the promoted item at index 0
    // falls outside the LazyRow's composed range and is never laid out, so
    // no FocusRequester for it exists yet no matter how long
    // restoreClickedItemFocus retries. scrollToItem(0) is an instant jump
    // (not animated), so this doesn't produce a visible scroll animation
    // before the focus lands - if the clicked item wasn't actually
    // promoted (e.g. just viewed, not played), it's still found wherever
    // it already was since these scrolls only affect what's laid out, not
    // which item restoreClickedItemFocus looks for.
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRestoreScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                focusRestoreScope.launch {
                    if (FocusRegistry.awaitPendingRestore(route)) {
                        liveRowState.scrollToItem(0)
                        moviesRowState.scrollToItem(0)
                        seriesRowState.scrollToItem(0)
                    }
                    FocusRegistry.restoreClickedItemFocus(route)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (liveChannels.isNotEmpty()) {
                    item { Text("Recent Live TV", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        NetflixStyleCarousel(
                            data = liveChannels, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 160.dp,
                            itemSpacing = 12.dp,
                            state = liveRowState,
                            key = { it.id }
                        ) { channel ->
                            val isFirstItem = firstSection == "live" && channel == liveChannels.firstOrNull()
                            // D-pad focus: stable, registered requester keyed
                            // by (route, channel.id) so Back from the player
                            // can restore focus onto the exact channel that
                            // was clicked.
                            val itemFocusRequester = remember(channel.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, channel.id, itemFocusRequester)
                            Box {
                                ChannelTile(
                                    channel = channel,
                                    isFavorite = favoriteIds.contains(channel.id),
                                    modifier = Modifier
                                        .size(160.dp)
                                        .focusRequester(itemFocusRequester)
                                        .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                        .then(
                                        if (lastSection == "live") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                    ).then(
                                        if (firstSection == "live") Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier
                                    ),
                                    onClick = {
                                        FocusRegistry.rememberClickedItem(route, channel.id)
                                        viewModel.getStreamUrl(channel.cmd) { url -> if (url.isNotEmpty()) onPlayLive(url, channel.id) }
                                    },
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
                if (movieItems.isNotEmpty()) {
                    item { Text("Recent Movies", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        NetflixStyleCarousel(
                            data = movieItems, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 140.dp,
                            itemSpacing = 12.dp,
                            state = moviesRowState,
                            key = { it.id }
                        ) { item ->
                            val isFirstItem = firstSection == "movies" && item == movieItems.firstOrNull()
                            // D-pad focus: stable, registered requester keyed
                            // by (route, item.id) so Back from the VOD detail
                            // screen can restore focus onto the exact poster
                            // that was clicked.
                            val itemFocusRequester = remember(item.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
                            Box {
                                val progressRatio = progressMap[item.id]?.let {
                                    if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                } ?: 0f
                                PosterCard(
                                    item = item,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .focusRequester(itemFocusRequester)
                                        .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                        .then(
                                        if (lastSection == "movies") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                    ).then(
                                        if (firstSection == "movies") Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier
                                    ),
                                    isFavorite = favoriteIds.contains(item.id),
                                    progressRatio = progressRatio,
                                    onClick = {
                                        FocusRegistry.rememberClickedItem(route, item.id)
                                        VodNavigationCache.currentItem = item
                                        val type = item.contentType.ifEmpty { if (item.isSeries) "series" else "vod" }
                                        onOpenVod(item.id, type)
                                    },
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
                if (seriesItems.isNotEmpty()) {
                    item { Text("Recent TV Shows", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        NetflixStyleCarousel(
                            data = seriesItems, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 140.dp,
                            itemSpacing = 12.dp,
                            state = seriesRowState,
                            key = { it.id }
                        ) { item ->
                            val isFirstItem = firstSection == "series" && item == seriesItems.firstOrNull()
                            // D-pad focus: same as the movies section above.
                            val itemFocusRequester = remember(item.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
                            Box {
                                val progressRatio = progressMap[item.id]?.let {
                                    if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                } ?: 0f
                                PosterCard(
                                    item = item,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .focusRequester(itemFocusRequester)
                                        .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                        .then(
                                        if (lastSection == "series") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                    ).then(
                                        if (firstSection == "series") Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier
                                    ),
                                    isFavorite = favoriteIds.contains(item.id),
                                    progressRatio = progressRatio,
                                    onClick = {
                                        FocusRegistry.rememberClickedItem(route, item.id)
                                        VodNavigationCache.currentItem = item
                                        val type = item.contentType.ifEmpty { if (item.isSeries) "series" else "vod" }
                                        onOpenVod(item.id, type)
                                    },
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