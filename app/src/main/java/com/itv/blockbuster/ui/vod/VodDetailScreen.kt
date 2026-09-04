package com.itv.blockbuster.ui.vod

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
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
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun VodDetailScreen(
    onPlay: (String) -> Unit,
    onOpenEpisodes: () -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isPortrait = rememberFormFactor() == FormFactor.MOBILE_PORTRAIT
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
            if (isPortrait) {
                PortraitLayout(
                    item = item,
                    state = state,
                    onPlayEpisode = { viewModel.playEpisode(it, onPlay) },
                    onPlayMovie = { viewModel.playMovie(onPlay) },
                    onRestartMovie = { viewModel.playMovieFromBeginning(onPlay) },
                    onRestartEpisode = { viewModel.playEpisodeFromBeginning(it, onPlay) },
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onToggleSort = { viewModel.toggleEpisodeSort() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                )
            } else {
                LandscapeLayout(
                    item = item,
                    state = state,
                    onPlayFirst = {
                        if (state.hasSeasons) {
                            state.episodes.firstOrNull()?.let { viewModel.playEpisode(it, onPlay) }
                        } else {
                            viewModel.playMovie(onPlay)
                        }
                    },
                    onRestart = {
                        if (state.hasSeasons) {
                            state.episodes.firstOrNull()?.let { viewModel.playEpisodeFromBeginning(it, onPlay) }
                        } else {
                            viewModel.playMovieFromBeginning(onPlay)
                        }
                    },
                    onMoreEpisodes = onOpenEpisodes,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                )
            }
        }
    }
}

