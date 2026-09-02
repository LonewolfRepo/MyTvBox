package com.itv.blockbuster.ui.hubs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row // FIX 2: Added missing Row import
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

// =====================================================================
// MODELS
// =====================================================================

data class HubItem(
    val id: String,
    val title: String,
    val logoUrl: String = "",
    val kind: String = "MOVIE", // "LIVE" | "MOVIE" | "SERIES"
    val badge: String = "",
    val remainingLabel: String = "",
    val progressRatio: Float = 0f,
    val channelId: String = "",
    val cmd: String = "",
    val videoId: String = "",
    val year: String = ""
)

data class HubRow(
    val id: String,
    val title: String,
    val items: List<HubItem>
)

fun formatRemaining(ms: Long): String {
    val totalMin = ms / 60000
    if (totalMin <= 0) return ""
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// =====================================================================
// CARD
// =====================================================================

@Composable
fun HubCard(
    item: HubItem,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "hubScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(BbCard)
            .then(
                if (focused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
    ) {
        if (item.kind == "LIVE") {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (item.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    item.title,
                    color = BbTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            if (item.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.title,
                        color = BbTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // S:E badge (top center)
            if (item.badge.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.badge, color = BbTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Bottom scrim: remaining time + progress bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (item.remainingLabel.isNotEmpty()) {
                    Text(
                        item.remainingLabel,
                        color = BbTextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (item.progressRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.progressRatio.coerceIn(0f, 1f))
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(BbAccent)
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// ROW
// =====================================================================

@Composable
fun HubRowComposable(
    row: HubRow,
    onItemClicked: (HubItem) -> Unit,
    onItemFocused: (HubItem) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            color = BbTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(row.items, key = { it.id + it.kind }) { item ->
                HubCard(
                    item = item,
                    onClick = { onItemClicked(item) },
                    onFocusChanged = { if (it) onItemFocused(item) }
                )
            }
        }
    }
}

// =====================================================================
// DYNAMIC HERO (TV)
// =====================================================================

@Composable
fun HubHero(item: HubItem?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(BbSurface)
    ) {
        if (item != null && item.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BbBackground.copy(alpha = 0.9f))
                    )
                )
        )
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    item.title,
                    color = BbTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // FIX 2: Row is now resolved because the import was added
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.badge.isNotEmpty()) {
                        Text(item.badge, color = BbAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (item.year.isNotEmpty()) {
                        Text(item.year, color = BbTextSecondary, fontSize = 14.sp)
                    }
                    if (item.remainingLabel.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text("${item.remainingLabel} left", color = BbTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}