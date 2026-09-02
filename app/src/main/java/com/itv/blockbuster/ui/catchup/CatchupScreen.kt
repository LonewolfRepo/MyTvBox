package com.itv.blockbuster.ui.catchup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.repository.LiveTvRepository
import com.itv.blockbuster.domain.model.EpgDay
import com.itv.blockbuster.domain.model.EpgProgram
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalPage
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbCardHover
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// =====================================================================
// VIEW MODEL
// =====================================================================

@HiltViewModel
class CatchupViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val channelId: String = savedStateHandle.get<String>("channelId") ?: ""

    data class CatchupState(
        val isLoading: Boolean = true,
        val channel: PortalChannel? = null,
        val days: List<EpgDay> = emptyList(),
        val selectedDay: EpgDay? = null,
        val programs: List<EpgProgram> = emptyList()
    )

    private val _state = MutableStateFlow(CatchupState())
    val state: StateFlow<CatchupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val channels = liveTvRepository.getAllChannels()
                .getOrDefault(PortalPage(emptyList(), 0)).items
            val channel = channels.firstOrNull { it.id == channelId }
            val days = liveTvRepository.getEpgWeek(channelId)
            val today = days.firstOrNull { it.isToday } ?: days.firstOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    channel = channel,
                    days = days,
                    selectedDay = today
                )
            }
            today?.let { loadDay(it.mysqlDate) }
        }
    }

    fun selectDay(day: EpgDay) {
        _state.update { it.copy(selectedDay = day, programs = emptyList()) }
        loadDay(day.mysqlDate)
    }

    private fun loadDay(date: String) {
        viewModelScope.launch {
            val programs = liveTvRepository.getEpgTable(channelId, date)
            _state.update { it.copy(programs = programs) }
        }
    }

    fun play(program: EpgProgram, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            val cmd = program.cmd ?: return@launch
            val url = liveTvRepository.createStreamLink(cmd).getOrDefault("")
            if (url.isNotEmpty()) onUrl(url)
        }
    }
}

// =====================================================================
// SCREEN
// =====================================================================

@Composable
fun CatchupScreen(
    onPlay: (String) -> Unit,
    viewModel: CatchupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbBackground)
            .padding(24.dp)
    ) {
        Text(
            "Catchup TV — ${state.channel?.name ?: ""}",
            color = BbTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        // ── Day selector ──
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.days) { day ->
                var focused by remember { mutableStateOf(false) }
                val selected = state.selectedDay?.mysqlDate == day.mysqlDate
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) BbAccent.copy(alpha = 0.25f) else BbCard)
                        .then(
                            if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                            else if (selected) Modifier.border(1.dp, BbAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { viewModel.selectDay(day) }
                        .focusable()
                        .onFocusChanged { focused = it.isFocused }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        day.humanLabel,
                        color = if (selected) BbAccent else BbTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = BbCard)

        // ── Programs ──
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BbAccent)
                }
            }
            state.programs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No programs available for this day.",
                        color = BbTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.programs) { program ->
                        CatchupProgramRow(
                            program = program,
                            onPlay = {
                                viewModel.play(program) { url -> onPlay(url) }
                            }
                        )
                        HorizontalDivider(color = BbCard, thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// =====================================================================
// PROGRAM ROW
// =====================================================================

@Composable
private fun CatchupProgramRow(
    program: EpgProgram,
    onPlay: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val playable = program.hasArchive && !program.cmd.isNullOrEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) BbCardHover else BbBackground)
            .then(if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(6.dp)) else Modifier)
            .then(
                if (playable) Modifier.clickable(onClick = onPlay) else Modifier
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            program.time,
            color = BbAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(64.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                program.name,
                color = if (playable) BbTextPrimary else BbTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program.description.isNotEmpty()) {
                Text(
                    program.description,
                    color = BbTextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (playable) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play archive",
                tint = if (focused) BbAccent else BbTextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            Text(
                "No archive",
                color = BbTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}