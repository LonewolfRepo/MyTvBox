package com.itv.blockbuster.ui.vod

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.domain.model.PortalVodItem
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
import java.util.Locale

@Composable
fun VodEpisodesScreen(
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isPortrait = rememberFormFactor() == FormFactor.MOBILE_PORTRAIT

    // D-pad focus: this screen has no rail (it's a NoRailRoutes screen, same
    // as VodDetailScreen/PlayerScreen/CatchupScreen), so it needs to claim
    // focus onto something itself rather than relying on a rail handoff.
    // FIX: there was previously NO initial-focus logic here at all - a
    // D-pad user landing on this screen could find nothing focused until
    // they pressed a direction key. Uses the exact same
    // FocusRequester+retry-loop shape as VodDetailScreen's own
    // playFocusRequester, but targets the SAME episode the Play/Resume
    // button on the detail page would play (state.playTarget.episode) -
    // i.e. arriving here already lands you on "continue watching" rather
    // than always the top of the list.
    val listState = rememberLazyListState()
    val targetEpisodeFocusRequester = remember { FocusRequester() }
    val targetEpisodeId = state.playTarget?.episode?.id
    LaunchedEffect(state.item != null, state.selectedSeason?.id, targetEpisodeId) {
        if (state.item == null || targetEpisodeId == null) return@LaunchedEffect
        // Give the freshly-composed layout a beat to lay out before
        // requesting, same as VodDetailScreen - and re-check the episode
        // list fresh on each attempt, since it may still be loading for
        // the target's season when this effect first starts.
        repeat(20) { attempt ->
            delay(50)
            val index = state.episodes.indexOfFirst { it.id == targetEpisodeId }
            if (index < 0) return@repeat
            runCatching { listState.scrollToItem(index) }
            if (targetEpisodeFocusRequester.runCatching { requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // FIX: Refresh playback progress whenever we return from the player.
    // Without this, episodeProgressMap stayed as loaded at first composition
    // and progress bars never updated after watching an episode.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        val item = state.item
        if (item == null) {
            CircularProgressIndicator(color = BbAccent, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with back button and title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = BbTextPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${item.name} — Episodes",
                        color = BbTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Persistent sort icon (top right)
                    IconButton(
                        onClick = { viewModel.toggleEpisodeSort() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            if (state.episodeSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (state.episodeSortAscending) "Sort 3-2-1" else "Sort 1-2-3",
                            tint = BbTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (isPortrait) {
                    PortraitEpisodes(
                        state = state,
                        listState = listState,
                        targetEpisodeId = targetEpisodeId,
                        targetEpisodeFocusRequester = targetEpisodeFocusRequester,
                        onSelectSeason = { viewModel.selectSeason(it) },
                        onPlay = { viewModel.playEpisode(it, onPlay) }
                    )
                } else {
                    LandscapeEpisodes(
                        state = state,
                        listState = listState,
                        targetEpisodeId = targetEpisodeId,
                        targetEpisodeFocusRequester = targetEpisodeFocusRequester,
                        onSelectSeason = { viewModel.selectSeason(it) },
                        onPlay = { viewModel.playEpisode(it, onPlay) }
                    )
                }
            }
        }
    }
}

// =====================================================================
// LANDSCAPE — Two-column: Seasons left, Episodes right
// =====================================================================
@Composable
private fun LandscapeEpisodes(
    state: VodDetailState,
    listState: LazyListState,
    targetEpisodeId: String?,
    targetEpisodeFocusRequester: FocusRequester,
    onSelectSeason: (PortalVodItem) -> Unit,
    onPlay: (PortalVodItem) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Seasons column (left)
        LazyColumn(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .padding(end = 12.dp)
        ) {
            item {
                Text(
                    "Seasons",
                    color = BbTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(state.seasons, key = { it.id }) { season ->
                SeasonRow(
                    season = season,
                    selected = season.id == state.selectedSeason?.id,
                    episodeCount = if (season.id == state.selectedSeason?.id) state.episodes.size else 0,
                    onSelect = { onSelectSeason(season) }
                )
            }
        }
        // Episodes column (right)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(0.68f).fillMaxHeight()
        ) {
            item {
                Text(
                    state.selectedSeason?.name ?: "Episodes",
                    color = BbTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(state.episodes, key = { it.id }) { episode ->
                EpisodeCard(
                    episode = episode,
                    seasonNumber = state.selectedSeason?.seasonNumber ?: "",
                    progress = state.episodeProgressMap[episode.id],
                    // D-pad focus: this is the episode state.playTarget
                    // points at - see VodEpisodesScreen's own doc comment.
                    focusRequester = if (episode.id == targetEpisodeId) targetEpisodeFocusRequester else null,
                    onPlay = { onPlay(episode) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// =====================================================================
// PORTRAIT — Dropdown + vertical list
// =====================================================================
@Composable
private fun PortraitEpisodes(
    state: VodDetailState,
    listState: LazyListState,
    targetEpisodeId: String?,
    targetEpisodeFocusRequester: FocusRequester,
    onSelectSeason: (PortalVodItem) -> Unit,
    onPlay: (PortalVodItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        // Season dropdown
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BbSurface)
                    .clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.selectedSeason?.name ?: "Select Season",
                    color = BbTextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.seasons.forEach { season ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                season.name,
                                color = if (season.id == state.selectedSeason?.id) BbAccent else BbTextPrimary
                            )
                        },
                        onClick = { onSelectSeason(season); expanded = false }
                    )
                }
            }
        }
        // Episodes list
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
        ) {
            items(state.episodes, key = { it.id }) { episode ->
                EpisodeCard(
                    episode = episode,
                    seasonNumber = state.selectedSeason?.seasonNumber ?: "",
                    progress = state.episodeProgressMap[episode.id],
                    focusRequester = if (episode.id == targetEpisodeId) targetEpisodeFocusRequester else null,
                    onPlay = { onPlay(episode) }
                )
            }
        }
    }
}

// =====================================================================
// REUSABLE ROWS
// =====================================================================
@Composable
private fun SeasonRow(
    season: PortalVodItem,
    selected: Boolean,
    episodeCount: Int,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> BbCard
                    selected -> BbAccent.copy(alpha = 0.15f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (selected || focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            season.name,
            color = if (selected) BbAccent else BbTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (episodeCount > 0) Text("$episodeCount Episodes", color = BbTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EpisodeCard(
    episode: PortalVodItem,
    seasonNumber: String,
    progress: PlaybackProgressEntity?,
    // D-pad focus: set only for the one episode state.playTarget points at
    // - see VodEpisodesScreen's own doc comment.
    focusRequester: FocusRequester? = null,
    onPlay: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val progressRatio = if (progress != null && progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(10.dp))
                    .background(BbCard.copy(alpha = 0.4f))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onPlay)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.width(150.dp).height(84.dp)
                .clip(RoundedCornerShape(8.dp)).background(BbCard)
        ) {
            if (episode.logoUrl.isNotEmpty()) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(episode.logoUrl) {
                        ImageRequest.Builder(context)
                            .data(episode.logoUrl)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Box(
                modifier = Modifier.align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "S${seasonNumber}:E${episode.episodeNumber}",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            // Progress bar embedded inside thumbnail
            if (progressRatio > 0f) {
                LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = BbAccent,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${episode.episodeNumber}. ${episode.name}",
                color = BbTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            if (episode.description.isNotBlank()) {
                Text(
                    episode.description, color = BbTextSecondary, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            val meta = buildList {
                if (episode.duration.isNotBlank()) add("${episode.duration}m")
                val date = formatAddedDate(episode.addedDate)
                if (date.isNotEmpty()) add(date)
            }.joinToString(", ")
            if (meta.isNotEmpty()) Text("($meta)", color = BbTextMuted, fontSize = 12.sp)
        }
    }
}

private fun formatAddedDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val parsed = input.parse(raw) ?: return raw
        output.format(parsed)
    } catch (e: Exception) {
        raw
    }
}