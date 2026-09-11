package com.itv.blockbuster.ui.hubs

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.components.PosterCard
import com.itv.blockbuster.ui.components.NetflixStyleCarousel
import com.itv.blockbuster.util.VodNavigationCache
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.local.dao.FavoriteDao
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun FavoritesHubScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenVod: (String, String) -> Unit,
    viewModel: FavoritesHubViewModel = hiltViewModel(),
    // D-pad focus: this screen's route, used to hand focus off from the rail
    // to its first item once content has loaded (see AppShell/FocusRegistry).
    route: String = Routes.MY_LIST
) {
    val movieItems by viewModel.movieItems.collectAsState()
    val seriesItems by viewModel.seriesItems.collectAsState()
    val liveChannels by viewModel.liveChannels.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()

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
        Log.d("DpadFocus", "FavoritesHubScreen(route=$route): firstSection=$firstSection")
        if (firstSection != null) FocusRegistry.notifyContentReady(route)
    }

    // D-pad focus: on resume (e.g. Back popping a VOD detail screen or the
    // player pushed from this one), restore focus onto the exact item that
    // was clicked - no-ops unless RailShell's route-change handling armed
    // this route for restoration (see FocusRegistry.armRestoreFocus/
    // restoreClickedItemFocus and AppShell.kt).
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRestoreScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                focusRestoreScope.launch { FocusRegistry.restoreClickedItemFocus(route) }
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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                        NetflixStyleCarousel(
                            data = liveChannels, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 160.dp,
                            itemSpacing = 12.dp,
                            key = { it.id }
                        ) { channel ->
                            val isFirstItem = firstSection == "live" && channel == liveChannels.firstOrNull()
                            // D-pad focus: stable, registered requester keyed
                            // by (route, channel.id) so Back from the player
                            // can restore focus onto the exact channel that
                            // was clicked.
                            val itemFocusRequester = remember(channel.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, channel.id, itemFocusRequester)
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
                        NetflixStyleCarousel(
                            data = movieItems, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 140.dp,
                            itemSpacing = 12.dp,
                            key = { it.id }
                        ) { item ->
                            val progressRatio = progressMap[item.id]?.let {
                                if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            } ?: 0f
                            val isFirstItem = firstSection == "movies" && item == movieItems.firstOrNull()
                            // D-pad focus: stable, registered requester keyed
                            // by (route, item.id) so Back from the VOD detail
                            // screen can restore focus onto the exact poster
                            // that was clicked.
                            val itemFocusRequester = remember(item.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
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
                                onLongClick = { viewModel.toggleFavorite(item) },
                                onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
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
                        NetflixStyleCarousel(
                            data = seriesItems, // FIX: renamed from 'items' to 'data'
                            collapsedMenuWidth = collapsedMenuWidth,
                            itemWidth = 140.dp,
                            itemSpacing = 12.dp,
                            key = { it.id }
                        ) { item ->
                            val progressRatio = progressMap[item.id]?.let {
                                if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            } ?: 0f
                            val isFirstItem = firstSection == "series" && item == seriesItems.firstOrNull()
                            // D-pad focus: same as the movies section above.
                            val itemFocusRequester = remember(item.id) { FocusRequester() }
                            FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
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
                                    val type = item.contentType.ifEmpty { "series" }
                                    onOpenVod(item.id, type)
                                },
                                onLongClick = { viewModel.toggleFavorite(item) },
                                onFavoriteIconClick = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}