package com.itv.blockbuster.ui.livetv

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.components.ChannelCarouselRow
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.components.DefaultChannelTileLogoHeight
import com.itv.blockbuster.ui.components.DefaultChannelTileNameFontSize
import com.itv.blockbuster.ui.components.DefaultChannelTileNowPlayingFontSize
import com.itv.blockbuster.ui.components.DefaultChannelTileNumberFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileLogoHeight
import com.itv.blockbuster.ui.components.PortraitChannelTileNameFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileNowPlayingFontSize
import com.itv.blockbuster.ui.components.PortraitChannelTileNumberFontSize
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.components.HeroContent
import com.itv.blockbuster.ui.components.SearchIconButton
import com.itv.blockbuster.ui.components.rememberCarouselBlockHeight
import com.itv.blockbuster.ui.components.rememberTopPinningBringIntoViewSpec
import com.itv.blockbuster.ui.components.rememberChannelCarouselItemWidth
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.RailCollapsedWidth
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.FocusEntry
import com.itv.blockbuster.util.claimFocusEntry
import com.itv.blockbuster.util.safeFocusEscape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class SortMode { DEFAULT, A_Z, Z_A, NUMERIC }

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LiveTvScreen(
    onPlayChannel: (String, String) -> Unit, // (streamUrl, channelId)
    onOpenCatchup: (String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
    // D-pad focus: this screen's route, used to hand focus off from the rail
    // to its first item once content has loaded (see AppShell/FocusRegistry).
    route: String = Routes.LIVE_TV
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // FIX: Use rememberSaveable so state survives navigation away and back
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DEFAULT) }

    // FIX (search debounce): searchQuery itself updates immediately, so the
    // text field always shows exactly what's typed with no lag - but
    // filtering (searchFiltered below) and focus-claiming (focusClaimId)
    // react to this debounced copy instead, so rapid typing doesn't
    // refilter the list or yank focus toward a first result on every
    // single keystroke. 2s matches this app's other search debounces (see
    // HomeViewModel/VodBrowserViewModel's own SEARCH_DEBOUNCE_MS) in spirit,
    // though those debounce a network call where this debounces local
    // filtering and focus movement instead.
    var debouncedSearchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(2000)
        debouncedSearchQuery = searchQuery
    }

    // NEW: search field starts hidden, revealed only by tapping the search
    // icon button - matches Home's toggle behavior exactly (same shared
    // SearchIconButton component). Also force-shown if there's already an
    // active search query, so the field showing the active filter is never
    // hidden out from under it.
    var showSearchBar by remember { mutableStateOf(false) }
    val isSearchActive = searchQuery.isNotBlank()
    val searchBarVisible = showSearchBar || isSearchActive
    val searchFieldFocusRequester = remember { FocusRequester() }
    // Stable requester for the search ICON BUTTON itself, so Back (below)
    // has something explicit to send focus to once it clears the search and
    // collapses the field again - matches Home's searchButtonFocusRequester.
    val searchButtonFocusRequester = remember { FocusRequester() }
    val backPressScope = rememberCoroutineScope()
    LaunchedEffect(showSearchBar) {
        if (showSearchBar) {
            delay(50)
            runCatching { searchFieldFocusRequester.requestFocus() }
        }
    }

    // D-pad focus / Back: while a search is active, Back should clear the
    // search filter (and hide the field again) instead of AppShell's normal
    // "refocus the rail" behavior - matches Home's exact same interceptor
    // pattern. AppShell's own BackHandler always wins priority, so this goes
    // through the shared interceptor hook rather than a second BackHandler
    // here, which would simply never fire - see
    // FocusRegistry.consumeBackPressInterceptor.
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

    val configuration = LocalConfiguration.current
    val dropdownWidth = (configuration.screenWidthDp.dp * 0.35f)
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // D-pad focus: explicit Up target for the first channel row/grid row,
    // pointing at this screen's own top bar (category filter dropdown)
    // rather than relying on Compose's default spatial search - see
    // HomeScreen/VodBrowserScreen for the same fix and rationale.
    val topBarFocusRequester = remember { FocusRequester() }

    // HOISTED (was declared much further down, inside the content Column,
    // after the loading/error branches): needed up here now so focusTarget
    // below can be computed from the actual filtered/sorted list, the same
    // way HomeScreen computes it from state.rows. See the original
    // declarations' own doc comments (memoized against ANRs on large
    // channel lists) - unchanged, just relocated. Their use sites further
    // down are unaffected since these keep the same names.
    val isAllCategory = state.selectedCategory?.id == "*" ||
            state.selectedCategory?.id == "0" ||
            state.selectedCategory?.id == "all" ||
            state.selectedCategory == null
    val categoryFiltered = remember(state.allChannels, isAllCategory, state.selectedCategory) {
        if (isAllCategory) {
            state.allChannels
        } else {
            state.allChannels.filter { it.genreId == state.selectedCategory?.id }
        }
    }
    val searchFiltered = remember(categoryFiltered, debouncedSearchQuery) {
        if (debouncedSearchQuery.isBlank()) {
            categoryFiltered
        } else {
            val query = debouncedSearchQuery.trim().lowercase()
            categoryFiltered.filter { channel ->
                channel.name.lowercase().contains(query) ||
                        channel.number.lowercase().contains(query) ||
                        channel.nowPlaying.lowercase().contains(query)
            }
        }
    }
    val sortedChannels = remember(searchFiltered, sortMode) {
        when (sortMode) {
            SortMode.DEFAULT -> searchFiltered
            SortMode.A_Z -> searchFiltered.sortedBy { it.name.lowercase() }
            SortMode.Z_A -> searchFiltered.sortedByDescending { it.name.lowercase() }
            SortMode.NUMERIC -> searchFiltered.sortedWith(
                compareBy(
                    // Try to parse as Double for "10.1" cases.
                    // Fallback to Double.MAX_VALUE to push non-numeric strings (e.g., "HD 1") to the end.
                    { it.number.toDoubleOrNull() ?: Double.MAX_VALUE },
                    { it.number } // Tie-breaker
                )
            )
        }
    }

    // HOISTED (was declared separately inside each of the two layout
    // branches below as liveTvCarouselListState/gridState): needed up here
    // now so the None-transition scroll reset below can reach whichever
    // one is actually in use, regardless of which layout branch is
    // currently rendering. Both are always created (cheap - just state
    // holders), but only one is ever attached to an actual composed
    // LazyColumn/LazyVerticalGrid at a time depending on isAllCategory.
    val liveTvCarouselListState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // D-pad focus: the single decision behind where focus should land as
    // this screen's content settles - see FocusEntry's own doc comment.
    // Mirrors HomeScreen's identical computation exactly: None while
    // genuinely loading, FirstItem once there's something to land on,
    // Fallback (the filter row) if the current filter/search came back
    // empty. Replaces the old notifyContentReady()/registerFirstItem()-
    // driven handoff and the separate awaitingFilterRefocus/
    // isInitialFilterState bookkeeping that used to re-trigger it on a
    // category change - both collapse into this one value now, since a
    // category change is just another route through the same
    // isLoading -> rows-empty-or-not transition FirstItem/Fallback already
    // cover.
    val focusTarget = when {
        state.isLoading || state.isConnecting -> FocusEntry.None
        sortedChannels.isNotEmpty() -> FocusEntry.FirstItem
        else -> FocusEntry.Fallback
    }
    // FIX: see HomeScreen's identical focusClaimId doc comment - a fresh
    // identity per genuine transition (even when the new value repeats,
    // e.g. Fallback -> None -> Fallback again for two different empty
    // searches back to back) is what lets claimFocusEntry below tell those
    // apart, rather than treating the second as already consumed.
    // FIX (confirmed root cause of "changing category/searching doesn't
    // move focus to the first item" - focus just stays on the filter row
    // instead): FocusEntry.FirstItem is a singleton object (see its own
    // doc comment), and category/search/sort filtering here is entirely
    // client-side and instant (no network round-trip, no intermediate
    // isLoading window) - so switching from one category with results to
    // another with results, or typing a search that still matches
    // something, means focusTarget can stay AS FirstItem continuously,
    // with no recomposition in between where it was ever anything else.
    // remember(focusTarget) alone never sees a changed key in that case,
    // so this never minted a new identity, claimFocusEntry's own
    // LaunchedEffect(claimId) never re-fired for the new content, and the
    // OLD claimId was already marked consumed by the PREVIOUS category's
    // claim - so the new one never got a turn. Home never hit this: a real
    // network reload always passes through an intermediate None first,
    // which IS a different key, so the following FirstItem always mints a
    // fresh identity there. Keying on the actual filter inputs too closes
    // the gap for an instant, client-side reload like this one.
    val focusClaimId = remember(focusTarget, state.selectedCategory, debouncedSearchQuery, sortMode) { Any() }

    LaunchedEffect(focusTarget, state.selectedCategory, debouncedSearchQuery, sortMode) {
        Log.d("DpadFocus", "LiveTvScreen(route=$route): focusTarget=$focusTarget channelCount=${sortedChannels.size}")
        // FIX (confirmed: rail sometimes staying visually collapsed even
        // when the user genuinely navigates Left to it): this effect used
        // to be keyed on focusTarget alone, so it - like focusClaimId
        // above - never re-fired for an instant, client-side category/
        // search/sort change where focusTarget's VALUE never actually
        // changes (see focusClaimId's own doc comment for the full
        // mechanism). CategoryDropdown's onClick arms
        // setContentTransitioning(true) synchronously the moment a
        // category is selected; without this effect reliably re-running
        // afterward, the line below that's supposed to set it back to
        // false again never got the chance to - leaving the rail's own
        // visual-expand suppression stuck on indefinitely, well past the
        // instant reload it was meant to cover, until some LATER change
        // happened to give focusTarget a genuinely different value.
        // Expanding the key set to match focusClaimId's closes the same
        // gap here.
        // FIX: see HomeScreen's identical setContentTransitioning doc
        // comment - suppresses the rail's own visual expand-on-focus
        // reaction for the duration of a reload, armed synchronously
        // wherever a reload can be triggered (CategoryDropdown's onClick
        // below) rather than only reactively here, which - as the whole
        // Home investigation found - can arm too late to matter on its
        // own. This reactive arm here still matters for reloads not
        // triggered through the dropdown (e.g. the initial connect).
        FocusRegistry.setContentTransitioning(focusTarget == FocusEntry.None)
        if (focusTarget == FocusEntry.FirstItem || focusTarget == FocusEntry.Fallback) {
            keyboardController?.hide()
        }
        if (focusTarget == FocusEntry.None) {
            // FIX: see HomeScreen's identical claim - gives focus one
            // guaranteed, always-composed place to land for the entire
            // loading window (the category dropdown trigger), regardless
            // of what disposed whatever was previously focused or why.
            runCatching { topBarFocusRequester.requestFocus() }
            // FIX: see HomeScreen's identical scroll-reset doc comment -
            // a stale scroll position from a longer PREVIOUS filter's list
            // can leave the viewport misaligned with a shorter NEW one,
            // breaking both the pagination trigger and the FirstItem claim
            // below (which needs its target to actually be composed).
            // Harmless no-op via runCatching on whichever of the two isn't
            // currently backing a composed list.
            runCatching { liveTvCarouselListState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
        if (focusTarget == FocusEntry.Fallback) {
            runCatching { topBarFocusRequester.requestFocus() }
        }
    }

    // D-pad focus: on resume (e.g. Back popping the player pushed from this
    // screen), restore focus onto the exact channel that was clicked -
    // no-ops unless RailShell's route-change handling armed this route for
    // restoration (see FocusRegistry.armRestoreFocus/restoreClickedItemFocus
    // and AppShell.kt), so it doesn't interfere with the ordinary rail-then-
    // first-item handoff on other resumes (rail clicks, tab switches).
    //
    // NOT YET migrated to FocusEntry.RestoreItem/claimFocusEntry - this
    // reaches across to the previously-active screen (wherever the player
    // was pushed from, not necessarily still this route by the time it
    // resumes) via FocusRegistry's own restoreClickedItemFocus, rather than
    // this screen deciding its own focusTarget the way the rest of this
    // migration does. Left as its own mechanism for this pass; folding it
    // into FocusEntry.RestoreItem is TV Guide's phase, where the same
    // pattern is needed for the last-played channel.
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

    // D-pad focus: for routes with no corresponding rail item (Adult Live,
    // opened from AdultHubScreen's card rather than the rail - see
    // FocusRegistry.isRailRegistered), the rail can never legitimately hold
    // focus while this screen loads, since requestRail(route) has nothing
    // to succeed against. Without claiming focus onto something of our own,
    // whatever was focused before navigating here (the Adult Live card)
    // simply falls through to Android's own default-focus-search once it's
    // disposed - which, since the rail is still composed and adjacent,
    // visibly lands there and stays until real content is ready. Claiming
    // this loading placeholder's focus wins that race, so the rail never
    // visibly opens for a route it was never going to end up controlling
    // anyway.
    //
    // FIX: was a LaunchedEffect(Unit) retry loop (repeat(20) { delay(50) })
    // that only ever ran once, at initial mount - reacting to focusTarget
    // instead means this correctly re-arms every time the screen genuinely
    // re-enters a loading state (a reconnect, not just the first ever
    // load), matching claimFocusEntry's own single-reactive-attempt
    // philosophy rather than a fixed-count poll.
    val loadingFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusTarget) {
        if (!FocusRegistry.isRailRegistered(route) && focusTarget == FocusEntry.None) {
            runCatching { loadingFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (state.isLoading || state.isConnecting) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(loadingFocusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = BbAccent)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (state.isConnecting) "Connecting..." else "Loading Channels...",
                    color = BbTextMuted
                )
            }
        } else if (state.connectionError != null) {
            Text(
                text = "Error: ${state.connectionError}",
                color = BbTextSecondary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // NEW: only rendered for portrait now - TV/landscape uses
                // LiveTvOverlayFilterRow instead, overlaid on the hero
                // banner inside each hero layout below (matching Home's
                // HomeOverlayFilterRow pattern) rather than pushing content
                // down as a separate row.
                if (isPortrait) {
                    LiveTvTopBar(
                        categories = state.categories,
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        sortMode = sortMode,
                        onSortModeToggle = {
                            sortMode = when (sortMode) {
                                SortMode.DEFAULT -> SortMode.A_Z
                                SortMode.A_Z -> SortMode.Z_A
                                SortMode.Z_A -> SortMode.NUMERIC
                                SortMode.NUMERIC -> SortMode.DEFAULT
                            }
                        },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        dropdownWidth = dropdownWidth,
                        isPortrait = isPortrait,
                        searchBarVisible = searchBarVisible,
                        onToggleSearchBar = { showSearchBar = !showSearchBar },
                        searchFieldFocusRequester = searchFieldFocusRequester,
                        searchButtonFocusRequester = searchButtonFocusRequester,
                        categoryFocusRequester = null,
                        route = route
                    )
                }

                // isAllCategory/categoryFiltered/searchFiltered/sortedChannels
                // are now computed near the top of this function (hoisted
                // so focusTarget can be derived from them) - see there.

                // FIX (root cause of "focus reaches the rail switching
                // category, and search-with-no-results hides the category
                // filter entirely" - same structural bug found and fixed on
                // Home, see HomeScreen's LoadingOverlay gate doc comment for
                // the full investigation): HeroBanner and
                // LiveTvOverlayFilterRow used to be declared THREE separate
                // times - once inside the carousel branch, once inside the
                // grid branch, and not at all when sortedChannels was empty.
                // Switching between "All Categories" (carousel) and a
                // specific category (grid), or between having results and
                // not, meant Compose disposed and recreated the ENTIRE
                // branch each time - including topBarFocusRequester's own
                // target, genuinely removing it from the composition tree
                // for a stretch, not merely racing to refocus it in time.
                // With nothing real to reclaim and no rail-suppression
                // reason to look elsewhere, Compose's own fallback search
                // found the rail. Hoisting both to a single call site,
                // sibling to (not inside) the empty/carousel/grid branching
                // below, means they - and topBarFocusRequester's actual
                // target - now stay mounted across all of it.
                //
                // Portrait is unaffected here: LiveTvTopBar above already
                // renders unconditionally (see its own site, before this
                // whole block), so it never had this problem.
                if (!isPortrait) {
                    // FIX: was two separate states (focusedHeroChannel for
                    // the carousel branch, focusedHeroChannelGrid for the
                    // grid branch) - one is enough now that both branches
                    // share this single hero site instead of each owning
                    // their own.
                    var focusedHeroChannel by remember { mutableStateOf<PortalChannel?>(null) }
                    // Reset whenever the filtered/sorted list changes
                    // (category/search/sort) so a stale focused channel
                    // from a previous filter never lingers in the hero.
                    LaunchedEffect(state.selectedCategory, debouncedSearchQuery, sortMode) {
                        focusedHeroChannel = null
                    }
                    val heroChannel = focusedHeroChannel ?: sortedChannels.firstOrNull()

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val heroHeight = maxHeight * 0.5f
                        // Same shared sizing Home's hero-overlay layout
                        // uses, so the first carousel row overlapping
                        // the hero here looks consistent with Home's own
                        // overlap amount, not an arbitrarily different one.
                        val block = rememberCarouselBlockHeight(maxHeight)

                        HeroBanner(
                            hero = heroChannel?.let { HeroContent.LiveChannel(it) },
                            height = heroHeight,
                            modifier = Modifier.align(Alignment.TopStart)
                        )

                        // NEW: filters overlaid on the hero instead of
                        // LiveTvTopBar's separate pushed-down row - see
                        // LiveTvOverlayFilterRow's doc comment.
                        LiveTvOverlayFilterRow(
                            categories = state.categories,
                            selectedCategory = state.selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) },
                            sortMode = sortMode,
                            onSortModeToggle = {
                                sortMode = when (sortMode) {
                                    SortMode.DEFAULT -> SortMode.A_Z
                                    SortMode.A_Z -> SortMode.Z_A
                                    SortMode.Z_A -> SortMode.NUMERIC
                                    SortMode.NUMERIC -> SortMode.DEFAULT
                                }
                            },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            searchBarVisible = searchBarVisible,
                            onToggleSearchBar = { showSearchBar = !showSearchBar },
                            searchFieldFocusRequester = searchFieldFocusRequester,
                            searchButtonFocusRequester = searchButtonFocusRequester,
                            categoryFocusRequester = topBarFocusRequester,
                            focusTarget = focusTarget,
                            route = route,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 24.dp)
                        )

                        if (sortedChannels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = block),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No channels match your search" else "No channels in this category",
                                    color = BbTextMuted
                                )
                            }
                        } else if (isAllCategory) {
                            // Carousels: "All Categories" only.
                            //
                            // FIX: moved out of the LazyColumn content builder
                            // and memoized - groupBy/filter on a large channel
                            // list is real work, and doing it unmemoized on
                            // every recomposition (including ones from
                            // unrelated state like focusedHeroChannel
                            // changing on every focus move) contributed to
                            // the same ANR risk fixed above for
                            // categoryFiltered/searchFiltered/sortedChannels.
                            val grouped = remember(sortedChannels) { sortedChannels.groupBy { it.genreId } }
                            // FIX: precompute which categories will actually render a row
                            // (non-empty, not the "All" placeholder), so both the first AND
                            // last rendered row can be identified up front - needed for
                            // isFirstRow (initial D-pad focus target) and isLastRow (blocks
                            // D-pad Down from escaping past the bottom into the rail).
                            val renderableCategories = remember(grouped, state.categories) {
                                state.categories.filter { cat ->
                                    cat.id != "*" && cat.id != "0" && cat.id != "all" && !grouped[cat.id].isNullOrEmpty()
                                }
                            }

                            // liveTvCarouselListState is now hoisted to the
                            // top of this function (needed for the
                            // None-transition scroll reset) - see there.

                            // FIX: D-pad focus - wraps this LazyColumn's own
                            // focus-triggered scrolling in the same top-pinning
                            // BringIntoViewSpec Home's carousel lists use, so
                            // moving focus into the next/previous category row
                            // snaps it fully flush with the top of the viewport
                            // instead of Compose's default "scroll the minimum
                            // needed" behavior - which was leaving the tail end
                            // of the previous row's poster/tile peeking on
                            // screen above the newly-focused row. See
                            // rememberTopPinningBringIntoViewSpec's doc comment.
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTopPinningBringIntoViewSpec()) {
                                LazyColumn(
                                    state = liveTvCarouselListState,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxSize()
                                        .padding(top = block)
                                ) {
                                    renderableCategories.forEachIndexed { index, cat ->
                                        val channelsInCat = grouped[cat.id] ?: return@forEachIndexed
                                        item(key = cat.id) {
                                            ChannelCarouselRow(
                                                title = cat.title,
                                                channels = channelsInCat,
                                                favoriteIds = favoriteIds,
                                                onChannelClick = { channel ->
                                                    viewModel.getStreamUrl(channel.cmd) { url ->
                                                        onPlayChannel(url, channel.id)
                                                    }
                                                },
                                                onChannelLongClick = { channel ->
                                                    if (channel.hasArchive) onOpenCatchup(channel.id)
                                                },
                                                onFavoriteIconClick = { channel ->
                                                    viewModel.toggleFavorite(channel)
                                                },
                                                onChannelFocused = { channel -> focusedHeroChannel = channel },
                                                route = route,
                                                isFirstRow = index == liveTvCarouselListState.firstVisibleItemIndex,
                                                focusTarget = focusTarget,
                                                focusClaimId = focusClaimId,
                                                isLastRow = index == renderableCategories.lastIndex,
                                                upEscapeTarget = if (index == 0) topBarFocusRequester else null
                                            )
                                        }
                                    }
                                    item { Spacer(Modifier.height(32.dp)) }
                                }
                            }
                        } else {
                            // Tile grid: a specific category selected.
                            // D-pad focus: computed FIXED column count (matching
                            // GridCells.Adaptive's visual density) so boundary items
                            // (leftmost column -> rail on Left; bottom row blocks
                            // Down from escaping to the rail) can be identified
                            // precisely - see PosterGrid for the same pattern. The
                            // top row's Up is pointed at the top bar's category
                            // filter. Also registers its first VISIBLE item as
                            // gridFirstItemRequester, which both claimFocusEntry
                            // (auto-claims it once content loads) and
                            // FocusRegistry.firstItemTarget(route) (the filter
                            // row's "press Down" escape target) rely on.
                            //
                            // FIX (issue: grid tiles didn't visually match the
                            // carousel's - two independently-tuned sizing
                            // formulas): itemMinWidth now reuses the exact same
                            // rememberChannelCarouselItemWidth formula the
                            // carousel above uses (with the same
                            // collapsedMenuWidth-equivalent input), instead of a
                            // separate, arbitrary 150.dp - so a channel tile
                            // looks the same size whether it's reached via the
                            // "All Categories" carousel or a specific category's
                            // grid.
                            val railReservedWidth = RailCollapsedWidth
                            val itemMinWidth = rememberChannelCarouselItemWidth(railReservedWidth)
                            val horizontalSpacing = 12.dp
                            // gridState is now hoisted to the top of this
                            // function (needed for the None-transition scroll
                            // reset) - see there.
                            val gridFirstItemRequester = remember { FocusRequester() }
                            FocusRegistry.registerFirstItem(route, gridFirstItemRequester)
                            // FIX: unregister on dispose - see
                            // FocusRegistry.unregisterFirstItem's doc comment.
                            // Without this, a stale/detached requester could
                            // still be handed out as a `down = ...` focus target
                            // after this grid is torn down (category switch back
                            // to the carousel view, or a layout churn), crashing
                            // uncatchably on the next real D-pad key press.
                            DisposableEffect(route, gridFirstItemRequester) {
                                onDispose { FocusRegistry.unregisterFirstItem(route, gridFirstItemRequester) }
                            }
                            // FIX (issue: carousel showing 7 items/row, grid
                            // showing only 6, even with itemMinWidth reusing
                            // the carousel's exact width formula): this used
                            // to subtract horizontalContentPadding (48dp,
                            // this grid's own LazyVerticalGrid contentPadding)
                            // from the available width before dividing by
                            // itemMinWidth - but rememberChannelCarouselItemWidth's
                            // own formula ((screenWidth - collapsedMenuWidth -
                            // spacing*8) / 7.2) doesn't assume any such
                            // separate padding subtraction; its -spacing*8
                            // term is baked into the /7.2 divisor itself, not
                            // an independent content-padding removal. Two
                            // different "how much space is actually
                            // available" assumptions meant the same
                            // itemMinWidth value still produced a different
                            // column count. Computing columns from the same
                            // (screenWidth - railReservedWidth) basis the
                            // carousel's own formula is built on - rather
                            // than this grid's separately-padded figure -
                            // reproduces its ~7.2 (7 fully visible, one
                            // peeking) result here too.
                            val availableWidth = (configuration.screenWidthDp.dp - railReservedWidth)
                                .coerceAtLeast(itemMinWidth)
                            val columns = ((availableWidth + horizontalSpacing) / (itemMinWidth + horizontalSpacing))
                                .toInt()
                                .coerceAtLeast(1)
                            val lastRowStartIndex = ((sortedChannels.size - 1) / columns) * columns
                            val visibleIndex = gridState.firstVisibleItemIndex.coerceIn(0, sortedChannels.size - 1)

                            // NEW: grid's own viewport is explicitly
                            // BOUNDED to start at `block` (external
                            // padding+height) rather than using the
                            // grid's internal contentPadding for that
                            // offset - same fix as Home's PosterGrid:
                            // internal content padding only offsets the
                            // FIRST screen of content, so a fillMaxSize()
                            // grid would still be able to scroll LATER
                            // rows all the way up to y=0, covering the
                            // entire hero as you scroll deeper into the
                            // list. Bounding the box itself means the
                            // grid can never lay out content above
                            // `block`, regardless of scroll position.
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .padding(top = block)
                                    .height(maxHeight - block)
                            ) {
                                itemsIndexed(sortedChannels, key = { _, channel -> channel.id }) { index, channel ->
                                    val isLeftColumn = index % columns == 0
                                    val isTopRow = index < columns
                                    val isBottomRow = index >= lastRowStartIndex
                                    val isFirstItem = index == visibleIndex
                                    // D-pad focus: stable, registered requester
                                    // keyed by (route, channel.id) so Back from
                                    // the player can restore focus onto the
                                    // exact channel that was clicked.
                                    val itemFocusRequester = remember(channel.id) { FocusRequester() }
                                    FocusRegistry.registerItemFocus(route, channel.id, itemFocusRequester)
                                    ChannelTile(
                                        channel = channel,
                                        isFavorite = favoriteIds.contains(channel.id),
                                        modifier = Modifier
                                            .focusRequester(itemFocusRequester)
                                            .then(
                                                if (isFirstItem) {
                                                    // FIX: this item claims its
                                                    // own focus the moment
                                                    // focusTarget says it's its
                                                    // turn (see FocusEntry's doc
                                                    // comment). gridFirstItemRequester
                                                    // itself is unchanged and
                                                    // still registered above,
                                                    // since
                                                    // FocusRegistry.firstItemTarget(route)
                                                    // (the filter row's "press
                                                    // Down" escape target) still
                                                    // needs it.
                                                    Modifier.claimFocusEntry(
                                                        current = focusTarget,
                                                        mine = FocusEntry.FirstItem,
                                                        requester = gridFirstItemRequester,
                                                        scopeKey = route,
                                                        claimId = focusClaimId
                                                    )
                                                } else Modifier
                                            )
                                            // FIX: columns above is now
                                            // computed on the same width
                                            // basis the carousel's own
                                            // formula assumes (see its doc
                                            // comment) - so the per-column
                                            // width GridCells.Fixed produces
                                            // is already very close to the
                                            // carousel's item width without
                                            // needing an explicit .width()
                                            // override here, which risked
                                            // overflowing its cell now that
                                            // columns doesn't separately
                                            // account for this grid's own
                                            // contentPadding.
                                            .fillMaxWidth()
                                            .then(if (isLeftColumn) Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() } else Modifier)
                                            .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                                            .then(if (isTopRow) Modifier.focusProperties { up = topBarFocusRequester } else Modifier),
                                        // TV/landscape only reaches this branch
                                        // now (portrait's own grid, further
                                        // below, keeps its own Portrait* sizing) -
                                        // always the compact defaults here.
                                        logoHeight = DefaultChannelTileLogoHeight,
                                        nameFontSize = DefaultChannelTileNameFontSize,
                                        nowPlayingFontSize = DefaultChannelTileNowPlayingFontSize,
                                        numberFontSize = DefaultChannelTileNumberFontSize,
                                        onClick = {
                                            FocusRegistry.rememberClickedItem(route, channel.id)
                                            viewModel.getStreamUrl(channel.cmd) { url ->
                                                onPlayChannel(url, channel.id)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleFavorite(channel) },
                                        onFavoriteIconClick = { viewModel.toggleFavorite(channel) },
                                        onFocus = { ch -> focusedHeroChannel = ch }
                                    )
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                            }
                        }
                    }
                } else {
                    // Portrait: no hero, no rail - LiveTvTopBar above already
                    // renders unconditionally, so only the content itself
                    // (empty message or grid) needs to branch here.
                    if (sortedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No channels match your search" else "No channels in this category",
                                color = BbTextMuted
                            )
                        }
                    } else {
                        val itemMinWidth = 150.dp
                        val horizontalSpacing = 12.dp
                        val horizontalContentPadding = 24.dp * 2
                        val gridFirstItemRequester = remember { FocusRequester() }
                        FocusRegistry.registerFirstItem(route, gridFirstItemRequester)
                        DisposableEffect(route, gridFirstItemRequester) {
                            onDispose { FocusRegistry.unregisterFirstItem(route, gridFirstItemRequester) }
                        }
                        val availableWidth = (configuration.screenWidthDp.dp - horizontalContentPadding)
                            .coerceAtLeast(itemMinWidth)
                        val columns = ((availableWidth + horizontalSpacing) / (itemMinWidth + horizontalSpacing))
                            .toInt()
                            .coerceAtLeast(1)
                        val lastRowStartIndex = ((sortedChannels.size - 1) / columns) * columns
                        val visibleIndex = gridState.firstVisibleItemIndex.coerceIn(0, sortedChannels.size - 1)

                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(sortedChannels, key = { _, channel -> channel.id }) { index, channel ->
                                    val isLeftColumn = index % columns == 0
                                    val isBottomRow = index >= lastRowStartIndex
                                    val isFirstItem = index == visibleIndex
                                    val itemFocusRequester = remember(channel.id) { FocusRequester() }
                                    FocusRegistry.registerItemFocus(route, channel.id, itemFocusRequester)
                                    ChannelTile(
                                        channel = channel,
                                        isFavorite = favoriteIds.contains(channel.id),
                                        modifier = Modifier
                                            .focusRequester(itemFocusRequester)
                                            .then(
                                                if (isFirstItem) {
                                                    Modifier.claimFocusEntry(
                                                        current = focusTarget,
                                                        mine = FocusEntry.FirstItem,
                                                        requester = gridFirstItemRequester,
                                                        scopeKey = route,
                                                        claimId = focusClaimId
                                                    )
                                                } else Modifier
                                            )
                                            .fillMaxWidth()
                                            .then(if (isLeftColumn) Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() } else Modifier)
                                            .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier),
                                        // Portrait gets the larger Portrait*
                                        // sizing (bigger logo, name, and
                                        // now-playing text) - see ChannelTile's
                                        // sizing doc comment. Favorites/Recents'
                                        // portrait carousel uses these exact
                                        // same constants so every portrait
                                        // channel tile in the app matches.
                                        logoHeight = PortraitChannelTileLogoHeight,
                                        nameFontSize = PortraitChannelTileNameFontSize,
                                        nowPlayingFontSize = PortraitChannelTileNowPlayingFontSize,
                                        numberFontSize = PortraitChannelTileNumberFontSize,
                                        onClick = {
                                            FocusRegistry.rememberClickedItem(route, channel.id)
                                            viewModel.getStreamUrl(channel.cmd) { url ->
                                                onPlayChannel(url, channel.id)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleFavorite(channel) },
                                        onFavoriteIconClick = { viewModel.toggleFavorite(channel) },
                                        onFocus = {}
                                    )
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// OVERLAID FILTER ROW (TV/landscape hero layouts only) - mirrors Home's
// HomeOverlayFilterRow: compact search/sort/category controls meant to sit
// on TOP of the hero banner rather than push content down like LiveTvTopBar
// below does. Used by both the "All Channels" carousel-hero layout and the
// specific-category grid-hero layout, so the two can't visually drift apart.
// =====================================================================
@Composable
private fun LiveTvOverlayFilterRow(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    sortMode: SortMode,
    onSortModeToggle: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    // NEW: search field starts hidden, revealed only by tapping the search
    // icon button - matches Home's HomeOverlayFilterRow exactly, using the
    // SAME shared SearchIconButton component (see CarouselComponents.kt) so
    // the two screens' toggle behavior can never visually drift apart.
    searchBarVisible: Boolean,
    onToggleSearchBar: () -> Unit,
    searchFieldFocusRequester: FocusRequester,
    searchButtonFocusRequester: FocusRequester,
    categoryFocusRequester: FocusRequester?,
    // D-pad focus: see CategoryDropdown's own doc comment on its identical
    // parameter - lets its deferred focus-return skip reclaiming the
    // trigger once focusTarget has already, correctly, moved on to
    // FirstItem by the time that deferred attempt fires.
    focusTarget: FocusEntry,
    route: String,
    modifier: Modifier = Modifier
) {
    val downToFirstItem = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIconButton(
            onClick = onToggleSearchBar,
            modifier = Modifier
                .focusRequester(searchButtonFocusRequester)
                .then(downToFirstItem)
        )
        if (searchBarVisible) {
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .width(200.dp)
                    .height(44.dp)
                    .focusRequester(searchFieldFocusRequester)
                    .then(downToFirstItem),
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
        SortIconButton(mode = sortMode, onClick = onSortModeToggle, modifier = downToFirstItem, compact = true)
        Spacer(Modifier.width(10.dp))
        CategoryDropdown(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            focusRequester = categoryFocusRequester,
            focusTarget = focusTarget,
            modifier = downToFirstItem,
            compact = true
        )
    }
}

// =====================================================================
// UNIFIED TOP BAR (Adaptive)
// =====================================================================
@Composable
private fun LiveTvTopBar(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    sortMode: SortMode,
    onSortModeToggle: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    dropdownWidth: Dp,
    isPortrait: Boolean,
    // NEW: search field starts hidden, revealed only by tapping the search
    // icon button - matches Home's portrait pattern.
    searchBarVisible: Boolean,
    onToggleSearchBar: () -> Unit,
    searchFieldFocusRequester: FocusRequester,
    searchButtonFocusRequester: FocusRequester,
    categoryFocusRequester: FocusRequester? = null,
    route: String
) {
    val downToFirstItem = Modifier.safeFocusEscape(Key.DirectionDown) { FocusRegistry.firstItemTarget(route) }
    if (isPortrait) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchIconButton(
                    onClick = onToggleSearchBar,
                    modifier = Modifier
                        .focusRequester(searchButtonFocusRequester)
                        .then(downToFirstItem)
                )
                Spacer(Modifier.width(12.dp))
                SortIconButton(mode = sortMode, onClick = onSortModeToggle, modifier = downToFirstItem)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                CategoryDropdown(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                    modifier = downToFirstItem
                )
            }
            // Search bar for Portrait - only shown once toggled on
            if (searchBarVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldFocusRequester)
                            .then(downToFirstItem),
                        placeholder = { Text("Search channels...", color = BbTextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BbTextMuted) },
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
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).then(downToFirstItem),
                placeholder = { Text("Search channels...", color = BbTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BbTextMuted) },
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
            Spacer(modifier = Modifier.width(16.dp))
            SortIconButton(mode = sortMode, onClick = onSortModeToggle, modifier = downToFirstItem)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.width(dropdownWidth)) {
                CategoryDropdown(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                    focusRequester = categoryFocusRequester,
                    modifier = downToFirstItem
                )
            }
        }
    }
}

