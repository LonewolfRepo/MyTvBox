package com.itv.blockbuster.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

@Composable
fun VodDetailScreen(
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // PRESERVED: refresh progress when returning from the player
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = BbAccent,
                modifier = Modifier.align(Alignment.Center)
            )
            state.item == null -> Text(
                "Item not found",
                color = BbTextMuted,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> {
                val item = state.item!!

                if (item.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.2f
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Poster
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BbCard)
                        ) {
                            if (item.logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = item.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(Modifier.width(24.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = BbTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (item.year.isNotEmpty()) Text(item.year, color = BbTextSecondary)
                                if (item.duration.isNotEmpty()) Text(item.duration, color = BbTextSecondary)
                                if (item.ratingImdb.isNotEmpty()) {
                                    Text("IMDb ${item.ratingImdb}", color = BbAccent, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // RESTORED: Play/Resume wired to real stream link generation
                                val canResume = state.movieProgress != null &&
                                        state.movieProgress!!.positionMs > 5000
                                Button(
                                    onClick = { viewModel.playMovie(onPlay) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BbAccent)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (canResume) "Resume" else "Play")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.toggleFavorite() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (state.isFavorite) BbAccent else BbTextSecondary
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = SolidColor(if (state.isFavorite) BbAccent else BbTextMuted)
                                    )
                                ) {
                                    Icon(
                                        if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.isFavorite) "Favorited" else "Favorite")
                                }
                            }
                        }
                    }

                    if (item.description.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text("Description", color = BbTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(item.description, color = BbTextSecondary, fontSize = 14.sp)
                    }

                    // PRESERVED: Seasons & Episodes sections
                    if (state.hasSeasons) {
                        Spacer(Modifier.height(24.dp))
                        Text("Seasons", color = BbTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.seasons.forEach { season ->
                                val selected = state.selectedSeason?.id == season.id
                                OutlinedButton(
                                    onClick = { viewModel.selectSeason(season) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) BbAccent else BbTextSecondary
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = SolidColor(if (selected) BbAccent else BbTextMuted)
                                    )
                                ) { Text(season.name) }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("Episodes", color = BbTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        state.episodes.forEach { episode ->
                            EpisodeRow(
                                episode = episode,
                                seasonNumber = state.selectedSeason?.seasonNumber ?: "",
                                progress = state.episodeProgressMap[episode.id],
                                onPlay = { viewModel.playEpisode(episode, onPlay) }
                            )
                            HorizontalDivider(color = BbCard, thickness = 0.5.dp)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PortalVodItem,
    seasonNumber: String,
    progress: PlaybackProgressEntity?,
    onPlay: () -> Unit
) {
    val hasProgress = progress != null && progress.positionMs > 5000
    val progressRatio = if (progress != null && progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "S${seasonNumber.padStart(2, '0')}E${episode.episodeNumber.padStart(2, '0')}  " +
                        episode.name.ifEmpty { "Episode ${episode.episodeNumber}" },
                color = BbTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (hasProgress) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressRatio)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BbAccent)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = BbAccent)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (hasProgress) "Resume" else "Play", fontSize = 12.sp)
        }
    }
}