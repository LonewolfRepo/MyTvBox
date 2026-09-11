package com.itv.blockbuster.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

data class HomeRow(
    val id: String,
    val title: String,
    val items: List<PortalVodItem>,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingPage: Boolean = false
)

/**
 * A Netflix-style horizontal carousel that locks focus to a specific left-anchored coordinate.
 *
 * The FIRST item of every row (index 0) declares a custom D-pad Left focus
 * target pointing back at the app rail (see FocusRegistry.leftEscapeTarget)
 * via Modifier.focusProperties. That's Compose's own designed mechanism for
 * overriding a specific direction's focus search on a boundary item, so it's
 * consulted by moveFocus() BEFORE Compose's default spatial "nearest
 * neighbour" heuristic ever runs - unlike intercepting the raw key event,
 * this can't be preempted or fall back to picking whichever rail row happens
 * to be geometrically closest. D-pad Left on any other item is untouched, so
 * normal within-row navigation is unaffected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> NetflixStyleCarousel(
    data: List<T>, // FIX: Renamed from 'items' to 'data' to prevent shadowing LazyListScope.items()
    collapsedMenuWidth: Dp = 0.dp,
    itemWidth: Dp,
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
    // D-pad focus: hoistable so callers can inspect state.firstVisibleItemIndex
    // to find which item is CURRENTLY on screen (e.g. after a restored scroll
    // position), rather than assuming index 0.
    state: LazyListState = rememberLazyListState(),
    // FIX: without this, itemsIndexed below defaulted to keying each
    // composable slot by its POSITION in the list, not by item identity.
    // That's invisible normally, but breaks the moment the underlying list
    // REORDERS while off screen (e.g. a Recents row promoting an item to
    // the front right after it's watched): the item that moved gets treated
    // as "new data at an existing slot", so its remember{}-scoped state -
    // including the stable per-item FocusRequester registered via
    // FocusRegistry.registerItemFocus - gets silently discarded and
    // recreated fresh, orphaning whatever was registered under its old
    // position and breaking focus restoration on return from a detour
    // (VOD detail, player). Callers should always pass a stable identity
    // key (e.g. { it.id }) when the list can reorder.
    key: ((T) -> Any)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current

    val peekWidth = itemWidth * 0.20f
    val focusAnchorLine = peekWidth + itemSpacing
    val lazyRowWidth = screenWidth - collapsedMenuWidth
    val endPadding = (lazyRowWidth - focusAnchorLine - itemWidth).coerceAtLeast(0.dp)
    val focusAnchorLinePx = with(density) { focusAnchorLine.toPx() }

    val customSpec = remember(focusAnchorLinePx) {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                spring(stiffness = Spring.StiffnessHigh)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return offset - focusAnchorLinePx
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides customSpec) {
        LazyRow(
            state = state,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = focusAnchorLine,
                end = endPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                data,
                key = if (key != null) { _, item -> key(item) } else null
            ) { index, item ->
                if (index == 0) {
                    Box(modifier = Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() }) {
                        itemContent(item)
                    }
                } else {
                    itemContent(item)
                }
            }
            if (trailingContent != null) {
                item { trailingContent() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: PortalVodItem,
    modifier: Modifier = Modifier.width(140.dp),
    isFavorite: Boolean = false,
    progressRatio: Float = 0f,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onFavoriteIconClick: () -> Unit = {},
    onFocus: () -> Unit = {},
    actionIcon: ImageVector? = null,
    actionIconTint: Color = Color.White
) {
    // FIX: track focus via a shared InteractionSource between combinedClickable and
    // focusable, rather than a manual onFocusChanged further down the modifier
    // chain. combinedClickable creates its own implicit focus target internally;
    // with a separate, later .focusable() + .onFocusChanged(), D-pad focus could
    // land on THAT implicit target instead of the one onFocusChanged was actually
    // observing, so onFocus() (and therefore the persistent hero banner on TV)
    // could silently stop firing on some focus transitions even though the visual
    // highlight still looked fine. Sharing one interactionSource ties both to the
    // same focus/press state, which is the reliable, recommended way to observe
    // focus in Compose.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "posterScale"
    )
    LaunchedEffect(focused) {
        if (focused) onFocus()
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(BbCard)
            .then(
                if (focused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        if (item.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name,
                    color = BbTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        if (actionIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .focusable()
                    .clickable(onClick = onFavoriteIconClick)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = actionIconTint,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onFavoriteIconClick)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }
        if (item.logoUrl.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.name,
                    color = BbTextPrimary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (progressRatio > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomStart)
                    .zIndex(2f)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .background(BbAccent)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselRow(
    row: HomeRow,
    favoriteIds: Set<String> = emptySet(),
    progressMap: Map<String, PlaybackProgressEntity> = emptyMap(),
    onItemClick: (PortalVodItem) -> Unit = {},
    onItemLongClick: (PortalVodItem) -> Unit = {},
    onFavoriteIconClick: (PortalVodItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    // NEW: fires when a poster in this row gains focus - used to drive a
    // persistent, focus-following hero banner on TV/large form factor.
    onItemFocused: (PortalVodItem) -> Unit = {},
    // D-pad focus: true when this is the outer list's structurally LAST row
    // (not "last visible" - the true bottom of the data). Blocks D-pad Down
    // from escaping past the bottom of the content into the rail: with
    // nothing focusable below the last row (the "load more" spinner and
    // bottom spacer aren't focus targets), Compose's default spatial search
    // would otherwise jump to whichever rail item happens to sit closest -
    // which is how it was landing on Settings. Left (from the leftmost item,
    // via NetflixStyleCarousel) and Back remain the only ways to reach the
    // rail from content.
    route: String? = null,
    isFirstRow: Boolean = false,
    isLastRow: Boolean = false,
    // D-pad focus: true when this is the outer list's structurally FIRST row
    // (index 0, NOT "first visible" - isFirstRow above tracks that separately
    // for the initial-focus target, since a restored scroll position can put
    // a different row on screen first).
    //
    // Screens with NOTHING focusable above the list (Favorites, Recents) set
    // isTopRow = true, which blocks D-pad Up from escaping to the rail.
    //
    // Screens WITH a focusable top bar (search/genre/category - Home,
    // Movies/TV Shows) should instead pass upEscapeTarget pointing at that
    // bar's own FocusRequester: Compose's default spatial search for Up
    // doesn't reliably reach it, since the top bar's controls are usually
    // right-aligned while the leftmost poster is left-aligned, so the
    // heuristic ends up picking whichever rail item is geometrically
    // closest instead - the exact bug this fixes. upEscapeTarget takes
    // priority over isTopRow when both are set.
    isTopRow: Boolean = false,
    upEscapeTarget: FocusRequester? = null
) {
    val formFactor = rememberFormFactor()
    val collapsedMenuWidth = if (formFactor == FormFactor.MOBILE_PORTRAIT) 0.dp else 84.dp
    val firstItemRequester = remember(route, isFirstRow) { FocusRequester() }
    // D-pad focus: this row's OWN scroll position (its internal LazyRow) also
    // survives navigating away and back, same as the outer LazyColumn - so
    // the actual first-visible item within this row (not necessarily index
    // 0) is what notifyContentReady should target.
    val rowState = rememberLazyListState()
    if (isFirstRow && route != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            color = BbTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
        )
        NetflixStyleCarousel(
            data = row.items, // FIX: Updated parameter name to match NetflixStyleCarousel signature
            collapsedMenuWidth = collapsedMenuWidth,
            itemWidth = 140.dp,
            itemSpacing = 12.dp,
            state = rowState,
            key = { it.id },
            trailingContent = {
                if (row.hasMore) {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BbCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (row.isLoadingPage) {
                            CircularProgressIndicator(
                                color = BbAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) { item ->
            val progress = progressMap[item.id]
            val ratio = if (progress != null && progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val visibleIndex = rowState.firstVisibleItemIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0))
            val isFirstItem = isFirstRow && route != null && item === row.items.getOrNull(visibleIndex)
            // D-pad focus: every item (not just the first) gets a STABLE,
            // registered requester keyed by (route, item.id) - see
            // FocusRegistry.registerItemFocus/restoreClickedItemFocus. This
            // is what lets focus land back on the exact poster that was
            // clicked when returning from a detail screen via Back, rather
            // than wherever the rail/first-item handoff would otherwise
            // send it. Safe to layer alongside firstItemRequester below -
            // Compose allows multiple FocusRequesters on one focus target.
            val itemFocusRequester = remember(route, item.id) { FocusRequester() }
            if (route != null) {
                FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
            }
            PosterCard(
                item = item,
                modifier = Modifier
                    .width(140.dp)
                    .focusRequester(itemFocusRequester)
                    .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                    .then(
                        if (isLastRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                    ).then(
                        when {
                            upEscapeTarget != null -> Modifier.focusProperties { up = upEscapeTarget }
                            isTopRow -> Modifier.focusProperties { up = FocusRequester.Cancel }
                            else -> Modifier
                        }
                    ),
                isFavorite = favoriteIds.contains(item.id),
                progressRatio = ratio,
                onClick = {
                    if (route != null) FocusRegistry.rememberClickedItem(route, item.id)
                    onItemClick(item)
                },
                onLongClick = { onItemLongClick(item) },
                onFavoriteIconClick = { onFavoriteIconClick(item) },
                onFocus = { onItemFocused(item) }
            )
        }
    }
}

/**
 * NEW: Scrollable poster grid used when a specific category is selected (as opposed
 * to the "All Categories"/"All Genres" carousel view). One category = one flat list
 * of items, so a vertical grid reads better than a single horizontal row here.
 * Supports the same favorite/progress/click callbacks as CarouselRow, plus the same
 * "load more on reaching the end" pagination pattern (a trailing full-width item that
 * fires onLoadMore when it comes into view).
 *
 * D-pad focus: uses a computed FIXED column count (matching GridCells.Adaptive's
 * visual density) instead of Adaptive directly, since knowing the exact column
 * count is what lets boundary items be identified precisely - Adaptive's actual
 * column count isn't available at composition time. Computed from the device's
 * full, stable screenWidthDp minus collapsedMenuWidth (a fixed constant, same as
 * NetflixStyleCarousel) rather than from BoxWithConstraints' live maxWidth: the
 * rail's width animates between collapsed and expanded as focus moves in and out
 * of it, and since moving focus INTO this grid is exactly what collapses the
 * rail, computing columns from the live (mid-animation) content width caused the
 * grid to reflow columns while the very focus-request that triggered the
 * collapse was still in flight - shifting which item was actually at the
 * "first" index moment to moment and landing focus somewhere unpredictable.
 * The leftmost column escapes to the rail on D-pad Left (matching every
 * carousel row); the bottom row blocks D-pad Down from escaping to the rail,
 * the same fix as CarouselRow's isLastRow. The top row's Up is pointed at
 * upEscapeTarget (the screen's own top bar, e.g. its search field) when
 * provided, rather than relying on Compose's default spatial search - which
 * was picking the rail instead, since the top bar's controls are typically
 * right-aligned while the grid's leftmost column is not.
 *
 * When route is provided, this also registers its first VISIBLE item (per the
 * grid's own scroll position, same idea as CarouselRow's rowState) as the
 * target FocusRegistry.notifyContentReady(route) shifts focus onto once the
 * screen finishes loading - the same auto-focus-on-load handoff carousels
 * already had, retrofitted for the grid view.
 */
