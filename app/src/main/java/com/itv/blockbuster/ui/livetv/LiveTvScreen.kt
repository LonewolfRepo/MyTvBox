package com.itv.blockbuster.ui.livetv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.ui.components.ChannelCarouselRow
import com.itv.blockbuster.ui.components.ChannelTile
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
import kotlinx.coroutines.launch

enum class SortMode { DEFAULT, A_Z, Z_A, NUMERIC }

@Composable
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
    val isTvOrLandscape = formFactor != FormFactor.MOBILE_PORTRAIT
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // FIX: Use rememberSaveable so state survives navigation away and back
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DEFAULT) }

    val configuration = LocalConfiguration.current
    val dropdownWidth = (configuration.screenWidthDp.dp * 0.35f)
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    // D-pad focus: explicit Up target for the first channel row/grid row,
    // pointing at this screen's own top bar (category filter dropdown)
    // rather than relying on Compose's default spatial search - see
    // HomeScreen/VodBrowserScreen for the same fix and rationale.
    val topBarFocusRequester = remember { FocusRequester() }

    // FIX: D-pad focus - once the first row of channels is actually on
    // screen, hand focus off from the rail to the first tile (see AppShell/
    // FocusRegistry). No-ops unless this route is the one currently pending.
    LaunchedEffect(state.allChannels.isNotEmpty(), state.isLoading) {
        Log.d("DpadFocus", "LiveTvScreen(route=$route): isLoading=${state.isLoading} channelCount=${state.allChannels.size}")
        if (!state.isLoading && !state.isConnecting && state.allChannels.isNotEmpty()) {
            FocusRegistry.notifyContentReady(route)
        }
    }

    // D-pad focus: when the category filter, search query, or sort mode
    // changes while the user is already on this screen, explicitly re-focus
    // the first item of the newly-filtered/sorted list once it finishes
    // loading - see HomeScreen for the same fix and rationale.
    var isInitialFilterState by remember { mutableStateOf(true) }
    var awaitingFilterRefocus by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedCategory, searchQuery, sortMode) {
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

    // D-pad focus: on resume (e.g. Back popping the player pushed from this
    // screen), restore focus onto the exact channel that was clicked -
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
        if (state.isLoading || state.isConnecting) {
            Column(
                modifier = Modifier.fillMaxSize(),
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
                    categoryFocusRequester = if (!isPortrait) topBarFocusRequester else null,
                    route = route
                )

                val isAllCategory = state.selectedCategory?.id == "*" ||
                        state.selectedCategory?.id == "0" ||
                        state.selectedCategory?.id == "all" ||
                        state.selectedCategory == null

                // 1. Filter by Category
                val categoryFiltered = if (isAllCategory) {
                    state.allChannels
                } else {
                    state.allChannels.filter { it.genreId == state.selectedCategory?.id }
                }

                // 2. Filter by Search Query
                val searchFiltered = if (searchQuery.isBlank()) {
                    categoryFiltered
                } else {
                    val query = searchQuery.trim().lowercase()
                    categoryFiltered.filter { channel ->
                        channel.name.lowercase().contains(query) ||
                                channel.number.lowercase().contains(query) ||
                                channel.nowPlaying.lowercase().contains(query)
                    }
                }

                // 3. Apply Sorting
                val sortedChannels = when (sortMode) {
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

                if (sortedChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No channels match your search" else "No channels in this category",
                            color = BbTextMuted
                        )
                    }
                } else {
                    // Carousels ONLY for ALL on TV/landscape.
                    // Everything else (portrait ALL + all specific categories) uses the tile grid.
                    if (isAllCategory && isTvOrLandscape) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                        val grouped = sortedChannels.groupBy { it.genreId }
                        // FIX: precompute which categories will actually render a row
                        // (non-empty, not the "All" placeholder), so both the first AND
                        // last rendered row can be identified up front - needed for
                        // isFirstRow (initial D-pad focus target) and isLastRow (blocks
                        // D-pad Down from escaping past the bottom into the rail).
                        val renderableCategories = state.categories.filter { cat ->
                            cat.id != "*" && cat.id != "0" && cat.id != "all" && !grouped[cat.id].isNullOrEmpty()
                        }
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
                                        route = route,
                                        isFirstRow = index == 0,
                                        isLastRow = index == renderableCategories.lastIndex,
                                        upEscapeTarget = if (index == 0) topBarFocusRequester else null
                                    )
                                }
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                    } else {
                        // Tile grid: specific categories (any form factor) + portrait ALL + Search Results
                        // D-pad focus: computed FIXED column count (matching
                        // GridCells.Adaptive's visual density) so boundary items
                        // (leftmost column -> rail on Left; bottom row blocks
                        // Down from escaping to the rail) can be identified
                        // precisely - see PosterGrid for the same pattern. The
                        // top row's Up is pointed at the top bar's category
                        // filter (landscape only - portrait has no rail). Also
                        // registers its first VISIBLE item as the target
                        // FocusRegistry.notifyContentReady(route) shifts focus
                        // onto once content loads, same as the carousel rows.
                        //
                        // FIX: width computed from the device's full, stable
                        // screenWidthDp minus a fixed rail-width constant, NOT
                        // from BoxWithConstraints' live maxWidth - see
                        // PosterGrid's doc comment for why: the rail's width
                        // animates as focus moves in/out of it, and since
                        // landing focus on this grid is exactly what collapses
                        // the rail, computing columns from the live (mid-
                        // animation) width caused the grid to reflow while the
                        // focus-request that triggered the collapse was still
                        // in flight, landing focus somewhere unpredictable.
                        val itemMinWidth = 150.dp
                        val horizontalSpacing = 12.dp
                        val horizontalContentPadding = 24.dp * 2
                        val gridState = rememberLazyGridState()
                        val gridFirstItemRequester = remember { FocusRequester() }
                        FocusRegistry.registerFirstItem(route, gridFirstItemRequester)
                        val railReservedWidth = if (isPortrait) 0.dp else 84.dp
                        val availableWidth = (configuration.screenWidthDp.dp - railReservedWidth - horizontalContentPadding)
                            .coerceAtLeast(itemMinWidth)
                        val columns = ((availableWidth + horizontalSpacing) / (itemMinWidth + horizontalSpacing))
                            .toInt()
                            .coerceAtLeast(1)
                        val lastRowStartIndex = if (sortedChannels.isEmpty()) 0 else ((sortedChannels.size - 1) / columns) * columns
                        val visibleIndex = gridState.firstVisibleItemIndex.coerceIn(0, (sortedChannels.size - 1).coerceAtLeast(0))
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
                                            .then(if (isFirstItem) Modifier.focusRequester(gridFirstItemRequester) else Modifier)
                                            .fillMaxWidth()
                                            .then(if (isLeftColumn) Modifier.focusProperties { left = FocusRegistry.leftEscapeTarget() } else Modifier)
                                            .then(if (isBottomRow) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                                            .then(if (isTopRow && !isPortrait) Modifier.focusProperties { up = topBarFocusRequester } else Modifier),
                                        onClick = {
                                            FocusRegistry.rememberClickedItem(route, channel.id)
                                            viewModel.getStreamUrl(channel.cmd) { url ->
                                                onPlayChannel(url, channel.id)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleFavorite(channel) },
                                        onFavoriteIconClick = { viewModel.toggleFavorite(channel) }
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
    categoryFocusRequester: FocusRequester? = null,
    route: String
) {
    val downToFirstItem = Modifier.focusProperties { down = FocusRegistry.firstItemTarget(route) }
    if (isPortrait) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            // Search bar for Portrait
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().then(downToFirstItem),
                    placeholder = { Text("Search channels...", color = BbTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BbTextMuted) },
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
private fun SortIconButton(mode: SortMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val (icon, contentDesc) = when (mode) {
        SortMode.DEFAULT -> Icons.Default.Sort to "Sort: Default"
        SortMode.A_Z -> Icons.Default.ArrowUpward to "Sort: A to Z"
        SortMode.Z_A -> Icons.Default.ArrowDownward to "Sort: Z to A"
        SortMode.NUMERIC -> Icons.Default.Numbers to "Sort: Numeric"
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
            .then(if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
            .then(modifier)
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (isFocused) BbAccent else BbTextSecondary
        )
    }
}

@Composable
private fun CategoryDropdown(
    categories: List<PortalCategory>,
    selectedCategory: PortalCategory?,
    onCategorySelected: (PortalCategory) -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) BbAccent.copy(alpha = 0.1f) else BbCard)
                .then(if (isFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp)) else Modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(modifier)
                .clickable { expanded = true }
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedCategory?.title ?: "All Categories",
                color = BbTextPrimary,
                fontSize = 16.sp,
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