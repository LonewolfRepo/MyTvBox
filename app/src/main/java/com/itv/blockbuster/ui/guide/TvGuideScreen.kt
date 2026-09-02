package com.itv.blockbuster.ui.guide

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbCardHover
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// =====================================================================
// HELPERS
// =====================================================================

private const val WINDOW_MIN = 180
private val ROW_HEIGHT = 64.dp
private val CHANNEL_COL = 220.dp

private fun parseMinutes(t: String): Int {
    val parts = t.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

// =====================================================================
// SCREEN
// =====================================================================

@Composable
fun TvGuideScreen(
    onPlayLive: (String, String) -> Unit,
    onOpenCatchup: (String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formFactor = rememberFormFactor()
    val isPortrait = formFactor == FormFactor.MOBILE_PORTRAIT

    val pxPerMin = if (isPortrait) 3.dp else 5.dp
    val gridStart = (state.nowMin / 30) * 30

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TV Guide", color = BbTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BbCard)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(state.clock, color = BbTextSecondary, fontSize = 14.sp)
                }
            }

            // ── Time header ──
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(CHANNEL_COL))
                Box(Modifier.width(pxPerMin * WINDOW_MIN).height(28.dp)) {
                    var m = 0
                    while (m <= WINDOW_MIN) {
                        val labelMin = gridStart + m
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, labelMin / 60 % 24)
                            set(Calendar.MINUTE, labelMin % 60)
                        }
                        Text(
                            SimpleDateFormat("h:mm a", Locale.US).format(cal.time),
                            color = BbTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.offset(x = pxPerMin * m)
                        )
                        m += 30
                    }
                }
            }
            HorizontalDivider(color = BbCard)

            // ── Grid rows ──
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BbAccent)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.channels, key = { it.id }) { channel ->
                        GuideChannelRow(
                            channel = channel,
                            programs = state.epg[channel.id] ?: emptyList(),
                            gridStart = gridStart,
                            nowMin = state.nowMin,
                            pxPerMin = pxPerMin,
                            isPortrait = isPortrait,
                            isPreviewing = state.previewChannel?.id == channel.id,
                            onPreview = { viewModel.selectForPreview(channel) },
                            onProgramClick = { program ->
                                val isCurrent = parseMinutes(program.time) <= state.nowMin &&
                                        parseMinutes(program.time) + program.duration > state.nowMin
                                when {
                                    isCurrent -> {
                                        viewModel.selectForPreview(channel)
                                        state.previewUrl?.let { onPlayLive(it, channel.id) }
                                    }
                                    program.hasArchive && !program.cmd.isNullOrEmpty() -> {
                                        viewModel.playArchive(program) { url -> onPlayLive(url, channel.id) }
                                    }
                                    else -> onOpenCatchup(channel.id)
                                }
                            }
                        )
                        viewModel.ensureEpg(channel.id)
                    }
                }
            }
        }

        // ── PiP preview overlay ──
        if (state.previewChannel != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 24.dp)
                    .width(if (isPortrait) 180.dp else 280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                        .clickable {
                            state.previewUrl?.let { onPlayLive(it, state.previewChannel!!.id) }
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
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
                }
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        state.previewChannel!!.name,
                        color = BbTextPrimary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val now = state.epg[state.previewChannel!!.id]?.firstOrNull()
                    if (now != null) {
                        Text("Now: ${now.name}", color = BbTextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                    Text("● Live", color = BbAccent, fontSize = 11.sp)
                }
            }
        }
    }
}

// =====================================================================
// GRID ROW
// =====================================================================

@Composable
private fun GuideChannelRow(
    channel: PortalChannel,
    programs: List<EpgProgram>,
    gridStart: Int,
    nowMin: Int,
    pxPerMin: Dp,
    isPortrait: Boolean,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        // Channel cell
        var chFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .width(CHANNEL_COL)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isPreviewing -> BbAccent.copy(alpha = 0.2f)
                        chFocused -> BbCardHover
                        else -> BbSurface
                    }
                )
                .then(if (chFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(6.dp)) else Modifier)
                .clickable(onClick = onPreview)
                .focusable()
                .onFocusChanged { state -> chFocused = state.isFocused }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(BbCard),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        channel.number.ifEmpty { channel.id.take(3) },
                        color = BbTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name, color = BbTextPrimary, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Programs area
        val scrollState = rememberScrollState()
        Box(
            Modifier
                .width(pxPerMin * WINDOW_MIN)
                .fillMaxHeight()
                .then(if (isPortrait) Modifier.horizontalScroll(scrollState) else Modifier)
        ) {
            programs.forEach { program ->
                var pFocused by remember { mutableStateOf(false) }
                val start = parseMinutes(program.time)
                val end = start + program.duration
                if (end > gridStart && start < gridStart + WINDOW_MIN) {
                    val x = (start.coerceAtLeast(gridStart) - gridStart)
                    val w = (end.coerceAtMost(gridStart + WINDOW_MIN) - start.coerceAtLeast(gridStart))
                    Box(
                        modifier = Modifier
                            .offset(x = pxPerMin * x)
                            .width((w * pxPerMin.value).dp.coerceAtLeast(48.dp))
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (pFocused) BbCardHover else BbCard)
                            .then(if (pFocused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable { onProgramClick(program) }
                            .focusable()
                            .onFocusChanged { state -> pFocused = state.isFocused }
                            .padding(4.dp)
                    ) {
                        Text(
                            program.name, color = BbTextPrimary, fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Now line
            val nowOffset = (nowMin - gridStart).coerceIn(0, WINDOW_MIN)
            Box(
                Modifier
                    .offset(x = pxPerMin * nowOffset)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(BbAccent)
            )
        }
    }
    HorizontalDivider(color = BbCard, thickness = 0.5.dp)
}