package com.itv.blockbuster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NoAdultContent
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
    HOME("home", "Home", Icons.Default.Home),
    SEARCH("search", "Search", Icons.Default.Search),
    MOVIES("movies", "Movies", Icons.Default.Movie),
    TV_SHOWS("tv_shows", "TV Shows", Icons.Default.Tv),
    LIVE_TV("live_tv", "Live TV", Icons.Default.LiveTv),
    TV_GUIDE("tv_guide", "TV Guide", Icons.Default.CalendarMonth),
    MY_LIST("my_list", "Favorites", Icons.Default.Star),
    RECENT("recent", "Recent", Icons.Default.History),
    ADULT("adult", "Adult", Icons.Default.NoAdultContent), // FIX: rail entry for the gated Adult hub
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}
