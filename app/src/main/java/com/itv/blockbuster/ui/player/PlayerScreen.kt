package com.itv.blockbuster.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbTextSecondary
import kotlinx.coroutines.delay

// =====================================================================
// HELPERS
// =====================================================================

private object PlayerHelpers {
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
    // FIX: replaces the old `countdown: Int?` popup state - see the
    // LaunchedEffect(ended) block below for the full behavior change
    // (movies/no-next-episode/setting-off close the player automatically;
    // series with a next episode and the setting on show the Netflix-style
    // corner overlay below instead of a modal popup).
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // NEW (player-open latency): replaces the old pre-navigation
    // StreamValidator.isReachable() gate in AppNavigation - navigation here
    // is now immediate, so this screen owns showing "connecting" while
    // ExoPlayer buffers, and surfacing failure itself if the stream
    // actually turns out to be bad (rather than a separate network probe
    // deciding that before the player ever opened).
    var isConnecting by remember { mutableStateOf(true) }
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()

    val rewindMs by viewModel.rewindMs.collectAsState()
    val forwardMs by viewModel.forwardMs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    // ── NEW: VOD touch gesture state (double-tap seek, swipe brightness/volume) ──
    // and the translucent title overlay (Movie/Series name + S#E# + episode name).
    // topOverlayVisible doubles as "is the native player controller visible" - see
    // the ControllerVisibilityListener wired up on the PlayerView below, which keeps
    // this in sync (including the controller's own auto-hide timeout) so our custom
    // gestures only run while the native controller/seek-bar/buttons are hidden and
    // never fight them for touches.
    var titleInfo by remember { mutableStateOf(PlayerTitleInfo.from(playbackManager)) }
    var topOverlayVisible by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var seekToken by remember { mutableStateOf(0) }
    var dragIndicatorType by remember { mutableStateOf<DragIndicatorType?>(null) }
    val brightnessController = rememberBrightnessController(activity)
    val volumeController = rememberVolumeController()

    // Auto-hide the double-tap seek pill shortly after each tap.
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

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

    // Playback setup + orientation lock + cleanup.
    // The resume position was already resolved in VodDetailViewModel at
    // play-click time and stored in playbackManager.pendingSeekMs.
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        playbackManager.isFullscreenActive = true

