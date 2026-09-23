package com.itv.blockbuster.ui.components

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.RailCollapsedWidth
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.FocusEntry
import com.itv.blockbuster.util.claimFocusEntry

/**
 * Shared sizing so every ChannelTile in the app - Live TV's carousels/grid,
 * and Favorites/Recents' Live TV carousel - stays visually consistent
 * within its own form factor. TV/landscape (carousels, ~7 columns) uses the
 * compact Default* sizing; portrait (Live TV's grid, and Favorites/
 * Recents' carousel when on a phone) uses the larger Portrait* sizing -
 * bigger logo/number badge and bigger text, since portrait tiles have much
 * more room per item than a 7-across TV carousel does. Every portrait
 * caller passes the SAME Portrait* constants (and the same tile width, see
 * PortraitChannelTileWidth) so Live TV/Favorites/Recents' portrait tiles
 * are pixel-for-pixel identical rather than each screen picking its own.
 */
val DefaultChannelTileLogoHeight = 28.dp
val DefaultChannelTileNameFontSize = 11.sp
val DefaultChannelTileNowPlayingFontSize = 10.sp
val DefaultChannelTileNumberFontSize = 13.sp

val PortraitChannelTileWidth = 150.dp
val PortraitChannelTileLogoHeight = 40.dp
val PortraitChannelTileNameFontSize = 15.sp
val PortraitChannelTileNowPlayingFontSize = 13.sp
val PortraitChannelTileNumberFontSize = 18.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelTile(
    channel: PortalChannel,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteIconClick: () -> Unit = {},
    // NEW: mirrors PosterCard's onFocus - lets a caller (LiveTvScreen's
    // hero banner) track which channel currently has focus, same as Home's
    // hero already does for VOD posters.
    onFocus: (PortalChannel) -> Unit = {},
    modifier: Modifier = Modifier,
    // NEW: lets callers request the larger portrait sizing (see the
    // Portrait* constants above) instead of the compact TV/landscape
    // carousel defaults - logo, channel-number badge, name, and
    // now-playing text all scale together.
    logoHeight: Dp = DefaultChannelTileLogoHeight,
    nameFontSize: TextUnit = DefaultChannelTileNameFontSize,
    nowPlayingFontSize: TextUnit = DefaultChannelTileNowPlayingFontSize,
    numberFontSize: TextUnit = DefaultChannelTileNumberFontSize
) {
    // FIX: was a plain combinedClickable{} + separate later .focusable() +
    // .onFocusChanged{} - combinedClickable creates its OWN implicit focus
    // target internally, so that separate .focusable() could end up being a
    // SECOND, different focus node than the one D-pad focus actually lands
    // on - meaning `focused` (and therefore the border, AND the new onFocus
    // callback above) could silently never fire on some focus transitions.
    // Same fix already applied to PosterCard/SearchIconButton/dropdowns:
    // share one interactionSource between clickable and focusable.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocus(channel)
    }
    // FIX: was an instant Modifier.border() toggle - matches PosterCard's
    // animated border now (fade in/out instead of an abrupt pop), part of
    // making focus movement between carousel items feel smooth rather than
    // snappy/jumpy.
    val borderColor by animateColorAsState(
        targetValue = if (focused) BbAccent else Color.Transparent,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "channelTileBorder"
    )
    // FIX: D-pad long-press support - combinedClickable's own onLongClick
    // detection below is pointer/touch-gesture based and does NOT fire
    // from a held Enter/DPad-Center KEY event at all - see PosterCard's
    // matching comment for the full explanation. Manually tracks key-down/
    // key-up timing for the D-pad confirm keys instead.
    var pressStartUptimeMs by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            // NOTE: deliberately no scale/zoom animation here (previously
            // animateFloatAsState + graphicsLayer{scaleX/scaleY}) - matches
            // the same fix already applied to PosterCard: a focus-scale
            // transform visually "jumps" neighboring tiles even though it
            // doesn't change measured layout size. The border below is the
            // ONLY focus indicator now, drawn within this Box's existing
            // bounds, so switching focus between tiles never reflows or
            // visually shifts anything else on screen.
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(BbCard)
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
            // Positioned between combinedClickable and focusable - see
            // PosterCard's matching comment for why this ordering matters.
            .onKeyEvent { keyEvent ->
                if (keyEvent.key != Key.Enter && keyEvent.key != Key.NumPadEnter && keyEvent.key != Key.DirectionCenter) {
                    return@onKeyEvent false
                }
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        if (pressStartUptimeMs == 0L) pressStartUptimeMs = SystemClock.uptimeMillis()
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val startedAt = pressStartUptimeMs
                        pressStartUptimeMs = 0L
                        if (startedAt != 0L && SystemClock.uptimeMillis() - startedAt >= 400L) {
                            onLongClick()
                        } else {
                            onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
    ) {
        // FIX: compact 1x1 layout - logo/channel# badge at the TOP of the
        // card, horizontally centered (requested for visual balance),
        // followed by the channel name (single line, cut off with an
        // ellipsis if it doesn't fit) and the currently-playing program
        // name below it, both left-aligned. Previously this Column centered
        // a big 64dp logo and used 14sp SemiBold for the name - both
        // larger/heavier than every other card title in the app and
        // leaving little room for nowPlaying, unlike the compact
        // guide-style layout used elsewhere in the portal (logo -> name ->
        // now playing, top-aligned).
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // FIX: was Alignment.Start (logo/number sat flush left) -
            // centered horizontally per request, while the name/nowPlaying
            // text below stays left-aligned.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(logoHeight),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(logoHeight)
                            .widthIn(min = logoHeight * 1.3f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BbAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.number.ifEmpty { channel.id.take(4) },
                            color = BbAccent,
                            fontSize = numberFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // FIX: matches Home's PosterCard item-name styling (11sp, no
            // bold) for consistency across VOD posters and channel tiles -
            // was 14sp SemiBold, visibly larger/heavier than every other
            // card title in the app.
            Text(
                text = channel.name,
                color = BbTextPrimary,
                fontSize = nameFontSize,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.nowPlaying.isNotEmpty()) {
                Text(
                    text = channel.nowPlaying,
                    color = BbTextSecondary,
                    fontSize = nowPlayingFontSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Favorite Icon Overlay
        // FIX: removed the circular black backdrop per request - just the
        // star icon now, same as PosterCard's overlay.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(20.dp)
                .clickable(onClick = onFavoriteIconClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Shared channel-tile sizing so every carousel of channels (Live TV's "All
 * Categories" view, and now Favorites/Recents' Live TV section) fits the
 * same ~7 tiles per screen instead of each caller picking its own width -
 * same idea as CarouselComponents.kt's rememberDefaultCarouselItemWidth for
 * VOD posters. See ChannelCarouselRow's own comment (below, at its call
 * site) for the derivation: solving NetflixStyleCarousel's own start-padding
 * formula (focusAnchorLine = itemWidth*0.2 + itemSpacing) for ~7 fully-
 * visible items.
 */
@Composable
fun rememberChannelCarouselItemWidth(collapsedMenuWidth: Dp): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    return ((screenWidth - collapsedMenuWidth - ChannelCarouselItemSpacing * 8) / 7.2f)
        .coerceIn(110.dp, 170.dp)
}

/** Shared item spacing for every channel carousel - see ChannelCarouselRow. */
val ChannelCarouselItemSpacing = 12.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCarouselRow(
    title: String,
    channels: List<PortalChannel>,
    favoriteIds: Set<String> = emptySet(),
    onChannelClick: (PortalChannel) -> Unit,
    onChannelLongClick: (PortalChannel) -> Unit,
    onFavoriteIconClick: (PortalChannel) -> Unit = {},
    // D-pad focus: when this is the screen's very first/top row, its first
    // item is registered as the target FocusRegistry.notifyContentReady(route)
    // shifts focus onto once the screen finishes loading. When this is the
    // LAST row, its items block D-pad Down from escaping past the bottom of
    // the content into the rail (see CarouselRow's isLastRow for the same
    // fix elsewhere). When this is the top row, Up is pointed at
    // upEscapeTarget (the screen's own top bar category filter) when
    // provided, rather than relying on Compose's default spatial search -
    // which was picking the rail instead, matching CarouselRow's fix.
    route: String? = null,
    isFirstRow: Boolean = false,
    isLastRow: Boolean = false,
    upEscapeTarget: FocusRequester? = null,
    // D-pad focus: replaces the old isFirstRow + route ->
    // FocusRegistry.registerFirstItem()/notifyContentReady() handoff - see
    // FocusEntry's own doc comment. Only meaningful when isFirstRow is also
    // true; null on every other row (which never claims initial focus
    // regardless of these). Caller passes its own focusTarget/focusClaimId
    // (see LiveTvScreen) so this row's actual first-visible channel can
    // claim focus via the same claimFocusEntry mechanism every other
    // FirstItem consumer in the app uses now, rather than a bespoke one
    // just for this component.
    focusTarget: FocusEntry? = null,
    focusClaimId: Any? = null,
    // NEW: mirrors CarouselRow's onItemFocused - lets a caller (LiveTvScreen's
    // hero banner) keep a persistent hero banner following whichever channel
    // tile currently has focus, matching Home's existing VOD hero behavior.
    onChannelFocused: (PortalChannel) -> Unit = {}
) {
    val formFactor = rememberFormFactor()
    val collapsedMenuWidth = if (formFactor == FormFactor.MOBILE_PORTRAIT) 0.dp else RailCollapsedWidth
    val itemSpacing = ChannelCarouselItemSpacing
    // FIX: was a fixed 160dp, which only fit ~5 tiles in the carousel
    // viewport on a typical TV/landscape screen. Solving
    // NetflixStyleCarousel's own start-padding formula (focusAnchorLine =
    // itemWidth*0.2 + itemSpacing, i.e. a "peek" lead-in of 20% of an
    // item's width) for ~7 fully-visible items keeps this in sync with
    // whatever the carousel's actual viewport width is (screen width minus
    // the rail) instead of a value that was simply eyeballed.
    val itemWidth = rememberChannelCarouselItemWidth(collapsedMenuWidth)
    val firstItemRequester = remember(route, isFirstRow) { FocusRequester() }
    // D-pad focus: this row's OWN scroll position (its internal LazyRow) also
    // survives navigating away and back, same as CarouselRow's rowState - so
    // the actual first-visible channel within this row (not necessarily
    // index 0) is what claims focus. See CarouselRow for the identical
    // pattern/rationale.
    val rowState = rememberLazyListState()
    // FIX (confirmed: after searching, a row's matched item scrolled it
    // partway in - clearing the search brought back the full channel list,
    // but this row's OWN scroll position stayed wherever the search had
    // left it, so it appeared scrolled into the middle instead of starting
    // fresh): this row is keyed by its category id (see its call site's
    // item(key = cat.id)), so it stays the SAME composed instance across a
    // search - only the channels list passed in changes - meaning rowState
    // itself never resets on its own. focusClaimId already reliably mints
    // a fresh identity on every category/search/sort change (see
    // LiveTvScreen's own doc comment on it), so reacting to it here closes
    // the same gap already fixed for the outer LazyColumn/grid's own
    // scroll positions - just one level down, per-row instead of
    // screen-wide.
    LaunchedEffect(focusClaimId) {
        runCatching { rowState.scrollToItem(0) }
    }
    // FIX (confirmed regression: D-pad Down from the filter row, and
    // AppShell's own rail Right-escape, both landing on the nearest item
    // by Compose's default spatial search instead of this row's actual
    // first item): registerFirstItem was removed entirely when this row's
    // AUTO-CLAIM-on-load behavior migrated to claimFocusEntry below, but
    // firstItemTarget(route) - which both of those escape paths call
    // independently of the auto-claim - depends on this registration
    // existing regardless. Without it, firstItemTarget(route) falls back
    // to FocusRequester.Default, requestFocus() on which fails, and the
    // key event goes unconsumed straight into ordinary spatial navigation.
    // claimFocusEntry and this registration are separate concerns that
    // happen to share the same requester - see the grid's identical
    // gridFirstItemRequester in LiveTvScreen for the same split.
    if (isFirstRow && route != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
        // FIX: unregister on dispose - without this, if this row stops
        // being the first row (filtered out, category change, or torn down
        // during a layout churn like the search field's keyboard opening),
        // FocusRegistry keeps handing out this now-detached requester as a
        // `down = ...` focus target, which crashes uncatchably the moment
        // real D-pad input tries to navigate into it. See
        // FocusRegistry.unregisterFirstItem's doc comment for the full
        // explanation.
        DisposableEffect(route, firstItemRequester) {
            onDispose { FocusRegistry.unregisterFirstItem(route, firstItemRequester) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // FIX: was 20sp Bold - matches Home's shared carousel-row header
        // style now (DefaultCarouselHeaderFontSize/Weight, defined in
        // CarouselComponents.kt) for consistency between the VOD carousels
        // and these channel-category rows, same fixed-height Box so every
        // row's total height stays predictable.
        Box(
            modifier = Modifier.fillMaxWidth().height(DefaultCarouselHeaderBlockHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = title,
                color = BbTextPrimary,
                fontSize = DefaultCarouselHeaderFontSize,
                fontWeight = DefaultCarouselHeaderWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
        NetflixStyleCarousel(
            data = channels, // FIX: renamed from 'items' to 'data'
            collapsedMenuWidth = collapsedMenuWidth,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            state = rowState,
            key = { it.id }
        ) { channel ->
            // FIX: was `channel == channels.firstOrNull()` - always index 0
            // regardless of scroll position, so D-pad Down from the
            // category/sort/search filters (via FocusRegistry.firstItemTarget)
            // could land on a channel scrolled off-screen instead of whatever
            // is actually first VISIBLE in this row. Mirrors CarouselRow's
            // identical fix.
            val visibleIndex = rowState.firstVisibleItemIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
            val isFirstItem = isFirstRow && route != null && channel === channels.getOrNull(visibleIndex)
            // D-pad focus: every channel (not just the first) gets a stable,
            // registered requester keyed by (route, channel.id) - see
            // FocusRegistry.registerItemFocus/restoreClickedItemFocus. This
            // is what lets focus land back on the exact channel that was
            // clicked when returning from the player via Back - see
            // CarouselRow's itemFocusRequester for the same pattern.
            val itemFocusRequester = remember(route, channel.id) { FocusRequester() }
            if (route != null) {
                FocusRegistry.registerItemFocus(route, channel.id, itemFocusRequester)
            }
            ChannelTile(
                channel = channel,
                isFavorite = favoriteIds.contains(channel.id),
                modifier = Modifier
                    .width(itemWidth)
                    .focusRequester(itemFocusRequester)
                    .then(
                        if (isFirstItem && focusTarget != null && focusClaimId != null) {
                            Modifier.claimFocusEntry(
                                current = focusTarget,
                                mine = FocusEntry.FirstItem,
                                requester = firstItemRequester,
                                scopeKey = route,
                                claimId = focusClaimId
                            )
                        } else if (isFirstItem) {
                            // focusTarget/focusClaimId not provided by this
                            // caller - fall back to a plain attachment (no
                            // auto-claim) rather than silently doing
                            // nothing, so upEscapeTarget/other callers that
                            // still reference firstItemRequester elsewhere
                            // aren't left pointing at an unattached one.
                            Modifier.focusRequester(firstItemRequester)
                        } else Modifier
                    )
                    .then(
                        if (isLastRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                    ).then(
                        if (isFirstRow && upEscapeTarget != null) Modifier.focusProperties { up = upEscapeTarget } else Modifier
                    ),
                onClick = {
                    if (route != null) FocusRegistry.rememberClickedItem(route, channel.id)
                    onChannelClick(channel)
                },
                onLongClick = { onChannelLongClick(channel) },
                onFavoriteIconClick = { onFavoriteIconClick(channel) },
                onFocus = onChannelFocused
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelListItem(
    channel: PortalChannel,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteIconClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BbCard.copy(alpha = 0.8f) else BbCard)
            .then(if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit)
        } else {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(BbAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(text = channel.number.ifEmpty { channel.id.take(3) }, color = BbAccent, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = channel.name, color = BbTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.nowPlaying.isNotEmpty()) {
                Text(text = channel.nowPlaying, color = BbTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Favorite Icon Overlay
        // FIX: removed the circular black backdrop per request - just the
        // star icon now.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onFavoriteIconClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White
            )
        }
    }
}