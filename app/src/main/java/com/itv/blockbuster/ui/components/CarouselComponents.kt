package com.itv.blockbuster.ui.components

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.itv.blockbuster.R
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.util.FocusEntry
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.claimFocusEntry
import com.itv.blockbuster.util.safeFocusEscape
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.ui.theme.RailCollapsedWidth

/**
 * Shared carousel/grid sizing so poster size, header size/weight, and spacing
 * between carousels are IDENTICAL everywhere a carousel or poster grid is
 * used (Home, Movies, TV Shows, hubs, etc.) rather than each screen picking
 * its own values. Callers that need this behavior simply omit the
 * corresponding CarouselRow/PosterGrid parameter to pick up these defaults.
 *
 * DefaultCarouselHeaderBlockHeight is a FIXED render height (see CarouselRow
 * below, which wraps the title in a Box of exactly this height rather than
 * letting it wrap-size from font metrics + padding) - deliberately, so every
 * row's total height is byte-for-byte predictable from itemWidth alone. That
 * predictability is what lets HomeScreen's hero-overlap math (below) come
 * out exact instead of approximate - any drift here (e.g. an intrinsically-
 * sized header) is what previously let a sliver of a 3rd carousel peek onto
 * screen and shifted the 1st carousel's center off the screen's center.
 */
val DefaultCarouselHeaderFontSize = 13.sp
val DefaultCarouselHeaderWeight = FontWeight.Normal // NOT bold, per design
val DefaultCarouselHeaderBlockHeight = 34.dp

/**
 * Poster width AND the resulting per-row block height (header + poster row)
 * on TV/landscape, computed TOGETHER so they can never drift apart.
 *
 * itemWidth is derived first from an ideal 1/3-of-container-height target,
 * THEN clamped to a sane range - and the block height used everywhere else
 * (HomeScreen's hero-overlap/viewport math) is derived back OUT of that
 * final, already-clamped itemWidth, not out of the original 1/3 target.
 * Deriving them independently (block from the ideal target, width
 * separately clamped) previously let a 3rd row peek onto screen on taller
 * devices: whenever the ideal width got clamped down, the real rendered row
 * ended up shorter than the assumed 1/3-height block, leaving just enough
 * leftover space in the "2-row" viewport for a sliver of a 3rd row.
 * Computing block FROM the final width, always, closes that gap regardless
 * of whether/how much clamping kicked in.
 *
 * containerHeightOverride lets a caller that has already measured its own
 * real available height (e.g. via BoxWithConstraints) use that exact value
 * instead of the device's global LocalConfiguration.screenHeightDp, which
 * isn't guaranteed to equal what a specific composable actually receives.
 * Callers that omit it keep the previous device-wide behavior.
 */
@Composable
private fun rememberCarouselSizing(containerHeightOverride: Dp? = null): Pair<Dp, Dp> { // (itemWidth, blockHeight)
    val formFactor = rememberFormFactor()
    if (formFactor == FormFactor.MOBILE_PORTRAIT) {
        val itemWidth = 140.dp
        return itemWidth to (DefaultCarouselHeaderBlockHeight + itemWidth * 1.5f)
    }
    val containerHeight = containerHeightOverride ?: LocalConfiguration.current.screenHeightDp.dp
    val idealBlock = (containerHeight / 3f - 4.dp).coerceAtLeast(140.dp) // small buffer, see below
    val idealPosterHeight = (idealBlock - DefaultCarouselHeaderBlockHeight).coerceAtLeast(60.dp)
    // Upper bound is generous (rarely the active constraint) precisely so
    // the "derive block from ideal 1/3 height" and "derive block from the
    // final width" numbers stay as close together as possible in the common
    // case; the lower bound guards genuinely tiny/unusual screens.
    val itemWidth = (idealPosterHeight / 1.5f).coerceIn(84.dp, 220.dp) // 2:3 aspect -> width = height / 1.5
    // The block used for ALL positioning math (viewport height, hero-overlap
    // offset) is derived from this FINAL, already-clamped width - never from
    // idealBlock - so it always matches what actually gets rendered.
    val realBlock = DefaultCarouselHeaderBlockHeight + itemWidth * 1.5f
    return itemWidth to realBlock
}

/**
 * The real, exact height of "header + poster row" for a single carousel on
 * TV/landscape - i.e. exactly what CarouselRow actually renders when using
 * the shared defaults above. HomeScreen's hero-overlap layout multiplies
 * this by 2 for its viewport height, and uses it directly as the top offset
 * that places the first carousel's center on the hero's bottom edge - see
 * LargeHomeHeroLayout for the full derivation.
 *
 * Pass containerHeightOverride (e.g. from BoxWithConstraints.maxHeight) to
 * size against a specific measured available height instead of the device's
 * global screenHeightDp.
 */
@Composable
fun rememberCarouselBlockHeight(containerHeightOverride: Dp? = null): Dp =
    rememberCarouselSizing(containerHeightOverride).second

/**
 * Poster width used by every carousel/grid on a given form factor, so
 * switching between Home, Movies, TV Shows, a specific category's grid, etc.
 * never changes how big posters look.
 *
 * Pass containerHeightOverride (e.g. from BoxWithConstraints.maxHeight) to
 * size against a specific measured available height instead of the device's
 * global screenHeightDp - see rememberCarouselSizing's doc comment.
 */
