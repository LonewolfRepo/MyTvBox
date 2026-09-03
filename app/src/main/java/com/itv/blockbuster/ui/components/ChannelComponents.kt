package com.itv.blockbuster.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelTile(
    channel: PortalChannel,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteIconClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "channelScale")

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(BbCard)
            .then(if (focused) Modifier.border(3.dp, BbAccent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(64.dp), contentScale = ContentScale.Fit)
            } else {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(BbAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(text = channel.number.ifEmpty { channel.id.take(4) }, color = BbAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(text = channel.name, color = BbTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (channel.nowPlaying.isNotEmpty()) {
                Text(text = channel.nowPlaying, color = BbTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Favorite Icon Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onFavoriteIconClick)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCarouselRow(
    title: String,
    channels: List<PortalChannel>,
    favoriteIds: Set<String> = emptySet(),
    onChannelClick: (PortalChannel) -> Unit,
    onChannelLongClick: (PortalChannel) -> Unit,
    onFavoriteIconClick: (PortalChannel) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = BbTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels, key = { it.id }) { channel ->
                ChannelTile(
                    channel = channel,
                    isFavorite = favoriteIds.contains(channel.id),
                    modifier = Modifier.width(160.dp),
                    onClick = { onChannelClick(channel) },
                    onLongClick = { onChannelLongClick(channel) },
                    onFavoriteIconClick = { onFavoriteIconClick(channel) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelListItem(
    channel: PortalChannel,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteIconClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BbCard.copy(alpha = 0.8f) else BbCard)
            .then(if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit)
        } else {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(BbAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(text = channel.number.ifEmpty { channel.id.take(3) }, color = BbAccent, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = channel.name, color = BbTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.nowPlaying.isNotEmpty()) {
                Text(text = channel.nowPlaying, color = BbTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Favorite Icon Overlay
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onFavoriteIconClick)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White
            )
        }
    }
}