// =====================================================================
// PORTRAIT (Mobile)
// =====================================================================
@Composable
private fun PortraitLayout(
    item: PortalVodItem,
    state: VodDetailState,
    onPlayEpisode: (PortalVodItem) -> Unit,
    onPlayMovie: () -> Unit,
    onRestartMovie: () -> Unit,
    onRestartEpisode: (PortalVodItem) -> Unit,
    onSelectSeason: (PortalVodItem) -> Unit,
    onToggleSort: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp).background(BbCard),
            contentAlignment = Alignment.Center,
        ) {
            if (item.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, BbBackground)))
            )
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            if (state.hasSeasons) {
                                state.episodes.firstOrNull()?.let { onPlayEpisode(it) }
                            } else {
                                onPlayMovie()
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(item.name, color = BbTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.year.isNotBlank()) Text(item.year, color = BbTextSecondary, fontSize = 14.sp)
                    if (state.hasSeasons) {
                        Text("•", color = BbTextMuted)
                        Text("${state.seasons.size} Seasons", color = BbTextSecondary, fontSize = 14.sp)
                    }
                    if (item.ratingImdb.isNotBlank()) {
                        Text("•", color = BbTextMuted)
                        Box(
                            modifier =
                                Modifier.border(1.dp, BbTextMuted, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("IMDb", color = BbTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            item.ratingImdb,
                            color = BbTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (item.duration.isNotBlank()) {
                        Text("•", color = BbTextMuted)
                        Text(item.duration, color = BbTextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (
                    !state.hasSeasons && state.movieProgress != null && state.movieProgress.durationMs > 0
                ) {
                    val ratio =
                        (state.movieProgress.positionMs.toFloat() / state.movieProgress.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = BbAccent,
                        trackColor = BbCard,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val hasProgress =
                        if (state.hasSeasons) {
                            state.episodeProgressMap.values.any {
                                it.positionMs > 5000 && it.positionMs < it.durationMs - 5000
                            }
                        } else {
                            state.movieProgress != null && state.movieProgress.positionMs > 5000
                        }
                    if (hasProgress) {
                        IconButton(
                            onClick = {
                                if (state.hasSeasons) {
                                    val ep = state.episodes.firstOrNull()
                                    if (ep != null) onRestartEpisode(ep)
                                } else {
                                    onRestartMovie()
                                }
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Replay,
                                "Restart",
                                tint = BbTextPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            "Favorite",
                            tint = if (state.isFavorite) BbAccent else BbTextPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (item.description.isNotBlank()) {
                    Text(item.description, color = BbTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                }
                if (item.genres.isNotBlank())
                    Text("Genres:  ${item.genres}", color = BbTextSecondary, fontSize = 13.sp)
                if (item.director.isNotBlank())
                    Text("Director:  ${item.director}", color = BbTextSecondary, fontSize = 13.sp)
                if (item.actors.isNotBlank())
                    Text("Cast:  ${item.actors}", color = BbTextSecondary, fontSize = 13.sp)
            }

            if (state.hasSeasons) {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Episodes",
                        color = BbTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactSeasonDropdown(
                                seasons = state.seasons,
                                selected = state.selectedSeason,
                                onSelect = onSelectSeason,
                            )
                        }
                        IconButton(
                            onClick = onToggleSort,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                if (state.episodeSortAscending) Icons.Default.ArrowUpward
                                else Icons.Default.ArrowDownward,
                                contentDescription =
                                    if (state.episodeSortAscending) "Sort 3-2-1" else "Sort 1-2-3",
                                tint = BbTextPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    state.episodes.forEach { episode ->
                        // FIX: Use composite key for lookup
                        val key = EpisodeProgressKey(item.id, state.selectedSeason?.id ?: "", episode.id)
                        EpisodeCard(
                            episode = episode,
                            seasonNumber = state.selectedSeason?.seasonNumber ?: "",
                            progress = state.episodeProgressMap[key],
                            onPlay = { onPlayEpisode(episode) },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// =====================================================================
// LANDSCAPE (TV)
// =====================================================================
@Composable
private fun LandscapeLayout(
    item: PortalVodItem,
    state: VodDetailState,
    onPlayFirst: () -> Unit,
    onRestart: () -> Unit,
    onMoreEpisodes: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (item.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                BbBackground,
                                BbBackground.copy(alpha = 0.95f),
                                BbBackground.copy(alpha = 0.7f),
                                Color.Transparent,
                            ),
                            startX = 0f,
                            endX = 1200f,
                        )
                    )
        )
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BbBackground.copy(alpha = 0.9f))
                        )
                    )
        )

        Column(
            modifier =
                Modifier.fillMaxWidth(0.55f)
                    .fillMaxHeight()
                    .padding(start = 48.dp, end = 24.dp, top = 48.dp, bottom = 120.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            Text(item.name, color = BbTextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.year.isNotBlank()) Text(item.year, color = BbTextSecondary, fontSize = 15.sp)
                if (state.hasSeasons) {
                    Text("•", color = BbTextMuted)
                    Text("${state.seasons.size} Seasons", color = BbTextSecondary, fontSize = 15.sp)
                }
                if (item.ratingImdb.isNotBlank()) {
                    Text("•", color = BbTextMuted)
                    Box(
                        modifier =
                            Modifier.border(1.dp, BbTextMuted, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("IMDb", color = BbTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        item.ratingImdb,
                        color = BbTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (item.ratingMpaa.isNotBlank()) {
                    Text("•", color = BbTextMuted)
                    Text(item.ratingMpaa, color = BbTextSecondary, fontSize = 15.sp)
                }
                if (item.duration.isNotBlank()) {
                    Text("•", color = BbTextMuted)
                    Text(item.duration, color = BbTextSecondary, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (item.description.isNotBlank()) {
                Text(item.description, color = BbTextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(20.dp))
            if (item.genres.isNotBlank()) {
                Text("Genres:  ${item.genres}", color = BbTextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
            }
            if (item.director.isNotBlank()) {
                Text("Director:  ${item.director}", color = BbTextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
            }
            if (item.actors.isNotBlank()) {
                Text("Cast:  ${item.actors}", color = BbTextSecondary, fontSize = 14.sp)
            }
        }

        val hasAnyProgress = computeHasAnyProgress(state)
        Column(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 48.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val playLabel = buildPlayLabel(state)
            val progressRatio = computePlayProgressRatio(state)
            PlayButtonWithProgress(
                label = playLabel,
                progressRatio = progressRatio,
                onClick = onPlayFirst,
            )
            if (hasAnyProgress) {
                DetailActionButton(
                    icon = Icons.Default.Replay,
                    label = "Restart from Beginning",
                    primary = false,
                    onClick = onRestart,
                )
            }
            if (state.hasSeasons) {
                DetailActionButton(
                    icon = Icons.Default.ListAlt,
                    label = "More Episodes",
                    primary = false,
                    onClick = onMoreEpisodes,
                )
            }
            DetailActionButton(
                icon = if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                label = if (state.isFavorite) "In Favorites" else "Add to Favorites",
                primary = false,
                onClick = onToggleFavorite,
            )
        }
    }
}

// =====================================================================
// PLAY BUTTON WITH EMBEDDED PROGRESS (Landscape)
// =====================================================================
@Composable
private fun PlayButtonWithProgress(
    label: String,
    progressRatio: Float,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(340.dp).focusable().onFocusChanged { focused = it.isFocused }) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = BbAccent),
            modifier =
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .then(
                        if (focused)
                            Modifier.border(
                                2.dp,
                                Color.White,
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                            )
                        else Modifier
                    ),
            shape =
                RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp,
                ),
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (progressRatio > 0f) {
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = BbAccent,
                trackColor = BbCard,
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

// =====================================================================
// HELPERS
// =====================================================================
@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = BbAccent),
            modifier =
                Modifier.width(340.dp)
                    .height(48.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier =
                Modifier.width(340.dp)
                    .height(48.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BbTextPrimary),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CompactSeasonDropdown(
    seasons: List<PortalVodItem>,
    selected: PortalVodItem?,
    onSelect: (PortalVodItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BbSurface)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label =
                selected?.seasonNumber?.let {
                    if (it.isNotBlank()) "Season $it" else selected.name
                } ?: "Select Season"
            Text(
                label,
                color = BbTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, null, tint = BbTextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEachIndexed { index, season ->
                val display =
                    season.seasonNumber.let {
                        if (it.isNotBlank()) "Season $it" else "Season ${index + 1}"
                    }
                DropdownMenuItem(
                    text = {
                        Text(
                            display,
                            color = if (season.id == selected?.id) BbAccent else BbTextPrimary,
                        )
                    },
                    onClick = {
                        onSelect(season)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: PortalVodItem,
    seasonNumber: String,
    progress: PlaybackProgressEntity?,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progressRatio =
        if (progress != null && progress.durationMs > 0) {
            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (focused)
                        Modifier.border(2.dp, BbAccent, RoundedCornerShape(10.dp))
                            .background(BbCard.copy(alpha = 0.4f))
                    else Modifier
                )
                .clickable(onClick = onPlay)
                .focusable()
                .onFocusChanged { focused = it.isFocused }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier.width(140.dp).height(78.dp).clip(RoundedCornerShape(8.dp)).background(BbCard)
            ) {
                if (episode.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = episode.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "S${seasonNumber}:E${episode.episodeNumber}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Progress bar embedded inside thumbnail
                if (progressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = BbAccent,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${episode.episodeNumber}. ${episode.name}",
                    color = BbTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (episode.description.isNotBlank()) {
                    Text(
                        episode.description,
                        color = BbTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    if (episode.duration.isNotBlank()) add("${episode.duration}m")
                    val date = formatAddedDate(episode.addedDate)
                    if (date.isNotEmpty()) add(date)
                }
                    .joinToString(", ")
                if (meta.isNotEmpty()) Text("($meta)", color = BbTextMuted, fontSize = 12.sp)
            }
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

private fun buildPlayLabel(state: VodDetailState): String {
    if (state.hasSeasons) {
        val ep = state.episodes.firstOrNull() ?: return "Play Episode"
        val movieId = state.item?.id ?: ""
        val seasonId = state.selectedSeason?.id ?: ""
        // FIX: Use composite key for lookup
        val key = EpisodeProgressKey(movieId, seasonId, ep.id)
        val progress = state.episodeProgressMap[key]
        val hasProgress =
            progress != null &&
                    progress.positionMs > 5000 &&
                    progress.positionMs < progress.durationMs - 5000
        val prefix = if (hasProgress) "Resume" else "Play"
        return if (ep.seasonNumber.isNotBlank() && ep.episodeNumber.isNotBlank()) {
            "$prefix Episode S${ep.seasonNumber}E${ep.episodeNumber}"
        } else {
            "$prefix Episode"
        }
    }
    val progress = state.movieProgress
    val hasProgress =
        progress != null &&
                progress.positionMs > 5000 &&
                progress.positionMs < progress.durationMs - 5000
    return if (hasProgress) "Resume Movie" else "Play Movie"
}

private fun computeHasAnyProgress(state: VodDetailState): Boolean {
    if (!state.hasSeasons) {
        return state.movieProgress != null && state.movieProgress.positionMs > 5000
    }
    return state.episodeProgressMap.values.any {
        it.positionMs > 5000 && it.positionMs < it.durationMs - 5000
    }
}

private fun computePlayProgressRatio(state: VodDetailState): Float {
    if (state.hasSeasons) {
        val ep = state.episodes.firstOrNull() ?: return 0f
        val movieId = state.item?.id ?: ""
        val seasonId = state.selectedSeason?.id ?: ""
        // FIX: Use composite key for lookup
        val key = EpisodeProgressKey(movieId, seasonId, ep.id)
        val progress = state.episodeProgressMap[key] ?: return 0f
        if (progress.durationMs <= 0) return 0f
        return (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    }
    val progress = state.movieProgress ?: return 0f
    if (progress.durationMs <= 0) return 0f
    return (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
}
