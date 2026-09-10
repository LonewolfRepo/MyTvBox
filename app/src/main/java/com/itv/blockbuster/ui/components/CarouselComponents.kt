package com.itv.blockbuster.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
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
    val items: List<PortalVodItem>,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingPage: Boolean = false
)

/**
 * A Netflix-style horizontal carousel that locks focus to a specific left-anchored coordinate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> NetflixStyleCarousel(
    data: List<T>, // FIX: Renamed from 'items' to 'data' to prevent shadowing LazyListScope.items()
    collapsedMenuWidth: Dp = 0.dp,
    itemWidth: Dp,
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current

    val peekWidth = itemWidth * 0.20f
    val focusAnchorLine = peekWidth + itemSpacing
    val lazyRowWidth = screenWidth - collapsedMenuWidth
    val endPadding = (lazyRowWidth - focusAnchorLine - itemWidth).coerceAtLeast(0.dp)
    val focusAnchorLinePx = with(density) { focusAnchorLine.toPx() }

    val customSpec = remember(focusAnchorLinePx) {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                spring(stiffness = Spring.StiffnessHigh)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return offset - focusAnchorLinePx
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides customSpec) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = focusAnchorLine,
                end = endPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(data) { item -> // FIX: Now correctly resolves to LazyListScope.items()
                itemContent(item)
            }
            if (trailingContent != null) {
                item { trailingContent() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: PortalVodItem,
    modifier: Modifier = Modifier.width(140.dp),
    isFavorite: Boolean = false,
    progressRatio: Float = 0f,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onFavoriteIconClick: () -> Unit = {},
    actionIcon: ImageVector? = null,
    actionIconTint: Color = Color.White
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "posterScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
        if (actionIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .focusable()
                    .clickable(onClick = onFavoriteIconClick)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = actionIconTint,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
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
        if (progressRatio > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomStart)
                    .zIndex(2f)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .background(BbAccent)
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
    progressMap: Map<String, PlaybackProgressEntity> = emptyMap(),
    onItemClick: (PortalVodItem) -> Unit = {},
    onItemLongClick: (PortalVodItem) -> Unit = {},
    onFavoriteIconClick: (PortalVodItem) -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    val formFactor = rememberFormFactor()
    val collapsedMenuWidth = if (formFactor == FormFactor.MOBILE_PORTRAIT) 0.dp else 84.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            color = BbTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
        )
        NetflixStyleCarousel(
            data = row.items, // FIX: Updated parameter name to match NetflixStyleCarousel signature
            collapsedMenuWidth = collapsedMenuWidth,
            itemWidth = 140.dp,
            itemSpacing = 12.dp,
            trailingContent = {
                if (row.hasMore) {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BbCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (row.isLoadingPage) {
                            CircularProgressIndicator(
                                color = BbAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) { item ->
            val progress = progressMap[item.id]
            val ratio = if (progress != null && progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            PosterCard(
                item = item,
                isFavorite = favoriteIds.contains(item.id),
                progressRatio = ratio,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onFavoriteIconClick = { onFavoriteIconClick(item) }
            )
        }
    }
}

/**
 * NEW: Scrollable poster grid used when a specific category is selected (as opposed
 * to the "All Categories"/"All Genres" carousel view). One category = one flat list
 * of items, so a vertical grid reads better than a single horizontal row here.
 * Supports the same favorite/progress/click callbacks as CarouselRow, plus the same
 * "load more on reaching the end" pagination pattern (a trailing full-width item that
 * fires onLoadMore when it comes into view).
 */
@Composable
fun PosterGrid(
    row: HomeRow,
    favoriteIds: Set<String> = emptySet(),
    progressMap: Map<String, PlaybackProgressEntity> = emptyMap(),
    onItemClick: (PortalVodItem) -> Unit = {},
    onItemLongClick: (PortalVodItem) -> Unit = {},
    onFavoriteIconClick: (PortalVodItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(row.items, key = { it.id }) { item ->
            val progress = progressMap[item.id]
            val ratio = if (progress != null && progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            PosterCard(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                isFavorite = favoriteIds.contains(item.id),
                progressRatio = ratio,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onFavoriteIconClick = { onFavoriteIconClick(item) }
            )
        }
        if (row.hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // FIX: mirrors CarouselRow's trailing load-more trigger - fires once
                // this spacer scrolls into view, keyed on item count so it re-arms
                // after every page appended.
                LaunchedEffect(row.items.size) { onLoadMore() }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (row.isLoadingPage) {
                        CircularProgressIndicator(
                            color = BbAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner(hero: PortalVodItem?) {
    // FIX: render nothing (not even an empty placeholder box) when there's no hero
    // item - previously this always reserved a fixed 280/380dp block of blank
    // space even with hero == null (e.g. Adult VOD, when the pool used to seed a
    // hero happens to have no adult-flagged items), which read as a broken layout.
    if (hero == null) return
    val formFactor = rememberFormFactor()
    val height = if (formFactor == FormFactor.MOBILE_PORTRAIT) 280.dp else 380.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BbBackground)
    ) {
        if (hero.logoUrl.isNotEmpty()) {
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
