package com.itv.blockbuster.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.components.PosterGrid
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
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.VodNavigationCache
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenPortals: () -> Unit,
    onOpenVodDetail: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    // D-pad focus: which route this instance represents (HomeScreen is reused
    // for both the Home tab and the Adult VOD browser), so
    // FocusRegistry.notifyContentReady only advances focus when THIS screen
    // is the one the rail is currently waiting on.
    route: String = Routes.HOME
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val hasMoreCategories by viewModel.hasMoreCategories.collectAsState()

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

    // FIX: D-pad focus - once the first row of content is actually on screen,
    // hand focus off from the rail to the first poster (see AppShell/
    // FocusRegistry). No-ops unless this route is the one currently pending.
    LaunchedEffect(state.rows.isNotEmpty(), state.isLoading) {
        Log.d("DpadFocus", "HomeScreen(route=$route): isLoading=${state.isLoading} rowCount=${state.rows.size}")
        if (!state.isLoading && state.rows.isNotEmpty()) {
            FocusRegistry.notifyContentReady(route)
        }
    }

    // D-pad focus: when a category/genre/search filter changes while the user
    // is already on this screen (as opposed to navigating in fresh from the
    // rail - that initial handoff is the pendingContentFocusRoute-gated
    // notifyContentReady above), explicitly re-focus the first item of the
    // newly-filtered list once it finishes loading. Skips the very first
    // composition (which just establishes the initial filter state on screen
    // entry, already covered by the rail handoff) so it doesn't fight that
    // sequence; only genuine subsequent filter changes arm this.
    var isInitialFilterState by remember { mutableStateOf(true) }
    var awaitingFilterRefocus by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedCategory, state.selectedGenre, state.searchQuery) {
        if (isInitialFilterState) {
            isInitialFilterState = false
        } else {
            awaitingFilterRefocus = true
        }
    }
    LaunchedEffect(awaitingFilterRefocus, state.isLoading) {
        if (awaitingFilterRefocus && !state.isLoading) {
            FocusRegistry.focusFirstItem(route)
            awaitingFilterRefocus = false
        }
    }

    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // D-pad focus: explicit Up target for the first carousel row, pointing at
    // this screen's own top bar (category filter dropdown) rather than
    // relying on Compose's default spatial search - the top bar's controls
    // are right-aligned while the leftmost poster is left-aligned, so the
    // default heuristic was picking whichever rail item sat geometrically
    // closest instead of a top bar control, letting Up escape to the rail.
    val topBarFocusRequester = remember { FocusRequester() }

    // NEW: Hero banner persistence + focus-following (TV/large form factor only).
    // - On phones in portrait, the hero is hidden entirely (see isPortrait below) -
    //   there just isn't enough vertical room to spare for it there.
    // - On TV/landscape, it becomes a persistent element (not a scrolling list
    //   item) sized to at most ~1/3 of the screen height, and its content follows
    //   whichever poster currently has D-pad focus rather than staying fixed to
    //   the "Recently Added" item - falling back to that only until something
    //   actually gets focused.
    var focusedHeroItem by remember { mutableStateOf<PortalVodItem?>(null) }
    val heroItem = focusedHeroItem ?: state.hero
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeightDp / 3

    // Reset the focus-followed hero item whenever the row set changes (category/
    // genre/search change) so it doesn't keep showing a poster from a filter that
    // no longer applies.
    LaunchedEffect(state.selectedCategory, state.selectedGenre, state.searchQuery) {
        focusedHeroItem = null
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
            (state.isLoading || state.isConnecting) && state.rows.isEmpty() ->
                LoadingOverlay(isConnecting = state.isConnecting)
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isPortrait) {
                        // Portrait Layout: Row 1 (Title + Filters) | Row 2 (Search)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Home",
                                color = BbAccent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            HomeGenreDropdown(
                                genres = state.genres,
                                selectedGenre = state.selectedGenre,
                                onGenreSelected = { viewModel.selectGenre(it) },
                                modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                            )
                        }
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearch(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp) // FIX: Corrected padding parameters
                                .focusProperties { down = FocusRegistry.firstItemTarget(route) },
                            placeholder = { Text("Search...", color = BbTextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BbAccent,
                                unfocusedBorderColor = BbTextMuted.copy(alpha = 0.3f),
                                cursorColor = BbAccent,
                                focusedTextColor = BbTextPrimary,
                                unfocusedTextColor = BbTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        // TV / Landscape Layout: Single Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Home",
                                color = BbAccent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            // NEW: Search field (left of Genres filter); debounced in the VM
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.updateSearch(it) },
                                modifier = Modifier
                                    .width(260.dp)
                                    .focusProperties { down = FocusRegistry.firstItemTarget(route) },
                                placeholder = { Text("Search...", color = BbTextMuted) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BbAccent,
                                    unfocusedBorderColor = BbTextMuted.copy(alpha = 0.3f),
                                    cursorColor = BbAccent,
                                    focusedTextColor = BbTextPrimary,
                                    unfocusedTextColor = BbTextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeGenreDropdown(
                                genres = state.genres,
                                selectedGenre = state.selectedGenre,
                                onGenreSelected = { viewModel.selectGenre(it) },
                                modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                focusRequester = topBarFocusRequester,
                                modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                            )
                        }
                    }

                    // NEW: A specific category (anything but "All Categories") switches
                    // from the horizontal carousel rows to a scrollable poster grid,
                    // since there's only ever one flat list of items to show at that
                    // point - a grid reads better than a single wide row for that.
                    val isAllCategories = state.selectedCategory?.id == "*" || state.selectedCategory?.id == "0"
                    val isSearching = state.searchQuery.isNotBlank()

                    if (isAllCategories) {
                        // NEW: on TV/landscape the hero is persistent (sits above the
                        // scrolling rows, not inside the LazyColumn as a scrolling
                        // item) and follows D-pad focus. On phones in portrait there's
                        // no room to spare for it, so it's skipped entirely and rows
                        // simply scroll from the top like before.
                        if (!isPortrait) {
                            if (!isSearching && heroItem != null) {
                                HeroBanner(
                                    hero = heroItem,
                                    height = heroHeight,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                    CarouselRow(
                                        row = row,
                                        favoriteIds = favoriteIds,
                                        progressMap = progressMap,
                                        onItemClick = { item ->
                                            VodNavigationCache.currentItem = item
                                            onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                        },
                                        onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                        onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                        onLoadMore = { viewModel.loadMoreRowItems(row.id) },
                                        onItemFocused = { item -> focusedHeroItem = item },
                                        route = route,
                                        isFirstRow = index == listState.firstVisibleItemIndex,
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
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
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
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                    CarouselRow(
                                        row = row,
                                        favoriteIds = favoriteIds,
                                        progressMap = progressMap,
                                        onItemClick = { item ->
                                            VodNavigationCache.currentItem = item
                                            onOpenVodDetail(item.id, if (item.isSeries) "series" else "vod")
                                        },
                                        onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                                        onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                                        onLoadMore = { viewModel.loadMoreRowItems(row.id) },
                                        route = route,
                                        isFirstRow = index == listState.firstVisibleItemIndex,
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
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
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
                                modifier = Modifier.fillMaxSize(),
                                upEscapeTarget = if (!isPortrait) topBarFocusRequester else null,
                                route = route,
                                collapsedMenuWidth = if (isPortrait) 0.dp else 84.dp
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

@Composable
private fun HomeGenreDropdown(
    genres: List<PortalCategory>,
    selectedGenre: PortalCategory?,
    onGenreSelected: (PortalCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(180.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(
                    if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .then(modifier)
                .clickable { expanded = true }
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedGenre?.title ?: "All Genres",
                color = BbTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle genres",
                tint = BbTextSecondary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
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
                        onGenreSelected(genre)
                        expanded = false
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(180.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(
                    if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(modifier)
                .clickable { expanded = true }
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedCategory?.title ?: "All Categories",
                color = BbTextPrimary,
                fontSize = 14.sp,
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
            onDismissRequest = { expanded = false },
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
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(isConnecting: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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