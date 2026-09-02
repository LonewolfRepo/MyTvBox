package com.itv.blockbuster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppSection(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.LiveTv),
    SEARCH("search", "Search", Icons.Default.Search),
    MOVIES("movies", "Movies", Icons.Default.Movie),
    TV_SHOWS("tv_shows", "TV Shows", Icons.Default.Tv),
    LIVE_TV("live_tv", "Live TV", Icons.Default.LiveTv),
    TV_GUIDE("tv_guide", "TV Guide", Icons.Default.CalendarMonth),
    MY_LIST("my_list", "My List", Icons.Default.Star),
    RECENT("recent", "Recent", Icons.Default.History),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}