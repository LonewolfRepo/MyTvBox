package com.itv.blockbuster.ui.guide

import android.app.Activity
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.itv.blockbuster.data.player.PlaybackManager
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.livetv.SortMode
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.ui.components.SearchIconButton
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.FocusEntry
import com.itv.blockbuster.util.claimFocusEntry
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val CHANNEL_COL_TV = 260.dp
private val CHANNEL_COL_PORTRAIT = 150.dp
// FIX (UI redesign - "reduce the list size, so that approx ~7 to 7.5
// channels can be displayed at a time" then "I still dont see 7~7.5
// channels... it regressed to 4~4.5 channels"): the previous change (56dp
// -> 64dp) went the wrong direction - taller rows show FEWER of them in
// the same space, which is exactly the regression reported. Scaling down
// from the observed ~4.5 rows at 64dp to the target ~7.5 gives roughly
// 64 * (4.5/7.5) ≈ 38dp; rounded to 40dp. Still an estimate pending
// on-device confirmation, but corrected to move in the right direction
// this time.
private val ROW_HEIGHT = 40.dp
// FIX (UI redesign - "Adjust the card size, color (lighter grey)... of
// the channels to approx match the image"): BbCard (0xFF1E1E26, a very
// dark navy-grey) at various alphas read as noticeably darker than the
// reference image's channel boxes - this is a dedicated, lighter grey
// used at full opacity instead.
private val GuideCardGrey = Color(0xFF38383F)
// FIX (UI redesign - "decrease player size by 20%" then "Reduce the
// player size by another 10%"): was 352dp/220dp, then 282dp/176dp.
private val PLAYER_WIDTH_TV = 254.dp
private val PLAYER_WIDTH_PORTRAIT = 158.dp
private const val WINDOW_MIN = 240 // 4 hours visible window
// FIX (UI redesign - single-row header): shrunk from 180dp now that the
// search field sits inline in the same row instead of a separate row
// below the filters - only needs to fit the PIP itself (254dp wide /
// 16:9 ~= 143dp tall) plus a little padding.
private val HEADER_HEIGHT_TV = 155.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvGuideScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenCatchup: (String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel(),
    // D-pad focus: this screen's route, used to hand focus off from the
    // rail to its first (or last-played, via FocusEntry.RestoreItem) channel
    // row once content has loaded - see focusTarget/claimFocusEntry in
    // GuideChannelRow below. FIX: this screen never took a route param or
    // called into FocusRegistry at all originally - unlike every other rail
    // screen (Home, Live TV, Settings, etc.), navigating here from the rail
    // left focus stuck on the rail's own TV Guide icon; the only thing that
    // ever moved focus into the grid was a channel row's own OPPORTUNISTIC
    // "if I happen to be the last-played channel" effect, which never fired
    // at all on a first-ever visit (no last-played channel yet) and, either
    // way, never told FocusRegistry the handoff was complete. Migrated to
    // the same FocusEntry/claimFocusEntry mechanism Home/Live TV use -
    // RestoreItem is TV Guide's own reason that case exists in the first
    // place (see FocusEntry.kt's own doc comment).
    route: String = Routes.TV_GUIDE
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT
    // D-pad focus: the real, measured height of the grid's own viewport
    // (the Box wrapping GuideGridContent below the time header - see
    // where onSizeChanged is attached to it), in pixels. Read by
    // initialIndex/initialOffset below to compute the restore target's
    // centered position EXACTLY, rather than guessing how many rows fit
    // before any real layout has happened. onSizeChanged fires from the
    // very first layout pass, which - given how much longer the channel
    // network fetch itself takes - has already settled to the real value
    // well before guideChannels is ever populated and this is actually
    // needed, so there's no separate loading gate required for it here.
    var gridHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // D-pad focus: the whole auto-restore/focus-claim system below (and the
    // centering scroll tied to it) is meaningful only on a device driven by
    // a D-pad/remote - a touch device has no "rail" to escape to and no
    // reason to programmatically steal focus toward a channel the user
    // hasn't touched. FormFactor.TV is the precise check for that; the
    // existing isPortrait only distinguishes MOBILE_PORTRAIT, which left
    // MOBILE_LANDSCAPE getting the same D-pad-oriented treatment as TV
    // (see upEscapeTarget's own isPortrait check below, unchanged from
    // before this fix - a separate, narrower layout concern this one
    // doesn't touch).
    val isTv = formFactor == FormFactor.TV
    val pxPerMin = if (isPortrait) 2.5.dp else 5.dp
    val channelCol = if (isPortrait) CHANNEL_COL_PORTRAIT else CHANNEL_COL_TV
    val gridStart = (state.nowMin / 30) * 30
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // FIX (search debounce, matching Live TV's identical fix - see its own
    // doc comment): searchQuery updates immediately for the text field
    // itself, but filtering and focus-claiming react to this debounced
    // copy instead, so rapid typing doesn't refilter the list or move
    // focus toward a result on every keystroke. Selecting a category here
    // is also entirely client-side (see TvGuideViewModel.selectCategory -
    // no isLoading toggle, no network round-trip), so the same debounce
    // need applies as it did there.
    var debouncedSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(2000)
        debouncedSearchQuery = searchQuery
    }

    // NEW: search field starts hidden, revealed only by tapping the search
    // icon button - matches Home's/Live TV's toggle behavior.
    var showSearchBar by remember { mutableStateOf(false) }
    val isSearchActive = searchQuery.isNotBlank()
    val searchBarVisible = showSearchBar || isSearchActive
    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }

    // Auto-focus the field whenever it's supposed to be visible - matches
    // Home's/Live TV's LaunchedEffect exactly.
    LaunchedEffect(showSearchBar, isSearchActive) {
        if (showSearchBar || isSearchActive) {
            delay(50)
            runCatching { searchFieldFocusRequester.requestFocus() }
        }
    }

    // FIX (was entirely missing on this screen - unlike Home/Live TV, this
    // never registered a Back-press interceptor at all): AppShell's own
    // BackHandler gives a registered interceptor first refusal (see
    // FocusRegistry.consumeBackPressInterceptor's doc comment); with none
    // registered here, Back while searching fell straight through to
    // AppShell's own default behavior, which sends focus to the rail -
    // instead of clearing the search the way every other screen with a
    // search field does.
    val backPressScope = rememberCoroutineScope()
    DisposableEffect(isSearchActive) {
        if (isSearchActive) {
            FocusRegistry.setBackPressInterceptor {
                searchQuery = ""
                showSearchBar = false
                backPressScope.launch {
                    delay(100)
                    runCatching { searchButtonFocusRequester.requestFocus() }
                }
                true
            }
        } else {
            FocusRegistry.setBackPressInterceptor(null)
        }
        onDispose { FocusRegistry.setBackPressInterceptor(null) }
    }

    // D-pad focus: explicit Up target for the first (topmost) channel row,
    // pointing at this screen's own top bar (category filter dropdown)
    // rather than relying on Compose's default spatial search - see
    // HomeScreen/VodBrowserScreen/LiveTvScreen for the same fix and
    // rationale.
    val topBarFocusRequester = remember { FocusRequester() }

    // React to fullscreen enter/exit so the PIP surface is (re)bound safely
    val fullscreenActive by viewModel.playbackManager.isFullscreenActiveFlow.collectAsState()
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    // Search + Sort pipeline (category filtering is handled by the ViewModel)
    // FIX: was a plain inline computation, recomputed on every recomposition
    // of this screen (clock ticking, focus changes, anything unrelated to
    // search) even when neither the channel list nor the query had changed.
    // Live TV's equivalent filter next door already does this correctly via
    // remember() - this brings TV Guide's filter in line with it.
    val searchFiltered = remember(state.channels, debouncedSearchQuery) {
        if (debouncedSearchQuery.isBlank()) {
            state.channels
        } else {
            val q = debouncedSearchQuery.trim().lowercase()
            state.channels.filter {
                it.name.lowercase().contains(q) ||
                        it.number.lowercase().contains(q) ||
                        it.nowPlaying.lowercase().contains(q)
            }
        }
    }
    val guideChannels = when (sortMode) {
        SortMode.DEFAULT -> searchFiltered
        SortMode.A_Z -> searchFiltered.sortedBy { it.name.lowercase() }
        SortMode.Z_A -> searchFiltered.sortedByDescending { it.name.lowercase() }
        SortMode.NUMERIC -> searchFiltered.sortedWith(
            compareBy(
                { it.number.toDoubleOrNull() ?: Double.MAX_VALUE },
                { it.number }
            )
        )
    }

    // D-pad focus: the screen's own explicit focus intent, set imperatively
    // by exactly one of three distinct triggers below rather than derived
    // as a single expression recomputed from ambient state on every
    // recomposition - the previous, flat derivation applied one rule
    // (previewChannel?.id ?: lastPlayedChannelId, else FirstItem)
    // unconditionally regardless of which of the three had actually
    // happened, which is why searching reused the last-played channel
    // instead of always landing on the first result, and why a category
    // change couldn't be distinguished from a first load at all. Each
    // trigger below implements one specific product rule:
    //   - first load / first-ever rail entry: restore to last FOCUSED
    //     channel, falling back to last PLAYED if nothing has been
    //     focused yet this session, falling back to first item
    //   - category changed: restore to last PLAYED channel only (not
    //     last focused - a genuine category switch is a fresh navigation,
    //     not a continuation of wherever the user's eyes happened to be),
    //     falling back to first item
    //   - search changed: always the first result, ignoring both ids
    // All three fall back to Fallback (the top bar - category dropdown or
    // search field, whichever is visible) when the resulting list is
    // empty.
    //
    // This state machine only ever needs to run once per trigger. Restoring
    // focus to "whatever was actually last focused" on every subsequent
    // rail round-trip (product rule 2) is handled separately and
    // continuously by FocusRegistry itself - see GuideChannelRow's own
    // onFocusChanged, where every row keeps the registry's escape target
    // pointed at itself the moment it gains real focus, and updates
    // state.lastFocusedChannelId to match. That is more correct than
    // re-running this state machine on every round-trip: it naturally
    // follows the user's actual last position even after ordinary Up/Down
    // navigation within the grid, not just the position this state machine
    // last computed.
    var focusTarget by remember { mutableStateOf<FocusEntry>(FocusEntry.None) }
    var focusClaimId by remember { mutableStateOf<Any>(Any()) }
    fun claimFocus(entry: FocusEntry) {
        focusTarget = entry
        focusClaimId = Any()
    }
    fun restoreOrFirstOrFallback(targetId: String?): FocusEntry = when {
        guideChannels.isEmpty() -> FocusEntry.Fallback
        targetId != null && guideChannels.any { it.id == targetId } -> FocusEntry.RestoreItem(targetId)
        else -> FocusEntry.FirstItem
    }
    // The currently playing (or, before auto-preview lands, last played)
    // channel - "last played/playing channel" in the product rules above.
    // Reading state.previewChannel directly (rather than via a separate
    // remember()) means this always reflects whichever channel is
    // ACTUALLY showing right now, not just the one the session started on.
    val playingChannelId = state.previewChannel?.id ?: state.lastPlayedChannelId

    var hasClaimedInitial by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, isTv) {
        if (!isTv) return@LaunchedEffect
        if (state.isLoading) {
            claimFocus(FocusEntry.None)
        } else if (!hasClaimedInitial) {
            hasClaimedInitial = true
            claimFocus(restoreOrFirstOrFallback(state.lastFocusedChannelId ?: playingChannelId))
        }
    }
    LaunchedEffect(state.selectedCategory, isTv) {
        if (isTv && hasClaimedInitial) {
            claimFocus(restoreOrFirstOrFallback(playingChannelId))
        }
    }
    LaunchedEffect(debouncedSearchQuery, isTv) {
        if (isTv && hasClaimedInitial) {
            claimFocus(if (guideChannels.isEmpty()) FocusEntry.Fallback else FocusEntry.FirstItem)
        }
    }
    val restoreTargetId = (focusTarget as? FocusEntry.RestoreItem)?.itemId

    // Side effects that react to focusTarget's VALUE changing, regardless
    // of which of the three triggers above caused it - was previously
    // bundled into one giant effect keyed on focusTarget plus every filter
    // input separately (a workaround for focusTarget being a purely
    // derived value that could stay equal across a genuine content
    // change); now that focusTarget is set imperatively by exactly one
    // trigger at a time, keying on focusTarget alone is sufficient and
    // correct.
    //
    // D-pad focus: on a touch device, focusTarget is never set to anything
    // but its initial None (the three triggers above are all gated on
    // isTv), so this effect would otherwise fire once on mobile too,
    // pointlessly arming setContentTransitioning and stealing focus toward
    // topBarFocusRequester. Gated the same way for consistency.
    LaunchedEffect(focusTarget, isTv) {
        if (!isTv) return@LaunchedEffect
        android.util.Log.d("DpadFocus", "TvGuideScreen(route=$route): focusTarget=$focusTarget channelCount=${guideChannels.size} restoreTargetId=$restoreTargetId")
        // FIX (confirmed root cause of "focus landed on rail when category
        // filter changed"): this used to unconditionally call
        // setContentTransitioning(focusTarget == FocusEntry.None) - since
        // focusTarget recomputes to FirstItem/RestoreItem SYNCHRONOUSLY
        // the instant state.selectedCategory changes, this immediately set
        // the flag to false on every category change, telling the rail
        // it's safe to take over - racing against the positioning effect
        // below, which hadn't even started yet (both effects react to the
        // same state change with no ordering guarantee between them). This
        // effect now only ever ARMS the flag true (on None); the effect
        // below is the sole owner of clearing it, tied to the same
        // completion point as the actual focus claim, so there's no
        // window where it reads false before something has actually
        // claimed focus.
        if (focusTarget == FocusEntry.None) {
            FocusRegistry.setContentTransitioning(true)
        }
        if (focusTarget == FocusEntry.FirstItem || focusTarget == FocusEntry.Fallback || focusTarget is FocusEntry.RestoreItem) {
            keyboardController?.hide()
        }
        if (focusTarget == FocusEntry.None || focusTarget == FocusEntry.Fallback) {
            val result = runCatching { topBarFocusRequester.requestFocus() }
            android.util.Log.d("DpadFocus", "TvGuideScreen: topBarFocusRequester.requestFocus() success=${result.isSuccess}")
        }
    }

    // FIX (this replaces the previous scrollToItem-based approach
    // entirely, not just its overlay - confirmed root cause of both the
    // visible scroll jump AND the "wheel of death" hang that returned
    // after removing the earlier timeout): rememberLazyListState's
    // initialFirstVisibleItemIndex is read exactly ONCE, at this
    // composable's very first-ever composition (it's implemented via
    // rememberSaveable with no external key, so the init block never
    // re-runs) - by the time guideChannels/restoreTargetId actually
    // resolve (after the network load completes), that parameter has
    // already been read and discarded, so passing it a fresh value on
    // later recompositions does nothing. That's why this screen always
    // needed a SEPARATE scrollToItem() call after the fact instead - and
    // scrollToItem() to a potentially very deep index in a 5000+ item
    // list is real, unbounded async work that can hang for reasons
    // separate from (and in addition to) the EPG fetch burst already
    // fixed. The actual fix: key the LazyListState's own CREATION on
    // scrollTargetToken (below), so a genuinely NEW state object is
    // constructed - already positioned at the right index from the
    // moment it exists - every time the target changes, exactly the
    // architecture Gemini's recommendation was reaching for, just without
    // the flaw in the specific API call it suggested. This eliminates the
    // scroll entirely: there's no jump to hide because there's no
    // separate scroll step, and no long-running suspend call left to hang.
    //
    // FIX (confirmed root cause of "clicking play makes the channel list
    // disappear/reappear and jump up and down"): this was keyed on
    // (restoreTargetId, guideChannels) - restoreTargetId is
    // state.previewChannel?.id ?: state.lastPlayedChannelId, which changes
    // every time the user clicks ANY channel to preview it, not just on a
    // genuine category/search/sort change. That meant every ordinary
    // preview click recreated listState from scratch, repositioned at
    // whichever channel was just clicked - snapping it to the very TOP of
    // the viewport even when it hadn't been there before, which is
    // exactly the jump reported. Re-keyed to the actual filter identity
    // (category/search/sort) plus guideChannels.isEmpty() (a stable
    // boolean that flips exactly once, on the genuine loading-to-loaded
    // transition, rather than the full list reference/content changing on
    // every recomposition) - repositioning now only happens when the
    // displayed list itself has genuinely changed, never on an ordinary
    // preview click within the same one.
    // FIX (confirmed root cause of "focus not landing on the playing
    // channel on first entry, even though the list should start there"
    // and, very likely, "Right from rail lands on the closest channel
    // instead of the last focused one"): focusTarget now resolves
    // ASYNCHRONOUSLY, via LaunchedEffect - a coroutine scheduled to run
    // AFTER the composition pass completes, not a synchronous derived
    // value the way it was before this screen's focus redesign. This
    // token was still only keyed on the filter identity, not on
    // restoreTargetId/focusTarget - so listState got created and pinned
    // at index 0 using focusTarget's stale None value (its value in THIS
    // composition pass), and by the time the LaunchedEffect actually ran
    // and resolved it to RestoreItem a frame later, nothing was watching
    // for that change to reposition the list. For a restore target deep
    // in a 5000+ item list, that row was then never even composed - so it
    // could never claim focus, and nothing was ever registered as the
    // route's rail-escape target either, which is exactly the mechanism
    // "Right from rail lands on the closest channel" describes. Safe to
    // key on focusTarget directly now (unlike the old restoreTargetId,
    // which used to change on every ordinary preview click before this
    // redesign - see restoreTargetId's own doc comment): it's set
    // imperatively by exactly the three legitimate triggers now, an
    // ordinary click no longer touches it at all.
    val scrollTargetToken = remember(focusTarget, sortMode) { Any() }
    // D-pad focus: computes the EXACT index and pixel offset that puts the
    // restore target's row precisely centered in the grid's real,
    // measured viewport (gridHeightPx) - not an estimate of how many rows
    // fit, and not a position that then relies on centeringSpec's
    // automatic bring-into-view to correct it afterward. That correction
    // is exactly what the previous approach needed and exactly what made
    // it visible: pinning the target at its own literal index put it at
    // the TOP of the viewport, and the subsequent animated scroll from top
    // to center was a large, plainly visible jump, not a small nudge.
    // With rowHeightPx and gridHeightPx both real, measured quantities,
    // the row starts EXACTLY where centeringSpec would put it anyway, so
    // its calculateScrollDistance call on focus gain computes zero (or a
    // sub-pixel rounding difference, imperceptible) - nothing left to
    // animate.
    val initialPosition = remember(scrollTargetToken, gridHeightPx) {
        val idx = restoreTargetId?.let { id -> guideChannels.indexOfFirst { it.id == id } } ?: -1
        if (idx < 0 || gridHeightPx <= 0) {
            0 to 0
        } else {
            val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
            // Target row's top edge, in px from the viewport's own top,
            // once centered: half the leftover space above/below the row.
            val centerOffsetPx = (gridHeightPx - rowHeightPx) / 2f
            // How many whole rows fit above that point - those rows sit
            // above the viewport (scrolled past), and the target becomes
            // firstVisibleItemIndex + that count.
            val rowsAbove = kotlin.math.ceil(centerOffsetPx / rowHeightPx).toInt().coerceAtLeast(0)
            val firstVisibleIndex = (idx - rowsAbove).coerceAtLeast(0)
            // The remaining fractional row is how far the first visible
            // row itself is scrolled up past its own top - i.e. exactly
            // what firstVisibleItemScrollOffset means.
            val offsetPx = (rowsAbove * rowHeightPx - centerOffsetPx).toInt().coerceAtLeast(0)
            firstVisibleIndex to offsetPx
        }
    }
    val listState = rememberSaveable(scrollTargetToken, gridHeightPx, saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = initialPosition.first,
            firstVisibleItemScrollOffset = initialPosition.second
        )
    }

    // FIX: was a plain var mutated INSIDE the async scrollToItem
    // LaunchedEffect (set to false as its first line) - meaning there was
    // still a gap on every recomposition between guideChannels/
    // restoreTargetId actually changing and that effect getting a chance
    // to run and flip the flag, during which the new (unpositioned) list
    // was already visible with the overlay not yet up. Now that
    // positioning itself happens synchronously (via listState's own keyed
    // creation above, not a later effect), the only thing left to wait
    // for is the target row's own claimFocusEntry actually succeeding -
    // scrollTargetToken changes SYNCHRONOUSLY, on the very same
    // recomposition as restoreTargetId/guideChannels themselves (same
    // pattern as focusClaimId above), so comparing it against
    // settledToken makes isScrollSettled correct immediately, with no
    // window where it's stale.
    var settledToken by remember { mutableStateOf<Any?>(null) }
    val isScrollSettled = settledToken === scrollTargetToken

    // CLAIM FOCUS on whichever channel focusTarget says is live, now that
    // listState already starts pre-positioned there (see listState's own
    // doc comment above) - the target row is composed as part of the very
    // first layout pass, so its own claimFocusEntry (in GuideChannelRow)
    // should already be enough on its own; this effect exists as a fast,
    // lightweight confirmation pass and as the sole owner of clearing
    // isContentTransitioning (see the main LaunchedEffect's own doc
    // comment above for why that used to race).
    LaunchedEffect(scrollTargetToken) {
        FocusRegistry.setContentTransitioning(true)
        if (focusTarget == FocusEntry.FirstItem || focusTarget is FocusEntry.RestoreItem) {
            runCatching { FocusRegistry.firstItemTarget(route).requestFocus() }
        }
        settledToken = scrollTargetToken
        FocusRegistry.setContentTransitioning(false)
    }

    // FIX: Destroy the PIP stream ONLY when really leaving the guide.
    // Skip teardown when handing off to fullscreen, or when the activity is
    // being recreated (orientation flip in portrait).
    DisposableEffect(Unit) {
        onDispose {
            val pm = viewModel.playbackManager
            val recreating = (context as? Activity)?.isChangingConfigurations == true
            if (!recreating) {
                if (pm.isFullscreenActive) {
                    // The guide is being destroyed while the fullscreen player is active.
                    // The hand-back will never happen, so clear the flag to prevent the
                    // player from leaking when it eventually closes.
                    pm.keepLivePlayingOnExit = false
                } else if (!pm.keepLivePlayingOnExit) {
                    pm.player.stop()
                    pm.player.clearMediaItems()
                    pm.clearLiveContext()
                }
            }
        }
    }

    // FIX: race-proof PIP (re)bind. Runs on first composition and every time
    // fullscreen goes true -> false, AFTER the fullscreen player has detached.
    LaunchedEffect(fullscreenActive) {
        if (!fullscreenActive) {
            // The hand-off to fullscreen is complete. Clear the flag so that
            // subsequent navigation away from the guide correctly stops the player.
            viewModel.playbackManager.keepLivePlayingOnExit = false

            delay(200)
            textureViewRef.value?.let { view ->
                viewModel.playbackManager.player.setVideoTextureView(view)
            }
            val pm = viewModel.playbackManager
            val preview = viewModel.uiState.value.previewChannel
            when {
                pm.player.currentMediaItem == null && preview != null ->
                    viewModel.selectForPreview(preview)
                pm.player.currentMediaItem != null && !pm.player.isPlaying ->
                    pm.player.play()
            }
        }
    }

    val preview = state.previewChannel
    val previewPrograms = preview?.let { state.epg[it.id] } ?: emptyList()
    val nowProgram = previewPrograms.firstOrNull {
        parseMinutes(it.time) <= state.nowMin && parseMinutes(it.time) + it.duration > state.nowMin
    } ?: previewPrograms.firstOrNull()
    val nextProgram = nowProgram?.let { current ->
        previewPrograms.firstOrNull { parseMinutes(it.time) > parseMinutes(current.time) }
    }

    Column(Modifier.fillMaxSize().background(BbBackground)) {
        // ── ROW 1: Search / Clock / Sort / Category ──
        if (isPortrait) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BbCard)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(state.clock, color = BbTextSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                SearchIconButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier.focusRequester(searchButtonFocusRequester)
                )
                Spacer(Modifier.width(12.dp))
                SortIconButton(
                    mode = sortMode,
                    onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DEFAULT -> SortMode.A_Z
                            SortMode.A_Z -> SortMode.Z_A
                            SortMode.Z_A -> SortMode.NUMERIC
                            SortMode.NUMERIC -> SortMode.DEFAULT
                        }
                    }
                )
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.width(140.dp)) {
                    CategoryDropdown(
                        categories = state.categories,
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) }
                    )
                }
            }
            if (searchBarVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(searchFieldFocusRequester),
                    placeholder = { Text("Search channels...", color = BbTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        // FIX: search fields had no container color at all (fully
                        // transparent) - fine over the app's own flat dark
                        // background, but low/no contrast wherever this is overlaid
                        // on a hero banner's own image content. An explicit opaque
                        // container keeps the field legible regardless of what's
                        // behind it.
                        focusedContainerColor = BbCard,
                        unfocusedContainerColor = BbCard,
                        focusedBorderColor = BbAccent,
                        unfocusedBorderColor = BbTextMuted.copy(alpha = 0.3f),
                        cursorColor = BbAccent,
                        focusedTextColor = BbTextPrimary,
                        unfocusedTextColor = BbTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // ── ROW 2 (portrait): PIP player (left) + Channel Info (right) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PipPlayer(
                    preview = preview,
                    width = PLAYER_WIDTH_PORTRAIT,
                    textureViewRef = textureViewRef,
                    playbackManager = viewModel.playbackManager
                )
                Spacer(Modifier.width(16.dp))
                ChannelInfoColumn(preview = preview, nowProgram = nowProgram, nextProgram = nextProgram, modifier = Modifier.weight(1f))
            }
        } else {
            // FIX (VerifyError crash: "copy-cat1 ... type=Reference:
            // java.lang.String" on entering TV Guide): TvGuideScreen had
            // grown to 650+ lines in one function - past a size where the
            // Kotlin/Compose compiler's generated bytecode for this
            // function's local-variable slots can trip the DEX verifier.
            // This whole landscape header (PIP+info pinned top-left,
            // filters pinned top-right - see LandscapeGuideHeader's own
            // doc comment for why they're overlaid rather than stacked)
            // was the single largest block added this session and is
            // fully self-contained, so it's extracted into its own
            // composable to shrink this function back down.
            LandscapeGuideHeader(
                preview = preview,
                nowProgram = nowProgram,
                nextProgram = nextProgram,
                textureViewRef = textureViewRef,
                playbackManager = viewModel.playbackManager,
                clock = state.clock,
                showSearchBar = showSearchBar,
                onShowSearchBarChange = { showSearchBar = it },
                searchButtonFocusRequester = searchButtonFocusRequester,
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) },
                topBarFocusRequester = topBarFocusRequester,
                isScrollSettled = isScrollSettled,
                searchBarVisible = searchBarVisible,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchFieldFocusRequester = searchFieldFocusRequester
            )
        }

        // ── Time header + guide grid, with the playhead line spanning
        // both (see the Canvas below's own doc comment) ──
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                // ── Time header ──
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(channelCol))
                    Box(Modifier.fillMaxWidth().height(28.dp)) {
                        var m = 0
                        while (m <= WINDOW_MIN) {
                            Text(
                                text = formatMin(gridStart + m),
                                color = BbTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.offset(x = pxPerMin * m)
                            )
                            m += 30
                        }
                    }
                }
                HorizontalDivider(color = BbCard)

                // ── Guide grid ──
                Box(
                    Modifier
                        .weight(1f)
                        .onSizeChanged { gridHeightPx = it.height }
                ) {
                    // FIX (VerifyError crash - see LandscapeGuideHeader's own doc
                    // comment for the full explanation): extracted alongside it,
                    // as the other largest self-contained block in a function
                    // that had grown past a size where the compiler's generated
                    // bytecode could trip the DEX verifier.
                    GuideGridContent(
                        state = state,
                        isScrollSettled = isScrollSettled,
                        guideChannels = guideChannels,
                        listState = listState,
                        focusTarget = focusTarget,
                        focusClaimId = focusClaimId,
                        route = route,
                        isPortrait = isPortrait,
                        isTv = isTv,
                        topBarFocusRequester = topBarFocusRequester,
                        restoreTargetId = restoreTargetId,
                        channelCol = channelCol,
                        gridStart = gridStart,
                        pxPerMin = pxPerMin,
                        viewModel = viewModel,
                        onPlayLive = onPlayLive,
                        onOpenCatchup = onOpenCatchup
                    )
                }
            }
            // FIX (UI redesign - "Extend the line from top of the time
            // instead of from channel list"): this Canvas is now drawn
            // over the whole Column above (time header + divider + grid
            // together), not just the grid on its own, so the line
            // genuinely starts at the top of the time row - right where
            // the time labels are - and runs down through the entire
            // channel list, crossing the divider seamlessly, matching the
            // reference image. channelCol/gridStart/pxPerMin are the same
            // values the time header uses to position its own labels, so
            // the line's x position lines up exactly under whichever half-
            // hour mark represents the current time.
            Canvas(Modifier.matchParentSize()) {
                val x = (channelCol + pxPerMin * (state.nowMin - gridStart)).toPx()
                drawLine(
                    color = BbAccent,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 3f
                )
                // Small downward-pointing triangle at the very top of the
                // line, matching the reference image's marker.
                val triangleHalfWidth = 7.dp.toPx()
                val triangleHeight = 9.dp.toPx()
                drawPath(
                    path = Path().apply {
                        moveTo(x - triangleHalfWidth, 0f)
                        lineTo(x + triangleHalfWidth, 0f)
                        lineTo(x, triangleHeight)
                        close()
                    },
                    color = BbAccent
                )
            }
        }
    }
}

