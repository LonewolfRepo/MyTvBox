package com.itv.blockbuster.ui.home

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.itv.blockbuster.ui.components.CarouselRow
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.VodNavigationCache

@Composable
fun HomeScreen(
    onOpenPortals: () -> Unit,
    onOpenVodDetail: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val hasMoreCategories by viewModel.hasMoreCategories.collectAsState()

    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    // Reload Home when returning from Settings if Home category
    // visibility/order was changed while away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadIfHomeOrderChanged()
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
                                onGenreSelected = { viewModel.selectGenre(it) }
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) }
                            )
                        }
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearch(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp), // FIX: Corrected padding parameters
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
                                modifier = Modifier.width(260.dp),
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
                                onGenreSelected = { viewModel.selectGenre(it) }
                            )
                            Spacer(Modifier.width(12.dp))
                            HomeCategoryDropdown(
                                categories = state.categories,
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) }
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Only show HeroBanner if "All Categories" is selected and no search is active
                        val isAllCategories = state.selectedCategory?.id == "*" || state.selectedCategory?.id == "0"
                        val isSearching = state.searchQuery.isNotBlank()
                        if (isAllCategories && !isSearching) {
                            item(key = "hero") { HeroBanner(hero = state.hero) }
                        }
                        items(state.rows, key = { it.id }) { row ->
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
                                onLoadMore = { viewModel.loadMoreRowItems(row.id) }
                            )
                        }
                        // Vertical Pagination Trigger (only for All Categories, no active search)
                        if (hasMoreCategories && isAllCategories && !isSearching) {
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
            }
        }
    }
}

@Composable
private fun HomeGenreDropdown(
    genres: List<PortalCategory>,
    selectedGenre: PortalCategory?,
    onGenreSelected: (PortalCategory) -> Unit
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
    onCategorySelected: (PortalCategory) -> Unit
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