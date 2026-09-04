package com.itv.blockbuster.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import kotlinx.coroutines.delay

// =====================================================================
// HELPERS
// =====================================================================

private object PlayerHelpers {

    fun isNearlyFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        val remaining = durationMs - positionMs
        if (remaining <= 0) return true
        val durationMinutes = durationMs / 60_000.0
        return if (durationMinutes <= 70.0) remaining < 60_000L else remaining < 180_000L
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }
}

// =====================================================================
// MAIN SCREEN
// =====================================================================

@Composable
fun PlayerScreen(
    streamUrl: String,
    channelId: String,
    videoId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {

    val playbackManager = viewModel.playbackManager
    val player = playbackManager.player
    val isLive = channelId != "none"

    val focusRequester = remember { FocusRequester() }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var bannerVisible by remember { mutableStateOf(false) }
    val banner by viewModel.liveBanner.collectAsState()
    var resumeProgress by remember { mutableStateOf<PlaybackProgressEntity?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var ended by remember { mutableStateOf(false) }
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()

    val rewindMs by viewModel.rewindMs.collectAsState()
    val forwardMs by viewModel.forwardMs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Live: load channel context + show banner on entry
    LaunchedEffect(channelId) {
        if (isLive) {
            viewModel.initLive(channelId)
            bannerVisible = true
        }
    }

    // Banner auto-hide
    LaunchedEffect(bannerVisible) {
        if (bannerVisible) {
            delay(5000)
            bannerVisible = false
        }
    }

    // VOD: resume prompt
    // FIX: Use playbackManager.currentVideoId instead of navigation videoId param.
    // The navigation route passes "none" as videoId, so we must use the
    // PlaybackManager value which was set by VodDetailViewModel before navigation.
    LaunchedEffect(videoId) {
        if (!isLive && !playbackManager.restartFromBeginning) {
            val actualVideoId = playbackManager.currentVideoId
            if (actualVideoId.isNotEmpty()) {
                val progress = viewModel.getProgress(actualVideoId)
                if (progress != null &&
                    progress.positionMs > 5000 &&
                    !PlayerHelpers.isNearlyFinished(progress.positionMs, progress.durationMs)
                ) {
                    player.pause()
                    resumeProgress = progress
                }
            }
        }
        playbackManager.restartFromBeginning = false
    }

    // Playback setup + orientation lock + cleanup
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        playbackManager.isFullscreenActive = true
        playbackManager.play(streamUrl)

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ended = (state == Player.STATE_ENDED)
            }
        }
        player.addListener(listener)

        onDispose {
            viewModel.saveCurrentProgress()
            player.removeListener(listener)
            playbackManager.isFullscreenActive = false
            player.clearVideoSurface()
            player.stop()
            player.clearMediaItems()
            playbackManager.clearLiveContext()
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Periodic progress persistence (VOD only)
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (!isLive && player.isPlaying) viewModel.saveCurrentProgress()
        }
    }

    // Next-episode countdown on end
    LaunchedEffect(ended) {
        if (ended && !isLive && autoPlayNext && playbackManager.nextInQueue() != null) {
            for (i in 5 downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = null
            ended = false
            viewModel.playNextEpisode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event: KeyEvent ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (isLive) { viewModel.zap(-1); bannerVisible = true; true } else false
                        }
                        Key.DirectionDown -> {
                            if (isLive) { viewModel.zap(1); bannerVisible = true; true } else false
                        }
                        Key.DirectionLeft -> {
                            if (!isLive) {
                                player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0))
                                true
                            } else { bannerVisible = true; true }
                        }
                        Key.DirectionRight -> {
                            if (!isLive) {
                                player.seekTo((player.currentPosition + forwardMs).coerceAtMost(player.duration))
                                true
                            } else { bannerVisible = true; true }
                        }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                            if (isLive) {
                                bannerVisible = !bannerVisible
                            } else {
                                playerViewRef?.showController()
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            true
                        }
                        Key.Back -> { onBack(); true }
                        Key.MediaPlay -> { player.play(); true }
                        Key.MediaPause -> { player.pause(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also { view ->
                    playerViewRef = view
                    view.player = playbackManager.player
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.useController = !isLive
                    view.keepScreenOn = true
                    view.hideController()
                    view.controllerShowTimeoutMs = 5000
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── LIVE INFO BANNER ──
        if (isLive && bannerVisible && banner != null) {
            LiveBannerOverlay(
                banner = banner!!,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }


        // ── RESUME DIALOG (VOD) ──
        resumeProgress?.let { progress ->
            ResumeDialog(
                progress = progress,
                onResume = {
                    player.seekTo(progress.positionMs)
                    player.play()
                    resumeProgress = null
                },
                onStartOver = {
                    player.seekTo(0)
                    player.play()
                    resumeProgress = null
                },
                onDismiss = {
                    player.play()
                    resumeProgress = null
                }
            )
        }

        // ── NEXT EPISODE COUNTDOWN ──
        countdown?.let { seconds ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BbSurface.copy(alpha = 0.95f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            "Next episode in $seconds s",
                            color = BbTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            playbackManager.nextInQueue()?.name ?: "",
                            color = BbTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            countdown = null
                            viewModel.playNextEpisode()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BbAccent)
                    ) { Text("Play Now") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { countdown = null }) {
                        Text("Cancel", color = BbTextSecondary)
                    }
                }
            }
        }
    }
}

// =====================================================================
// LIVE BANNER OVERLAY
// =====================================================================

@Composable
private fun LiveBannerOverlay(
    banner: LiveBannerData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (banner.channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = banner.channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    banner.channel.number.ifEmpty { banner.channel.id.take(4) },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                banner.channel.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (banner.now != null) {
                Text(
                    "Now: ${banner.now.name}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (banner.next != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Next:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text(
                    banner.next.name,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.KeyboardArrowUp, "Channel up", tint = BbAccent, modifier = Modifier.size(20.dp))
            Icon(Icons.Default.KeyboardArrowDown, "Channel down", tint = BbAccent, modifier = Modifier.size(20.dp))
        }
    }
}

// =====================================================================
// RESUME DIALOG (VOD)
// =====================================================================

@Composable
private fun ResumeDialog(
    progress: PlaybackProgressEntity,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BbSurface,
        title = { Text("Resume playback?", color = BbTextPrimary) },
        text = {
            Text(
                "You stopped at ${PlayerHelpers.formatTime(progress.positionMs)}. " +
                        "Resume from there or start from the beginning?",
                color = BbTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = BbAccent)
            ) {
                Text("Resume from ${PlayerHelpers.formatTime(progress.positionMs)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text("Play from beginning", color = BbTextSecondary)
            }
        }
    )
}