// =====================================================================
// GUIDE GRID CONTENT: loading / empty / channel list decision + the
// LazyColumn overlay itself (extracted from TvGuideScreen - see
// LandscapeGuideHeader's own doc comment for why)
// =====================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.GuideGridContent(
    state: GuideUiState,
    isScrollSettled: Boolean,
    guideChannels: List<PortalChannel>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusTarget: FocusEntry,
    focusClaimId: Any,
    route: String,
    isPortrait: Boolean,
    isTv: Boolean,
    topBarFocusRequester: FocusRequester,
    restoreTargetId: String?,
    channelCol: Dp,
    gridStart: Int,
    pxPerMin: Dp,
    viewModel: TvGuideViewModel,
    onPlayLive: (String, String) -> Unit,
    onOpenCatchup: (String) -> Unit
) {
    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BbAccent)
            }
        }
        guideChannels.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels in this category", color = BbTextMuted)
            }
        }
        else -> {
            val gridContent: @Composable () -> Unit = {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(guideChannels, key = { _, channel -> channel.id }) { index, channel ->
                        GuideChannelRow(
                            index = index,
                            channel = channel,
                            programs = state.epg[channel.id] ?: emptyList(),
                            gridStart = gridStart,
                            nowMin = state.nowMin,
                            pxPerMin = pxPerMin,
                            channelCol = channelCol,
                            isPreviewing = state.previewChannel?.id == channel.id,
                            isFirstVisible = index == 0,
                            isRestoreTarget = channel.id == restoreTargetId,
                            focusTarget = focusTarget,
                            focusClaimId = focusClaimId,
                            route = route,
                            isTv = isTv,
                            isBottomRow = index == guideChannels.lastIndex,
                            isTopRow = index == 0,
                            upEscapeTarget = if (!isPortrait) topBarFocusRequester else null,
                            // D-pad focus: called the moment this row gains
                            // real on-screen focus (see GuideChannelRow's own
                            // onFocusChanged) - the single source of truth
                            // for "restore focus to last focused item" on a
                            // rail round-trip (product rule 2). Replaces the
                            // old restoreTargetOffscreen/isFallbackVisible
                            // eligibility recomputation entirely: every row
                            // now keeps the registry pointed at itself the
                            // instant it's actually focused, rather than
                            // some rows retroactively guessing they qualify.
                            // GuideChannelRow itself no-ops this on non-TV.
                            onChannelFocused = { viewModel.setLastFocusedChannel(channel.id) },
                            onChannelClick = {
                                // CLICK ON CHANNEL CURRENTLY PLAYING -> FULLSCREEN
                                if (state.previewChannel?.id == channel.id &&
                                    !state.previewUrl.isNullOrEmpty()
                                ) {
                                    viewModel.playbackManager.keepLivePlayingOnExit = true
                                    onPlayLive(state.previewUrl!!, channel.id)
                                } else {
                                    viewModel.selectForPreview(channel)
                                }
                            },
                            onProgramClick = { program ->
                                val start = parseMinutes(program.time)
                                val isCurrent = start <= state.nowMin &&
                                        start + program.duration > state.nowMin
                                when {
                                    isCurrent -> {
                                        if (state.previewChannel?.id == channel.id &&
                                            !state.previewUrl.isNullOrEmpty()
                                        ) {
                                            viewModel.playbackManager.keepLivePlayingOnExit = true
                                            onPlayLive(state.previewUrl!!, channel.id)
                                        } else {
                                            viewModel.selectForPreview(channel)
                                        }
                                    }
                                    program.hasArchive && !program.cmd.isNullOrEmpty() ->
                                        viewModel.playArchive(program) { url -> onPlayLive(url, channel.id) }
                                    else -> onOpenCatchup(channel.id)
                                }
                            }
                        )
                        // FIX: kept as cheap insurance even though the
                        // EPG-fetch-burst it originally fixed (scrollToItem
                        // walking through thousands of intermediate rows on
                        // its way to a deep index, each triggering its own
                        // genuine network fetch) no longer applies now that
                        // listState starts pre-positioned instead - this
                        // just means normal composition doesn't fetch
                        // anything during the brief window before the
                        // target row's own claimFocusEntry confirms.
                        if (isScrollSettled) {
                            viewModel.ensureEpg(channel.id)
                        }
                    }
                }
            }
            if (isTv) {
                // D-pad focus: centers the focused/newly-scrolled-to row in
                // the viewport instead of pinning it to the top, per the
                // "pin it in the middle" requirement - TV only (see isTv's
                // own doc comment in TvGuideScreen: a touch device has no
                // D-pad-driven focus movement for this to react to, and
                // should just get Compose's own default scroll behavior).
                // Deliberately different from the spec that caused an ANR
                // on this same screen previously
                // (rememberTopPinningBringIntoViewSpec, built for Home/Live
                // TV's own carousel header layout): that one subtracted a
                // hard-coded header-height CONSTANT that didn't correspond
                // to anything in TV Guide's structurally different layout,
                // and an overshoot there could re-trigger itself in an
                // infinite oscillation. This one uses ONLY the parameters
                // BringIntoViewSpec itself provides (the item's offset and
                // size, and the real MEASURED container size) - a standard,
                // self-contained "center the item" formula with no
                // cross-screen constant to ever mismatch, and no possible
                // unbounded overshoot since every input is a real, bounded,
                // already-measured quantity. The isFinite() guard is a
                // defensive no-op in the normal case, kept only in case of
                // a genuine 0-sized measurement glitch.
                val centeringSpec = remember {
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                            val distance = offset - (containerSize - size) / 2f
                            return if (distance.isFinite()) distance else 0f
                        }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides centeringSpec, content = gridContent)
            } else {
                gridContent()
            }
            // FIX (UI redesign - "Extend the line from top of the time
            // instead of from channel list"): the playhead line + triangle
            // used to be drawn here, scoped only to this grid Box, so it
            // started at the grid's own top edge rather than the time
            // header above it. Moved up to TvGuideScreen's own wrapping
            // Box, which now spans the time header, the divider, and this
            // grid together, so the same line can run the full height
            // from the time row down through the channel list.
            // FIX (confirmed root cause of "channel list visibly scrolling
            // on startup" - see isScrollSettled's own doc comment
            // where it's declared in TvGuideScreen): drawn as an opaque
            // overlay on top of the (now always-composed) LazyColumn above,
            // for this one extra beat between state.isLoading going false
            // and the scroll effect actually completing, rather than
            // replacing the list outright - the list can still be scrolled
            // beneath it in the meantime.
            if (!isScrollSettled) {
                Box(
                    Modifier.fillMaxSize().background(BbBackground),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BbAccent)
                }
            }
        }
    }
}

