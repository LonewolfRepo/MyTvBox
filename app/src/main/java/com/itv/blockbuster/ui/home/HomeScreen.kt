package com.itv.blockbuster.ui.home

import android.util.Log
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.components.CarouselRow
import com.itv.blockbuster.ui.components.DefaultCarouselHeaderBlockHeight
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.components.HeroContent
import com.itv.blockbuster.ui.components.HomeRow
import com.itv.blockbuster.ui.components.PosterGrid
import com.itv.blockbuster.ui.components.SearchIconButton
import com.itv.blockbuster.ui.components.rememberCarouselBlockHeight
import com.itv.blockbuster.ui.components.rememberTopPinningBringIntoViewSpec
import com.itv.blockbuster.ui.components.rememberDefaultCarouselItemWidth
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
import com.itv.blockbuster.ui.theme.RailCollapsedWidth
import com.itv.blockbuster.util.FocusEntry
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.safeFocusEscape
import com.itv.blockbuster.util.VodNavigationCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenPortals: () -> Unit,
    onOpenVodDetail: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    // D-pad focus: which route this instance represents (HomeScreen is reused
    // for the Home tab, the Adult VOD browser, and now also Movies/TV Shows
    // - see contentType below), so FocusRegistry.notifyContentReady only
    // advances focus when THIS screen is the one the rail is currently
    // waiting on.
    route: String = Routes.HOME,
    // NEW: which section this screen instance is - "home" (default, no
    // filter, mixed movies+series), "vod" (Movies), or "series" (TV Shows).
    // Movies/TV Shows used to be a completely separate screen/ViewModel
    // (VodBrowserScreen/VodBrowserViewModel) that duplicated a large amount
    // of Home's own logic (and had fallen behind on all the newer hero/
    // search/pagination work done here); passing contentType lets this one
    // screen serve all three instead, matching VodBrowserScreen's own
    // initialize(contentType) pattern.
    contentType: String = "home"
) {
    // Mirrors VodBrowserScreen's own LaunchedEffect(contentType) - runs once
    // per distinct contentType value (i.e. once per screen instance in
    // practice, since a given NavHost destination always passes the same
    // value), and is a no-op on recomposition once already set.
    LaunchedEffect(contentType) {
        viewModel.initialize(contentType)
    }

    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val hasMoreCategories by viewModel.hasMoreCategories.collectAsState()

    // NEW: search field starts hidden, revealed only by tapping the search
    // icon button (left of the Genres dropdown). Also force-shown if there's
    // already an active search query (e.g. returning to this screen), so the
    // field showing the active filter is never hidden out from under it.
    var showSearchBar by remember { mutableStateOf(false) }
    val isSearchActive = state.searchQuery.isNotBlank()
    val searchBarVisible = showSearchBar || isSearchActive
    // Shared across both filter-row locations (LargeHomeHeroLayout's overlay
    // and the standalone TV top bar) - they're mutually exclusive at any
    // given moment (only one renders depending on isAllCategories/
    // isSearching), so attaching the same requester to whichever one is
    // currently composed is safe.
    val searchFieldFocusRequester = remember { FocusRequester() }
    // The icon button itself ALSO needs a stable requester (not just the
    // field): typing the very first character - or clearing the query back
    // to blank - flips isSearchActive, which is what LargeHomeHeroLayout's
    // guard (!isSearching) uses to decide whether to render AT ALL. That
    // means the entire hero-overlay-vs-plain-list layout structurally swaps
    // at that exact moment, tearing down and rebuilding the search row from
    // scratch - so whichever element had focus right then (the text field)
    // gets disposed mid-transition, and without an explicit target to move
    // to, Compose's own fallback focus search was landing on the rail
    // instead. Re-requesting focus after that swap (see the two effects/
    // callback below) is what actually fixes that, not merely toggling
    // visibility.
    val searchButtonFocusRequester = remember { FocusRequester() }
    val backPressScope = rememberCoroutineScope()

    // Auto-focus the field whenever it's supposed to be visible - both when
    // the user explicitly opens it via the button, AND when isSearchActive
    // flips true (the structural swap above) so typing the first character
    // doesn't leave focus stranded on whatever got disposed.
    LaunchedEffect(showSearchBar, isSearchActive) {
        if (showSearchBar || isSearchActive) {
            delay(50)
            // FIX (confirmed crash: "IllegalStateException: FocusRequester
            // is not initialized" - stack trace pinpointed exactly this
            // line): this fires whenever showSearchBar/isSearchActive
            // changes, but the search field's own composable can be mid-
            // disposal at that exact moment (e.g. a category/search
            // transition tearing down and rebuilding the layout - see this
            // effect's own doc comment above about the structural swap).
            // requestFocus() on a momentarily-detached requester throws
            // uncatchably from Compose's own internals if called bare; the
            // same class of risk SafeFocusEscape.kt documents for
            // focusProperties overrides, just reached via a direct
            // requestFocus() call instead. Wrapping it here is the correct
            // fix for THIS call site specifically, matching the
            // runCatching pattern already used everywhere else this
            // codebase calls requestFocus() directly.
            runCatching { searchFieldFocusRequester.requestFocus() }
        }
    }

    // D-pad focus / Back: while a search is active, Back should clear the
    // search filter (and hide the field again) instead of AppShell's normal
    // "refocus the rail" behavior. AppShell's own BackHandler always wins
    // priority (see its doc comment), so this goes through the shared
    // interceptor hook rather than a second BackHandler here, which would
    // simply never fire - see FocusRegistry.consumeBackPressInterceptor.
    DisposableEffect(isSearchActive) {
        if (isSearchActive) {
            FocusRegistry.setBackPressInterceptor {
                viewModel.updateSearch("")
                showSearchBar = false
                // Clearing the query flips isSearchActive back to false,
                // which - same structural swap as above, in reverse - tears
                // down the search field the moment it happens. Explicitly
                // send focus to the (stable, still-composed-either-way)
                // search button afterward instead of leaving it to fall
                // through to the rail.
                backPressScope.launch {
                    delay(100)
                    val result = runCatching { searchButtonFocusRequester.requestFocus() }
                    android.util.Log.d("DpadFocus", "Back-clear-search interceptor: searchButtonFocusRequester.requestFocus() success=${result.isSuccess}")
                }
                true
            }
        } else {
            FocusRegistry.setBackPressInterceptor(null)
        }
        onDispose { FocusRegistry.setBackPressInterceptor(null) }
    }

    // FIX: D-pad focus - the LazyColumn's scroll position survives navigating
    // away and back (rememberLazyListState is rememberSaveable-backed, and
    // Navigation Compose's restoreState=true preserves that across tab
    // switches). Hoisting the state here lets CarouselRow calls below
    // determine which row is CURRENTLY first-visible (via
    // listState.firstVisibleItemIndex), so the auto-focus-on-load handoff
    // targets whatever's actually on screen right now rather than always
    // row 0 - which also fixes row 0's FocusRequester failing to accept
    // focus when it's scrolled off-screen and not laid out.
    val listState = rememberLazyListState()

    // D-pad focus: explicit Up target for the first carousel row, pointing at
    // this screen's own top bar (category filter dropdown) rather than
    // relying on Compose's default spatial search - the top bar's controls
    // are right-aligned while the leftmost poster is left-aligned, so the
    // default heuristic was picking whichever rail item sat geometrically
    // closest instead of a top bar control, letting Up escape to the rail.
    // Moved above focusTarget below - it's also this screen's Fallback
    // claim target now, so it needs to exist before that LaunchedEffect
    // references it.
    val topBarFocusRequester = remember { FocusRequester() }

    // PHASE 3 (D-pad focus redesign - centralized FocusEntry mechanism,
    // see FocusEntry.kt): replaces BOTH of the old mechanisms this used to
    // be - the initial-load notifyContentReady() handoff, AND the separate
    // filter-change refocus dance with its own isInitialFilterState/
    // awaitingFilterRefocus bookkeeping - with one computed value. It
    // naturally covers "just navigated in fresh" AND "a filter changed
    // while already here" identically, since it's recomputed fresh on
    // every recomposition from current state, and the actual claiming
    // (via claimFocusEntry, used below on CarouselRow's first item and on
    // topBarFocusRequester) only reacts when this value actually CHANGES -
    // so it won't steal focus back if the user has already moved elsewhere
    // while it happens to still evaluate to the same entry.
    //
    // Deliberately NOT dependent on state.searchQuery directly: it only
    // changes as a side effect of state.rows/state.isLoading updating once
    // the search debounce actually fires - see HomeUiState's own debounce -
    // so typing itself never causes focusTarget to re-evaluate to something
    // different mid-keystroke, unlike the OLD mechanism this replaced.
    val focusTarget = when {
        state.isLoading -> FocusEntry.None
        state.rows.isNotEmpty() -> FocusEntry.FirstItem
        else -> FocusEntry.Fallback
    }
    // FIX (see claimFocusEntry's doc comment in FocusEntry.kt for the full
    // bug this closes): a fresh identity every time focusTarget genuinely
    // changes, even if the new value repeats an earlier one (e.g.
    // None -> FirstItem -> None -> FirstItem again). Without this,
    // FocusEntry.FirstItem being the same singleton object every time meant
    // claimFocusEntry re-fired on every scroll step, since a different item
    // becomes isFirstItem each time and sees current == mine as still true.
    val focusClaimId = remember(focusTarget) { Any() }
    // FIX (confirmed via device logcat - search field correctly lost
    // Compose focus and topBarFocusRequester correctly claimed it, but the
    // soft keyboard stayed visually open regardless): Compose focus moving
    // away from a text field does NOT automatically dismiss the IME, these
    // are two independent systems. The keyboard stayed connected for many
    // seconds afterward, until an unrelated Back press arrived - and
    // Android's own window-level IME handling consumed THAT press to
    // dismiss the still-open keyboard, entirely bypassing Compose's
    // BackHandler stack (confirmed: consumeBackPressInterceptor's own
    // diagnostic never fired). Explicitly hiding the keyboard the moment
    // focus settles away from the search field - either onto content
    // (FirstItem) or onto the filter row (Fallback) - closes this gap:
    // there's no longer a stale, forgotten-about open keyboard left for a
    // later, unrelated Back press to consume instead of reaching Compose.
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusTarget) {
        Log.d("DpadFocus", "HomeScreen(route=$route): focusTarget=$focusTarget rowCount=${state.rows.size}")
        // FIX ("focus falls on rail then transitions to content" switching
        // All Categories <-> an individual category - see FocusRegistry.
        // setContentTransitioning's doc comment): marks this screen as
        // transitioning for the duration of FocusEntry.None specifically -
        // covers both the ordinary loading case and the heavier All-
        // Categories/individual-category layout swap, which can outlast
        // the rail's normal 120ms expand debounce.
        FocusRegistry.setContentTransitioning(focusTarget == FocusEntry.None)
        if (focusTarget == FocusEntry.FirstItem || focusTarget == FocusEntry.Fallback) {
            keyboardController?.hide()
        }
        // FIX (confirmed via device logcat: even with rail-expansion
        // suppressed during isContentTransitioning, focus still visibly
        // touched the rail's profile icon before settling on the new
        // content - selecting a category from the dropdown, then waiting
        // for All Categories to reload): the Fallback case below already
        // claims topBarFocusRequester reliably, but None (loading) never
        // claimed anything at all - it only armed the rail-expansion
        // suppression, leaving focus to fall wherever Compose's own
        // fallback search happened to land once whatever was previously
        // focused (a dropdown menu item, a carousel item, anything) got
        // disposed by the transition. Repeated attempts to instead time a
        // focus-RETURN from inside the action that triggered this (the
        // dropdown's own onClick/onDismissRequest) kept racing against a
        // different disposal each time - the popup's own teardown, then
        // the content's own recomposition - and each fix just shifted
        // which race it lost. Claiming topBarFocusRequester here too,
        // exactly the way Fallback already does, sidesteps all of that:
        // it gives focus one guaranteed, always-composed place to land for
        // the entire loading window (see HomeOverlayFilterRow's own doc
        // comment on why its call site - and everything inside it,
        // including this trigger - stays stable across the isAllCategories
        // layout swap), regardless of what disposed the previous target or
        // why. FirstItem's own claim still takes over once the new content
        // is actually ready. Harmless no-op (via runCatching) on the very
        // first cold-start load, where this trigger may not be composed
        // yet - same as it already is for the Fallback case below.
        if (focusTarget == FocusEntry.None) {
            runCatching { topBarFocusRequester.requestFocus() }
            // FIX (confirmed as a side effect of the LoadingOverlay fix
            // above - "pagination broke selecting a detailed genre", focus
            // still reaching the rail on some reloads despite the
            // topBarFocusRequester claim above succeeding): LoadingOverlay
            // used to take over the whole screen on every reload, which
            // implicitly reset this LazyColumn's scroll position - a full
            // teardown-and-recompose naturally starts fresh. Now that the
            // content branch (and this LazyColumn) stays mounted through
            // every reload instead, nothing was left to reset it. If the
            // list had been scrolled deep into many rows under the
            // PREVIOUS filter, and the new one (a different genre, a
            // different category) produces fewer rows, the viewport stays
            // scrolled to that stale position - past the end of the new,
            // shorter list. CarouselRow's pagination trigger depends on
            // focus reaching near the bottom of what's actually visible,
            // and the FirstItem claim depends on the first row genuinely
            // being composed - LazyColumn only composes what's within (or
            // near) its current scroll position, so a stale position can
            // leave both without a real, composed target to land on,
            // which is exactly the kind of gap Compose's own fallback
            // search fills by landing on the rail instead. Wrapped in
            // runCatching since this listState may not be attached to a
            // composed LazyColumn at all when on the single-category grid
            // view (CategoryGridWithHeroLayout) - a harmless no-op there.
            runCatching { listState.scrollToItem(0) }
        }
        if (focusTarget == FocusEntry.Fallback) {
            // topBarFocusRequester (the "All Categories" field) is attached
            // via a parameter into HomeCategoryDropdown rather than a
            // direct .focusRequester() modifier at this level, so it's
            // claimed here directly rather than via claimFocusEntry's
            // Modifier-extension form (used below for CarouselRow's first
            // item, where a direct modifier attachment IS possible).
            val result = runCatching { topBarFocusRequester.requestFocus() }
            android.util.Log.d("DpadFocus", "Fallback claim: topBarFocusRequester.requestFocus() success=${result.isSuccess}")
        }
    }

    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // NEW: Hero banner persistence + focus-following (TV/large form factor only).
    // - On phones in portrait, the hero is hidden entirely (see isPortrait below) -
    //   there just isn't enough vertical room to spare for it there.
    // - On TV/landscape ("large form factor"), it's a persistent, non-focusable
    //   BACKGROUND element (not a scrolling list item) pinned to the top of the
    //   screen and sized to exactly half the screen height, and its content
    //   follows whichever poster currently has D-pad focus rather than staying
    //   fixed to the "Recently Added" item - falling back to that only until
    //   something actually gets focused.
    // FIX (rapid-scroll GC/decode pressure - same investigation as the
    // carousel prefetch cache-key fix): onItemFocused fires on EVERY poster
    // focus change, including during rapid held-key scrolling where focus
    // can change many times per second. focusedHeroItem used to be set
    // directly and synchronously from that callback, meaning the hero
    // banner - the single LARGEST image on this page - would start
    // decoding a brand new full-bleed backdrop on every one of those
    // transient focus changes, almost always getting superseded (and its
    // in-flight decode/crossfade abandoned) before the user ever actually
    // saw it, since they'd already moved on to the next item. Coil does
    // cancel a superseded request, but the decode/crossfade work already
    // started before cancellation lands isn't free, and doing this on
    // every single frame of a held-key scroll adds up to real, wasted
    // allocation pressure - exactly the kind the jank log's GC frequency
    // pointed at.
    //
    // rawFocusedHeroItem tracks the actual latest focus target immediately
    // (so anything else that depends on "what's focused right now" isn't
    // delayed); focusedHeroItem - which is what HeroBanner actually
    // renders - only follows it after a short settle delay confirms focus
    // has actually stopped moving. Rapid scrolling therefore never starts
    // more than one hero decode per brief pause, instead of one per item
    // passed through.
    var focusedHeroItem by remember { mutableStateOf<PortalVodItem?>(null) }
    var rawFocusedHeroItem by remember { mutableStateOf<PortalVodItem?>(null) }
    LaunchedEffect(rawFocusedHeroItem) {
        // UPDATED: was 150ms - raised to 500ms per explicit request, after
        // device data (gfx_stats.txt) showed "High input latency" affecting
        // 93.6% of frames (2244/2397) even after the first debounce - still
        // proportionally worse than before that fix, meaning 150ms wasn't
        // enough headroom during sustained rapid scrolling. Only committing
        // the hero change once focus has genuinely settled on an item for
        // half a second - not just paused briefly mid-scroll - means a
        // fast, continuous scroll through many items in under 500ms never
        // triggers a single hero decode at all, instead of one per brief
        // pause.
        delay(500)
        focusedHeroItem = rawFocusedHeroItem
    }
    // NEW: text (title/metadata/description) split out to its own, faster
    // debounce - unlike the image, rendering text isn't expensive enough to
    // meaningfully contribute to the GC/decode pressure driving the 500ms
    // delay above, so there's no reason to make the banner's text feel as
    // sluggish as the image during a brief pause. 150ms is enough to still
    // skip updating on every single item during genuinely rapid scrolling,
    // while feeling close to immediate once the user actually slows down.
    var textHeroItem by remember { mutableStateOf<PortalVodItem?>(null) }
    LaunchedEffect(rawFocusedHeroItem) {
        delay(150)
        textHeroItem = rawFocusedHeroItem
    }
    val heroItem = textHeroItem ?: state.hero
    val imageHeroItem = focusedHeroItem ?: state.hero
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeightDp * 0.5f

    // Reset the focus-followed hero item whenever the row set changes (category/
    // genre/search change) so it doesn't keep showing a poster from a filter that
    // no longer applies.
    LaunchedEffect(state.selectedCategory, state.selectedGenre, state.searchQuery) {
        focusedHeroItem = null
        rawFocusedHeroItem = null
    }

    // Reload Home when returning from Settings if Home category
    // visibility/order was changed while away.
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRestoreScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadIfHomeOrderChanged()
                // D-pad focus: restore focus onto the exact poster that was
                // clicked, if Back just popped a VOD detail screen pushed
                // from this one. No-ops unless RailShell's route-change
                // handling armed this route for restoration (see
                // FocusRegistry.armRestoreFocus/restoreClickedItemFocus and
                // AppShell.kt), so it doesn't interfere with the ordinary
                // rail-then-first-item handoff on other resumes (rail
                // clicks, tab switches).
                focusRestoreScope.launch { FocusRegistry.restoreClickedItemFocus(route) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        when {
            state.connectionError != null -> ConnectionErrorOverlay(
                message = state.connectionError!!,
                onRetry = viewModel::retry,
                onOpenPortals = onOpenPortals
            )
            // FIX (root cause of "focus flies to the rail" across every
            // reload path - search, category change, genre change alike):
            // this used to be (isLoading || isConnecting) && rows.isEmpty().
            // rows.isEmpty() is ALSO true during every ordinary mid-session
            // reload - selecting a category, searching, changing genre -
            // not just the true first-ever load. Whenever that condition
            // was true, this branch rendered LoadingOverlay INSTEAD of the
            // else branch below - which is where HomeOverlayFilterRow (and
            // therefore topBarFocusRequester's target, the category
            // dropdown trigger) actually lives. That's not a timing race
            // that a delay or a synchronous requestFocus() call could ever
            // reliably win: the target was disposed outright, genuinely
            // absent from the composition tree for the duration of every
            // reload, because the branch hosting it wasn't rendering at
            // all. Every fix attempted at the dropdown's own onClick/
            // onDismissRequest, or reactively in HomeScreen's
            // LaunchedEffect(focusTarget), was chasing this same symptom
            // from a different angle - none could work, because none of
            // them could conjure a composable that Compose had already
            // torn down. With nothing else focusable in LoadingOverlay for
            // a rail-registered route like home (see its own doc comment -
            // it deliberately skips claiming focus itself in that case),
            // Compose's own fallback search found the one thing that WAS
            // still composed and focusable: the rail, in AppShell, outside
            // this Box entirely.
            //
            // state.categories is populated exactly once, during the
            // initial connect+load (loadHome), and is never cleared by any
            // subsequent reload (selectCategory/selectGenre/updateSearch
            // all only ever touch rows, isLoading and related fields) -
            // see HomeViewModel. That makes it a reliable signal for "has
            // this screen ever completed its first load", independent of
            // whatever the CURRENT reload is doing to rows. Gating on it
            // instead means LoadingOverlay only ever takes over the whole
            // screen for a genuine cold start / reconnect, when there's
            // nothing to show a filter row FOR yet anyway. Every
            // subsequent reload now falls through to the else branch
            // below unconditionally, keeping HomeOverlayFilterRow (and
            // its dropdown trigger) mounted and focusable the entire time
            // - LazyColumn/the grid underneath it render zero items
            // against an empty rows list in the meantime (safe - see
            // LargeHomeHeroLayout/CategoryGridWithHeroLayout, both already
            // handle this), rather than focus having nowhere real to go.
            state.categories.isEmpty() && (state.isLoading || state.isConnecting) ->
                LoadingOverlay(isConnecting = state.isConnecting, route = route, focusTarget = focusTarget)
            else -> {
                // NEW: A specific category (anything but "All Categories") switches
                // from the horizontal carousel rows to a scrollable poster grid,
                // since there's only ever one flat list of items to show at that
                // point - a grid reads better than a single wide row for that.
                // Hoisted above the Column/LargeHomeHeroLayout branch below since
                // both need it to decide which layout to render.
                val isAllCategories = state.selectedCategory?.id == "*" || state.selectedCategory?.id == "0"
                val isSearching = state.searchQuery.isNotBlank()

                // NEW: on large form factor (TV/landscape), always use the
                // hero-overlay layout: a non-focusable hero background
                // pinned to the top half of the screen, with up to 2
                // carousels (Netflix-style) scrolling up over it, and the
                // filters overlaid on top of the hero rather than pushing
                // content down.
                //
                // Deliberately NOT gated on !isSearching anymore: this used
                // to fall through to a completely different, unconstrained
                // LazyColumn (no hero, no 2-row viewport sizing) the instant
                // a search started, which caused a jarring layout jump right
                // as the user typed the first character - the hero would
                // vanish and however many rows happened to fit would show
                // instead of exactly 2. Keeping this same layout for search
                // results too (state.rows is just a single "Search Results"
                // row in that case) keeps the hero persistent and the
                // viewport math identical whether browsing or searching -
                // see loadSearchResults, which was also changed to stop
                // nulling out the hero item when a search starts.
                // FIX ("focus falls on rail then transitions to content"
                // switching All Categories <-> an individual category):
                // LargeHomeHeroLayout and CategoryGridWithHeroLayout used
                // to each own a SEPARATE BoxWithConstraints AND a separate
                // HomeOverlayFilterRow call. Even though topBarFocusRequester
                // (the FocusRequester object) is stable across this toggle -
                // declared once, above, at this composable's own top level -
                // the ACTUAL UI ELEMENT it was attached to was not: Compose's
                // composition identity is call-site-based, not function-
                // identity-based, so two separate call sites to
                // HomeOverlayFilterRow (one per branch) meant switching
                // between them disposed one instance and composed a
                // genuinely new one, taking whatever had focus down with it.
                // The rail catching that transient disposal was a downstream
                // symptom, not the actual bug.
                //
                // Wrapping both branches in ONE shared BoxWithConstraints
                // and calling HomeOverlayFilterRow exactly ONCE, as a
                // sibling to the if/else rather than nested inside each
                // side, gives it a single, stable call site that survives
                // the isAllCategories toggle untouched - only the content
                // branch underneath it (rows vs. grid) swaps. Z-order is
                // preserved (filter row drawn after/on top of content,
                // matching the previous "overlaid filters" positioning)
                // since both are direct children of the same Box.
                if (!isPortrait) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        if (isAllCategories) {
                            LargeHomeHeroLayout(
                                state = state,
                                heroItem = heroItem,
                                imageHeroItem = imageHeroItem,
                                focusTarget = focusTarget,
                                focusClaimId = focusClaimId,
                                favoriteIds = favoriteIds,
                                progressMap = progressMap,
                                listState = listState,
                                hasMoreCategories = hasMoreCategories,
                                topBarFocusRequester = topBarFocusRequester,
                                route = route,
                                onItemClick = { item ->
                                    VodNavigationCache.currentItem = item
                                    onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                },
                                onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                onItemFocused = { item -> rawFocusedHeroItem = item },
                                onLoadMoreRowItems = { rowId -> viewModel.loadMoreRowItems(rowId) },
                                onLoadMoreCategories = { viewModel.loadMoreCategories() }
                            )
                        } else {
                            // FIX: was gated on `gridRow != null && gridRow.items.isNotEmpty()`,
                            // falling through to a bare "No items found" text with
                            // no filter row at all when the category was empty -
                            // see CategoryGridWithHeroLayout's own FIX comment.
                            // Always call it now with a safe empty-items fallback
                            // row; it decides internally whether to show the grid
                            // or the "no items" placeholder, but the hero always
                            // renders either way, and the filter row below is now
                            // shared with the All Categories case regardless.
                            val gridRow = state.rows.firstOrNull() ?: HomeRow(id = "empty", title = "", items = emptyList(), hasMore = false)
                            CategoryGridWithHeroLayout(
                                state = state,
                                heroItem = heroItem,
                                imageHeroItem = imageHeroItem,
                                focusTarget = focusTarget,
                                focusClaimId = focusClaimId,
                                gridRow = gridRow,
                                favoriteIds = favoriteIds,
                                progressMap = progressMap,
                                topBarFocusRequester = topBarFocusRequester,
                                route = route,
                                onItemClick = { item ->
                                    VodNavigationCache.currentItem = item
                                    onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                },
                                onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                onItemFocused = { item -> rawFocusedHeroItem = item },
                                onLoadMore = { viewModel.loadMoreRowItems(gridRow.id) }
                            )
                        }

                        // Filters: shrunk down and overlaid directly on the hero
                        // card - one call site, a sibling to the if/else above,
                        // shared by both All Categories and a specific category
                        // (see the FIX comment above this whole block for why
                        // that matters).
                        HomeOverlayFilterRow(
                            state = state,
                            searchBarVisible = searchBarVisible,
                            onToggleSearchBar = { showSearchBar = !showSearchBar },
                            searchFieldFocusRequester = searchFieldFocusRequester,
                            searchButtonFocusRequester = searchButtonFocusRequester,
                            topBarFocusRequester = topBarFocusRequester,
                            route = route,
                            onGenreSelected = { viewModel.selectGenre(it) },
                            onCategorySelected = { viewModel.selectCategory(it) },
                            onSearchChanged = { viewModel.updateSearch(it) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 24.dp)
                        )
                    }
                    return@Box
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (isPortrait) {
                        // Portrait Layout (mobile): title removed - search icon +
                        // filters row instead, matching the TV/landscape pattern.
                        // Tapping the icon toggles the search field below (row 2),
                        // rather than always showing it.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchIconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                modifier = Modifier
                                    .focusRequester(searchButtonFocusRequester)
                                    .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                            Spacer(Modifier.weight(1f))
                            HomeGenreDropdown(
                                genres = state.genres,
                                selectedGenre = state.selectedGenre,
                                onGenreSelected = { viewModel.selectGenre(it) },
                                modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                        }
                        if (searchBarVisible) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.updateSearch(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp) // FIX: Corrected padding parameters
                                    // FIX ("FocusRequester is not initialized" crash on
                                    // typing into mobile search): this field never had
                                    // searchFieldFocusRequester attached, but the shared
                                    // auto-focus LaunchedEffect (keyed on showSearchBar/
                                    // isSearchActive) calls requestFocus() on it
                                    // unconditionally whenever the query becomes
                                    // non-blank - portrait and landscape are mutually
                                    // exclusive at any moment, so attaching the same
                                    // requester here too is safe (matches how
                                    // searchButtonFocusRequester is already shared above).
                                    .focusRequester(searchFieldFocusRequester)
                                    .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) },
                                placeholder = { Text("Search...", color = BbTextMuted) },
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
                    } else {
                        // TV / Landscape Layout: Single Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.weight(1f))
                            // NEW: Search icon button (left of Genres filter); the field
                            // itself stays hidden until tapped, matching
                            // LargeHomeHeroLayout's overlaid filter row exactly (same
                            // toggle behavior + compact styling) so filter controls never
                            // look or behave differently between the "All Categories"
                            // carousel view and this grid view.
                            SearchIconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                modifier = Modifier
                                    .focusRequester(searchButtonFocusRequester)
                                    .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                            if (searchBarVisible) {
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.updateSearch(it) },
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(44.dp)
                                        .focusRequester(searchFieldFocusRequester)
                                        .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) },
                                    placeholder = { Text("Search...", color = BbTextMuted, fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextMuted, modifier = Modifier.size(16.dp)) },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
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
                            Spacer(Modifier.width(10.dp))
                            HomeGenreDropdown(
                                genres = state.genres,
                                selectedGenre = state.selectedGenre,
                                onGenreSelected = { viewModel.selectGenre(it) },
                                compact = true,
                                modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                            Spacer(Modifier.width(10.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                compact = true,
                                focusRequester = topBarFocusRequester,
                                modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
                            )
                        }
                    }

                    // isAllCategories/isSearching are hoisted above (see the
                    // LargeHomeHeroLayout guard) since this whole Column is now only
                    // reached for: portrait, a specific category (grid view), or
                    // TV/landscape while actively searching.
                    if (isAllCategories) {
                        // NEW: on TV/landscape the hero is persistent (sits above the
                        // scrolling rows, not inside the LazyColumn as a scrolling
                        // item) and follows D-pad focus. On phones in portrait there's
                        // no room to spare for it, so it's skipped entirely and rows
                        // simply scroll from the top like before.
                        //
                        // NOTE: this !isPortrait branch is now only reached while
                        // isSearching is true (the non-searching case is diverted to
                        // LargeHomeHeroLayout above), so the hero itself never
                        // actually renders here (guarded by !isSearching below) -
                        // only the plain scrolling carousel rows do.
                        if (!isPortrait) {
                            if (!isSearching && heroItem != null) {
                                HeroBanner(
                                    hero = heroItem?.let { HeroContent.Vod(it) },
                                    imageHero = imageHeroItem?.let { HeroContent.Vod(it) },
                                    height = heroHeight,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // FIX: D-pad focus - matches LargeHomeHeroLayout's
                            // identical top-pinning BringIntoViewSpec wrap (see
                            // rememberTopPinningBringIntoViewSpec's doc comment)
                            // - this branch is only reached while actively
                            // searching in landscape, but was missing the same
                            // wrap, letting the tail of the previous carousel
                            // peek on screen above the newly-focused one.
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTopPinningBringIntoViewSpec()) {
                                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                        CarouselRow(
                                            row = row,
                                            focusTarget = focusTarget,
                                            focusClaimId = focusClaimId,                                        nextRowItems = state.rows.getOrNull(index + 1)?.items ?: emptyList(),
                                            favoriteIds = favoriteIds,
                                            progressMap = progressMap,
                                            onItemClick = { item ->
                                                VodNavigationCache.currentItem = item
                                                onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                            },
                                            onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                            onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                            onLoadMore = { viewModel.loadMoreRowItems(row.id) },
                                            // Trigger once focus is within the last batch-
                                            // size (5, matching loadMoreCategories' own batch
                                            // size) of currently-loaded rows, rather than only
                                            // the literal last row - so the very first batch
                                            // (up to 5 rows) already satisfies this on landing,
                                            // starting the next batch in the background right
                                            // away, cascading the same way as each subsequent
                                            // batch arrives. Same existing loadMoreCategories()
                                            // call, just triggered via a wider window instead
                                            // of relying on the trailing sentinel being
                                            // reachable via D-pad focus search (it isn't
                                            // reliably).
                                            onItemFocused = { item ->
                                                rawFocusedHeroItem = item
                                                if (index >= (state.rows.size - 5) && hasMoreCategories && !isSearching) {
                                                    viewModel.loadMoreCategories()
                                                }
                                            },
                                            route = route,
                                            isFirstRow = index == listState.firstVisibleItemIndex,
                                            // REVERTED: Down is unconditionally canceled at the
                                            // true last row again - letting default spatial
                                            // search run while still-paginating was landing
                                            // focus on the rail menu instead of doing nothing,
                                            // which is worse. Reaching the end while genuinely
                                            // still-loading is now made rare instead (see the
                                            // earlier trigger above), rather than trying to
                                            // make that moment itself safe.
                                            isLastRow = index == state.rows.lastIndex,
                                            upEscapeTarget = if (index == 0) topBarFocusRequester else null
                                        )
                                    }
                                    // Vertical Pagination Trigger (only for All Categories, no active search)
                                    if (hasMoreCategories && !isSearching) {
                                        item {
                                            // FIX: re-fire whenever the row set or filters change while
                                            // the spinner is visible, so pagination never stalls after
                                            // a genre selection or an empty category batch.
                                            LaunchedEffect(state.rows.size, state.selectedCategory, state.selectedGenre) {
                                                viewModel.loadMoreCategories()
                                            }
                                            // FIX: this sentinel had no focusable modifier, so
                                            // even with Down no longer blocked above, D-pad
                                            // navigation had nowhere to land - nothing to focus
                                            // meant nothing ever brought it into view, so this
                                            // LaunchedEffect never actually fired from the remote.
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .focusable()
                                                    .focusProperties { down = FocusRequester.Cancel },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = BbAccent,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                    item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(32.dp)) }
                                }
                            }
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                    CarouselRow(
                                        row = row,
                                        focusTarget = focusTarget,
                                        focusClaimId = focusClaimId,                                        nextRowItems = state.rows.getOrNull(index + 1)?.items ?: emptyList(),
                                        favoriteIds = favoriteIds,
                                        progressMap = progressMap,
                                        onItemClick = { item ->
                                            VodNavigationCache.currentItem = item
                                            onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                        },
                                        onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                        onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                        onLoadMore = { viewModel.loadMoreRowItems(row.id) },
                                        // Trigger once focus is within the last batch-
                                        // size (5, matching loadMoreCategories' own batch
                                        // size) of currently-loaded rows, rather than only
                                        // the literal last row - see the matching comment
                                        // on the !isPortrait branch above.
                                        onItemFocused = {
                                            if (index >= (state.rows.size - 5) && hasMoreCategories && !isSearching) {
                                                viewModel.loadMoreCategories()
                                            }
                                        },
                                        route = route,
                                        isFirstRow = index == listState.firstVisibleItemIndex,
                                        // REVERTED: see the matching comment on the
                                        // !isPortrait branch above - Down is
                                        // unconditionally canceled at the true last row
                                        // again.
                                        isLastRow = index == state.rows.lastIndex
                                    )
                                }
                                // Vertical Pagination Trigger (only for All Categories, no active search)
                                if (hasMoreCategories && !isSearching) {
                                    item {
                                        // FIX: re-fire whenever the row set or filters change while
                                        // the spinner is visible, so pagination never stalls after
                                        // a genre selection or an empty category batch.
                                        LaunchedEffect(state.rows.size, state.selectedCategory, state.selectedGenre) {
                                            viewModel.loadMoreCategories()
                                        }
                                        // FIX: see the matching comment on the !isPortrait
                                        // branch above - the sentinel needs to be focusable
                                        // for D-pad Down to ever reach/trigger it.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .focusable()
                                                .focusProperties { down = FocusRequester.Cancel },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = BbAccent,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(32.dp)) }
                            }
                        }
                    } else {
                        // NOTE: only ever reached in portrait now - the !isPortrait
                        // case (specific category, TV/landscape) is fully handled
                        // by the CategoryGridWithHeroLayout early-exit above.
                        val gridRow = state.rows.firstOrNull()
                        if (gridRow != null && gridRow.items.isNotEmpty()) {
                            PosterGrid(
                                row = gridRow,
                                favoriteIds = favoriteIds,
                                progressMap = progressMap,
                                onItemClick = { item ->
                                    VodNavigationCache.currentItem = item
                                    onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                },
                                onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                onLoadMore = { viewModel.loadMoreRowItems(gridRow.id) },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                upEscapeTarget = null,
                                route = route,
                                focusTarget = focusTarget,
                                focusClaimId = focusClaimId,
                                collapsedMenuWidth = 0.dp
                            )
                        } else if (!state.isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "No items found", color = BbTextMuted, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Large-form-factor (TV/landscape) grid layout for a SPECIFIC selected
 * category: same persistent, non-focusable hero banner as the carousel view
 * (LargeHomeHeroLayout below), but with a normal freely-scrolling
 * LazyVerticalGrid underneath instead of the 2-row carousel window - a grid
 * of posters doesn't have the same "D-pad Down with nothing to land on"
 * concern a fixed 2-row carousel viewport does, so there's no need to
 * constrain its height the same way.
 *
 * The grid's first row is aligned to start at EXACTLY the same position a
 * carousel's own first poster row would - see gridTopOffset below - so
 * switching between "All Categories" and a specific category never causes
 * a visible jump in where content starts, on top of the hero itself no
 * longer disappearing either (see loadCategoryContent, which stopped
 * nulling the hero out for the same reason loadSearchResults did earlier).
 */
@Composable
private fun BoxWithConstraintsScope.CategoryGridWithHeroLayout(
    state: HomeUiState,
    heroItem: PortalVodItem?,
    // NEW: independently-debounced image source - see HeroBanner's
    // matching doc comment. Defaults to heroItem so this stays a no-op for
    // anyone not explicitly splitting the two.
    imageHeroItem: PortalVodItem? = heroItem,
    // NEW (Phase 3 follow-up, D-pad focus redesign): forwarded straight
    // through to PosterGrid's own focusTarget param below.
    focusTarget: FocusEntry = FocusEntry.None,
    // FIX: forwarded straight through to PosterGrid's own focusClaimId -
    // see claimFocusEntry's doc comment for the bug this fixes.
    focusClaimId: Any = Unit,
    gridRow: HomeRow,
    favoriteIds: Set<String>,
    progressMap: Map<String, com.itv.blockbuster.data.local.entity.PlaybackProgressEntity>,
    topBarFocusRequester: FocusRequester,
    route: String,
    onItemClick: (PortalVodItem) -> Unit,
    onItemLongClick: (PortalVodItem) -> Unit,
    onFavoriteIconClick: (PortalVodItem) -> Unit,
    onItemFocused: (PortalVodItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val heroHeight = maxHeight * 0.5f
    // Same measured-height source LargeHomeHeroLayout uses for its own
    // carouselsTopOffset, so "block" here is guaranteed to be the exact
    // same value a carousel row would use on this same screen.
    val block = rememberCarouselBlockHeight(maxHeight)
    // A carousel's own POSTER (not its header/title) starts
    // DefaultCarouselHeaderBlockHeight below the row's top (block) -
    // matching the grid's first row to that same absolute position is
    // what "grid items begin exactly at the same position as carousel
    // items" means: poster-to-poster alignment, not row-to-row.
    val gridTopOffset = block + DefaultCarouselHeaderBlockHeight

    if (heroItem != null) {
        HeroBanner(
            hero = heroItem?.let { HeroContent.Vod(it) },
            imageHero = imageHeroItem?.let { HeroContent.Vod(it) },
            height = heroHeight,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }

    // The grid's own viewport is explicitly BOUNDED to start at
    // gridTopOffset (via this external padding+height) rather than
    // using PosterGrid's internal top content padding, which only
    // offsets the FIRST screen of content - once scrolled past that,
    // a fillMaxSize() grid's own scrollable region still spans all the
    // way up to y=0, so later rows would end up covering the ENTIRE
    // hero as you scroll, not just the fixed amount the first row
    // does. Bounding the box itself (matching how LargeHomeHeroLayout
    // constrains its LazyColumn) means the grid can NEVER scroll
    // content into the region above gridTopOffset, regardless of how
    // far down the list you go - exactly mirroring the carousel's
    // fixed-overlap behavior.
    //
    // FIX: was only ever CALLED when gridRow.items was non-empty - the
    // caller skipped this entire composable (hero, grid, AND the
    // filter row below) for an empty category, leaving nothing on
    // screen but a bare "No items found" text with no dropdown to
    // change the category back with. Now always called; the grid
    // itself is what's conditional, so the filter row always renders.
    if (gridRow.items.isNotEmpty()) {
        PosterGrid(
            row = gridRow,
            favoriteIds = favoriteIds,
            progressMap = progressMap,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onFavoriteIconClick = onFavoriteIconClick,
            onLoadMore = onLoadMore,
            onItemFocused = onItemFocused,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = gridTopOffset)
                .height(maxHeight - gridTopOffset),
            upEscapeTarget = topBarFocusRequester,
            route = route,
            focusTarget = focusTarget,
            focusClaimId = focusClaimId,
            collapsedMenuWidth = RailCollapsedWidth,
            // No internal top padding needed - the external padding above
            // already puts the grid's own box exactly at gridTopOffset, so
            // its first row can sit flush with ITS box's own top edge.
            contentPaddingTop = 0.dp,
            containerHeightOverride = maxHeight
        )
    } else if (!state.isLoading) {
        // PHASE 3 (D-pad focus redesign): focus lands directly on the
        // "All Categories" field (topBarFocusRequester) when this
        // screen's content comes back empty - see the
        // FocusEntry.Fallback claim in focusTarget's LaunchedEffect
        // near the top of this composable. This box no longer needs to
        // register itself as a first-item target for that to work;
        // it's kept focusable (with an explicit Up escape back to the
        // filter row) purely as a reasonable landing spot if reached
        // some other way, e.g. an explicit Down press from the filter
        // row.
        val emptyStateFocusRequester = remember(route) { FocusRequester() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = gridTopOffset)
                .height(maxHeight - gridTopOffset)
                .focusRequester(emptyStateFocusRequester)
                .focusProperties { up = topBarFocusRequester }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No items found", color = BbTextMuted, fontSize = 14.sp)
        }
    }
}

/**
 * Large-form-factor (TV/landscape) Home layout matching the reference design:
 * a non-focusable hero card pinned to the background, taking up exactly the
 * top 50% of the screen, with carousels scrolling up over it (so the first
 * carousel visually overlaps the lower portion of the hero), and the
 * search/genre/category filters shrunk down and overlaid on top of the hero
 * rather than pushing any content down.
 *
 * Sizing (poster width, header size/weight/spacing) all comes from the
 * shared CarouselRow/PosterGrid defaults - see
 * CarouselComponents.rememberDefaultCarouselItemWidth - so posters/headers
 * here look identical to every other carousel/grid in the app. Those
 * defaults are specifically tuned so that 2 carousels' worth of height
 * (header + poster row each) equals 1/3 of the screen height - which is
 * exactly the vertical space between the hero's midpoint (where the first
 * carousel's own center is deliberately placed, causing the overlap) and
 * the bottom of the screen. "2 carousels fit on screen" therefore falls out
 * of the sizing rather than being an artificial cap on the data - normal
 * vertical (more categories) and horizontal (more items per row) pagination
 * both keep working exactly as they do everywhere else in the app.
 *
 * Only reached for the "All Categories"/"All Genres" view with no active
 * search on TV/landscape - see the guard in HomeScreen above. Category-
 * specific browsing still uses PosterGrid, and portrait still uses the
 * original stacked (non-overlapping) layout, since there's no room to spare
 * for a background hero there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxWithConstraintsScope.LargeHomeHeroLayout(
    state: HomeUiState,
    heroItem: PortalVodItem?,
    // NEW: independently-debounced image source - see HeroBanner's
    // matching doc comment. Defaults to heroItem so this stays a no-op for
    // anyone not explicitly splitting the two.
    imageHeroItem: PortalVodItem? = heroItem,
    // NEW (Phase 3, D-pad focus redesign): forwarded straight through to
    // CarouselRow's own focusTarget param below - see FocusEntry.kt and
    // HomeScreen's focusTarget computation for what this drives.
    focusTarget: FocusEntry = FocusEntry.None,
    // FIX: forwarded straight through to CarouselRow's own focusClaimId -
    // see claimFocusEntry's doc comment for the bug this fixes.
    focusClaimId: Any = Unit,
    favoriteIds: Set<String>,
    progressMap: Map<String, com.itv.blockbuster.data.local.entity.PlaybackProgressEntity>,
    listState: LazyListState,
    hasMoreCategories: Boolean,
    topBarFocusRequester: FocusRequester,
    route: String,
    onItemClick: (PortalVodItem) -> Unit,
    onItemLongClick: (PortalVodItem) -> Unit,
    onFavoriteIconClick: (PortalVodItem) -> Unit,
    onItemFocused: (PortalVodItem) -> Unit,
    onLoadMoreRowItems: (String) -> Unit,
    onLoadMoreCategories: () -> Unit
) {
    // Both heroHeight and the carousel sizing below are derived from the
    // SAME BoxWithConstraints.maxHeight measurement - the REAL available
    // height this composable actually received, rather than the device's
    // global LocalConfiguration.screenHeightDp (which isn't guaranteed to
    // match what a specific composable gets, e.g. if anything upstream
    // reserves space or applies insets differently than assumed). Using one
    // shared, measured source for both is what keeps the hero-overlap math
    // and the poster/row sizing math from ever disagreeing with each other
    // OR with what's actually on screen.
    //
    // - carouselsTopOffset: where the first carousel's row starts, placing
    //   its vertical center exactly on the hero's bottom edge (the
    //   container's own vertical center, since the hero is exactly 50% of
    //   its height).
    // - carouselsViewportHeight: exactly 2 * block - the LazyColumn is given
    //   this as its OWN fixed height (not fillMaxSize), so it physically
    //   clips anything beyond 2 rows rather than merely being scrolled past
    //   it. That's also what makes vertical scrolling behave as a fixed
    //   2-row "window" sliding over the row list: because the window's
    //   height never lets more than 2 rows lay out inside it, scrolling to
    //   reveal the next row always pushes the previous one fully out the
    //   top rather than just revealing a sliver of a 3rd row - and since
    //   the window's on-screen position never changes, whichever row is on
    //   top never overlaps the (fixed, non-scrolling) hero behind it any
    //   more than the very first row did.
    val heroHeight = maxHeight * 0.5f
    val block = rememberCarouselBlockHeight(maxHeight)
    val itemWidth = rememberDefaultCarouselItemWidth(maxHeight)
    val carouselsTopOffset = block
    val carouselsViewportHeight = block * 2

    // Background hero: pinned to the top, non-focusable (no clickable/
    // focusable modifiers anywhere in HeroBanner), and never scrolls -
    // it stays fixed while the carousels below scroll up over it.
    if (heroItem != null) {
        HeroBanner(
            hero = heroItem?.let { HeroContent.Vod(it) },
            imageHero = imageHeroItem?.let { HeroContent.Vod(it) },
            height = heroHeight,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }

    // Carousels: same scrollable, paginated LazyColumn used everywhere
    // else (horizontal "load more items" per row, vertical "load more
    // categories" once the bottom sentinel scrolls into view) - just
    // clipped to a fixed 2-row-tall window positioned over the hero's
    // overlap point (see the comment above).
    //
    // Wrapped in CompositionLocalProvider so THIS LazyColumn's own
    // focus-triggered scrolling uses the top-pinning spec, while
    // each row's inner horizontal LazyRow (NetflixStyleCarousel) keeps
    // using its own separately-provided horizontal spec - a nested
    // CompositionLocalProvider deeper in the tree always shadows this
    // one for its own subtree, so the two don't conflict.
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTopPinningBringIntoViewSpec()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = carouselsTopOffset)
                .height(carouselsViewportHeight)
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                CarouselRow(
                    row = row,
                    focusTarget = focusTarget,
                    focusClaimId = focusClaimId,                        nextRowItems = state.rows.getOrNull(index + 1)?.items ?: emptyList(),
                    favoriteIds = favoriteIds,
                    progressMap = progressMap,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onFavoriteIconClick = onFavoriteIconClick,
                    onLoadMore = { onLoadMoreRowItems(row.id) },
                    // Trigger once focus is within the last batch-size (5,
                    // matching loadMoreCategories' own batch size) of
                    // currently-loaded rows, rather than only the literal
                    // last row - so the very first batch already
                    // satisfies this on landing, starting the next batch
                    // in the background right away and cascading the
                    // same way as each subsequent batch arrives. Same
                    // existing onLoadMoreCategories() call, just
                    // triggered via a wider window instead of relying on
                    // the trailing sentinel being reachable via D-pad
                    // focus search (it isn't reliably).
                    onItemFocused = { item ->
                        onItemFocused(item)
                        // TEMPORARY DIAGNOSTIC (remove once the "vertical
                        // pagination broke again on the main carousels
                        // page" investigation is resolved): logs every
                        // focus reaching a row, and specifically whether
                        // it's within the pagination trigger window -
                        // directly shows whether focus is even reaching
                        // rows near the end at all, versus getting stuck
                        // earlier and never reaching this window.
                        android.util.Log.d(
                            "DpadFocus",
                            "CarouselRow focus: index=$index of ${state.rows.size} hasMoreCategories=$hasMoreCategories triggerWindow=${index >= (state.rows.size - 5)}"
                        )
                        if (index >= (state.rows.size - 5) && hasMoreCategories) {
                            android.util.Log.d("DpadFocus", "onLoadMoreCategories() firing")
                            onLoadMoreCategories()
                        }
                    },
                    route = route,
                    isFirstRow = index == listState.firstVisibleItemIndex,
                    // REVERTED: Down is unconditionally canceled at the
                    // true last row again - letting default spatial
                    // search run while still-paginating was landing focus
                    // on the rail menu instead of doing nothing, which is
                    // worse. Reaching the end while genuinely still-
                    // loading is now made rare instead (see the earlier
                    // trigger above), rather than trying to make that
                    // moment itself safe.
                    isLastRow = index == state.rows.lastIndex,
                    upEscapeTarget = if (index == 0) topBarFocusRequester else null,
                    // Explicitly sized from this container's OWN measured
                    // height (see above) rather than left at the shared
                    // default, which would fall back to the device-wide
                    // screenHeightDp instead of what THIS layout measured.
                    itemWidth = itemWidth
                    // itemSpacing/headerFontSize/headerFontWeight/headerHeight
                    // are still left at their shared defaults so this matches
                    // every other carousel in the app exactly.
                )
            }
            // Vertical pagination trigger, same pattern as every other
            // all-categories carousel list in this screen.
            if (hasMoreCategories) {
                item {
                    LaunchedEffect(state.rows.size, state.selectedCategory, state.selectedGenre) {
                        onLoadMoreCategories()
                    }
                    // FIX: this sentinel had no focusable modifier, so
                    // even with Down no longer blocked above, D-pad
                    // navigation had nowhere to land on it - nothing
                    // focusable meant nothing ever brought it into view,
                    // so pagination could never trigger from the remote.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .focusable()
                            .focusProperties { down = FocusRequester.Cancel },
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = BbAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/**
 * The search icon/field + genre/category dropdowns overlaid on the hero
 * card - shared by LargeHomeHeroLayout (all categories) and
 * CategoryGridWithHeroLayout (a specific category selected) so the two can
 * never visually drift apart the way they did before this was extracted -
 * that drift (a separate, non-overlaid top bar for the grid case) was
 * exactly what made its hero look/behave differently from the carousel
 * view's.
 */
@Composable
private fun HomeOverlayFilterRow(
    state: HomeUiState,
    searchBarVisible: Boolean,
    onToggleSearchBar: () -> Unit,
    searchFieldFocusRequester: FocusRequester,
    searchButtonFocusRequester: FocusRequester,
    topBarFocusRequester: FocusRequester,
    route: String,
    onGenreSelected: (PortalCategory) -> Unit,
    onCategorySelected: (PortalCategory) -> Unit,
    onSearchChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIconButton(
            onClick = onToggleSearchBar,
            modifier = Modifier
                .focusRequester(searchButtonFocusRequester)
                .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
        )
        if (searchBarVisible) {
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier
                    .width(200.dp)
                    .height(44.dp)
                    .focusRequester(searchFieldFocusRequester)
                    .onFocusChanged {
                        // TEMPORARY DIAGNOSTIC (remove once the remaining
                        // rail-focus investigation is resolved): directly
                        // observes the search field's own focus lifecycle -
                        // specifically whether it actually loses focus
                        // (and when, relative to "Rail gained focus")
                        // rather than assuming from indirect evidence.
                        android.util.Log.d("DpadFocus", "Search field focus changed: hasFocus=${it.hasFocus}")
                    }
                    .safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) },
                placeholder = { Text("Search...", color = BbTextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextMuted, modifier = Modifier.size(16.dp)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
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
        Spacer(Modifier.width(10.dp))
        HomeGenreDropdown(
            genres = state.genres,
            selectedGenre = state.selectedGenre,
            onGenreSelected = onGenreSelected,
            compact = true,
            modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
        )
        Spacer(Modifier.width(10.dp))
        HomeCategoryDropdown(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = onCategorySelected,
            compact = true,
            focusRequester = topBarFocusRequester,
            modifier = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
        )
    }
}

@Composable
private fun HomeGenreDropdown(
    genres: List<PortalCategory>,
    selectedGenre: PortalCategory?,
    onGenreSelected: (PortalCategory) -> Unit,
    modifier: Modifier = Modifier,
    // NEW: reduced height/width/font used by the large-form-factor Home
    // layout, where this dropdown is overlaid on the hero card instead of
    // sitting in its own full-size row.
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    // FIX: was a plain .clickable{} + separate later .focusable() +
    // .onFocusChanged{} - see the matching comment on SearchIconButton
    // above for why that pattern could silently leave isFocused (and
    // therefore this border) never updating on D-pad focus.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // FIX ("Search -> Change Category resets focus to rail" - same root
    // cause found in HomeCategoryDropdown, fixed here too): closing the
    // menu disposes whichever DropdownMenuItem currently has focus, and
    // nothing returned focus to this dropdown's own trigger afterward, so
    // Compose's fallback was catching it on the rail instead. Unlike
    // HomeCategoryDropdown, this composable had no externally-supplied
    // FocusRequester for its trigger at all - added one locally purely for
    // this same fix.
    val triggerFocusRequester = remember { FocusRequester() }
    val width = if (compact) 150.dp else 180.dp
    // REVERTED: back to padding-derived height instead of a forced explicit
    // height shared with the search field/button - the forced-equal-height
    // version made the search field look cramped. Height here is just
    // shorter than before via a smaller vertical padding value.
    val verticalPadding = if (compact) 4.dp else 12.dp
    val fontSize = if (compact) 12.sp else 14.sp
    Box(modifier = Modifier.width(width)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(
                    if (isFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .then(modifier)
                .focusRequester(triggerFocusRequester)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = true }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedGenre?.title ?: "All Genres",
                color = BbTextPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle genres",
                tint = BbTextSecondary,
                modifier = if (compact) Modifier.size(16.dp) else Modifier
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                // FIX: dismissing without selecting anything (Back, or
                // clicking outside) disposes the focused menu item the same
                // way selecting one does - same fix as onClick above.
                //
                // FIX: see HomeCategoryDropdown's onDismissRequest doc
                // comment - deferring past DropdownMenu's own Popup
                // teardown, same mechanism applies here.
                //
                // FIX: uses FocusRegistry's process-wide scope, not this
                // composable's own rememberCoroutineScope() - see
                // FocusRegistry.deferredRequestFocus's doc comment for why
                // the latter can get silently cancelled by the very state
                // change this is reacting to.
                FocusRegistry.deferredRequestFocus(triggerFocusRequester)
            },
            modifier = Modifier.background(BbSurface)
        ) {
            genres.forEach { genre ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = genre.title,
                            color = if (genre.id == selectedGenre?.id) BbAccent else BbTextPrimary,
                            fontWeight = if (genre.id == selectedGenre?.id) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        // FIX: see HomeCategoryDropdown's onClick doc
                        // comment - arming this synchronously, before the
                        // selection call, closes the same race window
                        // (rail catching focus before focusTarget has even
                        // transitioned to None yet).
                        FocusRegistry.setContentTransitioning(true)
                        onGenreSelected(genre)
                        expanded = false
                        // FIX: see HomeCategoryDropdown's onClick doc
                        // comment (the synchronous-call-plus-deferred-call
                        // explanation) - this synchronous call wins the
                        // race against this menu item's own immediate
                        // disposal; the deferred call below then wins the
                        // separate race against DropdownMenu's own Popup
                        // teardown a little later. Neither alone covers
                        // both disposal events.
                        runCatching { triggerFocusRequester.requestFocus() }
                        // FIX: uses FocusRegistry's process-wide scope now -
                        // see FocusRegistry.deferredRequestFocus's doc
                        // comment. The synchronous call above still
                        // protects the rail regardless of whether this
                        // succeeds.
                        FocusRegistry.deferredRequestFocus(triggerFocusRequester)
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeCategoryDropdown(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    // NEW: see HomeGenreDropdown's compact param doc above.
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    // FIX: see the matching comment on SearchIconButton above.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val width = if (compact) 150.dp else 180.dp
    // REVERTED: see the matching comment on HomeGenreDropdown above.
    val verticalPadding = if (compact) 4.dp else 12.dp
    val fontSize = if (compact) 12.sp else 14.sp
    Box(modifier = Modifier.width(width)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(
                    if (isFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(modifier)
                .clickable(interactionSource = interactionSource, indication = null) {
                    expanded = true
                    android.util.Log.d("DpadFocus", "HomeCategoryDropdown: menu opened (expanded=true), currently selected=${selectedCategory?.title}")
                }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
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
                tint = BbTextSecondary,
                modifier = if (compact) Modifier.size(16.dp) else Modifier
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                // FIX: dismissing without selecting anything (Back, or
                // clicking outside) disposes the focused menu item the same
                // way selecting one does - same fix as onClick below.
                //
                // FIX (confirmed via device logcat: requestFocus()
                // reported success, but the rail caught focus ~43ms
                // later): DropdownMenu is implemented via a Popup - a
                // separate window/surface, not just a regular composable
                // in this layout tree. Calling requestFocus() synchronously
                // here runs it DURING the popup's own dismissal sequence -
                // it can report success against the composition as it
                // exists at that instant, but the popup's own window
                // teardown (a later, separate phase - closing the popup
                // window and returning focus to the main window) can still
                // override it afterward, landing wherever Android considers
                // the default target instead. Deferring past a short delay
                // - same pattern already proven for the search-clearing
                // Back interceptor elsewhere in this file - lets the popup
                // genuinely finish closing first, so this claim is the
                // last thing to touch focus, not something the teardown
                // steps on right after.
                //
                // FIX: uses FocusRegistry's process-wide scope now, not
                // this composable's own rememberCoroutineScope() - see
                // FocusRegistry.deferredRequestFocus's doc comment for why
                // the latter could get silently cancelled by the very
                // category change this is reacting to.
                if (focusRequester != null) {
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
                        // FIX (confirmed via device logcat: "Rail gained
                        // focus" landed 66ms BEFORE focusTarget even
                        // transitioned to None): the previous fix relied on
                        // HomeScreen's own LaunchedEffect(focusTarget) to
                        // arm FocusRegistry.setContentTransitioning(true),
                        // but that only runs AFTER focusTarget has already
                        // recomputed to None - a real gap during which the
                        // old content/dropdown item can already be
                        // disposed (as this very click's state change
                        // propagates) with nothing yet suppressing the
                        // rail. Arming it synchronously, right here, closes
                        // that race window entirely regardless of how fast
                        // the downstream recomposition happens - selecting
                        // a category always triggers a reload, so this is
                        // always safe to arm immediately.
                        FocusRegistry.setContentTransitioning(true)
                        onCategorySelected(cat)
                        expanded = false
                        // FIX ("Search -> Change Category resets focus to
                        // rail"): closing the menu disposes whichever
                        // DropdownMenuItem currently has focus, and nothing
                        // explicitly claimed the trigger afterward - so
                        // Compose's own fallback was catching it on the
                        // rail instead, the nearest always-composed
                        // focusable element. Explicitly returning focus to
                        // this dropdown's own trigger closes that gap
                        // entirely.
                        //
                        // FIX (confirmed via device logcat: even with
                        // setContentTransitioning(true) armed synchronously
                        // above, and the None-transition claim in
                        // HomeScreen's own LaunchedEffect, "Rail gained
                        // focus" still fired - 194ms BEFORE focusTarget
                        // even recomputed to None): every claim tried so
                        // far has been reactive - it runs after some state
                        // change has already propagated through
                        // recomposition, but the actual disposal of this
                        // menu item (as expanded=false takes effect) can
                        // still beat all of them to the punch, since
                        // Compose's own fallback search runs synchronously
                        // against whatever's composed at that instant. This
                        // synchronous call, immediately after expanded =
                        // false in the SAME callback that causes the
                        // disposal, is the only claim that can actually win
                        // that specific race - nothing reactive downstream
                        // can. It's expected to itself then be overridden
                        // ~43ms later by DropdownMenu's own Popup teardown
                        // (confirmed previously) - which is exactly what
                        // the deferred claim below is for. The two together
                        // cover both disposal events; neither alone does.
                        if (focusRequester != null) {
                            runCatching { focusRequester.requestFocus() }
                        }
                        //
                        // FIX (confirmed via device logcat: requestFocus()
                        // reported success here, but the rail still caught
                        // focus ~43ms later): see onDismissRequest's
                        // matching doc comment above for the full
                        // mechanism - DropdownMenu's own Popup teardown can
                        // override a focus claim made synchronously inside
                        // this callback. Deferring past a short delay lets
                        // the popup genuinely finish closing first.
                        //
                        // FIX (confirmed via device logcat: this deferred
                        // block never even ran - no log, no focus-return -
                        // for a category change that swaps the whole
                        // layout): the 100ms delay below used to run on
                        // this composable's own rememberCoroutineScope(),
                        // which the SAME category change's own
                        // recomposition could dispose before the delay
                        // completed, silently cancelling this coroutine
                        // before it ever ran. Now uses FocusRegistry's
                        // process-wide scope instead - see
                        // FocusRegistry.deferredRequestFocus's doc comment -
                        // which survives regardless of what this selection
                        // does to this composable's own lifecycle. The
                        // synchronous setContentTransitioning(true) call
                        // above still protects the rail regardless of
                        // whether this succeeds.
                        if (focusRequester != null) {
                            FocusRegistry.deferredRequestFocus(focusRequester)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(isConnecting: Boolean, route: String, focusTarget: FocusEntry) {
    // D-pad focus: for routes with no corresponding rail item (Adult VOD,
    // opened from AdultHubScreen's card rather than the rail - see
    // FocusRegistry.isRailRegistered), claim focus onto this loading
    // placeholder immediately on mount - see LiveTvScreen's identical fix
    // for the full rationale (the rail visibly opening and holding focus
    // for a route it was never going to end up controlling anyway).
    //
    // FIX: was a LaunchedEffect(Unit) retry loop (repeat(20) { delay(50) })
    // that only ever ran once, at initial mount - a genuine miss left over
    // from before this whole investigation, since Live TV's identical case
    // was already modernized to react to focusTarget instead (see its own
    // doc comment) but this shared LoadingOverlay never got the same
    // update. Reacting to focusTarget means this correctly re-arms every
    // time this screen genuinely re-enters a loading state (a reconnect,
    // not just the first ever load), matching claimFocusEntry's own
    // single-reactive-attempt philosophy rather than a fixed-count poll.
    val loadingFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusTarget) {
        if (!FocusRegistry.isRailRegistered(route) && focusTarget == FocusEntry.None) {
            runCatching { loadingFocusRequester.requestFocus() }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(loadingFocusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        CircularProgressIndicator(color = BbAccent)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isConnecting) "Connecting to portal…" else "Loading…",
            color = BbTextMuted, fontSize = 14.sp
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConnectionErrorOverlay(message: String, onRetry: () -> Unit, onOpenPortals: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text = "Connection problem", color = BbTextSecondary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = BbTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRetry) { Text("Retry") }
            OutlinedButton(onClick = onOpenPortals) { Text("Open Portals") }
        }
    }
}