@Composable
private fun SortIconButton(
    mode: SortMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // NEW: shrinks this for overlaying on the hero banner - see
    // LiveTvOverlayFilterRow.
    compact: Boolean = false
) {
    // FIX: same shared-InteractionSource fix as CategoryDropdown above.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
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
            .then(if (isFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
            .then(modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (isFocused) BbAccent else BbTextSecondary,
            modifier = if (compact) Modifier.size(18.dp) else Modifier
        )
    }
}

@Composable
private fun CategoryDropdown(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    focusRequester: FocusRequester? = null,
    // D-pad focus: read fresh (via rememberUpdatedState below) at the
    // moment the deferred focus-return actually fires, not captured back
    // when the click happened. FIX (confirmed: selecting a category here
    // is entirely client-side and can settle to FirstItem well within the
    // deferred call's own delay - unlike Home, where a real network
    // round-trip means the reload is still in flight when it fires):
    // without checking this, the deferred call would unconditionally yank
    // focus back to this dropdown's trigger even after claimFocusEntry has
    // already, correctly, moved it onto the actual first channel. Defaults
    // to None (a harmless placeholder) for callers where focusRequester is
    // also null (e.g. LiveTvTopBar's portrait usage), since the whole
    // guarded block above only runs when focusRequester is non-null anyway.
    focusTarget: FocusEntry = FocusEntry.None,
    modifier: Modifier = Modifier,
    // NEW: shrinks this for overlaying on the hero banner (see
    // LiveTvOverlayFilterRow), matching Home's HomeCategoryDropdown compact
    // mode - same width/padding/font values, for pixel consistency between
    // the two screens' overlaid filter rows.
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val latestFocusTarget by rememberUpdatedState(focusTarget)
    // FIX: was a plain .clickable{} + separate later .focusable() +
    // .onFocusChanged{} - same bug already found/fixed in ChannelTile/
    // PosterCard/HomeGenreDropdown etc.: clickable() creates its own
    // implicit focus target, so a separate .focusable() could end up being
    // a different node than the one .onFocusChanged was observing, leaving
    // isFocused (and this border) never updating on some focus transitions.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val horizontalPadding = if (compact) 16.dp else 16.dp
    val verticalPadding = if (compact) 4.dp else 14.dp
    val fontSize = if (compact) 12.sp else 16.sp
    Box(modifier = if (compact) Modifier.width(150.dp) else Modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(if (isFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(modifier)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = true }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
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
                tint = BbTextSecondary,
                modifier = if (compact) Modifier.size(16.dp) else Modifier
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                // FIX (same mechanism found and fixed on Home's
                // HomeCategoryDropdown - see its own doc comment for the
                // full investigation): dismissing without selecting
                // anything (Back, or clicking outside) disposes the
                // focused menu item, and DropdownMenu's own Popup teardown
                // can override a focus claim made synchronously here -
                // same fix as onClick below: a synchronous attempt wins
                // the immediate-disposal race, the deferred one (on
                // FocusRegistry's own process-wide scope, so it survives
                // regardless of what this dismissal does to this
                // composable's own lifecycle) wins the later Popup-teardown
                // race. Neither alone covers both disposal events.
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
                        // FIX (same mechanism found and fixed on Home's
                        // HomeCategoryDropdown): arming this synchronously,
                        // before the selection call, closes the race where
                        // focus could reach the rail before this screen's
                        // own state has even reacted to the selection yet.
                        FocusRegistry.setContentTransitioning(true)
                        onCategorySelected(cat)
                        expanded = false
                        if (focusRequester != null) {
                            // Synchronous attempt wins the race against this
                            // menu item's own immediate disposal (expanded =
                            // false above); the deferred one wins the
                            // separate, later race against DropdownMenu's
                            // own Popup teardown - see onDismissRequest's
                            // doc comment above for the full mechanism.
                            runCatching { focusRequester.requestFocus() }
                            // FIX: guarded (unlike onDismissRequest's own
                            // unconditional deferred call above, where
                            // nothing actually changes) - see focusTarget's
                            // own doc comment on this function's signature.
                            // Checked fresh via latestFocusTarget when this
                            // actually fires, not the value captured back
                            // when this click happened.
                            FocusRegistry.deferredRequestFocus(focusRequester) {
                                latestFocusTarget != FocusEntry.FirstItem
                            }
                        }
                    }
                )
            }
        }
    }
}