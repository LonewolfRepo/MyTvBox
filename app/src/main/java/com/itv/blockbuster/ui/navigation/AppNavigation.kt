package com.itv.blockbuster.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itv.blockbuster.ui.catchup.CatchupScreen
import com.itv.blockbuster.ui.common.SectionPlaceholder
import com.itv.blockbuster.ui.guide.TvGuideScreen
import com.itv.blockbuster.ui.home.HomeScreen
import com.itv.blockbuster.ui.hubs.FavoritesHubScreen
import com.itv.blockbuster.ui.hubs.RecentsHubScreen
import com.itv.blockbuster.ui.livetv.LiveTvScreen
import com.itv.blockbuster.ui.player.PlayerScreen
import com.itv.blockbuster.ui.profiles.ProfileHubScreen
import com.itv.blockbuster.ui.profiles.ProfilePickerScreen
import com.itv.blockbuster.ui.profiles.StartupViewModel
import com.itv.blockbuster.ui.servers.ServersScreen
import com.itv.blockbuster.ui.settings.SettingsScreen
import com.itv.blockbuster.ui.shell.AppShell
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.vod.VodBrowserScreen
import com.itv.blockbuster.ui.vod.VodEpisodesScreen
import com.itv.blockbuster.ui.vod.VodDetailScreen


object Routes {
    const val PROFILE_PICKER = "profile_picker"
    const val PROFILE_HUB = "profile_hub"
    const val SERVERS = "servers"

    val HOME = AppSection.HOME.route
    val SEARCH = AppSection.SEARCH.route
    val MOVIES = AppSection.MOVIES.route
    val TV_SHOWS = AppSection.TV_SHOWS.route
    val LIVE_TV = AppSection.LIVE_TV.route
    val TV_GUIDE = AppSection.TV_GUIDE.route
    val MY_LIST = AppSection.MY_LIST.route
    val RECENT = AppSection.RECENT.route
    val SETTINGS = AppSection.SETTINGS.route

    const val VOD_BROWSER = "vod_browser/{contentType}"
    const val VOD_DETAIL = "vod_detail/{itemId}/{contentType}"
    const val PLAYER = "player/{streamUrl}/{channelId}/{videoId}"
    const val CATCHUP = "catchup/{channelId}"
}

enum class FormFactor { TV, MOBILE_PORTRAIT, MOBILE_LANDSCAPE }

@Composable
fun rememberFormFactor(): FormFactor {
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return when {
        isTv -> FormFactor.TV
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT -> FormFactor.MOBILE_PORTRAIT
        else -> FormFactor.MOBILE_LANDSCAPE
    }
}

fun NavHostController.navigateToSection(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun encodeUrl(url: String): String =
    try { java.net.URLEncoder.encode(url, "UTF-8") } catch (e: Exception) { url }

@Composable
fun AppRoot() {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val state by startupViewModel.state.collectAsState()
    when (val current = state) {
        is StartupViewModel.StartupState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(BbBackground),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BbAccent) }
        }
        is StartupViewModel.StartupState.Resolved -> {
            AppNavigation(startAtPicker = current.showPicker)
        }
    }
}

@Composable
fun AppNavigation(startAtPicker: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startAtPicker) Routes.PROFILE_PICKER else Routes.HOME
    ) {
        composable(Routes.PROFILE_PICKER) {
            ProfilePickerScreen(
                onProfileSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PROFILE_PICKER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            AppShell(navController) {
                HomeScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") }
                )
            }
        }

        // FIX: Movies explicitly passes "vod" contentType
        composable(Routes.MOVIES) {
            AppShell(navController) {
                VodBrowserScreen(
                    contentType = "vod",
                    onOpenDetail = { itemId -> navController.navigate("vod_detail/$itemId/vod") }
                )
            }
        }

        // FIX: TV Shows explicitly passes "series" contentType
        composable(Routes.TV_SHOWS) {
            AppShell(navController) {
                VodBrowserScreen(
                    contentType = "series",
                    onOpenDetail = { itemId -> navController.navigate("vod_detail/$itemId/series") }
                )
            }
        }

        composable(Routes.LIVE_TV) {
            AppShell(navController) {
                LiveTvScreen(
                    onPlayChannel = { url, channelId -> navController.navigate("player/${encodeUrl(url)}/$channelId/none") },
                    onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") }
                )
            }
        }

        composable(Routes.TV_GUIDE) {
            AppShell(navController) {
                TvGuideScreen(
                    onPlayLive = { url, channelId -> navController.navigate("player/${encodeUrl(url)}/$channelId/none") },
                    onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") }
                )
            }
        }

        composable(
            route = Routes.CATCHUP,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) {
            AppShell(navController) {
                CatchupScreen(onPlay = { url -> navController.navigate("player/${encodeUrl(url)}/none/none") })
            }
        }

        composable(Routes.MY_LIST) {
            AppShell(navController) {
                FavoritesHubScreen(
                    onPlayLive = { url, channelId ->
                        navController.navigate("player/${encodeUrl(url)}/$channelId/none")
                    },
                    onOpenVod = { itemId, type ->
                        navController.navigate("vod_detail/$itemId/$type")
                    }
                )
            }
        }

        composable(Routes.RECENT) {
            AppShell(navController) {
                RecentsHubScreen(
                    onPlayLive = { url, channelId ->
                        navController.navigate("player/${encodeUrl(url)}/$channelId/none")
                    },
                    onOpenVod = { itemId, type ->
                        navController.navigate("vod_detail/$itemId/$type")
                    }
                )
            }
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("channelId") { type = NavType.StringType; defaultValue = "none" },
                navArgument("videoId") { type = NavType.StringType; defaultValue = "none" }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("streamUrl") ?: ""
            val decodedUrl = try {
                java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            } catch (e: Exception) { encodedUrl }
            val channelId = backStackEntry.arguments?.getString("channelId") ?: "none"
            val videoId = backStackEntry.arguments?.getString("videoId") ?: "none"
            PlayerScreen(
                streamUrl = decodedUrl,
                channelId = channelId,
                videoId = videoId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SERVERS) {
            AppShell(navController) { ServersScreen() }
        }

        composable(Routes.SEARCH) {
            AppShell(navController) { SectionPlaceholder(AppSection.SEARCH) }
        }

        composable(Routes.SETTINGS) {
            AppShell(navController) {
                SettingsScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onLogout = {
                        navController.navigate(Routes.PROFILE_PICKER) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.PROFILE_HUB) {
            AppShell(navController) {
                ProfileHubScreen(
                    onOpenProfilePicker = { navController.navigate(Routes.PROFILE_PICKER) },
                    onOpenSettings = { navController.navigateToSection(Routes.SETTINGS) },
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) }
                )
            }
        }

        composable(
            route = Routes.VOD_DETAIL,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "vod"
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            AppShell(navController) {
                VodDetailScreen(
                    onPlay = { url -> navController.navigate("player/${encodeUrl(url)}/none/none") },
                    onOpenEpisodes = {
                        navController.navigate("vod_episodes/$itemId/$contentType")
                    }
                )
            }
        }

        composable(
            route = "vod_episodes/{itemId}/{contentType}",
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) {
            AppShell(navController) {
                VodEpisodesScreen(
                    onPlay = { url -> navController.navigate("player/${encodeUrl(url)}/none/none") },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}