@Composable
fun rememberDefaultCarouselItemWidth(containerHeightOverride: Dp? = null): Dp =
    rememberCarouselSizing(containerHeightOverride).first

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
            // REVERTED: a tween(250ms, FastOutSlowInEasing) was tried here to
            // smooth out item-to-item scrolling, but with rapid D-pad
            // navigation each keypress re-triggers/interrupts the animation,
            // so the visible scroll position kept lagging behind actual
            // focus - which read as the app being unresponsive/frozen
            // rather than smooth. Back to the original near-instant spring.
            //
            // Uses CarouselFocusStiffness (defined below, near PosterCard) so
            // this row's scroll-into-view motion and each PosterCard's own
            // focus highlight share identical spring dynamics - see
            // CarouselFocusStiffness's doc comment for why.
            override val scrollAnimationSpec: AnimationSpec<Float> = spring(stiffness = CarouselFocusStiffness)

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

// Shared spring stiffness for BOTH the row's scroll-into-view motion (above)
// and each PosterCard's own focus highlight (below). Kept as a stiffness
// VALUE, not a single AnimationSpec instance, since spring<T>() is generic
// per animated type (Color/Dp/Float are different AnimationSpec<T> types) -
// each call site below builds its own spring(stiffness = ...) from this.
// highlight a tween(200ms) fade, the scroll a separately-tuned spring - so a
// keypress landed the highlight "in place" while the row was still
// physically sliding the card into its anchored position a beat later,
// reading as "focus jumps, then the item snaps into position". Driving both
// off the exact same AnimationSpec doesn't fully unify them into one motion
// (that would need scaling the focused card itself, which was tried before
// and reverted - see the NOTE in PosterCard's modifier - since it visually
// "jumps" neighboring items even without changing layout size), but it does
// mean the highlight and the scroll now share identical start/settle
// dynamics instead of visibly disagreeing about how fast "focused" should
// look. Spring (not tween) specifically because tween(250ms) was already
// tried and reverted for the scroll: a fixed-duration animation restarts
// from scratch on every interruption, so rapid D-pad presses made the
// visible position compound-lag behind actual focus. A spring re-targets
// smoothly from its current position/velocity instead, so rapid repeated
// presses stay responsive rather than stacking delay.
//
// UPDATED: was Spring.StiffnessHigh, which settles fast enough that both
// the highlight and the scroll motion read as an instant snap rather than
// a glide - "feels snappy, no natural animated feel". Lowering stiffness
// doesn't reintroduce the rapid-press lag the tween had: that regression
// was specifically about TWEEN restarting from scratch on every
// interruption, not about how fast the animation settles - a spring
// re-targets from its current position/velocity at ANY stiffness, so
// slowing it down just makes the glide more visible without bringing back
// the compounding-lag behavior. StiffnessMediumLow gives a noticeably
// softer, more natural-feeling motion while still comfortably resolving
// between consecutive D-pad presses at normal navigation speed.
private val CarouselFocusStiffness: Float = Spring.StiffnessMediumLow

