package com.itv.blockbuster.ui.livetv

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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.ui.components.ChannelCarouselRow
import com.itv.blockbuster.ui.components.ChannelTile
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

enum class SortMode { DEFAULT, A_Z, Z_A, NUMERIC }

@Composable
fun LiveTvScreen(
    onPlayChannel: (String, String) -> Unit, // (streamUrl, channelId)
    onOpenCatchup: (String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isTvOrLandscape = formFactor != FormFactor.MOBILE_PORTRAIT
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    val configuration = LocalConfiguration.current
    val dropdownWidth = (configuration.screenWidthDp.dp * 0.35f)
    val favoriteIds by viewModel.favoriteIds.collectAsState()

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
                    isPortrait = isPortrait
                )

                val isAllCategory = state.selectedCategory?.id == "*" ||
                        state.selectedCategory?.id == "0" ||
                        state.selectedCategory?.id == "all" ||
                        state.selectedCategory == null
                val filteredChannels = if (isAllCategory) {
                    state.allChannels
                } else {
                    state.allChannels.filter { it.genreId == state.selectedCategory?.id }
                }

                if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No channels in this category", color = BbTextMuted)
                    }
                } else {
                    // FIX: Carousels ONLY for ALL on TV/landscape.
                    // Everything else (portrait ALL + all specific categories) uses the tile grid.
                    if (isAllCategory && isTvOrLandscape) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            val grouped = filteredChannels.groupBy { it.genreId }
                            state.categories.forEach { cat ->
                                if (cat.id == "*" || cat.id == "0" || cat.id == "all") return@forEach
                                val channelsInCat = grouped[cat.id] ?: return@forEach
                                if (channelsInCat.isNotEmpty()) {
                                    item(key = cat.id) {
                                        ChannelCarouselRow(
                                            title = cat.title,
                                            channels = channelsInCat,
                                            favoriteIds = favoriteIds,          // FIX: was missing
                                            onChannelClick = { channel ->
                                                viewModel.getStreamUrl(channel.cmd) { url ->
                                                    onPlayChannel(url, channel.id)
                                                }
                                            },
                                            onChannelLongClick = { channel ->
                                                if (channel.hasArchive) onOpenCatchup(channel.id)
                                            },
                                            onFavoriteIconClick = { channel ->  // FIX: was missing
                                                viewModel.toggleFavorite(channel)
                                            }
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(32.dp)) }
                        }
                    } else {
                        // Tile grid: specific categories (any form factor) + portrait ALL
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels, key = { it.id }) { channel ->
                                ChannelTile(
                                    channel = channel,
                                    isFavorite = favoriteIds.contains(channel.id),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
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
    isPortrait: Boolean
) {
    if (isPortrait) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortIconButton(mode = sortMode, onClick = onSortModeToggle)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                CategoryDropdown(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
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
                modifier = Modifier.weight(1f),
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
            SortIconButton(mode = sortMode, onClick = onSortModeToggle)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.width(dropdownWidth)) {
                CategoryDropdown(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
            }
        }
    }
}

@Composable
private fun SortIconButton(mode: SortMode, onClick: () -> Unit) {
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
    onCategorySelected: (PortalCategory) -> Unit
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