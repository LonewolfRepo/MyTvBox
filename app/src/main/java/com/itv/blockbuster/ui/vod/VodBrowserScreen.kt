package com.itv.blockbuster.ui.vod

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.ui.components.CarouselRow
import com.itv.blockbuster.ui.components.PosterGrid
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.VodNavigationCache
import kotlinx.coroutines.launch

@Composable
fun VodBrowserScreen(
    contentType: String,
    onOpenDetail: (String) -> Unit,
    viewModel: VodBrowserViewModel = hiltViewModel(),
    // D-pad focus: which route this instance represents (Movies vs TV Shows
    // both use this same screen), so FocusRegistry.notifyContentReady only
    // advances focus when THIS screen is the one the rail is waiting on.
    route: String = if (contentType == "series") Routes.TV_SHOWS else Routes.MOVIES
) {
    val state by viewModel.state.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val hasMoreCategories by viewModel.hasMoreCategories.collectAsState()

    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // D-pad focus: explicit Up target for the first carousel row, pointing at
    // this screen's own top bar (category filter dropdown) rather than
    // relying on Compose's default spatial search - see HomeScreen for the
    // same fix and rationale.
    val topBarFocusRequester = remember { FocusRequester() }

    // FIX: D-pad focus - the LazyColumn's scroll position survives navigating
    // away and back (rememberLazyListState is rememberSaveable-backed, and
    // Navigation Compose's restoreState=true preserves that across tab
    // switches). Hoisting the state here lets CarouselRow calls below
    // determine which row is CURRENTLY first-visible (via
    // listState.firstVisibleItemIndex), so the auto-focus-on-load handoff
    // targets whatever's actually on screen right now rather than always
    // row 0.
    val listState = rememberLazyListState()

    LaunchedEffect(contentType) {
        viewModel.initialize(contentType)
    }

    // FIX: D-pad focus - once the first row of content is actually on screen,
    // hand focus off from the rail to the first poster (see AppShell/
    // FocusRegistry). No-ops unless this route is the one currently pending.
    LaunchedEffect(state.rows.isNotEmpty(), state.isLoading) {
        Log.d("DpadFocus", "VodBrowserScreen(route=$route): isLoading=${state.isLoading} rowCount=${state.rows.size}")
        if (!state.isLoading && state.rows.isNotEmpty()) {
            FocusRegistry.notifyContentReady(route)
        }
    }

    // D-pad focus: when a category/genre/search filter changes while the user
    // is already on this screen, explicitly re-focus the first item of the
    // newly-filtered list once it finishes loading - see HomeScreen for the
    // same fix and rationale.
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

    // D-pad focus: on resume (e.g. Back popping a VOD detail screen pushed
    // from this one), restore focus onto the exact poster that was clicked -
    // no-ops unless RailShell's route-change handling armed this route for
    // restoration (see FocusRegistry.armRestoreFocus/restoreClickedItemFocus
    // and AppShell.kt), so it doesn't interfere with the ordinary rail-then-
    // first-item handoff on other resumes (rail clicks, tab switches).
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
        if (state.isLoading && state.rows.isEmpty()) {
            CircularProgressIndicator(color = BbAccent, modifier = Modifier.align(Alignment.Center))
        } else {
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
                            text = if (contentType == "series") "TV Shows" else "Movies",
                            color = BbAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        BrowserGenreDropdown(
                            genres = state.genres,
                            selectedGenre = state.selectedGenre,
                            onGenreSelected = { viewModel.selectGenre(it) },
                            modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                        )
                        Spacer(Modifier.width(12.dp))
                        BrowserCategoryDropdown(
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
                        placeholder = { Text("Search...", color = BbTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BbAccent,
                            unfocusedBorderColor = BbCard,
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
                            text = if (contentType == "series") "TV Shows" else "Movies",
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
                            placeholder = { Text("Search...", color = BbTextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = BbTextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BbAccent,
                                unfocusedBorderColor = BbCard,
                                cursorColor = BbAccent,
                                focusedTextColor = BbTextPrimary,
                                unfocusedTextColor = BbTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        BrowserGenreDropdown(
                            genres = state.genres,
                            selectedGenre = state.selectedGenre,
                            onGenreSelected = { viewModel.selectGenre(it) },
                            modifier = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
                        )
                        Spacer(Modifier.width(12.dp))
                        BrowserCategoryDropdown(
                            categories = state.categories,
                            selectedCategory = state.selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) },
                            focusRequester = topBarFocusRequester
                        )
                    }
                }

                // NEW: A specific category switches to a scrollable poster grid instead
                // of the carousel rows - see HomeScreen for the same pattern/rationale.
                val isAllCategories = state.selectedCategory?.id == "*" || state.selectedCategory?.id == "0"

                if (isAllCategories) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                            CarouselRow(
                                row = row,
                                progressMap = progressMap,
                                favoriteIds = favoriteIds,
                                onItemClick = { item ->
                                    VodNavigationCache.currentItem = item
                                    onOpenDetail(item.id)
                                },
                                onFavoriteIconClick = { item ->
                                    viewModel.toggleFavorite(item)
                                },
                                onLoadMore = { viewModel.loadMoreRowItems(row.id) },
                                route = route,
                                isFirstRow = index == listState.firstVisibleItemIndex,
                                isLastRow = index == state.rows.lastIndex,
                                // FIX: only attached to the landscape search field below,
                                // so only wire it up there - portrait has no rail to escape
                                // to anyway.
                                upEscapeTarget = if (index == 0 && !isPortrait) topBarFocusRequester else null
                            )
                        }

                        // Vertical Pagination Trigger
                        if (hasMoreCategories) {
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
                                onOpenDetail(item.id)
                            },
                            onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) },
                            onLoadMore = { viewModel.loadMoreRowItems(gridRow.id) },
                            modifier = Modifier.fillMaxSize(),
                            upEscapeTarget = if (!isPortrait) topBarFocusRequester else null,
                            route = route,
                            collapsedMenuWidth = if (isPortrait) 0.dp else 84.dp
                        )
                    } else if (!state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No items found", color = BbTextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserGenreDropdown(
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
private fun BrowserCategoryDropdown(
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