// =====================================================================
// LANDSCAPE HEADER: PIP+info (top-left) overlaid with filters (top-right)
// (extracted from TvGuideScreen - see the call site's own doc comment for
// why: this was the single largest block in a function that had grown
// past a size where the compiler's generated bytecode could trip the DEX
// verifier)
// =====================================================================
@Composable
private fun LandscapeGuideHeader(
    preview: PortalChannel?,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    textureViewRef: androidx.compose.runtime.MutableState<TextureView?>,
    playbackManager: PlaybackManager,
    clock: String,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    searchButtonFocusRequester: FocusRequester,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    topBarFocusRequester: FocusRequester,
    isScrollSettled: Boolean,
    searchBarVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFieldFocusRequester: FocusRequester
) {
    // FIX (UI redesign - "Move the clock next to the player and fit the
    // search bar between the clock and search icon"): was a Box with
    // PIP+info pinned to TopStart and clock+filters pinned to TopEnd as
    // two separate corners, with the search field a further row below
    // the filters when visible. Now a single Row: PIP, info, clock,
    // (optionally) the search field, then the search/sort/category
    // controls - one contiguous header reading left to right, with the
    // search field fitting inline in its own place in that sequence
    // instead of pushing anything else around when it appears.
    //
    // FIX (UI redesign - "the left border of the player should align
    // with the left border of the channels"): was padding(horizontal =
    // 24.dp) on the old PIP+info Row, while the channel grid below has no
    // horizontal inset of its own (TvGuideScreen's outer Column has none)
    // - the two never lined up. This row's start padding is 0 to match
    // the grid's own left edge exactly; only the end (right) side keeps
    // an inset, so the category dropdown doesn't sit flush against the
    // screen edge.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT_TV)
            .padding(start = 0.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        PipPlayer(
            preview = preview,
            width = PLAYER_WIDTH_TV,
            textureViewRef = textureViewRef,
            playbackManager = playbackManager
        )
        Spacer(Modifier.width(16.dp))
        // FIX (confirmed root cause of "filters are aligned with the
        // channel name... they should remain on top - I only asked for
        // Channel Name, Now and Next to be centered"): the previous fix
        // set verticalAlignment = CenterVertically on the WHOLE Row,
        // which centered every child relative to the tallest one (the
        // PIP) - including the clock/search/sort/category filters, which
        // were never supposed to move. Reverted the Row itself to Top and
        // applied Modifier.align(Alignment.CenterVertically) (a RowScope
        // modifier that only affects the one child it's on) to just this
        // column instead - the filters stay put.
        ChannelInfoColumn(
            preview = preview,
            nowProgram = nowProgram,
            nextProgram = nextProgram,
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BbCard)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // FIX (UI redesign - "Bold Time on the header").
            Text(clock, color = BbTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        // FIX (UI redesign - "fit the search bar between the clock and
        // search icon"): was a separate row below the filters, right-
        // aligned under the category dropdown - now sits inline here,
        // in its own place between the clock (above) and the search
        // icon (below), only taking space when actually visible.
        if (searchBarVisible) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BbCard)
                    .border(1.dp, BbTextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = BbTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search channels...", color = BbTextMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFieldFocusRequester)
                                .focusProperties { left = FocusRegistry.leftEscapeTarget() },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = BbTextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(BbAccent)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        // FIX (UI redesign - "search button between Time and Sort
        // filter"): was before the search field, at the far left of
        // this row - moved here, between the clock and sort button,
        // to match the reference layout.
        SearchIconButton(
            onClick = { onShowSearchBarChange(!showSearchBar) },
            modifier = Modifier
                .focusRequester(searchButtonFocusRequester)
                .focusProperties { left = FocusRegistry.leftEscapeTarget() }
        )
        Spacer(Modifier.width(12.dp))
        // FIX (UI redesign - "filters matching the format and height of
        // the filters on other pages"): compact = true matches Live
        // TV's identical overlay filter row sizing.
        SortIconButton(
            mode = sortMode,
            onClick = {
                onSortModeChange(
                    when (sortMode) {
                        SortMode.DEFAULT -> SortMode.A_Z
                        SortMode.A_Z -> SortMode.Z_A
                        SortMode.Z_A -> SortMode.NUMERIC
                        SortMode.NUMERIC -> SortMode.DEFAULT
                    }
                )
            },
            compact = true,
            modifier = Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() }
        )
        Spacer(Modifier.width(12.dp))
        CategoryDropdown(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            focusRequester = topBarFocusRequester,
            isScrollSettled = isScrollSettled,
            compact = true
        )
    }
}

