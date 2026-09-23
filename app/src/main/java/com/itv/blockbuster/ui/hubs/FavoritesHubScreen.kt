package com.itv.blockbuster.ui.hubs

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.itv.blockbuster.ui.components.DefaultCarouselHeaderBlockHeight
import com.itv.blockbuster.ui.components.DefaultCarouselHeaderFontSize
import com.itv.blockbuster.ui.components.DefaultCarouselHeaderWeight
import com.itv.blockbuster.ui.components.DefaultChannelTileLogoHeight
import com.itv.blockbuster.ui.components.DefaultChannelTileNameFontSize
import com.itv.blockbuster.ui.components.DefaultChannelTileNowPlayingFontSize
import com.itv.blockbuster.ui.components.DefaultChannelTileNumberFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileLogoHeight
import com.itv.blockbuster.ui.components.PortraitChannelTileNameFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileNowPlayingFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileNumberFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileWidth
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.components.HeroContent
import com.itv.blockbuster.ui.components.rememberCarouselBlockHeight
import com.itv.blockbuster.ui.components.rememberChannelCarouselItemWidth
import com.itv.blockbuster.ui.components.rememberDefaultCarouselItemWidth
import com.itv.blockbuster.ui.components.rememberTopPinningBringIntoViewSpec
import com.itv.blockbuster.util.VodNavigationCache
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.RailCollapsedWidth
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT
    val collapsedMenuWidth = if (isPortrait) 0.dp else RailCollapsedWidth

    // FIX: D-pad focus - the LazyColumn's scroll position survives navigating
    // away and back (rememberLazyListState is rememberSaveable-backed, and
    // Navigation Compose's restoreState=true preserves that across tab
    // switches). If the user had scrolled down before leaving, the first
    // section's row can be scrolled off-screen and not actually laid out on
    // return, so its FocusRequester can never accept focus - hoisting the
    // state here lets the effect below scroll back to the top before
    // attempting to focus it.
    val listState = rememberLazyListState()
    val liveRowState = rememberLazyListState()
    val moviesRowState = rememberLazyListState()
    val seriesRowState = rememberLazyListState()

    // UI consistency: sections now render Live TV -> TV Shows -> Movies,
    // matching the order Home/Live TV/Movies/TV Shows already agree on -
    // whichever is non-empty first is the one whose first item receives
    // initial focus.
    val firstSection = when {
        liveChannels.isNotEmpty() -> "live"
        seriesItems.isNotEmpty() -> "series"
        movieItems.isNotEmpty() -> "movies"
        else -> null
    }
    // D-pad focus: whichever non-empty section renders LAST - its items
    // block D-pad Down from escaping past the bottom of the content into
    // the rail (see CarouselRow's isLastRow for the same fix elsewhere).
    val lastSection = when {
        movieItems.isNotEmpty() -> "movies"
        seriesItems.isNotEmpty() -> "series"
        liveChannels.isNotEmpty() -> "live"
        else -> null
    }
    val firstItemRequester = remember(route) { FocusRequester() }
    if (firstSection != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
        // FIX: unregister on dispose - see FocusRegistry.unregisterFirstItem's
        // doc comment. Without this, a stale/detached requester could still
        // be handed out as a `down = ...` focus target after this section
        // is torn down, crashing uncatchably on the next real D-pad press.
        DisposableEffect(route, firstItemRequester) {
            onDispose { FocusRegistry.unregisterFirstItem(route, firstItemRequester) }
        }
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

    // NEW: persistent, focus-following hero banner (TV/large form factor
    // only - phones in portrait skip it, same as Home/Live TV), matching
    // the UI-consistency work already done for Home/Movies/TV Shows/Live
    // TV. HeroContent generalizes over both a VOD item and a live channel
    // so ONE hero can follow focus across all three sections here, falling
    // back to the first section's own first item until something's
    // actually been focused.
    var focusedHero by remember { mutableStateOf<HeroContent?>(null) }
    val defaultHero: HeroContent? = when (firstSection) {
        "live" -> liveChannels.firstOrNull()?.let { HeroContent.LiveChannel(it) }
        "series" -> seriesItems.firstOrNull()?.let { HeroContent.Vod(it) }
        "movies" -> movieItems.firstOrNull()?.let { HeroContent.Vod(it) }
        else -> null
    }
    val heroContent = focusedHero ?: defaultHero
    // Clears a stale focused-hero item once it's no longer present in any
    // list - e.g. right after it's un-favorited via the hero's own delete
    // button below, or via long-pressing it in the carousel - otherwise the
    // hero would keep showing an item that's already gone.
    LaunchedEffect(movieItems, seriesItems, liveChannels) {
        val current = focusedHero
        val stillPresent = when (current) {
            is HeroContent.Vod -> movieItems.any { it.id == current.item.id } || seriesItems.any { it.id == current.item.id }
            is HeroContent.LiveChannel -> liveChannels.any { it.id == current.channel.id }
            null -> true
        }
        if (!stillPresent) focusedHero = null
    }
    // D-pad focus: Up target for the first carousel row - there's no filter/
    // search top bar on this screen (unlike Home/Live TV) to point at, so
    // this points at the hero's own delete button instead, the only other
    // focusable element above the carousels.
    val heroDeleteFocusRequester = remember { FocusRequester() }

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
            // NEW: same hero-overlap layout Home/Live TV use - a non-
            // focusable hero pinned to the top half of the screen (TV/
            // landscape only), with a fixed 2-row carousel window
            // positioned so its first row's vertical center lands exactly
            // on the hero's bottom edge (carouselsTopOffset == block),
            // scrolling up over it. See HomeScreen's LargeHomeHeroLayout
            // for the full derivation - this mirrors it exactly so the
            // carousels here sit at the same on-screen position as every
            // other screen.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val heroHeight = maxHeight * 0.5f
                val block = rememberCarouselBlockHeight(maxHeight)
                val itemWidth = rememberDefaultCarouselItemWidth(maxHeight)
                // FIX: portrait uses the exact same fixed tile width Live
                // TV's portrait grid uses (PortraitChannelTileWidth), not
                // the ~7-column TV/landscape carousel formula - see the
                // matching comment on RecentsHubScreen.
                val channelItemWidth = if (isPortrait) PortraitChannelTileWidth else rememberChannelCarouselItemWidth(collapsedMenuWidth)
                val carouselsTopOffset = block
                val carouselsViewportHeight = block * 2

                if (!isPortrait && heroContent != null) {
                    HeroBanner(
                        hero = heroContent,
                        height = heroHeight,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    // NEW: delete overlay on the hero - un-favorites
                    // whichever item is currently shown in the hero,
                    // without needing to long-press it in the carousel
                    // first. Positioned like Live TV's overlaid filter row
                    // (top-right of the hero).
                    IconButton(
                        onClick = {
                            when (val hero = heroContent) {
                                is HeroContent.Vod -> viewModel.toggleFavorite(hero.item)
                                is HeroContent.LiveChannel -> viewModel.toggleLiveFavorite(hero.channel)
                                null -> {}
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 24.dp)
                            .focusRequester(heroDeleteFocusRequester)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove from Favorites", tint = BbDestructive)
                    }
                }

                // FIX: wraps this LazyColumn's own focus-triggered scrolling
                // in the same top-pinning BringIntoViewSpec Home/Live TV/
                // Movies/TV Shows use, so moving focus into the next/
                // previous section snaps it fully flush with the top of
                // the viewport instead of Compose's default "scroll the
                // minimum needed" behavior.
                CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTopPinningBringIntoViewSpec()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .then(
                                if (isPortrait) Modifier.fillMaxHeight()
                                else Modifier.padding(top = carouselsTopOffset).height(carouselsViewportHeight)
                            )
                    ) {
                        if (liveChannels.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(DefaultCarouselHeaderBlockHeight),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Live TV",
                                        color = BbTextPrimary,
                                        fontSize = DefaultCarouselHeaderFontSize,
                                        fontWeight = DefaultCarouselHeaderWeight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                }
                            }
                            item {
                                NetflixStyleCarousel(
                                    data = liveChannels, // FIX: renamed from 'items' to 'data'
                                    collapsedMenuWidth = collapsedMenuWidth,
                                    itemWidth = channelItemWidth,
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
                                    ChannelTile(
                                        channel = channel,
                                        isFavorite = favoriteIds.contains(channel.id),
                                        modifier = Modifier
                                            .width(channelItemWidth)
                                            .focusRequester(itemFocusRequester)
                                            .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                            .then(
                                            if (lastSection == "live") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                        ).then(
                                            if (firstSection == "live" && !isPortrait) Modifier.focusProperties { up = heroDeleteFocusRequester }
                                            else if (firstSection == "live") Modifier.focusProperties { up = FocusRequester.Cancel }
                                            else Modifier
                                        ),
                                        onClick = {
                                            FocusRegistry.rememberClickedItem(route, channel.id)
                                            viewModel.getStreamUrl(channel.cmd) { url ->
                                                if (url.isNotEmpty()) onPlayLive(url, channel.id)
                                            }
                                        },
                                        // FIX: matches Live TV's portrait
                                        // grid tile sizing exactly (same
                                        // Portrait* constants) - see
                                        // ChannelTile's sizing doc comment.
                                        logoHeight = if (isPortrait) PortraitChannelTileLogoHeight else DefaultChannelTileLogoHeight,
                                        nameFontSize = if (isPortrait) PortraitChannelTileNameFontSize else DefaultChannelTileNameFontSize,
                                        nowPlayingFontSize = if (isPortrait) PortraitChannelTileNowPlayingFontSize else DefaultChannelTileNowPlayingFontSize,
                                        numberFontSize = if (isPortrait) PortraitChannelTileNumberFontSize else DefaultChannelTileNumberFontSize,
                                        // FIX: was a no-op - long-pressing a
                                        // live channel here silently did
                                        // nothing, unlike the Movies/TV
                                        // Shows sections below (which
                                        // already call toggleFavorite on
                                        // long-press). Favorites' long-press
                                        // should remove the item directly
                                        // for every content type - no menu,
                                        // that's Recents' job.
                                        onLongClick = { viewModel.toggleLiveFavorite(channel) },
                                        onFavoriteIconClick = { viewModel.toggleLiveFavorite(channel) },
                                        onFocus = { focusedHero = HeroContent.LiveChannel(channel) }
                                    )
                                }
                            }
                        }
                        if (seriesItems.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(DefaultCarouselHeaderBlockHeight),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "TV Shows",
                                        color = BbTextPrimary,
                                        fontSize = DefaultCarouselHeaderFontSize,
                                        fontWeight = DefaultCarouselHeaderWeight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                }
                            }
                            item {
                                NetflixStyleCarousel(
                                    data = seriesItems, // FIX: renamed from 'items' to 'data'
                                    collapsedMenuWidth = collapsedMenuWidth,
                                    itemWidth = itemWidth,
                                    itemSpacing = 12.dp,
                                    state = seriesRowState,
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
                                            .width(itemWidth)
                                            .focusRequester(itemFocusRequester)
                                            .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                            .then(
                                            if (lastSection == "series") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                        ).then(
                                            if (firstSection == "series" && !isPortrait) Modifier.focusProperties { up = heroDeleteFocusRequester }
                                            else if (firstSection == "series") Modifier.focusProperties { up = FocusRequester.Cancel }
                                            else Modifier
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
                                        onFavoriteIconClick = { viewModel.toggleFavorite(item) },
                                        onFocus = { focusedHero = HeroContent.Vod(item) }
                                    )
                                }
                            }
                        }
                        if (movieItems.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(DefaultCarouselHeaderBlockHeight),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "Movies",
                                        color = BbTextPrimary,
                                        fontSize = DefaultCarouselHeaderFontSize,
                                        fontWeight = DefaultCarouselHeaderWeight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                }
                            }
                            item {
                                NetflixStyleCarousel(
                                    data = movieItems, // FIX: renamed from 'items' to 'data'
                                    collapsedMenuWidth = collapsedMenuWidth,
                                    itemWidth = itemWidth,
                                    itemSpacing = 12.dp,
                                    state = moviesRowState,
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
                                            .width(itemWidth)
                                            .focusRequester(itemFocusRequester)
                                            .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                                            .then(
                                            if (lastSection == "movies") Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                                        ).then(
                                            if (firstSection == "movies" && !isPortrait) Modifier.focusProperties { up = heroDeleteFocusRequester }
                                            else if (firstSection == "movies") Modifier.focusProperties { up = FocusRequester.Cancel }
                                            else Modifier
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
                                        onFavoriteIconClick = { viewModel.toggleFavorite(item) },
                                        onFocus = { focusedHero = HeroContent.Vod(item) }
                                    )
                                }
                            }
                        }
                        item(key = "bottom_spacer") { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}