@Composable
fun PosterGrid(
    row: HomeRow,
    favoriteIds: Set<String> = emptySet(),
    progressMap: Map<String, PlaybackProgressEntity> = emptyMap(),
    onItemClick: (PortalVodItem) -> Unit = {},
    onItemLongClick: (PortalVodItem) -> Unit = {},
    onFavoriteIconClick: (PortalVodItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    upEscapeTarget: FocusRequester? = null,
    route: String? = null,
    // D-pad focus: fixed width reserved for the rail, same constant
    // NetflixStyleCarousel uses - NOT the rail's live/animating width. See
    // the class doc above for why this must stay decoupled from the actual
    // current layout width.
    collapsedMenuWidth: Dp = 0.dp
) {
    val itemMinWidth = 130.dp
    val horizontalSpacing = 14.dp
    val horizontalContentPadding = 20.dp * 2
    val gridState = rememberLazyGridState()
    val firstItemRequester = remember(route) { FocusRequester() }
    if (route != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val availableWidth = (screenWidth - collapsedMenuWidth - horizontalContentPadding).coerceAtLeast(itemMinWidth)
    val columns = ((availableWidth + horizontalSpacing) / (itemMinWidth + horizontalSpacing))
        .toInt()
        .coerceAtLeast(1)
    val lastRowStartIndex = if (row.items.isEmpty()) 0 else ((row.items.size - 1) / columns) * columns
    val visibleIndex = gridState.firstVisibleItemIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0))

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                val progress = progressMap[item.id]
                val ratio = if (progress != null && progress.durationMs > 0) {
                    (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val isLeftColumn = index % columns == 0
                val isTopRow = index < columns
                val isBottomRow = index >= lastRowStartIndex
                val isFirstItem = route != null && index == visibleIndex
                // D-pad focus: every item gets a stable, registered requester
                // keyed by (route, item.id) so Back from a detail screen can
                // restore focus onto the exact item that was clicked - see
                // CarouselRow's itemFocusRequester for the same pattern.
                val itemFocusRequester = remember(route, item.id) { FocusRequester() }
                if (route != null) {
                    FocusRegistry.registerItemFocus(route, item.id, itemFocusRequester)
                }
                PosterCard(
                    item = item,
                    modifier = Modifier
                        .focusRequester(itemFocusRequester)
                        .then(if (isFirstItem) Modifier.focusRequester(firstItemRequester) else Modifier)
                        .fillMaxWidth()
                        .then(if (isLeftColumn) Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() } else Modifier)
                        .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                        .then(if (isTopRow && upEscapeTarget != null) Modifier.focusProperties { up = upEscapeTarget } else Modifier),
                    isFavorite = favoriteIds.contains(item.id),
                    progressRatio = ratio,
                    onClick = {
                        if (route != null) FocusRegistry.rememberClickedItem(route, item.id)
                        onItemClick(item)
                    },
                    onLongClick = { onItemLongClick(item) },
                    onFavoriteIconClick = { onFavoriteIconClick(item) }
                )
            }
            if (row.hasMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    // FIX: mirrors CarouselRow's trailing load-more trigger - fires once
                    // this spacer scrolls into view, keyed on item count so it re-arms
                    // after every page appended.
                    LaunchedEffect(row.items.size) { onLoadMore() }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (row.isLoadingPage) {
                            CircularProgressIndicator(
                                color = BbAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Crops off the top [skipFraction] of the source bitmap outright (e.g. 0.07 = the
 * top 7%), before Coil/Compose does anything else with it. Used so the "skip the
 * top slice, then crop the remainder from the top" behavior operates on the
 * image's REAL pixel dimensions rather than an assumed/guessed aspect ratio -
 * which is what made the previous offset-and-clip approach fragile.
 */
private class TopCropTransformation(private val skipFraction: Float) : Transformation {
    override val cacheKey: String = "TopCropTransformation(skipFraction=$skipFraction)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val skipPx = (input.height * skipFraction).toInt().coerceIn(0, input.height - 1)
        val remainingHeight = (input.height - skipPx).coerceAtLeast(1)
        return Bitmap.createBitmap(input, 0, skipPx, input.width, remainingHeight)
    }
}

/**
 * Hero banner design.
 *
 * The portal only ever gives us a portrait poster (logoUrl) - there's no separate
 * landscape backdrop image in the data - so it's scaled/cropped to read as a
 * landscape image on the right side of the banner:
 *
 *  - The image is physically anchored to the RIGHT and sized to exactly 60% of
 *    the banner's own width (clearWidth below) - it does NOT span the full
 *    banner width. Rendering it against the full (much wider) banner width was
 *    the earlier bug: Crop always scales to *cover* whatever box it's given, so
 *    a full-width box forced a far more extreme zoom/crop than intended, which
 *    is what read as "stretched out." Sizing the actual image box to 60% keeps
 *    the crop proportionate.
 *  - "Bleed the rest": rather than cutting off hard at that 60% line, the SAME
 *    image is rendered slightly wider than the clear 60% (bleedExtra below) and
 *    a gradient fades that extra sliver - real image content, not empty space -
 *    down to the solid background. The clear 60% itself is never touched by
 *    this gradient.
 *  - Vertically, this is a genuine two-stage crop, done on the REAL decoded
 *    bitmap rather than an assumed aspect ratio:
 *      1. TopCropTransformation (above) discards the top skipFraction of the
 *         poster's actual pixel height outright, before anything else - many
 *         posters have mostly empty headroom/sky there.
 *      2. Compose's normal ContentScale.Crop + Alignment.TopCenter then crops
 *         the REMAINDER from the top to fill the image box - i.e. it starts
 *         right where step 1 left off and crops down from there, rather than
 *         centering within the remainder.
 *  - A bottom gradient keeps a clean, legible seam into the rows that scroll
 *    below this (now persistent) banner.
 *  - The title/metadata/description sit on the LEFT, overlaid on top of the
 *    bleed area, wrapped to at most 50% of the banner's width so they never
 *    reach into the clear right 60% of the image.
 *
 * height is supplied by the caller (HomeScreen sizes it to at most ~1/3 of the
 * screen height on TV/landscape) rather than computed here, since this is now
 * also used as a persistent (non-scrolling) element sized against the viewport.
 */
@Composable
fun HeroBanner(hero: PortalVodItem?, height: Dp, modifier: Modifier = Modifier) {
    // Render nothing (not even an empty placeholder box) when there's no hero item
    // - e.g. Adult VOD, when the pool used to seed a hero has no adult-flagged
    // items - so it doesn't reserve empty space for a banner that never renders.
    if (hero == null) return
    val formFactor = rememberFormFactor()
    val context = LocalContext.current
    // Fraction of the poster's real pixel height to discard from the top
    // outright, before the normal top-anchored crop of what's left.
    val topSkipFraction = 0.07f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(BbBackground)
    ) {
        val totalWidth = maxWidth
        val clearWidth = totalWidth * 0.6f    // guaranteed fully-clear image zone
        val bleedExtra = totalWidth * 0.15f   // extra width where that SAME image fades out
        val imageBoxWidth = clearWidth + bleedExtra

        if (hero.logoUrl.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(imageBoxWidth)
                    .fillMaxHeight()
            ) {
                val imageRequest = remember(hero.logoUrl, topSkipFraction) {
                    ImageRequest.Builder(context)
                        .data(hero.logoUrl)
                        .transformations(TopCropTransformation(topSkipFraction))
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = hero.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
                // Bleed: fades this SAME image - real content, not empty space -
                // from solid background down to fully transparent, over just this
                // box's leading (left) edge. The clear 60% zone to its right is
                // completely untouched by this gradient.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(bleedExtra)
                        .background(Brush.horizontalGradient(listOf(BbBackground, Color.Transparent)))
                )
            }
        }
        // Bottom-edge scrim: keeps a clean, legible seam into the rows that
        // scroll below this (now persistent) banner.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BbBackground.copy(alpha = 0.85f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = totalWidth * 0.5f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = hero.name,
                color = BbTextPrimary,
                fontSize = if (formFactor == FormFactor.MOBILE_PORTRAIT) 22.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (hero.year.isNotEmpty()) Text(hero.year, color = BbTextSecondary, fontSize = 13.sp)
                if (hero.duration.isNotEmpty()) {
                    Text("•", color = BbTextSecondary)
                    Text(hero.duration, color = BbTextSecondary, fontSize = 13.sp)
                }
                if (hero.ratingImdb.isNotEmpty()) {
                    Text("•", color = BbTextSecondary)
                    Text("IMDb ${hero.ratingImdb}", color = BbTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (hero.ratingMpaa.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, BbTextSecondary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(hero.ratingMpaa, color = BbTextSecondary, fontSize = 11.sp)
                    }
                }
            }
            if (hero.description.isNotEmpty()) {
                Text(
                    text = hero.description,
                    color = BbTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
