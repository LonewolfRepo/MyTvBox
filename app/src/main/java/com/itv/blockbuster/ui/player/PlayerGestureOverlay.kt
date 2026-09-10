package com.itv.blockbuster.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.itv.blockbuster.data.player.PlaybackManager
import kotlin.math.roundToInt

// =====================================================================
// TITLE OVERLAY DATA
// =====================================================================

/**
 * Snapshot of the currently-playing item's title info, read from PlaybackManager
 * at the moments playback starts/advances (PlaybackManager's fields are plain vars,
 * not observable state, so the caller re-derives this after every play/playNext call
 * to trigger recomposition).
 */
data class PlayerTitleInfo(
    val title: String,
    val seasonEpisodeLabel: String?, // e.g. "S2E12"
    val episodeName: String?         // subtitle - only present for series
) {
    companion object {
        fun from(playbackManager: PlaybackManager): PlayerTitleInfo {
            val isSeries = playbackManager.currentContentType == "series"
            val season = playbackManager.currentSeasonNumber
            val episode = playbackManager.currentEpisodeNumber
            val label = if (isSeries && (season.isNotBlank() || episode.isNotBlank())) {
                buildString {
                    if (season.isNotBlank()) append("S$season")
                    if (episode.isNotBlank()) append("E$episode")
                }
            } else null
            return PlayerTitleInfo(
                title = playbackManager.currentTitle,
                seasonEpisodeLabel = label,
                episodeName = if (isSeries) playbackManager.currentEpisodeName.ifBlank { null } else null
            )
        }
    }
}

/**
 * Translucent top overlay: Movie/Series name + "S2E12" on one line, episode name
 * (when it's a series) as a subtitle underneath.
 */
