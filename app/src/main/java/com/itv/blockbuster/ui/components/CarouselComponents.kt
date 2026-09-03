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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

data class HomeRow(
    val id: String,
    val title: String,
    val items: List<PortalVodItem>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: PortalVodItem,
    isFavorite: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onFavoriteIconClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "posterScale"
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
    ) {
        if (item.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name,
                    color = BbTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Favorite Icon Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onFavoriteIconClick)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) BbAccent else Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }

        if (item.logoUrl.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.name,
                    color = BbTextPrimary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselRow(
    row: HomeRow,
    favoriteIds: Set<String> = emptySet(),
    onItemClick: (PortalVodItem) -> Unit = {},
    onItemLongClick: (PortalVodItem) -> Unit = {},
    onFavoriteIconClick: (PortalVodItem) -> Unit = {}
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
            items(row.items, key = { it.id }) { item ->
                PosterCard(
                    item = item,
                    isFavorite = favoriteIds.contains(item.id),
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onFavoriteIconClick = { onFavoriteIconClick(item) }
                )
            }
        }
    }
}

@Composable
fun HeroBanner(hero: PortalVodItem?) {
    val formFactor = rememberFormFactor()
    val height = if (formFactor == FormFactor.MOBILE_PORTRAIT) 280.dp else 380.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BbBackground)
    ) {
        if (hero != null && hero.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = hero.logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            BbBackground.copy(alpha = 0.95f),
                            BbBackground.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BbBackground.copy(alpha = 0.9f))
                    )
                )
        )

        if (hero != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .fillMaxWidth(0.6f)
            ) {
                Text(
                    text = hero.name,
                    color = BbTextPrimary,
                    fontSize = if (formFactor == FormFactor.MOBILE_PORTRAIT) 26.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (hero.year.isNotEmpty()) Text(hero.year, color = BbTextSecondary, fontSize = 14.sp)
                    if (hero.duration.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text(hero.duration, color = BbTextSecondary, fontSize = 14.sp)
                    }
                    if (hero.ratingImdb.isNotEmpty()) {
                        Text("•", color = BbTextSecondary)
                        Text("IMDb ${hero.ratingImdb}", color = BbTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (hero.ratingMpaa.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, BbTextSecondary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(hero.ratingMpaa, color = BbTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (hero.description.isNotEmpty()) {
                    Text(
                        text = hero.description,
                        color = BbTextSecondary,
                        fontSize = 14.sp,
                        maxLines = if (formFactor == FormFactor.MOBILE_PORTRAIT) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}