        // Add the listener BEFORE play() so STATE_READY is not missed.
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ended = (state == Player.STATE_ENDED)
                // Apply the pending resume seek once the media is ready.
                if (state == Player.STATE_READY) {
                    isConnecting = false
                    val seekTo = playbackManager.pendingSeekMs
                    if (seekTo > 0) {
                        playbackManager.pendingSeekMs = -1L
                        player.seekTo(seekTo)
                        player.playWhenReady = true
                        player.play()
                    }
                }
            }

            // NEW: this is what replaces the old pre-navigation reachability
            // check's failure path. A bad/unreachable stream now surfaces
            // here, as a genuine ExoPlayer error, instead of being caught
            // ahead of time by a separate network probe - same user-facing
            // result (toast + back out), paid for only on the failure path
            // instead of on every playback attempt.
            override fun onPlayerError(error: PlaybackException) {
                isConnecting = false
                Toast.makeText(context, "Stream Not Available", Toast.LENGTH_LONG).show()
                onBack()
            }
        }
        player.addListener(listener)

        playbackManager.play(streamUrl)

        onDispose {
            viewModel.saveCurrentProgress()
            player.removeListener(listener)
            playbackManager.isFullscreenActive = false
            player.clearVideoSurface()

            // FIX: If the activity is being recreated (orientation flip, which always
            // happens when entering/exiting fullscreen in portrait), keep the media,
            // the playback and the keepLive flag intact — the player is re-attached
            // after recreation. Consuming keepLive here was killing portrait hand-back.
            val recreating = (context as? Activity)?.isChangingConfigurations == true
            if (!recreating) {
                // If this live session was launched from the TV Guide PIP, keep the
                // stream playing on exit so the PIP resumes seamlessly.
                // NOTE: keepLivePlayingOnExit is NOT consumed here anymore; the guide
                // clears it in resumePreviewIfNeeded() when it (re)enters composition.
                val keepLive = isLive && playbackManager.keepLivePlayingOnExit
                // playbackManager.keepLivePlayingOnExit = false
                if (!keepLive) {
                    player.stop()
                    player.clearMediaItems()
                    playbackManager.clearLiveContext()
                    playbackManager.pendingSeekMs = -1L
                }
                activity?.requestedOrientation =
                    originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Periodic progress persistence (VOD only)
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (!isLive && player.isPlaying) viewModel.saveCurrentProgress()
        }
    }

    // Playback-end handling.
    //
    // FIX ("irrespective of Play or Cancel, the same episode repeats / if I
    // don't answer it continues to the next episode anyway"): the OLD
    // version ran its whole 5-second countdown loop INSIDE this
    // LaunchedEffect(ended) coroutine, with the "Play Now"/"Cancel" buttons
    // only mutating the separate `countdown` UI state from the OUTSIDE.
    // Neither button actually STOPPED this coroutine - it kept counting
    // down in the background regardless, so a few seconds after either
    // button was pressed, this same coroutine would finish its loop anyway
    // and call viewModel.playNextEpisode() a SECOND time on top of
    // whatever had already happened - which is what looked like "the
    // episode restarts/repeats no matter what you press".
    //
    // NEW behavior (per spec):
    //  - Movies (no next item queued): close the player automatically.
    //  - Series, "Auto Play Next Episode" OFF: close the player automatically.
    //  - Series, "Auto Play Next Episode" ON, and there IS a next episode:
    //    show the corner overlay below (which owns its OWN 7-second timer
    //    and both of its actions) instead of auto-playing or closing here.
    LaunchedEffect(ended) {
        if (ended && !isLive) {
            val hasNext = playbackManager.nextInQueue() != null
            if (hasNext && autoPlayNext) {
                showNextEpisodeOverlay = true
            } else {
                onBack()
            }
        } else {
            showNextEpisodeOverlay = false
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
                                // FIX: was missing showController() - D-pad
                                // seeking silently moved the playhead with
                                // no visible seek bar at all (only the
                                // separate double-tap SeekFeedbackOverlay
                                // pill showed anything, and only for touch).
                                // showController() both displays the native
                                // seek bar AND resets its own auto-hide
                                // timer (controllerShowTimeoutMs) on every
                                // press, so it stays up for as long as the
                                // user keeps seeking and only hides once
                                // seeking has actually stopped.
                                playerViewRef?.showController()
                                player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0))
                                true
                            } else { bannerVisible = true; true }
                        }
                        Key.DirectionRight -> {
                            if (!isLive) {
                                playerViewRef?.showController()
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
                    // NEW: mirror the native controller's own show/hide state (including
                    // its auto-hide timeout) into topOverlayVisible, so our custom touch
                    // gestures below re-enable themselves the instant the controller
                    // hides, and disable themselves the instant it's shown - avoiding any
                    // conflict with tapping its buttons or dragging its seek bar.
                    view.setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            topOverlayVisible = visibility == View.VISIBLE
                        }
                    )
                    view.hideController()
                    view.controllerShowTimeoutMs = 5000
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // NEW: shown while ExoPlayer is still buffering/preparing, since
        // navigating here no longer waits on a pre-flight reachability
        // check first (see AppNavigation's navigateToPlayerIfReachable).
        // Without this, a bad stream would sit on the plain black
        // background above for the ~few seconds ExoPlayer takes to give up
        // and report onPlayerError, with nothing telling the user anything
        // is happening.
        if (isConnecting) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BbAccent)
                    Spacer(Modifier.size(12.dp))
                    Text("Connecting...", color = BbTextSecondary, fontSize = 14.sp)
                }
            }
        }

        // ── VOD TOUCH GESTURES ──
        // Double-tap left/right half to rewind/forward, vertical swipe on the left
        // half for brightness and the right half for volume. Disabled while the
        // native controller is visible (see the listener above) so we never steal
        // touches from its buttons/seek bar, and disabled entirely for Live TV
        // (which keeps its existing remote-first banner/zap controls).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .vodTouchGestures(
                    enabled = !isLive && !topOverlayVisible,
                    player = player,
                    rewindMs = rewindMs,
                    forwardMs = forwardMs,
                    brightnessController = brightnessController,
                    volumeController = volumeController,
                    onSeekFeedback = { direction, amount ->
                        // REVERTED: showController() here was fighting the
                        // gesture layer itself - vodTouchGestures is
                        // deliberately disabled while the native controller
                        // is visible (enabled = !isLive && !topOverlayVisible,
                        // above) so touches don't get stolen from its
                        // buttons/seek bar. Calling showController() on
                        // every double-tap immediately re-disabled the
                        // gesture layer for the next several seconds,
                        // breaking a quick rewind/forward-again sequence.
                        // The custom SeekFeedbackOverlay pill below is the
                        // only feedback for touch seeking; D-pad seeking
                        // (Key.DirectionLeft/Right above) still shows the
                        // native seek bar, since that path has no
                        // competing gesture layer to fight.
                        seekToken++
                        seekFeedback = SeekFeedback(direction, amount, seekToken)
                    },
                    onDragIndicatorChange = { dragIndicatorType = it },
                    onSingleTap = { playerViewRef?.showController() }
                )
        )

        // ── TITLE OVERLAY (VOD only): Movie/Series name + S#E# + episode subtitle ──
        if (!isLive) {
            PlayerTitleOverlay(
                info = titleInfo,
                visible = topOverlayVisible,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            SeekFeedbackOverlay(feedback = seekFeedback, modifier = Modifier.fillMaxSize())
            DragIndicatorOverlay(
                type = dragIndicatorType,
                level = when (dragIndicatorType) {
                    DragIndicatorType.BRIGHTNESS -> brightnessController.level
                    DragIndicatorType.VOLUME -> volumeController.level
                    null -> 0f
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── LIVE INFO BANNER ──
        if (isLive && bannerVisible && banner != null) {
            LiveBannerOverlay(
                banner = banner!!,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── NEXT EPISODE OVERLAY ──
        // FIX: replaces the old bottom-CENTER modal-style "Next episode in
        // Ns / Play Now / Cancel" popup with a Netflix-style bottom-RIGHT
        // corner button per spec. See NextEpisodeOverlay's own doc comment
        // for the timer/focus/cancel behavior.
        if (showNextEpisodeOverlay) {
            NextEpisodeOverlay(
                onPlayNext = {
                    showNextEpisodeOverlay = false
                    ended = false
                    viewModel.playNextEpisode()
                    titleInfo = PlayerTitleInfo.from(playbackManager)
                },
                // Spec: choosing Cancel closes the player, same as Back -
                // this is NOT "dismiss the overlay and keep watching the
                // ended screen" (there's nothing to watch - the episode has
                // already finished).
                onCancel = onBack,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

// =====================================================================
// NEXT EPISODE OVERLAY (Netflix-style corner prompt)
// =====================================================================

/**
 * Bottom-right "Next Episode" prompt shown when a series episode finishes
 * with "Auto Play Next Episode" on and another episode queued. Owns its own
 * 7-second countdown entirely internally - firing [onPlayNext] itself once
 * it reaches zero - so there's exactly one place that can trigger the next
 * episode, unlike the old popup where the countdown lived in one coroutine
 * and the buttons lived in another with no way to actually cancel it (see
 * the FIX comment on PlayerScreen's LaunchedEffect(ended)).
 *
 * FIX: was a single Row with both buttons sharing one BbSurface card
 * background, the "Next Episode" button styled as a solid accent-colored
 * Material Button with no visible countdown, and the episode's name printed
 * under the label. Per the latest request: back to a solid white filled
 * button (dark icon/text for contrast) with a circular countdown ring
 * around the play icon - Netflix-style - firing automatically at zero; no
 * episode name. The Cancel icon stays a separate, unfilled control to its
 * left rather than sharing the white button's background.
 */
@Composable
private fun NextEpisodeOverlay(
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    totalSeconds: Int = 7
) {
    var secondsLeft by remember { mutableStateOf(totalSeconds) }
    val nextButtonFocusRequester = remember { FocusRequester() }
    var nextFocused by remember { mutableStateOf(false) }

    // Spec: "it should claim focus" - grabs D-pad focus the moment this
    // overlay appears, so a remote user can just press Center/Enter (or
    // wait out the timer) without having to navigate to it first.
    LaunchedEffect(Unit) {
        runCatching { nextButtonFocusRequester.requestFocus() }
    }

    // The countdown itself: ticks once a second, firing onPlayNext exactly
    // once when it reaches zero. Restarting this screen's whole composable
    // (leaving and re-entering) is the only way this timer resets - normal
    // recomposition (e.g. from unrelated state elsewhere in the player)
    // does not restart it, since `Unit` never changes.
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        onPlayNext()
    }

    // FIX: was 24dp bottom padding on a shared Row - sat right on top of
    // the native controller's seek bar/time labels whenever they were
    // showing. 96dp clears that control bar with room to spare.
    Row(
        modifier = modifier.padding(bottom = 96.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spec: "on the left of it, there should be cancel (undo) icon" -
        // its own standalone icon button, no shared card background.
        // Spec: choosing Cancel closes the player entirely (onCancel ==
        // onBack from the caller), not just dismisses this overlay.
        IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.Undo,
                contentDescription = "Cancel",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(20.dp))
        // Spec: "white button with countdown timer of 7 seconds (netflix
        // style)". A solid white filled button (dark content for contrast)
        // with a small circular countdown ring around the play icon that
        // depletes as secondsLeft counts down - focus is shown via an
        // accent-colored border, since the button itself is always
        // solid/opaque.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .then(
                    if (nextFocused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .focusRequester(nextButtonFocusRequester)
                .focusable()
                .onFocusChanged { nextFocused = it.isFocused }
                .clickable(onClick = onPlayNext)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { secondsLeft.toFloat() / totalSeconds.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                    trackColor = Color.Black.copy(alpha = 0.2f),
                    strokeWidth = 2.dp
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Next Episode",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
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