@Composable
fun PlayerTitleOverlay(
    info: PlayerTitleInfo,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = info.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (info.seasonEpisodeLabel != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = info.seasonEpisodeLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            if (info.episodeName != null) {
                Text(
                    text = info.episodeName,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// =====================================================================
// DOUBLE-TAP SEEK FEEDBACK
// =====================================================================

enum class SeekDirection { REWIND, FORWARD }

data class SeekFeedback(val direction: SeekDirection, val amountMs: Long, val token: Int)

private fun formatSeekSeconds(ms: Long): String = "${ms / 1000}s"

@Composable
fun SeekFeedbackOverlay(feedback: SeekFeedback?, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = feedback != null && feedback.direction == SeekDirection.REWIND,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            feedback?.let { SeekPill(icon = Icons.Default.FastRewind, label = "-${formatSeekSeconds(it.amountMs)}") }
        }
        AnimatedVisibility(
            visible = feedback != null && feedback.direction == SeekDirection.FORWARD,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            feedback?.let { SeekPill(icon = Icons.Default.FastForward, label = "+${formatSeekSeconds(it.amountMs)}") }
        }
    }
}

@Composable
private fun SeekPill(icon: ImageVector, label: String) {
    Column(
        modifier = Modifier
            .padding(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================
// SWIPE BRIGHTNESS / VOLUME FEEDBACK
// =====================================================================

enum class DragIndicatorType { BRIGHTNESS, VOLUME }

@Composable
fun DragIndicatorOverlay(type: DragIndicatorType?, level: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = type == DragIndicatorType.BRIGHTNESS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            LevelPill(icon = Icons.Default.BrightnessHigh, level = level)
        }
        AnimatedVisibility(
            visible = type == DragIndicatorType.VOLUME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val icon = when {
                level <= 0.01f -> Icons.Default.VolumeOff
                level < 0.5f -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            }
            LevelPill(icon = icon, level = level)
        }
    }
}

@Composable
private fun LevelPill(icon: ImageVector, level: Float) {
    Column(
        modifier = Modifier
            .padding(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        // Small vertical level bar, filled bottom-up to reflect the current level.
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((90.dp) * level.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("${(level * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================
// BRIGHTNESS / VOLUME CONTROLLERS
// =====================================================================

/**
 * Adjusts the current Activity window's brightness (0f..1f). This only affects this
 * screen's window - no WRITE_SETTINGS permission needed, unlike changing the
 * system-wide brightness.
 */
class BrightnessController(private val activity: Activity?) {
    var level: Float by mutableFloatStateOf(readCurrent())
        private set

    private fun readCurrent(): Float {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        return if (current in 0f..1f) current else 0.5f
    }

    fun adjust(delta: Float) {
        val next = (level + delta).coerceIn(0.02f, 1f)
        level = next
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = next
        window.attributes = params
    }
}

@Composable
fun rememberBrightnessController(activity: Activity?): BrightnessController =
    remember(activity) { BrightnessController(activity) }

/** Adjusts the device's media (STREAM_MUSIC) volume, matched to the video player. */
class VolumeController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val maxVolume = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)

    var level: Float by mutableFloatStateOf(readCurrent())
        private set

    private fun readCurrent(): Float {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        return current.toFloat() / maxVolume
    }

    fun adjust(delta: Float) {
        val next = (level + delta).coerceIn(0f, 1f)
        level = next
        val target = (next * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}

@Composable
fun rememberVolumeController(): VolumeController {
    val context = LocalContext.current
    return remember(context) { VolumeController(context) }
}

// =====================================================================
// GESTURE MODIFIER
// =====================================================================

private enum class DragRegion { NONE, BRIGHTNESS, VOLUME }

/**
 * Touch gesture controls for VOD playback:
 *  - Double-tap left half: rewind by [rewindMs]. Double-tap right half: forward by [forwardMs].
 *  - Vertical swipe on the left half: adjust screen brightness.
 *  - Vertical swipe on the right half: adjust media volume.
 *  - Single tap: toggles the player controls/title overlay via [onSingleTap].
 *
 * No-ops entirely when [enabled] is false (used to gate this to VOD-only, touch-only
 * playback - Live TV keeps its existing remote-first banner/zap controls, and TV
 * remotes drive playback via onPreviewKeyEvent instead of touch gestures).
 */
fun Modifier.vodTouchGestures(
    enabled: Boolean,
    player: Player,
    rewindMs: Long,
    forwardMs: Long,
    brightnessController: BrightnessController,
    volumeController: VolumeController,
    onSeekFeedback: (SeekDirection, Long) -> Unit,
    onDragIndicatorChange: (DragIndicatorType?) -> Unit,
    onSingleTap: () -> Unit
): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(player, rewindMs, forwardMs) {
            detectTapGestures(
                onTap = { onSingleTap() },
                onDoubleTap = { offset ->
                    if (offset.x < size.width / 2f) {
                        val target = (player.currentPosition - rewindMs).coerceAtLeast(0)
                        player.seekTo(target)
                        onSeekFeedback(SeekDirection.REWIND, rewindMs)
                    } else {
                        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        val target = (player.currentPosition + forwardMs).coerceAtMost(duration)
                        player.seekTo(target)
                        onSeekFeedback(SeekDirection.FORWARD, forwardMs)
                    }
                }
            )
        }
        .pointerInput(brightnessController, volumeController) {
            var region = DragRegion.NONE
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    region = if (offset.x < size.width / 2f) DragRegion.BRIGHTNESS else DragRegion.VOLUME
                    onDragIndicatorChange(
                        when (region) {
                            DragRegion.BRIGHTNESS -> DragIndicatorType.BRIGHTNESS
                            DragRegion.VOLUME -> DragIndicatorType.VOLUME
                            DragRegion.NONE -> null
                        }
                    )
                },
                onDragEnd = {
                    region = DragRegion.NONE
                    onDragIndicatorChange(null)
                },
                onDragCancel = {
                    region = DragRegion.NONE
                    onDragIndicatorChange(null)
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    // Full screen height drag = full 0..1 sweep.
                    val delta = -dragAmount / size.height.toFloat()
                    when (region) {
                        DragRegion.BRIGHTNESS -> brightnessController.adjust(delta)
                        DragRegion.VOLUME -> volumeController.adjust(delta)
                        DragRegion.NONE -> {}
                    }
                }
            )
        }
}