// =====================================================================
// PIP PLAYER + CHANNEL INFO (shared between portrait's sequential row and
// landscape's fixed overlay - see TvGuideScreen's own doc comment on the
// landscape header Box for why these needed to be extracted)
// =====================================================================
@Composable
private fun PipPlayer(
    preview: PortalChannel?,
    width: Dp,
    textureViewRef: androidx.compose.runtime.MutableState<TextureView?>,
    playbackManager: PlaybackManager
) {
    // FIX (UI redesign - "Dont make the player focusable and remove the
    // blue border from player"): was clickable (implicitly focusable in
    // Compose Foundation) with an accent border shown whenever a preview
    // was loaded - both removed; this is now a pure visual preview with
    // no interaction surface of its own.
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    textureViewRef.value = this
                }
            },
            // GUARDED: never steal the video surface while fullscreen.
            update = { view ->
                if (!playbackManager.isFullscreenActive) {
                    playbackManager.player.setVideoTextureView(view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (preview == null) {
            Text(
                "No preview",
                color = BbTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ChannelInfoColumn(
    preview: PortalChannel?,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = preview?.name ?: "Select a channel",
            color = BbTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Now: ${nowProgram?.name ?: "No info"}",
            color = BbTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Next: ${nextProgram?.name ?: "-"}",
            color = BbTextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =====================================================================
// GUIDE ROW: channel cell + program strip
// =====================================================================
@Composable
private fun GuideChannelRow(
    index: Int,
    channel: PortalChannel,
    programs: List<EpgProgram>,
    gridStart: Int,
    nowMin: Int,
    pxPerMin: Dp,
    channelCol: Dp,
    isPreviewing: Boolean,
    // D-pad focus: whether THIS row is the one focusTarget's FirstItem case
    // would refer to (the actual first-visible row, per listState -
    // mirrors Home/Live TV's identical isFirstItem pattern) or its
    // RestoreItem case would refer to (this channel is the last-played
    // one). A row only mounts the corresponding claimFocusEntry effect
    // when the relevant flag is true, rather than every row checking both
    // possibilities unconditionally - see the doc comment below for why
    // that matters with potentially thousands of channels.
    isFirstVisible: Boolean,
    isRestoreTarget: Boolean,
    // D-pad focus: see FocusEntry's own doc comment. Both come from
    // TvGuideScreen's single focusTarget/focusClaimId computation - passed
    // through unchanged, not recomputed per row.
    focusTarget: FocusEntry,
    focusClaimId: Any,
    // D-pad focus: this screen's route - still needed for
    // FocusRegistry.leftEscapeTarget()/firstItemTarget(route) (the filter
    // row's "press Down" escape target, and AppShell's own rail
    // Right-escape - see LiveTvScreen/ChannelCarouselRow for the identical
    // split between claimFocusEntry's auto-claim and this registration,
    // which are separate concerns that happen to share the same
    // requester), independent of the auto-claim migration below.
    route: String,
    // D-pad focus: gates the pre-registration below and the
    // onFocusChanged-driven continuous re-registration/callback further
    // down - both are meaningless on a touch device with no rail to escape
    // to (see isTv's own doc comment in TvGuideScreen).
    isTv: Boolean,
    // D-pad focus: the channel cell is always the leftmost item in every row
    // (a vertical list of rows, not a single boundary row), so every row's
    // cell - not just the first - escapes to the rail on Left. isBottomRow
    // blocks Down from escaping to the rail at the very bottom of the
    // channel list, same as every other browser screen. When this is the
    // top row, Up is pointed at upEscapeTarget (the screen's own top bar
    // category filter) when provided, rather than relying on Compose's
    // default spatial search - which was picking the rail instead.
    isBottomRow: Boolean,
    isTopRow: Boolean = false,
    upEscapeTarget: FocusRequester? = null,
    // D-pad focus: called the instant this row gains real on-screen focus
    // (see the onFocusChanged block below) - lets the caller record
    // "wherever focus actually is now", independent of whichever
    // FocusEntry originally put it there. See onChannelFocused's own call
    // site in GuideGridContent for the full rationale.
    onChannelFocused: (String) -> Unit,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit
) {
    val channelFocusRequester = remember { FocusRequester() }
    // FIX (confirmed root cause of "focus lands on the right channel but
    // isn't visually highlighted until it happens to also be the playing
    // channel"): was a plain .clickable{} + separate later .focusable() +
    // .onFocusChanged{} - the same bug already found and fixed on this
    // screen's own CategoryDropdown (see its doc comment) and on Home/Live
    // TV's tiles: clickable() creates its own implicit focus-related node,
    // so a separate .focusable() further down the chain can end up being a
    // DIFFERENT node than the one .onFocusChanged was observing - leaving
    // this state out of sync specifically for PROGRAMMATIC focus changes
    // (claimFocusEntry's requestFocus()), even though genuine user-driven
    // D-pad focus happened to still update it correctly, which is exactly
    // why this only showed up on the initial claim and not on manual
    // navigation.
    val interactionSource = remember { MutableInteractionSource() }
    val channelFocused by interactionSource.collectIsFocusedAsState()

    // FIX: was a fixed delay(150) then a single requestFocus() attempt,
    // then later a registerFirstItem()/notifyContentReady() retry-loop
    // handoff - replaced with the same claimFocusEntry mechanism every
    // other migrated screen uses (see FocusEntry's own doc comment, and the
    // Row's own modifier chain below where the actual claimFocusEntry calls
    // live). A row claims focus the moment focusTarget says it's its turn -
    // either this channel is the RestoreItem target (the last-played
    // channel, TV Guide's own reason FocusEntry.RestoreItem exists), or
    // this row is the FirstItem target (the actual first-visible row,
    // whenever there's no valid last-played channel to restore to
    // instead).
    //
    // FocusRegistry.registerFirstItem is unchanged and still called below
    // for whichever of these two is actually the live target right now -
    // it's a separate concern (see the route param's own doc comment)
    // needed regardless of which claimFocusEntry call, if either, actually
    // fires for this specific row.
    val isLiveFirstItemTarget = isTv && focusTarget == FocusEntry.FirstItem && isFirstVisible
    val isLiveRestoreTarget = isTv && focusTarget is FocusEntry.RestoreItem && isRestoreTarget
    if (isLiveFirstItemTarget || isLiveRestoreTarget) {
        android.util.Log.d("DpadFocus", "GuideChannelRow channel=${channel.id} registering as firstItem (isLiveFirstItemTarget=$isLiveFirstItemTarget isLiveRestoreTarget=$isLiveRestoreTarget)")
        FocusRegistry.registerFirstItem(route, channelFocusRequester)
    }
    // FIX: unregister on dispose, unconditionally for every row rather than
    // only the ones that pre-registered above - unregisterFirstItem is
    // identity-safe (only clears the registry entry if it's still THIS
    // row's own requester, a no-op otherwise, see its own doc comment), and
    // a row can now also become the registered one later purely via
    // onFocusChanged below without ever having matched the eligibility
    // check above, so it needs the same cleanup guarantee on dispose.
    DisposableEffect(route, channelFocusRequester) {
        onDispose { FocusRegistry.unregisterFirstItem(route, channelFocusRequester) }
    }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        // ── Channel cell ──
        Row(
            modifier = Modifier
                .width(channelCol)
                .fillMaxHeight()
                // FIX (UI redesign - "grey channel boxes with padding
                // between channels"): matches ProgramCell's own padding/
                // rounding exactly, which this cell previously lacked -
                // was flush with the row's full height and unrounded,
                // while ProgramCell already had vertical=4.dp insets and
                // 6.dp rounding, leaving the two visibly out of step with
                // each other and neither reading as a distinct "box" the
                // way the reference image does.
                //
                // FIX (confirmed root cause of "the first channel is not
                // aligned to the top"): this padding was unconditional,
                // meaning index 0 always had the same 4.dp gap above it as
                // every other row - top is now 0 specifically for the top
                // row, so it sits flush against the grid's own top edge
                // while every other row keeps its spacing.
                .padding(top = if (isTopRow) 0.dp else 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isPreviewing) BbAccent.copy(alpha = 0.15f)
                    else GuideCardGrey
                )
                .then(
                    if (channelFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .focusProperties {
                    left = FocusRegistry.leftEscapeTarget()
                    if (isBottomRow) down = FocusRequester.Cancel
                    if (isTopRow && upEscapeTarget != null) up = upEscapeTarget
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onChannelClick)
                .focusRequester(channelFocusRequester)
                .then(
                    if (isRestoreTarget) {
                        Modifier.claimFocusEntry(
                            current = focusTarget,
                            mine = FocusEntry.RestoreItem(channel.id),
                            requester = channelFocusRequester,
                            scopeKey = route,
                            claimId = focusClaimId
                        )
                    } else Modifier
                )
                .then(
                    if (isFirstVisible) {
                        Modifier.claimFocusEntry(
                            current = focusTarget,
                            mine = FocusEntry.FirstItem,
                            requester = channelFocusRequester,
                            scopeKey = route,
                            claimId = focusClaimId
                        )
                    } else Modifier
                )
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FIX (confirmed root cause of "Right from rail lands on the
            // closest channel instead of the last focused one"): the block
            // this replaced was a raw .onFocusChanged{} placed AFTER
            // .focusable() in the modifier chain above - onFocusChanged
            // observes the focus state of whatever comes AFTER it (further
            // down the chain, closer to the actual content), so positioned
            // there it was watching nothing, not the .focusable() node
            // itself. It never fired even once across an entire session of
            // active D-pad navigation (confirmed by the complete absence
            // of its own log line in a real device capture) - meaning no
            // row ever re-registered itself as the route's firstItem once
            // the original pre-registered target scrolled out of view,
            // which is exactly why the registry was left empty by the time
            // the user pressed Left. channelFocused (via
            // collectIsFocusedAsState() above) is the interactionSource-
            // reported focus state already proven correct on this same row
            // for the border highlight, and unlike a raw onFocusChanged
            // modifier its correctness doesn't depend on chain position -
            // reacting to it here is the reliable equivalent.
            LaunchedEffect(channelFocused) {
                if (channelFocused && isTv) {
                    android.util.Log.d("DpadFocus", "GuideChannelRow channel=${channel.id} onFocusChanged: isFocused=true")
                    FocusRegistry.registerFirstItem(route, channelFocusRequester)
                    onChannelFocused(channel.id)
                }
            }
            // Show the channel's number (fallback: channel id, then row index)
            Text(
                text = channel.number.ifBlank { channel.id }.ifBlank { "${index + 1}" },
                color = BbTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(32.dp)
            )
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = channel.name,
                color = if (isPreviewing) BbAccent else BbTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isPreviewing) {
                Icon(Icons.Default.PlayArrow, null, tint = BbAccent, modifier = Modifier.size(16.dp))
            }
        }

        // ── Program strip ──
        Box(Modifier.weight(1f).fillMaxHeight()) {
            programs.forEach { program ->
                var start = parseMinutes(program.time)
                // Midnight-wrap normalization relative to the grid window
                if (start - gridStart > 12 * 60) start -= 24 * 60
                if (gridStart - start > 12 * 60) start += 24 * 60
                val end = start + program.duration
                if (end <= gridStart || start >= gridStart + WINDOW_MIN) return@forEach
                val x = pxPerMin * (start - gridStart).coerceAtLeast(0)
                val w = pxPerMin * program.duration.coerceAtLeast(15)
                ProgramCell(
                    program = program,
                    modifier = Modifier
                        .offset(x = x)
                        .width(w)
                        .fillMaxHeight()
                        .padding(top = if (isTopRow) 0.dp else 2.dp, bottom = 2.dp, start = 2.dp, end = 2.dp)
                        .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                        .then(if (isTopRow && upEscapeTarget != null) Modifier.focusProperties { up = upEscapeTarget } else Modifier),
                    onClick = { onProgramClick(program) }
                )
            }
        }
    }
}

@Composable
private fun ProgramCell(
    program: EpgProgram,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) BbAccent.copy(alpha = 0.25f) else GuideCardGrey.copy(alpha = 0.8f))
            .then(
                if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(6.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = program.name,
            color = if (focused) BbTextPrimary else BbTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =====================================================================
// TOP BAR CONTROLS
// =====================================================================
@Composable
private fun SortIconButton(
    mode: SortMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // FIX (UI redesign - "filters matching the format and height of the
    // filters on other pages"): matches Live TV's identical compact
    // parameter/sizing exactly, so this screen's icon buttons are visually
    // consistent with every other screen's filter row.
    compact: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val (icon, contentDesc) = when (mode) {
        SortMode.DEFAULT -> Icons.Default.Sort to "Sort: Default"
        SortMode.A_Z -> Icons.Default.ArrowUpward to "Sort: A to Z"
        SortMode.Z_A -> Icons.Default.ArrowDownward to "Sort: Z to A"
        SortMode.NUMERIC -> Icons.Default.Numbers to "Sort: Numeric"
    }
    Box(
        modifier = Modifier
            .size(if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
            .then(if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
            .then(modifier)
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = if (isFocused) BbAccent else BbTextSecondary)
    }
}

@Composable
private fun CategoryDropdown(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    focusRequester: FocusRequester? = null,
    // FIX (confirmed root cause of "focus landed on rail when category
    // filter changed"): this used to be a focusTarget: FocusEntry
    // parameter, and the guard below checked ITS value (skipping the
    // deferred re-claim once it read FirstItem/RestoreItem), on the
    // assumption that meant the target row had already claimed focus -
    // true on Live TV, where FirstItem is always index 0 and claims
    // immediately, but false here: focusTarget recomputes synchronously
    // the instant state.selectedCategory changes, while the actual row
    // claim is gated behind TvGuideScreen's own async scrollToItem
    // effect, which hasn't necessarily run yet at that exact moment. The
    // guard was skipping itself before the row had any chance to actually
    // claim anything, leaving nothing holding focus in that gap - which
    // the rail then caught by default. Guarding on whether the scroll has
    // genuinely settled instead reflects the real condition directly.
    isScrollSettled: Boolean = true,
    // FIX (UI redesign - "filters matching the format and height of the
    // filters on other pages"): matches Live TV's identical compact
    // parameter/sizing exactly.
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val latestIsScrollSettled by rememberUpdatedState(isScrollSettled)
    val scope = rememberCoroutineScope()
    val verticalPadding = if (compact) 4.dp else 14.dp
    val fontSize = if (compact) 12.sp else 16.sp
    Box(modifier = if (compact) Modifier.width(150.dp) else Modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties { left = FocusRegistry.leftEscapeTarget() }
                .clickable { expanded = true }
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedCategory?.title ?: "All Categories",
                color = BbTextPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle categories",
                tint = BbTextSecondary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                // FIX (same mechanism found and fixed on Home's/Live TV's
                // CategoryDropdown - see their own doc comments for the
                // full investigation): dismissing without selecting
                // anything disposes the focused menu item, and
                // DropdownMenu's own Popup teardown can override a focus
                // claim made synchronously here - a synchronous attempt
                // wins the immediate-disposal race, the deferred one (on
                // FocusRegistry's own process-wide scope, surviving
                // regardless of what this dismissal does to this
                // composable's own lifecycle) wins the later Popup-teardown
                // race. Nothing changes on a plain dismissal, so this one
                // stays unconditional (unlike onClick's guarded version
                // below).
                if (focusRequester != null) {
                    runCatching { focusRequester.requestFocus() }
                    FocusRegistry.deferredRequestFocus(focusRequester)
                }
            },
            modifier = Modifier.background(BbSurface)
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = cat.title,
                            color = if (cat.id == selectedCategory?.id) BbAccent else BbTextPrimary,
                            fontWeight = if (cat.id == selectedCategory?.id) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        // FIX (same mechanism found and fixed on Home's/
                        // Live TV's CategoryDropdown): arming this
                        // synchronously, before the selection call, closes
                        // the race where focus could reach the rail before
                        // this screen's own state has even reacted to the
                        // selection yet.
                        FocusRegistry.setContentTransitioning(true)
                        expanded = false
                        // Synchronous attempt wins the race against this
                        // menu item's own immediate disposal (expanded =
                        // false above) - unrelated to the deferred work
                        // below, so this stays immediate.
                        if (focusRequester != null) {
                            runCatching { focusRequester.requestFocus() }
                        }
                        // FIX (confirmed root cause of "the category
                        // dropdown is not collapsing immediately after I
                        // make a category selection - it waits until the
                        // list is populated to collapse"): selectCategory
                        // runs a synchronous .filter{} over up to 5038
                        // channels directly on the calling thread, which
                        // cascades into a full recomposition of the entire
                        // grid - all of it previously running inside this
                        // SAME onClick call, before Compose ever got a
                        // chance to paint a frame reflecting expanded =
                        // false on its own. Both changes landed in the
                        // same synchronous call stack, so Compose batched
                        // them into the same recomposition pass - the
                        // dropdown's close was applied in code immediately
                        // but never got to render as its own, separate,
                        // fast frame. Deferring the actual selection to
                        // just after the next frame renders means the
                        // dropdown's own collapse commits and paints
                        // first, independently of however long the
                        // heavier list recomputation takes afterward.
                        scope.launch {
                            withFrameNanos { }
                            onCategorySelected(cat)
                            if (focusRequester != null) {
                                // FIX (see isScrollSettled's own doc comment
                                // above): guarded on whether the scroll has
                                // actually settled, not on focusTarget's value
                                // - focusTarget recomputes the instant the
                                // category changes, well before the row's own
                                // scroll-then-claim sequence has had a chance
                                // to run, so checking it here was skipping this
                                // re-claim before the row could have possibly
                                // taken over yet.
                                FocusRegistry.deferredRequestFocus(focusRequester) {
                                    !latestIsScrollSettled
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// =====================================================================
// HELPERS
// =====================================================================
private fun parseMinutes(t: String): Int {
    val timePart = t.substringAfter(' ').trim()
    val parts = timePart.split(':')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun formatMin(total: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, (total / 60) % 24)
        set(Calendar.MINUTE, total % 60)
    }
    return SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
}