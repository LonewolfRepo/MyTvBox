package com.itv.blockbuster.ui.guide

import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.livetv.SortMode
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val CHANNEL_COL_TV = 260.dp
private val CHANNEL_COL_PORTRAIT = 150.dp
private val ROW_HEIGHT = 56.dp
private const val WINDOW_MIN = 240 // 4 hours visible window
private val PLAYER_WIDTH_TV = 300.dp
private val PLAYER_WIDTH_PORTRAIT = 170.dp

@Composable
fun TvGuideScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenCatchup: (String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT
    val pxPerMin = if (isPortrait) 2.5.dp else 5.dp
    val channelCol = if (isPortrait) CHANNEL_COL_PORTRAIT else CHANNEL_COL_TV
    val gridStart = (state.nowMin / 30) * 30

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val dropdownWidth = (configuration.screenWidthDp.dp * 0.28f)

    // Search + Sort pipeline (category filtering is handled by the ViewModel)
    val searchFiltered = if (searchQuery.isBlank()) state.channels else run {
        val q = searchQuery.trim().lowercase()
        state.channels.filter {
            it.name.lowercase().contains(q) ||
                    it.number.lowercase().contains(q) ||
                    it.nowPlaying.lowercase().contains(q)
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

    // LAND ON LAST PLAYED CHANNEL: scroll grid to it once channels are ready
    LaunchedEffect(state.lastPlayedChannelId, guideChannels.size) {
        val id = state.lastPlayedChannelId ?: return@LaunchedEffect
        val index = guideChannels.indexOfFirst { it.id == id }
        if (index >= 0) listState.scrollToItem(index)
    }

    // Pause preview when leaving the guide (unless handed to fullscreen player)
    DisposableEffect(Unit) {
        onDispose {
            if (!viewModel.playbackManager.isFullscreenActive) {
                viewModel.playbackManager.player.pause()
            }
        }
    }

    val preview = state.previewChannel
    val previewPrograms = preview?.let { state.epg[it.id] } ?: emptyList()
    val nowProgram = previewPrograms.firstOrNull {
        parseMinutes(it.time) <= state.nowMin && parseMinutes(it.time) + it.duration > state.nowMin
    } ?: previewPrograms.firstOrNull()

    Column(Modifier.fillMaxSize().background(BbBackground)) {
        // ── Top bar: player (top-left) + now-playing info + filters ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isPortrait) 12.dp else 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1) PLAYER TOP-LEFT
            Box(
                modifier = Modifier
                    .width(if (isPortrait) PLAYER_WIDTH_PORTRAIT else PLAYER_WIDTH_TV)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .then(
                        if (preview != null) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable {
                        // Tap player -> fullscreen current preview
                        val url = state.previewUrl
                        if (url != null && preview != null) onPlayLive(url, preview.id)
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (!viewModel.playbackManager.isFullscreenActive) {
                            viewModel.playbackManager.player.setVideoTextureView(view)
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

            Spacer(Modifier.width(16.dp))

            // 2) NOW PLAYING INFO
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview?.logoUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = preview.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = preview?.name ?: "Select a channel",
                        color = BbTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(state.clock, color = BbTextSecondary, fontSize = 14.sp)
                }
                if (preview != null && nowProgram != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = nowProgram.name,
                            color = BbTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${formatMin(parseMinutes(nowProgram.time))} - " +
                                    formatMin(parseMinutes(nowProgram.time) + nowProgram.duration),
                            color = BbTextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("● Live", color = BbAccent, fontSize = 12.sp)
                    }
                }
            }

            // 3) SEARCH / SORT / CATEGORY (same pattern as Live TV)
            if (!isPortrait) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.width(220.dp),
                    placeholder = { Text("Search channels...", color = BbTextMuted) },
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
            }
            SortIconButton(mode = sortMode) {
                sortMode = when (sortMode) {
                    SortMode.DEFAULT -> SortMode.A_Z
                    SortMode.A_Z -> SortMode.Z_A
                    SortMode.Z_A -> SortMode.NUMERIC
                    SortMode.NUMERIC -> SortMode.DEFAULT
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.width(if (isPortrait) 140.dp else dropdownWidth)) {
                CategoryDropdown(
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) }
                )
            }
        }

        // Portrait: search field on its own row
        if (isPortrait) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                placeholder = { Text("Search channels...", color = BbTextMuted) },
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
        }

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

        // ── Guide grid + playhead overlay ──
        Box(Modifier.weight(1f)) {
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(guideChannels, key = { it.id }) { channel ->
                            val index = guideChannels.indexOf(channel)
                            GuideChannelRow(
                                index = index,
                                channel = channel,
                                programs = state.epg[channel.id] ?: emptyList(),
                                gridStart = gridStart,
                                nowMin = state.nowMin,
                                pxPerMin = pxPerMin,
                                channelCol = channelCol,
                                isPreviewing = state.previewChannel?.id == channel.id,
                                requestInitialFocus = channel.id == state.lastPlayedChannelId,
                                onChannelClick = {
                                    // CLICK ON CHANNEL CURRENTLY PLAYING -> FULLSCREEN
                                    if (state.previewChannel?.id == channel.id &&
                                        !state.previewUrl.isNullOrEmpty()
                                    ) {
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
                            viewModel.ensureEpg(channel.id)
                        }
                    }

                    // Current-time playhead line over the grid
                    Canvas(Modifier.matchParentSize()) {
                        val x = (channelCol + pxPerMin * (state.nowMin - gridStart)).toPx()
                        drawLine(
                            color = BbAccent,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
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
    requestInitialFocus: Boolean,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit
) {
    val channelFocusRequester = remember { FocusRequester() }
    var channelFocused by remember { mutableStateOf(false) }

    // Focus the last-played channel row once it is composed
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            delay(150)
            runCatching { channelFocusRequester.requestFocus() }
        }
    }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        // ── Channel cell ──
        Row(
            modifier = Modifier
                .width(channelCol)
                .fillMaxHeight()
                .background(
                    if (isPreviewing) BbAccent.copy(alpha = 0.15f)
                    else BbCard.copy(alpha = 0.35f)
                )
                .then(
                    if (channelFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .clickable(onClick = onChannelClick)
                .focusRequester(channelFocusRequester)
                .focusable()
                .onFocusChanged { channelFocused = it.isFocused }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${index + 1}", color = BbTextMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
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

                // FIX: Dp must be the first operand (Dp * Int is valid, Int * Dp is not)
                val x = pxPerMin * (start - gridStart).coerceAtLeast(0)
                val w = pxPerMin * program.duration.coerceAtLeast(15)

                ProgramCell(
                    program = program,
                    modifier = Modifier
                        .offset(x = x)
                        .width(w)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 2.dp),
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
            .background(if (focused) BbAccent.copy(alpha = 0.25f) else BbCard.copy(alpha = 0.6f))
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
// TOP BAR CONTROLS (same pattern as Live TV)
// =====================================================================
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
        Icon(imageVector = icon, contentDescription = contentDesc, tint = if (isFocused) BbAccent else BbTextSecondary)
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
