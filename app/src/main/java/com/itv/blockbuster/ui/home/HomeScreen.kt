package com.itv.blockbuster.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.ui.components.CarouselRow
import com.itv.blockbuster.ui.components.HeroBanner
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextSecondary

@Composable
fun HomeScreen(
    onOpenPortals: () -> Unit,
    onOpenVodDetail: (String, String) -> Unit, // Added for VOD navigation
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        when {
            state.connectionError != null -> ConnectionErrorOverlay(
                message = state.connectionError!!,
                onRetry = viewModel::retry,
                onOpenPortals = onOpenPortals
            )
            (state.isLoading || state.isConnecting) && state.rows.isEmpty() -> LoadingOverlay(isConnecting = state.isConnecting)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "hero") { HeroBanner(hero = state.hero) }
                items(state.rows, key = { it.id }) { row ->
                    CarouselRow(
                        row = row,
                        favoriteIds = state.favoriteIds,
                        onItemClick = { item -> onOpenVodDetail(item.id, item.contentType) },
                        onItemLongClick = { item -> viewModel.toggleFavorite(item) },
                        onFavoriteIconClick = { item -> viewModel.toggleFavorite(item) }
                    )
                }
                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(isConnecting: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.weight(1f))
        CircularProgressIndicator(color = BbAccent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = if (isConnecting) "Connecting to portal…" else "Loading…", color = BbTextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConnectionErrorOverlay(message: String, onRetry: () -> Unit, onOpenPortals: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Connection problem", color = BbTextSecondary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = BbTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRetry) { Text("Retry") }
            OutlinedButton(onClick = onOpenPortals) { Text("Open Portals") }
        }
    }
}