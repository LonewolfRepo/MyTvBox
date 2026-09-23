package com.itv.blockbuster.ui.vod

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.itv.blockbuster.data.local.entity.PlaybackProgressEntity
import com.itv.blockbuster.domain.model.PortalVodItem
import com.itv.blockbuster.ui.components.formatRuntimeMinutes
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun VodDetailScreen(
    onPlay: (String) -> Unit,
    onOpenEpisodes: () -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isPortrait = rememberFormFactor() == FormFactor.MOBILE_PORTRAIT
    val lifecycleOwner = LocalLifecycleOwner.current

    // D-pad focus: land on the Play button once the item finishes loading.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.item != null) {
        if (state.item != null) {
            // Give the freshly-composed layout a beat to lay out before requesting.
            repeat(20) { attempt ->
                delay(50)
                if (playFocusRequester.runCatching { requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        val item = state.item
        if (item == null) {
            CircularProgressIndicator(color = BbAccent, modifier = Modifier.align(Alignment.Center))
        } else {
            if (isPortrait) {
                PortraitLayout(
                    item = item,
                    state = state,
                    onPlayEpisode = { viewModel.playEpisode(it, onPlay) },
                    onPlayMovie = { viewModel.playMovie(onPlay) },
                    onPlayTarget = { viewModel.playTargetEpisode(onPlay) },
                    onRestartMovie = { viewModel.playMovieFromBeginning(onPlay) },
                    onRestartTarget = { viewModel.playTargetFromBeginning(onPlay) },
                    onRestartEpisode = { viewModel.playEpisodeFromBeginning(it, onPlay) },
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onToggleSort = { viewModel.toggleEpisodeSort() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    playFocusRequester = playFocusRequester,
                )
            } else {
                LandscapeLayout(
                    item = item,
                    state = state,
                    onPlayFirst = {
                        if (state.hasSeasons) {
                            viewModel.playTargetEpisode(onPlay)
                        } else {
                            viewModel.playMovie(onPlay)
                        }
                    },
                    onRestart = {
                        if (state.hasSeasons) {
                            viewModel.playTargetFromBeginning(onPlay)
                        } else {
                            viewModel.playMovieFromBeginning(onPlay)
                        }
                    },
                    onMoreEpisodes = onOpenEpisodes,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    playFocusRequester = playFocusRequester,
                )
            }
        }
    }
}

// =====================================================================
// PORTRAIT (Mobile)
// =====================================================================
@Composable
private fun PortraitLayout(
    item: PortalVodItem,
    state: VodDetailState,
    onPlayEpisode: (PortalVodItem) -> Unit,
    onPlayMovie: () -> Unit,
    onPlayTarget: () -> Unit,
    onRestartMovie: () -> Unit,
    onRestartTarget: () -> Unit,
    onRestartEpisode: (PortalVodItem) -> Unit,
    onSelectSeason: (PortalVodItem) -> Unit,
    onToggleSort: () -> Unit,
    onToggleFavorite: () -> Unit,
    playFocusRequester: FocusRequester,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp).background(BbCard),
            contentAlignment = Alignment.Center,
        ) {
            if (item.logoUrl.isNotEmpty()) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(item.logoUrl) {
                        ImageRequest.Builder(context)
                            .data(item.logoUrl)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, BbBackground)))
            )
            var playFocused by remember { mutableStateOf(false) }
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .then(
                            if (playFocused) Modifier.border(2.dp, BbAccent, CircleShape) else Modifier
                        )
                        .focusRequester(playFocusRequester)
                        .focusable()
                        .onFocusChanged { playFocused = it.isFocused }
                        .clickable {
                            if (state.hasSeasons) onPlayTarget() else onPlayMovie()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(item.name, color = BbTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (item.year.isNotBlank()) Text(item.year, color = BbTextSecondary, fontSize = 14.sp)
                        if (state.hasSeasons) {
                            Text("•", color = BbTextMuted)
                            Text("${state.seasons.size} Seasons", color = BbTextSecondary, fontSize = 14.sp)
                        }
                        if (item.ratingImdb.isNotBlank()) {
                            Text("•", color = BbTextMuted)
                            Box(
                                modifier =
                                    Modifier.border(1.dp, BbTextMuted, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("IMDb", color = BbTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                item.ratingImdb,
                                color = BbTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (item.duration.isNotBlank()) {
                            Text("•", color = BbTextMuted)
                            Text(item.duration, color = BbTextSecondary, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (
                        !state.hasSeasons && state.movieProgress != null && state.movieProgress.durationMs > 0
                    ) {
                        val ratio =
                            (state.movieProgress.positionMs.toFloat() / state.movieProgress.durationMs.toFloat())
                                .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = BbAccent,
                            trackColor = BbCard,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val hasProgress =
                            if (state.hasSeasons) {
                                state.playTarget?.isResume == true
                            } else {
                                state.movieProgress != null && state.movieProgress.positionMs > 5000
                            }
                        if (hasProgress) {
                            IconButton(
                                onClick = {
                                    if (state.hasSeasons) onRestartTarget() else onRestartMovie()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Replay,
                                    "Restart",
                                    tint = BbTextPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                            Icon(
                                if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                "Favorite",
                                tint = if (state.isFavorite) BbAccent else BbTextPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (item.description.isNotBlank()) {
                        Text(item.description, color = BbTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (item.genres.isNotBlank())
                        Text("Genres:  ${item.genres}", color = BbTextSecondary, fontSize = 13.sp)
                    if (item.director.isNotBlank())
                        Text("Director:  ${item.director}", color = BbTextSecondary, fontSize = 13.sp)
                    if (item.actors.isNotBlank())
                        Text("Cast:  ${item.actors}", color = BbTextSecondary, fontSize = 13.sp)
                }
            }

            if (state.hasSeasons) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text("Episodes", color = BbTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactSeasonDropdown(
                                    seasons = state.seasons,
                                    selected = state.selectedSeason,
                                    onSelect = onSelectSeason,
                                )
                            }
                            IconButton(onClick = onToggleSort, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    if (state.episodeSortAscending) Icons.Default.ArrowUpward
                                    else Icons.Default.ArrowDownward,
                                    contentDescription =
                                        if (state.episodeSortAscending) "Sort 3-2-1" else "Sort 1-2-3",
                                    tint = BbTextPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                // FIX (1000+ episode seasons taking noticeable time/jank to
                // render/scroll): was state.episodes.forEach { ... } directly
                // inside the enclosing (non-lazy) Column above, which composed,
                // measured and laid out every single episode row up front
                // regardless of scroll position - including each row's own
                // AsyncImage thumbnail load firing immediately. items() with a
                // stable key only composes/measures rows actually near the
                // viewport, and lets rows whose data hasn't changed skip
                // recomposition entirely on state updates (e.g. a progress-map
                // refresh after closing the player) instead of the whole list
                // re-evaluating.
                items(state.episodes, key = { it.id }) { episode ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EpisodeCard(
                            episode = episode,
                            seasonNumber = state.selectedSeason?.seasonNumber ?: "",
                            progress = state.episodeProgressMap[episode.id],
                            onPlay = { onPlayEpisode(episode) },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// =====================================================================
// LANDSCAPE (TV)
// =====================================================================
// FIX (follow-up - "buttons too high up, running into the movie info
// cards"): the info block and the action-button area used to be two
// siblings inside ONE shared Column, with the button area's total height
// only IMPLICITLY reserved (as part of "availableHeight minus 4 rows") -
// but the info block and buttons still flowed top-to-bottom in the SAME
// Column, so anything that made the actual rendered info content taller
// than intended (font metrics varying by device, etc.) had nowhere to go
// but visually crowd whatever came right after it. They're now two fully
// INDEPENDENT regions positioned by alignment instead of Column flow: the
// info block anchored to the TOP, and the action-button area anchored to
// the BOTTOM with its own genuinely fixed height (reserved for exactly 4
// rows, regardless of how many actually render) - so the buttons are
// always pinned a known distance from the bottom of the screen, Play
// always starts at the top of that reserved area, and the info block's
// own height calculation explicitly subtracts that full reserved area up
// front, leaving no way for the two to visually collide. Description is
// also down to 3 lines (from 5), further shrinking the info block's
// natural footprint.
private val VodInfoBlockNaturalMax = 320.dp
private val VodInfoBlockMinHeight = 160.dp
// FIX (follow-up - "leaving space in the bottom after the buttons, Info
// card getting truncated"): this was a GUESSED per-row height
// (padding(12dp*2) + ~1 line of 16sp bold text/22dp icon = 56dp) used both
// to size the button area's reserved height AND to compute the info
// block's height. The guess ran high vs. the row's real measured height
// (icon/text metrics land closer to ~46-48dp per row), so a fixed
// 4-row Column sized off the guess always had left-over blank space below
// the last actually-rendered row - space that, being reserved off the
// guess rather than reality, was also being over-subtracted from the info
// block, truncating it. VodActionRowHeight below is now only the FALLBACK
// used for exactly one frame before the real row height is measured (see
// measuredRowHeight in LandscapeLayout) - once measured, the reserved area
// and the info block both resize to the true value, so no gap survives
// past that first frame and the info block gets back every bit of space
// the old guess was reserving unnecessarily.
private val VodActionRowHeight = 56.dp
private const val VodMaxActionRows = 4 // Play/Resume, Restart, More Episodes, Favorite
private val VodInfoButtonSpacing = 20.dp

private fun calculateVodInfoBlockHeight(
    availableHeight: Dp,
    topBottomPadding: Dp,
    buttonAreaHeight: Dp,
): Dp {
    val availableForInfo = availableHeight - topBottomPadding - buttonAreaHeight - VodInfoButtonSpacing
    return availableForInfo.coerceIn(VodInfoBlockMinHeight, VodInfoBlockNaturalMax)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LandscapeLayout(
    item: PortalVodItem,
    state: VodDetailState,
    onPlayFirst: () -> Unit,
    onRestart: () -> Unit,
    onMoreEpisodes: () -> Unit,
    onToggleFavorite: () -> Unit,
    playFocusRequester: FocusRequester,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // FIX: real, measured height of a single action row (see
        // VodActionRowHeight's doc comment above) - null until the Play row
        // below has laid out once. VodMaxActionRows worth of THIS value is
        // what's actually reserved for the button area, so the area's top
        // edge (where Play is pinned) lands exactly where 4 real rows end,
        // never short or long of it.
        var measuredRowHeight by remember { mutableStateOf<Dp?>(null) }
        val buttonAreaHeight = (measuredRowHeight ?: VodActionRowHeight) * VodMaxActionRows
        // 24dp top padding (trimmed - see the info block's own FIX comment
        // below) + 48dp bottom padding, matching the two content regions
        // below.
        val infoBlockHeight =
            calculateVodInfoBlockHeight(maxHeight, topBottomPadding = 72.dp, buttonAreaHeight = buttonAreaHeight)
        if (item.logoUrl.isNotEmpty()) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(item.logoUrl) {
                    ImageRequest.Builder(context)
                        .data(item.logoUrl)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                BbBackground,
                                BbBackground.copy(alpha = 0.95f),
                                BbBackground.copy(alpha = 0.7f),
                                Color.Transparent,
                            ),
                            startX = 0f,
                            endX = 1200f,
                        )
                    )
        )
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BbBackground.copy(alpha = 0.9f))
                        )
                    )
        )

        var showFullDescription by remember { mutableStateOf(false) }

        // ── INFO BLOCK - anchored to the top, independent of the button area. ──
        // FIX ("move the title up a bit so the 2nd line of Cast can show"): top
        // padding trimmed to 24dp, so the title (and everything under it)
        // starts a little higher, leaving more room for the info content below
        // - e.g. the Cast line's 2nd line. The matching topBottomPadding passed
        // to calculateVodInfoBlockHeight below is updated to this new top(24dp)
        // + bottom(48dp) = 72dp total. Nothing else about the info block (its
        // max height, line counts, etc.) is touched.
        Column(
            modifier =
                Modifier.fillMaxWidth(0.5f)
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, end = 24.dp, top = 24.dp)
        ) {
            // FIX (issue 3 - "Play/Resume button is still not at a fixed
            // position... now that you know the space for each line field, pin
            // the play button at fixed location"): a fixed height for a GIVEN
            // screen size (infoBlockHeight, computed above from the real
            // available space) rather than weight(1f)/wrap-content - see
            // calculateVodInfoBlockHeight's own comment for why a flat
            // hardcoded value doesn't work here. Shorter real content just
            // leaves blank space below rather than pulling anything up - the
            // deliberate tradeoff for a truly fixed layout. verticalScroll stays
            // as a safety net for the rare case actual content still slightly
            // exceeds the computed height.
            Column(
                modifier = Modifier.height(infoBlockHeight).verticalScroll(rememberScrollState())
            ) {
                // FIX ("single scrolling line for title that goes half way through
                // the screen"): was a plain Text with no line limit (would wrap a
                // long title across multiple lines, growing this block
                // unpredictably). maxLines=1 + basicMarquee() only actually
                // animates/scrolls when the text is wider than the space available
                // to it (bounded by this Column's fillMaxWidth(0.5f) above) - a
                // short title that already fits just displays normally, static.
                Text(
                    item.name,
                    color = BbTextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(12.dp))

                // FIX ("wrap to 2nd line if goes beyond half way through screen"):
                // was a plain Row, which never wraps - a long metadata line would
                // just get clipped/pushed off the edge of the 50%-width column.
                // FlowRow wraps whatever doesn't fit onto additional lines
                // instead, same pattern already used for the Home/Live TV hero
                // banner's own metadata row.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (item.year.isNotBlank()) {
                        Text(item.year, color = BbTextSecondary, fontSize = 15.sp)
                    }
                    // Runtime for a movie, season count for a series - never both.
                    val runtimeOrSeasons =
                        if (state.hasSeasons) {
                            val count = state.seasons.size
                            if (count > 0) "$count Season${if (count == 1) "" else "s"}" else ""
                        } else {
                            formatRuntimeMinutes(item.duration)
                        }
                    if (runtimeOrSeasons.isNotEmpty()) {
                        Text("•", color = BbTextMuted)
                        Text(runtimeOrSeasons, color = BbTextSecondary, fontSize = 15.sp)
                    }
                    // FIX (issue 4 - "Display PG & Age rating if provided, format
                    // '{rate} {age}'; don't display rate if none/blank/0, don't
                    // display age if it's '0'"): was just item.ratingMpaa alone -
                    // ratingMpaa (the "PG"-style rate) and age are two SEPARATE
                    // fields in the payload, and either one can be legitimately
                    // absent/unset (the portal appears to use "0" as its own "not
                    // set" sentinel for both, in addition to the more obvious blank
                    // string) without the other being.
                    val rate = item.ratingMpaa.trim()
                    val rateValid = rate.isNotBlank() && !rate.equals("none", ignoreCase = true) && rate != "0"
                    val age = item.age.trim()
                    val ageValid = age.isNotBlank() && age != "0"
                    val ratingDisplay =
                        when {
                            rateValid && ageValid -> "$rate $age"
                            rateValid -> rate
                            ageValid -> age
                            else -> ""
                        }
                    if (ratingDisplay.isNotEmpty()) {
                        Text("•", color = BbTextMuted)
                        Box(
                            modifier =
                                Modifier.border(1.dp, BbTextMuted, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                ratingDisplay,
                                color = BbTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (item.ratingImdb.isNotBlank()) {
                        Text("•", color = BbTextMuted)
                        Box(
                            modifier =
                                Modifier.border(1.dp, BbTextMuted, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("IMDb", color = BbTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            item.ratingImdb,
                            color = BbTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (item.country.isNotBlank()) {
                        Text("•", color = BbTextMuted)
                        Text(item.country, color = BbTextSecondary, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))

                // FIX (issue - "reduce description to 3 lines from 5"; also see
                // ExpandableDescriptionText's own doc comment for the inline "Read
                // More" behavior, unaffected by this line-count change).
                if (item.description.isNotBlank()) {
                    ExpandableDescriptionText(
                        text = item.description,
                        color = BbTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 3,
                        accentColor = BbAccent,
                        onReadMoreClick = { showFullDescription = true },
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (item.genres.isNotBlank()) {
                    Text(
                        "Genre:  ${item.genres}",
                        color = BbTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (item.director.isNotBlank()) {
                    Text(
                        "Director:  ${item.director}",
                        color = BbTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // FIX (issue 3): removed the "Country: ..." line that used to sit
                // here, underneath Cast - country is still shown up in the
                // metadata/subtitle row above, this was just a duplicate.
                if (item.actors.isNotBlank()) {
                    Text(
                        "Cast:  ${item.actors}",
                        color = BbTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── BUTTON AREA - anchored to the bottom, independent of the info
        // block above (see this file's top-level FIX comment). Fixed height,
        // reserved for exactly VodMaxActionRows regardless of how many
        // actually render, so this whole area - and Play's position within it
        // - never shifts based on hasAnyProgress/hasSeasons. ──
        val playLabel =
            if (state.hasSeasons) {
                state.playTarget?.label ?: "Play"
            } else {
                val mp = state.movieProgress
                if (mp != null && mp.positionMs > 5000 && mp.positionMs < mp.durationMs - 5000)
                    "Resume Movie"
                else "Play Movie"
            }

        val playProgress: PlaybackProgressEntity? =
            if (state.hasSeasons) {
                if (state.playTarget?.isResume == true) {
                    state.playTarget?.episode?.let { state.episodeProgressMap[it.id] }
                } else null
            } else {
                state.movieProgress
            }
        val playProgressRatio =
            if (playProgress != null && playProgress.durationMs > 0) {
                (playProgress.positionMs.toFloat() / playProgress.durationMs.toFloat()).coerceIn(
                    0f,
                    1f,
                )
            } else 0f
        val playTrailingText =
            playProgress?.let {
                if (it.durationMs > 0) formatTimeLeft(it.durationMs - it.positionMs) else null
            }

        val hasAnyProgress =
            if (state.hasSeasons) {
                state.playTarget?.isResume == true
            } else {
                state.movieProgress != null && state.movieProgress.positionMs > 5000
            }

        Column(
            modifier =
                Modifier.fillMaxWidth(0.5f)
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, end = 24.dp, bottom = 48.dp)
                    .height(buttonAreaHeight)
        ) {
            // FIX (issue 3 - "Play/Resume button is still not at a fixed
            // position... pin the play button at fixed location, other buttons
            // can move underneath it"): Play is a standalone sibling at the TOP
            // of this fixed-height, bottom-anchored area, with the
            // conditionally-shown rows in their own Column right after it -
            // Play's own Y position depends only on this whole area's top edge,
            // never on how many of Restart/More Episodes are actually present
            // below it. That top edge is now derived from buttonAreaHeight
            // (measuredRowHeight * VodMaxActionRows) rather than a guessed
            // constant, so it sits exactly 4 real rows up from the bottom
            // padding - no leftover gap beneath whichever row ends up last.
            LandscapeActionRow(
                icon = Icons.Default.PlayArrow,
                label = playLabel,
                progressRatio = if (playProgressRatio > 0f) playProgressRatio else null,
                trailingText = playTrailingText,
                onClick = onPlayFirst,
                focusRequester = playFocusRequester,
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        val heightDp = with(density) { coordinates.size.height.toDp() }
                        if (measuredRowHeight != heightDp) measuredRowHeight = heightDp
                    },
            )
            Column {
                if (hasAnyProgress) {
                    LandscapeActionRow(
                        icon = Icons.Default.Replay,
                        label = "Play From Beginning",
                        onClick = onRestart,
                    )
                }
                if (state.hasSeasons) {
                    LandscapeActionRow(
                        icon = Icons.Default.ListAlt,
                        label = "More Episodes",
                        onClick = onMoreEpisodes,
                    )
                }
                LandscapeActionRow(
                    icon = if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (state.isFavorite) "In Favorites" else "Add to Favorites",
                    onClick = onToggleFavorite,
                )
            }
        }

        if (showFullDescription) {
            AlertDialog(
                onDismissRequest = { showFullDescription = false },
                containerColor = BbBackground,
                title = { Text(item.name, color = BbTextPrimary) },
                text = {
                    Text(
                        item.description,
                        color = BbTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showFullDescription = false }) {
                        Text("Close", color = BbAccent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
    }
}

// =====================================================================
// LANDSCAPE ACTION ROW (Play/Resume, Restart, More Episodes, Favorite)
// =====================================================================
/**
 * FIX ("match the format of the buttons, focused button should be
 * highlighted, display progress bar within the episode button"): replaces
 * BOTH the old filled-accent PlayButtonWithProgress (Play/Resume only, with
 * its progress bar rendered as a separate strip BELOW the button) and
 * DetailActionButton (an outlined-border Material Button for the other
 * three actions) with ONE shared row style for all four actions - a plain,
 * unfilled icon+label row that only gains a border/tint when focused,
 * matching the reference screenshot where every action reads the same way
 * except for the focus highlight. The progress bar (when progressRatio is
 * given) is now inline, between the label and the trailing "time left"
 * text, inside the SAME row - not a separate element underneath it.
 */
@Composable
private fun LandscapeActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    progressRatio: Float? = null,
    trailingText: String? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    // FIX (issue 1 - "focus border is not being displayed... when moving
    // through Dpad"): was a separate .focusable() + .onFocusChanged{} placed
    // BEFORE a plain .clickable() in the modifier chain - .clickable()
    // installs its OWN internal focusable/indication node, so this row ended
    // up with TWO independent focus targets. D-pad focus was actually
    // landing on clickable()'s own internal node, which the explicit
    // .focusable()/.onFocusChanged{} pair above it never observed - so
    // `focused` never flipped to true and the border/tint never appeared,
    // even though the row visibly had focus. Same fix as PosterCard/
    // ChannelTile/etc. elsewhere in the app: share ONE interactionSource
    // between clickable and focusable so both describe the exact same node.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (focused) {
                        Modifier.background(BbAccent.copy(alpha = 0.15f))
                            .border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                    } else Modifier
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (focused) BbAccent else BbTextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (focused) BbAccent else BbTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (progressRatio != null) {
            Spacer(Modifier.width(16.dp))
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = BbAccent,
                trackColor = BbTextMuted.copy(alpha = 0.3f),
            )
            if (!trailingText.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(trailingText, color = BbTextSecondary, fontSize = 13.sp, maxLines = 1)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** "Xh Ym Left" for a play/resume row's trailing text - see LandscapeActionRow. */
private fun formatTimeLeft(remainingMs: Long): String? {
    if (remainingMs <= 0) return null
    val totalMinutes = (remainingMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val formatted =
        when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    return "$formatted Left"
}

// =====================================================================
// EXPANDABLE DESCRIPTION (inline "Read More" on the last visible line)
// =====================================================================
/**
 * FIX (issue 1 - "Read More is appearing on its own line, put it in the
 * same line 5"): renders [text] capped at [maxLines], and - only if it
 * actually overflows that many lines - swaps in a version trimmed to fit
 * exactly on the last visible line with a "… READ MORE" suffix appended
 * directly onto the end of it, instead of the plain 5-line text followed
 * by a separate "READ MORE" line underneath (which, whenever the
 * description was long enough to truncate, effectively became a 6th line).
 *
 * This is a two-pass measurement: the first composition renders the FULL
 * text to find out (a) whether it overflows [maxLines] at all, and (b)
 * where the last visible line ends. If it does overflow, the text is
 * trimmed to end a little before that point (leaving room for the
 * suffix's own width) and the suffix is appended - which triggers a
 * second, short-circuited layout pass (guarded by [measured]) that just
 * renders the now-shorter text normally. The trim amount is a fixed
 * character-count buffer rather than a second precise remeasurement, so
 * on unusual fonts/widths this can trim a few characters more than the
 * strict minimum - an acceptable tradeoff for not needing a measurement
 * loop.
 */
@Composable
private fun ExpandableDescriptionText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLines: Int,
    accentColor: Color,
    onReadMoreClick: () -> Unit,
) {
    var displayText by remember(text) { mutableStateOf<AnnotatedString?>(null) }
    var measured by remember(text) { mutableStateOf(false) }
    var isTruncated by remember(text) { mutableStateOf(false) }

    // Same shared-interactionSource fix as LandscapeActionRow - see its own
    // comment. Only installed once we know the text is actually truncated
    // (the whole paragraph becomes the tap target for "Read More", since
    // precisely hit-testing just the suffix substring isn't worth the extra
    // complexity here).
    val interactionSource = remember(text) { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = displayText ?: AnnotatedString(text),
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier =
            if (isTruncated) {
                Modifier
                    .then(if (focused) Modifier.border(1.dp, accentColor, RoundedCornerShape(4.dp)) else Modifier)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onReadMoreClick,
                    )
                    .focusable(interactionSource = interactionSource)
            } else Modifier,
        onTextLayout = { result ->
            if (measured) return@Text
            measured = true
            if (result.hasVisualOverflow) {
                isTruncated = true
                val suffix = "  READ MORE"
                val lastLineEnd = result.getLineEnd(maxLines - 1, visibleEnd = true)
                // Trim extra characters to make visual room for the suffix - a
                // fixed buffer rather than a precise remeasure, see the doc
                // comment above.
                val trimTo = (lastLineEnd - suffix.length - 6).coerceIn(0, text.length)
                val base = text.substring(0, trimTo).trimEnd().trimEnd('.', ',', ';', ':')
                displayText = buildAnnotatedString {
                    append(base)
                    append("… ")
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                        append("READ MORE")
                    }
                }
            }
        },
    )
}

// =====================================================================
// HELPERS
// =====================================================================
@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = BbAccent),
            modifier =
                Modifier.width(340.dp)
                    .height(48.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier =
                Modifier.width(340.dp)
                    .height(48.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BbTextPrimary),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CompactSeasonDropdown(
    seasons: List<PortalVodItem>,
    selected: PortalVodItem?,
    onSelect: (PortalVodItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BbSurface)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label =
                selected?.seasonNumber?.let {
                    if (it.isNotBlank()) "Season $it" else selected.name
                } ?: "Select Season"
            Text(
                label,
                color = BbTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, null, tint = BbTextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEachIndexed { index, season ->
                val display =
                    season.seasonNumber.let {
                        if (it.isNotBlank()) "Season $it" else "Season ${index + 1}"
                    }
                DropdownMenuItem(
                    text = {
                        Text(
                            display,
                            color = if (season.id == selected?.id) BbAccent else BbTextPrimary,
                        )
                    },
                    onClick = {
                        onSelect(season)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: PortalVodItem,
    seasonNumber: String,
    progress: PlaybackProgressEntity?,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progressRatio =
        if (progress != null && progress.durationMs > 0) {
            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (focused)
                        Modifier.border(2.dp, BbAccent, RoundedCornerShape(10.dp))
                            .background(BbCard.copy(alpha = 0.4f))
                    else Modifier
                )
                .clickable(onClick = onPlay)
                .focusable()
                .onFocusChanged { focused = it.isFocused }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier.width(140.dp).height(78.dp).clip(RoundedCornerShape(8.dp)).background(BbCard)
            ) {
                if (episode.logoUrl.isNotEmpty()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(episode.logoUrl) {
                            ImageRequest.Builder(context)
                                .data(episode.logoUrl)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .build()
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "S${seasonNumber}:E${episode.episodeNumber}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (progressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = BbAccent,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${episode.episodeNumber}. ${episode.name}",
                    color = BbTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (episode.description.isNotBlank()) {
                    Text(
                        episode.description,
                        color = BbTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    if (episode.duration.isNotBlank()) add("${episode.duration}m")
                    val date = formatAddedDate(episode.addedDate)
                    if (date.isNotEmpty()) add(date)
                }
                    .joinToString(", ")
                if (meta.isNotEmpty()) Text("($meta)", color = BbTextMuted, fontSize = 12.sp)
            }
        }
    }
}

private fun formatAddedDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val parsed = input.parse(raw) ?: return raw
        output.format(parsed)
    } catch (e: Exception) {
        raw
    }
}