// REVERTED (Grok-identified root cause of "vertical scroll: row looks
// stuck, then moves"): vertical row-to-row scroll used to have its own,
// lower Spring.StiffnessLow (200f) constant here, split off at an earlier
// request to make vertical feel more deliberate/slower than horizontal.
// But the border highlight (CarouselFocusStiffness, above) still moves at
// StiffnessMediumLow regardless of which axis triggered the focus change -
// so with vertical scroll softer than the highlight, the highlight would
// land on the new row's item almost immediately while the actual row was
// still barely moving (a low-stiffness spring starts with low velocity),
// then visibly accelerate and catch up a beat later. That reads as "stuck,
// then suddenly moves," not a smooth glide - matching the exact symptom
// reported. Vertical scroll now references CarouselFocusStiffness directly
// (see its usage below), so all three - horizontal, vertical, and the
// border highlight - move at the same rate and the highlight never
// "arrives" before the row does.

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
    LaunchedEffect(focused) {
        if (focused) onFocus()
    }
    // FIX: was an instant Modifier.border() toggle (present or absent with
    // no transition) - combined with the scroll specs above being near-
    // instant too, focus moving between items had nothing smooth about it
    // anywhere. Animating the border's color (always present in the
    // modifier chain, just transparent when unfocused, so this never
    // changes the Box's measured size/reflows anything) gives a quick
    // fade-in/out instead of an abrupt pop.
    //
    // UPDATED: both the color AND width now animate off a spring using
    // CarouselFocusStiffness
    // (the same spec driving the row's own scroll-into-view motion above),
    // instead of an independent tween(200ms). A static color-only pop reads
    // as "already focused" the instant a keypress lands, regardless of how
    // far the row still has to physically scroll to bring this card to its
    // anchored position - since a color change is a near-instantaneous
    // perceptual event but the scroll is a continuous, distance-dependent
    // one, even matched durations still visibly disagree about when the
    // card "arrives". Growing the border width alongside the color gives
    // the highlight a small amount of its own motion (without resizing the
    // card - Modifier.border draws inward, never reflowing neighbors) so it
    // reads more like it's arriving with the scroll instead of announcing
    // itself ahead of it.
    val borderColor by animateColorAsState(
        targetValue = if (focused) BbAccent else Color.Transparent,
        animationSpec = spring(stiffness = CarouselFocusStiffness),
        label = "posterCardBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 1.dp,
        animationSpec = spring(stiffness = CarouselFocusStiffness),
        label = "posterCardBorderWidth"
    )
    // FIX: D-pad long-press support - combinedClickable's own onLongClick
    // detection below is pointer/touch-gesture based
    // (awaitLongPressOrCancellation over PointerInputChanges) and does NOT
    // fire from a held Enter/DPad-Center KEY event at all, regardless of
    // how long it's held - only touch long-press works out of the box. A
    // D-pad remote's "hold OK to open the favorite/delete menu" gesture
    // was silently doing nothing. Manually tracks key-down/key-up timing
    // for the D-pad confirm keys (same 400ms threshold convention as
    // SettingsScreen's own sortable list's D-pad long-hold-to-grab
    // handling, below) and dispatches onLongClick/onClick itself.
    var pressStartUptimeMs by remember { mutableStateOf(0L) }
    Box(
        modifier = modifier
            // NOTE: deliberately no scale/zoom animation here. A focus-scale
            // graphicsLayer transform was here previously, but scaling a
            // poster up on focus visually "jumps" neighboring items even
            // though it doesn't change measured layout size - the border
            // below is the ONLY focus indicator, drawn within this Box's
            // existing bounds (Modifier.border doesn't add padding/change
            // size), so switching focus between items/rows never reflows
            // or visually shifts anything else on screen.
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(BbCard)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            // Positioned between combinedClickable and focusable so it gets
            // first refusal on the D-pad confirm keys during key dispatch
            // (bubbles from the focused leaf outward) - consuming the event
            // here means combinedClickable's own (always-onClick, hold-
            // duration-blind) key handling never ALSO fires for the same
            // physical press.
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
        if (item.logoUrl.isNotEmpty()) {
            // FIX (confirmed via device logcat - "NativeAlloc concurrent
            // copying GC" firing every ~2-3 seconds with pause times up to
            // 617ms, freeing 60k-200k objects each time - a strong signature
            // of native/Bitmap memory pressure): posters are opaque
            // rectangular thumbnails with no transparency, but Coil was
            // decoding them at its default ARGB_8888 config (4 bytes/pixel).
            // RGB_565 (2 bytes/pixel) halves the native memory footprint of
            // every decoded poster with no visible quality loss for opaque
            // JPEGs - this is the single highest-volume image type in the
            // app (every carousel item, in every row, on every screen), so
            // it's the first place this matters most.
            val context = LocalContext.current
            AsyncImage(
                model = remember(item.logoUrl) {
                    ImageRequest.Builder(context)
                        .data(item.logoUrl)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                },
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
                    // FIX: removed the circular black backdrop (was
                    // .clip(CircleShape).background(Color.Black.copy(alpha
                    // = 0.6f))) per request - the icon now sits directly on
                    // the poster with no underlay. Kept the same 24dp hit
                    // target size so touch/click area doesn't shrink.
                    .size(24.dp)
                    .focusable()
                    .clickable(onClick = onFavoriteIconClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = actionIconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                // FIX: removed the circular black backdrop (see the
                // matching comment above) - just the star icon now.
                .size(24.dp)
                .clickable(onClick = onFavoriteIconClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White,
                modifier = Modifier.size(14.dp)
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
    upEscapeTarget: FocusRequester? = null,
    // NEW (Phase 3, D-pad focus redesign - see FocusEntry.kt): the
    // screen's current FocusEntry decision. Only meaningful combined with
    // isFirstRow - when both this equals FirstItem and this row is the
    // structurally first row, the actual first-visible item in it
    // self-claims focus via claimFocusEntry() below. Every other row
    // simply ignores this parameter entirely.
    focusTarget: FocusEntry = FocusEntry.None,
    // FIX (see claimFocusEntry's doc comment for the full bug this closes):
    // a value unique to the CURRENT focusTarget transition specifically,
    // not just its value - the caller passes remember(focusTarget) { Any() }
    // (or equivalent) so this is a genuinely new identity every time
    // focusTarget changes, even if the new value repeats an earlier one.
    // Without this, claimFocusEntry re-fires every time a DIFFERENT item
    // becomes isFirstItem due to scrolling, since FocusEntry.FirstItem
    // itself never changes - see CarouselComponents' own history for what
    // that looked like in practice (focus snapping back on scroll,
    // carousel oscillation under rapid Right).
    focusClaimId: Any = Unit,
    // NEW: configurable poster/header sizing, defaulting to the shared
    // DefaultCarousel* constants/helper above so every caller that omits
    // these stays visually consistent with every other carousel/grid.
    itemWidth: Dp = rememberDefaultCarouselItemWidth(),
    itemSpacing: Dp = 12.dp,
    headerFontSize: TextUnit = DefaultCarouselHeaderFontSize,
    headerFontWeight: FontWeight = DefaultCarouselHeaderWeight,
    // FIXED height, not wrap-content: every row's total height must be
    // byte-for-byte predictable from itemWidth alone (see the doc comment
    // on DefaultCarouselHeaderBlockHeight) so HomeScreen's hero-overlap /
    // exactly-2-rows math comes out exact rather than approximate.
    headerHeight: Dp = DefaultCarouselHeaderBlockHeight,
    // NEW: the NEXT row's items (in outer scroll order), if any - used to
    // proactively warm the next row's images into Coil's cache while THIS
    // row is on screen, rather than waiting for the user to actually scroll
    // there. See the LaunchedEffect below for why this matters specifically
    // for D-pad row-to-row navigation.
    nextRowItems: List<PortalVodItem> = emptyList()
) {
    val formFactor = rememberFormFactor()
    val collapsedMenuWidth = if (formFactor == FormFactor.MOBILE_PORTRAIT) 0.dp else RailCollapsedWidth

    // FIX (confirmed via testing - horizontal movement within a row feels
    // fine, but the FIRST time you move down into a row you haven't visited
    // yet, it stutters; revisiting the same row afterward is fine): Compose
    // Foundation's built-in lazy-list prefetching is tuned around fling-
    // scroll velocity, which a discrete D-pad "move to the next row" jump
    // doesn't really have - so the next row's PosterCards, and the image
    // decodes their composition kicks off, were starting right as you
    // arrived rather than any earlier. Revisiting was fine because by then
    // Coil's memory cache already had the images from the first visit.
    //
    // This warms the next row's first several images into Coil's cache as
    // soon as THIS row is composed - well before the user actually presses
    // Down - so by the time they arrive, decoding is already done or well
    // underway instead of just starting. Uses the exact same RGB_565
    // ImageRequest shape as PosterCard's real display request (see its own
    // doc comment) so this actually lands a cache HIT there rather than a
    // second, differently-configured decode.
    val context = LocalContext.current
    // FIX (confirmed root cause of "images decoding again and again even on
    // a second pass through the same list", per user's own sharp
    // observation): this prefetch is fired via context.imageLoader.enqueue()
    // directly, NOT through AsyncImage - so unlike PosterCard's own display
    // request below, there's no Composable layout to auto-derive a size
    // from. Coil's cache key includes the requested size, and with no size
    // set at all here, it was defaulting to Size.ORIGINAL - the CDN's full
    // resolution, not the ~84-220dp poster width actually displayed. That
    // meant every prefetched image was cached under a DIFFERENT key than
    // the one PosterCard's own AsyncImage request would look up: a
    // guaranteed cache miss, forcing a second, separate decode at the
    // correct size - for every single image this prefetch ever touched,
    // every time, on every pass through the list. Computing the exact same
    // width/height PosterCard uses (itemWidth, 2:3 aspect ratio) and setting
    // it explicitly here means both requests resolve to the identical cache
    // key, so the prefetch's decode is what the real display request
    // actually reuses.
    val density = LocalDensity.current
    val prefetchSize = remember(itemWidth, density) {
        val widthPx = with(density) { itemWidth.roundToPx() }
        val heightPx = with(density) { (itemWidth * 1.5f).roundToPx() }
        Size(widthPx, heightPx)
    }
    LaunchedEffect(nextRowItems) {
        if (nextRowItems.isEmpty()) return@LaunchedEffect
        // FIX (confirmed via device logcat - three "Davey!" stalls totaling
        // ~3s, all clustered in the first ~7s of cold start, right as the
        // first Home rows populate): this used to enqueue immediately once
        // nextRowItems changed - which is the exact same moment THIS row's
        // own images are also decoding for the first time. During the
        // initial load burst, several rows reveal in quick succession (see
        // HomeViewModel's progressive reveal), so each one's prefetch was
        // piling straight onto the next row's own first-time decode instead
        // of trailing behind it. A short delay lets the current row's own
        // images get a head start before this row starts competing for
        // decode bandwidth on the next one's behalf - matters most exactly
        // during that initial burst; for ordinary steady-state scrolling
        // (where this fix's main benefit lives) 300ms of lead time before
        // the user could plausibly reach the next row is negligible.
        delay(300)
        // FIX (confirmed via device System Trace - 65 lock contention
        // events on coil.disk.DiskLruCache.get(), some with 4-5 coroutines
        // queued on the same lock at once): firing all 6 enqueue() calls
        // back-to-back in the same instant means they don't cleanly decode
        // in parallel - they partially serialize through this lock, and
        // whichever ones lose the race sit waiting. That's compounding
        // directly with whatever THIS row's own ~5-8 posters are also
        // doing as they compose. A small stagger between each prefetch call
        // spreads them out enough to meaningfully cut into that contention,
        // without meaningfully delaying how far ahead of the user's actual
        // navigation this prefetch still lands.
        nextRowItems.take(6).forEach { item ->
            if (item.logoUrl.isNotEmpty()) {
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(item.logoUrl)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .size(prefetchSize)
                        .build()
                )
                delay(60)
            }
        }
    }
    val firstItemRequester = remember(route, isFirstRow) { FocusRequester() }
    // D-pad focus: this row's OWN scroll position (its internal LazyRow) also
    // survives navigating away and back, same as the outer LazyColumn - so
    // the actual first-visible item within this row (not necessarily index
    // 0) is what notifyContentReady should target.
    val rowState = rememberLazyListState()
    if (isFirstRow && route != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
        // FIX: unregister on dispose - see FocusRegistry.unregisterFirstItem's
        // doc comment. Without this, a stale/detached requester could still
        // be handed out as a `down = ...` focus target after this row stops
        // being the first row (filtered out, category change, or torn down
        // during a layout churn), crashing uncatchably on the next real
        // D-pad key press.
        DisposableEffect(route, firstItemRequester) {
            onDispose { FocusRegistry.unregisterFirstItem(route, firstItemRequester) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = row.title,
                color = BbTextPrimary,
                fontSize = headerFontSize,
                fontWeight = headerFontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
        NetflixStyleCarousel(
            data = row.items, // FIX: Updated parameter name to match NetflixStyleCarousel signature
            collapsedMenuWidth = collapsedMenuWidth,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            state = rowState,
            key = { it.id },
            trailingContent = {
                if (row.hasMore) {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
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
            // FIX (scroll jank across every carousel): this used to read
            // rowState.firstVisibleItemIndex unconditionally, for every item
            // in every row - even though the result is only ever used on
            // the ONE row where isFirstRow is true. Since isFirstRow is
            // false for every other row on the page, every poster card in
            // every other row was subscribing to its row's scroll-position
            // state for no reason, forcing all of them to recompose every
            // time that row's first-visible-item index changed. `&&`
            // short-circuits, so wrapping the read on the right-hand side
            // means it's never even evaluated - and no subscription is ever
            // created - for any row except the one that actually needs it.
            val isFirstItem = isFirstRow && route != null && run {
                val visibleIndex = rowState.firstVisibleItemIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0))
                item === row.items.getOrNull(visibleIndex)
            }
            // NEW (Phase 1 of the D-pad focus redesign - requirement 5:
            // "once you reach the last item, focus should stay there, not
            // move to other carousels or the category filters"): without
            // this, pressing Right past a short row's last item had no
            // explicit handling at all, so it fell through to Compose's
            // default spatial focus search - which could land on an
            // unrelated row, the filter bar, or anywhere else it judged
            // "nearest," unpredictably. Mirrors the existing isLastRow
            // (down = Cancel) and first-item (left = leftEscapeTarget)
            // boundary patterns already used elsewhere in this same
            // modifier chain - this is the same idea, just for the
            // opposite (horizontal, trailing) edge. Reference equality
            // (===) matches the existing isFirstItem check just above.
            // Safe regardless of row.hasMore/pagination: the trailing
            // "load more" placeholder (see trailingContent above) isn't
            // itself focusable, and loading more is triggered by that
            // placeholder scrolling into composition, not by focus
            // reaching it - so this doesn't interfere with pagination.
            val isLastItem = item === row.items.lastOrNull()
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
                    .width(itemWidth)
                    .focusRequester(itemFocusRequester)
                    .then(
                        if (isFirstItem) Modifier.claimFocusEntry(focusTarget, FocusEntry.FirstItem, firstItemRequester, route, focusClaimId)
                        else Modifier
                    )
                    .then(
                        if (isLastRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier
                    ).then(
                        // FIX (crash: "IllegalStateException: FocusRequester
                        // is not initialized" - reported after selecting a
                        // category, searching with no results, then
                        // changing category again): Modifier.focusProperties
                        // { up = upEscapeTarget } is the exact raw,
                        // uncatchable-risk pattern SafeFocusEscape.kt's own
                        // doc comment warns about - if upEscapeTarget
                        // (topBarFocusRequester on Home) is ever momentarily
                        // unattached when Compose's focus system evaluates
                        // this binding (e.g. HomeCategoryDropdown
                        // recomposing during a category/search transition),
                        // it throws an exception no runCatching can catch,
                        // since Compose throws it internally, not from a
                        // requestFocus() call in our own code. safeFocusEscape
                        // handles the key event manually instead and wraps
                        // the actual requestFocus() call in runCatching, so
                        // a momentarily-unattached target just fails
                        // silently (event not consumed, falls through to
                        // normal search) instead of crashing.
                        if (upEscapeTarget != null) Modifier.safeFocusEscape(Key.DirectionUp, upEscapeTarget)
                        else if (isTopRow) Modifier.focusProperties { up = FocusRequester.Cancel }
                        else Modifier
                    ).then(
                        if (isLastItem) Modifier.focusProperties { right = FocusRequester.Cancel } else Modifier
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
    // NEW: mirrors CarouselRow's onItemFocused - lets a caller (Home's
    // category-grid-with-hero layout) keep the persistent hero banner
    // following whichever poster currently has focus, same as it already
    // does in the carousel view.
    onItemFocused: (PortalVodItem) -> Unit = {},
    modifier: Modifier = Modifier,
    upEscapeTarget: FocusRequester? = null,
    route: String? = null,
    // NEW (Phase 3 follow-up, D-pad focus redesign - see FocusEntry.kt):
    // same role as CarouselRow's matching parameter. PosterGrid renders
    // Home's single-category view (as opposed to CarouselRow's multi-row
    // "All Categories" view) - switching between the two tears down and
    // rebuilds the entire subtree, so PosterGrid needs its own claim into
    // the same FocusEntry system rather than relying on CarouselRow's.
    focusTarget: FocusEntry = FocusEntry.None,
    // FIX: same claimFocusEntry re-fire fix as CarouselRow's matching
    // parameter - see its doc comment for the full bug this closes.
    focusClaimId: Any = Unit,
    // D-pad focus: fixed width reserved for the rail, same constant
    // NetflixStyleCarousel uses - NOT the rail's live/animating width. See
    // the class doc above for why this must stay decoupled from the actual
    // current layout width.
    collapsedMenuWidth: Dp = 0.dp,
    // NEW: lets a caller push the grid's first row down to a precise offset
    // instead of the default fixed 16dp - e.g. Home's category-grid-with-
    // hero layout uses this to align the grid's first row exactly with
    // where a carousel's own poster row starts elsewhere on the same
    // screen, rather than an arbitrary/approximate value.
    contentPaddingTop: Dp = 16.dp,
    // NEW: lets a caller that has already measured its own real available
    // height (e.g. via BoxWithConstraints) size posters against that exact
    // value instead of the device's global screenHeightDp - see
    // rememberDefaultCarouselItemWidth's doc comment for why that matters.
    containerHeightOverride: Dp? = null
) {
    val itemMinWidth = rememberDefaultCarouselItemWidth(containerHeightOverride)
    val horizontalSpacing = 14.dp
    val horizontalContentPadding = 20.dp * 2
    val gridState = rememberLazyGridState()
    val firstItemRequester = remember(route) { FocusRequester() }
    if (route != null) {
        FocusRegistry.registerFirstItem(route, firstItemRequester)
        // FIX: unregister on dispose - see FocusRegistry.unregisterFirstItem's
        // doc comment. Without this, a stale/detached requester could still
        // be handed out as a `down = ...` focus target after this grid is
        // torn down (category switch, search cleared, etc.), crashing
        // uncatchably on the next real D-pad key press.
        DisposableEffect(route, firstItemRequester) {
            onDispose { FocusRegistry.unregisterFirstItem(route, firstItemRequester) }
        }
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPaddingTop, bottom = 16.dp),
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
                // Trigger once the focused item is within the last PAGE_SIZE
                // (14, matching fetchVodList's page size in the ViewModel)
                // items of the currently-loaded list, rather than only the
                // literal last row. This means the very first page (up to 14
                // items) ALWAYS satisfies this immediately on landing - so
                // the 2nd page starts fetching in the background right away,
                // before the user has scrolled at all - and the same holds
                // true again once page 2 arrives, cascading naturally as the
                // user moves toward row 3, 4, etc. No separate prefetch code
                // needed: this is the SAME onLoadMore/loadMoreRowItems this
                // screen already had, just triggered earlier via a wider
                // window instead of only at the very last row. Down is still
                // always canceled at the true last row (see below) to avoid
                // escaping to the rail; this just makes actually reaching
                // that boundary while still-paginating rare.
                val isNearEnd = index >= (row.items.size - 14).coerceAtLeast(0)
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
                        .then(
                            if (isFirstItem) Modifier.claimFocusEntry(focusTarget, FocusEntry.FirstItem, firstItemRequester, route, focusClaimId)
                            else Modifier
                        )
                        .fillMaxWidth()
                        .then(if (isLeftColumn) Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() } else Modifier)
                        // REVERTED: Down is unconditionally canceled at the
                        // true last row again (regardless of row.hasMore) -
                        // letting the default spatial search run while still
                        // paginating was landing focus on the rail menu
                        // instead of doing nothing, which is worse. Reaching
                        // the end while genuinely still-loading is now made
                        // rare instead (see isNearEnd above), rather than
                        // trying to make that moment itself safe.
                        .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                        .then(if (isTopRow && upEscapeTarget != null) Modifier.safeFocusEscape(Key.DirectionUp, upEscapeTarget) else Modifier),
                    isFavorite = favoriteIds.contains(item.id),
                    progressRatio = ratio,
                    // Still doesn't depend on the trailing sentinel span-item
                    // being reachable via D-pad focus search (see history in
                    // git blame / prior fix notes) - triggers directly off
                    // focus reaching the near-the-end rows instead. Safe to
                    // call repeatedly since onLoadMore/loadMoreRowItems
                    // already no-ops while a load is in progress or hasMore
                    // is false.
                    onFocus = {
                        onItemFocused(item)
                        if (isNearEnd && row.hasMore) onLoadMore()
                    },
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
                    // FIX: this sentinel previously had no focusable modifier
                    // at all, so even with Down no longer blocked above,
                    // D-pad navigation had nowhere to land - Compose's
                    // spatial search would just skip past it (nothing to
                    // focus = nothing to bring into view = this LaunchedEffect
                    // never actually composes/fires from D-pad navigation).
                    // Making it focusable gives Down somewhere real to go,
                    // which is what actually brings it into view and lets
                    // pagination trigger from the remote.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .focusable()
                            .focusProperties { down = FocusRequester.Cancel },
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
/**
 * Converts a raw runtime value into an "Xh Ym" display string. PortalVodItem.duration
 * is populated from the portal API as plain total minutes (see VodDto.toDomain,
 * and how VodDetailScreen/VodEpisodesScreen already just append "m" to it
 * directly) - this just formats that same raw number properly instead of
 * showing the bare minute count. Returns an empty string for anything that
 * doesn't parse as a positive number, so callers can just check
 * isNotEmpty() before displaying it.
 */
// FIX: was private - now shared with VodDetailScreen's landscape layout so
// both places format a raw "duration" string (minutes, as a plain integer
// string per the Stalker portal's convention) into "Xh Ym" identically
// rather than each maintaining their own copy that could drift apart.
internal fun formatRuntimeMinutes(rawDuration: String): String {
    val totalMinutes = rawDuration.trim().toIntOrNull() ?: return ""
    if (totalMinutes <= 0) return ""
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/**
 * Clickable search icon button - what's shown in place of a search field
 * when it's hidden, toggling it open on click. Shared between Home and Live
 * TV (and Favorites/Recents, going forward) so this control - and its
 * behavior - can never visually drift apart between screens. The icon
 * itself is tinted to match the dropdowns' own (BbCard) background color
 * for a consistent, low-key look; a border is the only focus indicator.
 */
/**
 * Vertical BringIntoViewSpec that unconditionally pins whichever row just
 * received D-pad focus to the TOP edge of a carousel viewport - anchored on
 * the ROW's own top (where its title sits), not on the focused item's top.
 * Shared between Home's VOD carousel and Live TV's channel carousel so the
 * two behave identically rather than one using this and the other falling
 * back to Compose's own default (and, in practice, less predictable)
 * bring-into-view behavior.
 *
 * The element that actually triggers BringIntoView is the focused item
 * itself, which sits headerHeightPx below its row's true top (the title is
 * above it). Naively returning `offset` (the item's own offset) pins the
 * ITEM's top to the viewport's top - which pushes the row's title
 * headerHeightPx above the viewport, i.e. off-screen. Subtracting
 * headerHeightPx instead targets the position the item would be at if the
 * ROW's top (title included) were flush with the viewport's top.
 *
 * Unlike the default BringIntoViewSpec (minimal scroll only when the target
 * isn't already fully visible), this scrolls EVERY time focus lands in a row
 * whose top isn't already flush with the viewport's top - including a row
 * that's already the second (bottom) of 2 visible rows. That's deliberate:
 * it's what makes "focus moves to row 2 -> row 2 becomes the new row 1, row
 * 3 slides into the row 2 slot" work, one row at a time, for both Down (rows
 * increasing) and Up (rows decreasing) navigation - exactly the behavior
 * requested, rather than only reacting once focus tries to leave the
 * currently visible pair entirely.
 *
 * This only affects scrolling triggered by FOCUS changes (D-pad/keyboard
 * navigation calling BringIntoView) - it has no effect on touch drag, fling,
 * or mouse wheel scrolling, which go through a separate gesture/fling code
 * path entirely.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTopPinningBringIntoViewSpec(): BringIntoViewSpec {
    val density = LocalDensity.current
    val headerHeightPx = remember(density) { with(density) { DefaultCarouselHeaderBlockHeight.toPx() } }
    return remember(headerHeightPx) {
        object : BringIntoViewSpec {
            // REVERTED: same reason as NetflixStyleCarousel's matching
            // comment - a tween here made rapid row-to-row navigation feel
            // like the app was lagging/frozen rather than smooth. Back to
            // a spring.
            //
            // REVERTED further (Grok-identified "stuck, then moves" bug):
            // this was briefly split into its own, lower
            // CarouselVerticalScrollStiffness constant to make vertical
            // feel more deliberate than horizontal - but the border
            // highlight doesn't distinguish which axis triggered a focus
            // change, so a softer vertical scroll than the highlight meant
            // the highlight visibly landed before the row finished moving.
            // Back to sharing CarouselFocusStiffness directly with
            // horizontal and the border highlight - see its doc comment.
            override val scrollAnimationSpec: AnimationSpec<Float> =
                spring(stiffness = CarouselFocusStiffness)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return offset - headerHeightPx
            }
        }
    }
}

@Composable
fun SearchIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // FIX: was a plain .clickable{} + separate later .focusable() +
    // .onFocusChanged{} - clickable() creates its OWN implicit focus target
    // internally, so that separate .focusable() could end up being a SECOND,
    // different focus node than the one D-pad focus actually lands on -
    // meaning isFocused (and therefore the border below) could silently
    // never update even though the element visually still looked
    // interactive. Sharing one interactionSource between clickable and
    // focusable (same fix already applied to PosterCard) ties both to the
    // exact same focus/press state.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            // FIX: this Box had NO background at all, and the icon below was
            // tinted BbCard - the same dark tone as every card/pill in the
            // app - so unfocused, the icon was effectively invisible against
            // whatever happened to be behind it (the flat background,
            // another card, or a hero image). Giving it the same opaque
            // pill background every other overlaid filter control uses
            // (CategoryDropdown/SortIconButton) means it always contrasts
            // against its OWN background rather than depending on the
            // background, which can't be reliably guaranteed for something
            // that gets overlaid on hero art.
            .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
            .then(
                if (isFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            // FIX: was BbCard (identical to the background above, i.e. zero
            // contrast) - matches CategoryDropdown/SortIconButton's own
            // icon tint logic now (BbTextSecondary normally, BbAccent when
            // focused).
            tint = if (isFocused) BbAccent else BbTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HeroBanner(
    hero: HeroContent?,
    height: Dp,
    modifier: Modifier = Modifier,
    // NEW: text (title/metadata/description below) and the backdrop image
    // are now driven by two independently-debounced sources upstream (see
    // HomeScreen's textHeroItem vs focusedHeroItem) - text settles faster
    // (150ms) since it's cheap to render, while the image - the expensive
    // part, a full decode of the single largest image on the page - waits
    // for a longer, more deliberate settle (500ms) before it starts at
    // all. Defaults to `hero` so call sites that don't care about this
    // split (Live TV's static drawable hero, anywhere not wired to the
    // dual-debounce) behave exactly as before, using one value for both.
    imageHero: HeroContent? = hero
) {
    // Render nothing (not even an empty placeholder box) when there's no hero item
    // - e.g. Adult VOD, when the pool used to seed a hero has no adult-flagged
    // items - so it doesn't reserve empty space for a banner that never renders.
    if (hero == null) return
    val formFactor = rememberFormFactor()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Fraction of the poster's real pixel height to discard from the top
    // outright, before the normal top-anchored crop of what's left. Only
    // meaningful for the Vod image treatment below.
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

        when (imageHero) {
            is HeroContent.Vod -> {
                val item = imageHero.item
                if (item.logoUrl.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(imageBoxWidth)
                            .fillMaxHeight()
                    ) {
                        val imageRequest = remember(item.logoUrl, topSkipFraction) {
                            ImageRequest.Builder(context)
                                .data(item.logoUrl)
                                .transformations(TopCropTransformation(topSkipFraction))
                                // FIX: same reasoning as PosterCard's poster
                                // image - opaque backdrop art with no
                                // transparency needed, decoded at the
                                // default ARGB_8888 (4 bytes/pixel). This is
                                // the single largest image on the page, so
                                // RGB_565's 2 bytes/pixel matters even more
                                // here than on individual poster thumbnails.
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .build()
                        }
                        // NEW: fade the image in from fully transparent once it's
                        // actually finished loading, rather than letting it pop in
                        // instantly (or, with Coil's default behavior, snap straight
                        // from the OLD hero's image to the new one the moment the
                        // model changes) - keyed on item.logoUrl so switching to a
                        // new hero always restarts from invisible rather than
                        // crossfading from whatever was showing before, per the
                        // "start with no picture, slowly fade in" request. This is
                        // what actually avoids the motion jerkiness: an instant
                        // swap draws the eye far more than a plain fade does.
                        //
                        // FIX: uses an explicit Animatable instead of
                        // animateFloatAsState. When Coil already has the image in
                        // its memory cache, onState can report Success essentially
                        // synchronously with composition - with animateFloatAsState,
                        // that could mean isImageLoaded flips to true before any
                        // frame at alpha=0 ever actually gets drawn, making the
                        // "fade" invisible (looks instant) even though the code
                        // looks correct. Animatable(0f) guarantees the starting
                        // value is genuinely 0, and animateTo() is only launched
                        // from onState once loading actually succeeds, so there's
                        // always at least one real rendered frame at alpha=0 first,
                        // regardless of how fast the load was.
                        val imageAlpha = remember(item.logoUrl) { Animatable(0f) }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = imageAlpha.value },
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    coroutineScope.launch {
                                        imageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 1400))
                                    }
                                }
                            }
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
            }
            is HeroContent.LiveChannel -> {
                // FIX: was a generic Icons.Default.LiveTv glyph on a flat
                // card-colored background - replaced with an actual hero
                // image (a wall of channel logos) so Live TV's hero reads
                // as a real banner instead of a placeholder. Still
                // deliberately NOT cropped/zoomed per item and deliberately
                // the SAME image regardless of which channel is focused (an
                // explicit product decision, not a stand-in for per-channel
                // art) - see HeroContent's doc comment. Reuses the same
                // "bleed" gradient treatment on top of it for visual
                // consistency with the Vod banner.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(imageBoxWidth)
                        .fillMaxHeight()
                        .background(BbCard)
                ) {
                    Image(
                        painter = painterResource(R.drawable.live_tv_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(bleedExtra)
                            .background(Brush.horizontalGradient(listOf(BbBackground, Color.Transparent)))
                    )
                }
            }
            null -> {
                // Text has settled (hero != null, checked above) but the
                // image hasn't caught up to it yet - render nothing here
                // rather than showing a stale or mismatched backdrop.
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
                .align(Alignment.TopStart)
                .widthIn(max = totalWidth * 0.5f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = hero.title,
                color = BbTextPrimary,
                fontSize = if (formFactor == FormFactor.MOBILE_PORTRAIT) 22.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                // NEW: scrolling (marquee) title instead of wrapping to a
                // 2nd line. maxLines=1 + basicMarquee() only actually
                // animates/scrolls when the text is wider than its
                // available space (bounded by the Column's own
                // widthIn(max = totalWidth * 0.5f) above) - a short title
                // that already fits just displays normally, static.
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee()
            )
            // NEW: Year, Runtime, Genre, IMDb rating, Country - separated by
            // bullets, wrapping to additional lines (via FlowRow) instead of
            // clipping/scrolling if the whole set doesn't fit within the
            // same 50%-width column the title uses. VOD-only - a live
            // channel has none of this metadata, so this row is skipped
            // entirely for HeroContent.LiveChannel.
            if (hero is HeroContent.Vod) {
                val item = hero.item
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    if (item.year.isNotEmpty()) {
                        Text(item.year, color = BbTextSecondary, fontSize = 13.sp)
                    }
                    val runtime = formatRuntimeMinutes(item.duration)
                    if (runtime.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text(runtime, color = BbTextSecondary, fontSize = 13.sp)
                    }
                    if (item.genres.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text(item.genres, color = BbTextSecondary, fontSize = 13.sp)
                    }
                    if (item.ratingImdb.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text(
                            "IMDb ${item.ratingImdb}",
                            color = BbTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (item.country.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text(item.country, color = BbTextSecondary, fontSize = 13.sp)
                    }
                }
            }
            // Shared: Vod shows its synopsis here; LiveChannel shows
            // nowPlaying instead (see HeroContent.description).
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