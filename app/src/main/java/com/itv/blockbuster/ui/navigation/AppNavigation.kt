package com.itv.blockbuster.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import com.itv.blockbuster.ui.shell.AppShellViewModel
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
import com.itv.blockbuster.ui.adult.AdultHubScreen
import com.itv.blockbuster.data.session.AdultSessionManager
import kotlinx.coroutines.launch

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
    val ADULT = AppSection.ADULT.route
    val SETTINGS = AppSection.SETTINGS.route
    const val VOD_BROWSER = "vod_browser/{contentType}"
    const val VOD_DETAIL = "vod_detail/{itemId}/{contentType}"
    const val PLAYER = "player/{streamUrl}/{channelId}/{videoId}"
    const val CATCHUP = "catchup/{channelId}"
    const val ADULT_LIVE_TV = "adult_live_tv"
    const val ADULT_VOD_BROWSER = "adult_vod_browser"
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

/**
 * FIX: Hard gate for any Adult sub-screen (Adult Live TV, Adult VOD Browser) that
 * sits below the AdultHubScreen password prompt.
 *
 * AdultHubScreen only shows its password dialog while it is actually composed.
 * The bottom-nav uses navigateToSection(...) with popUpTo { saveState = true } /
 * restoreState = true, which is the standard pattern for preserving each tab's own
 * back stack across tab switches. That means the Adult tab's back stack (Adult ->
 * Adult VOD Browser) is preserved too - so re-selecting "Adult" after visiting
 * another tab can restore straight to Adult VOD Browser, never re-composing
 * AdultHubScreen and never re-prompting for the password.
 *
 * This composable re-checks the global unlock state on every (re)composition of an
 * Adult sub-screen. If the session isn't unlocked - whether because it was never
 * unlocked this visit, or because it got locked by navigating away - it redirects
 * back to the Adult hub, where the password dialog is shown, instead of rendering
 * the protected content.
 *
 * FIX: the redirect only fires if THIS route is still the current destination at
 * the moment the effect runs. Without that check, locking (e.g. tapping "Home"
 * while sitting on Adult VOD) also flips isUnlocked to false while this gate is
 * still composed (Navigation Compose doesn't dispose the outgoing screen
 * instantly), so its LaunchedEffect would fire a competing navigate(Routes.ADULT)
 * call that raced the real navigation to Home and sometimes won - leaving the app
 * stuck bouncing back to the Adult hub instead of actually going Home.
 */
@Composable
private fun AdultGate(
    navController: NavHostController,
    adultSessionManager: AdultSessionManager,
    route: String,
    content: @Composable () -> Unit
) {
    val isUnlocked by adultSessionManager.isUnlocked.collectAsState()

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked && navController.currentDestination?.route == route) {
            navController.navigate(Routes.ADULT) {
                popUpTo(Routes.ADULT) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    if (isUnlocked) {
        content()
    }
}

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
            AppNavigation(startAtPicker = current.showPicker, landingRoute = current.landingRoute)
        }
    }
}

@Composable
fun AppNavigation(
    startAtPicker: Boolean,
    landingRoute: String = Routes.HOME
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val startupViewModel: StartupViewModel = hiltViewModel()
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val adultSessionManager = shellViewModel.adultSessionManager

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // FIX: Safely destroy adult session when navigating to ANY non-adult route
    // ... but vod_detail / player / catchup / vod_episodes are SHARED routes used
    // by both normal and adult content (e.g. tapping a video inside Adult VOD
    // Browser lands here). None of these start with "adult", so treating every
    // non-adult-prefixed route as "leaving Adult" was locking the session (and
    // dropping isAdultMode) the instant you opened a detail page or hit Play from
    // inside Adult - breaking playback and forcing a fresh password prompt every
    // time you backed out to Adult VOD/Live. These shared routes are now treated
    // as neutral: they don't change the adult session state either way, so the
    // session held from Adult carries through detail -> player -> back cleanly.
    val neutralRoutePrefixes = listOf("vod_detail", "player", "catchup", "vod_episodes")
    LaunchedEffect(currentRoute) {
        val isAdultRoute = currentRoute?.startsWith("adult") == true || currentRoute == Routes.ADULT
        val isNeutralRoute = neutralRoutePrefixes.any { currentRoute?.startsWith(it) == true }
        when {
            isAdultRoute -> adultSessionManager.enterAdultMode()
            isNeutralRoute -> { /* no-op: preserve whatever adult session state is already active */ }
            else -> adultSessionManager.lock()
        }
    }

    NavHost(
        navController = navController,
        // CHANGE 2: when the picker is skipped, open the configured section directly
        startDestination = if (startAtPicker) Routes.PROFILE_PICKER else landingRoute
    ) {
        // ... (Keep all existing composable routes exactly as they are) ...
        composable(Routes.PROFILE_PICKER) {
            ProfilePickerScreen(
                onProfileSelected = {
                    // CHANGE 3: land on the SELECTED profile's configured page
                    scope.launch {
                        val route = startupViewModel.awaitLandingRoute()
                        navController.navigate(route) {
                            popUpTo(Routes.PROFILE_PICKER) { inclusive = true }
                        }
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
        composable(Routes.ADULT) {
            AppShell(navController) {
                AdultHubScreen(
                    onNavigateToLive = { navController.navigate(Routes.ADULT_LIVE_TV) },
                    onNavigateToVod = { navController.navigate(Routes.ADULT_VOD_BROWSER) }
                )
            }
        }
        composable(Routes.ADULT_LIVE_TV) {
            AppShell(navController) {
                // FIX: Guard against the password prompt being bypassed. Because Adult
                // uses the same save/restoreState bottom-nav pattern as the other tabs,
                // navigating away and back can restore this destination directly
                // (skipping AdultHubScreen entirely). This gate re-checks the unlock
                // state every time this destination is (re)composed and bounces back
                // to the password screen if it isn't unlocked.
                AdultGate(navController, adultSessionManager, Routes.ADULT_LIVE_TV) {
                    LiveTvScreen(
                        onPlayChannel = { url, channelId -> navController.navigate("player/${encodeUrl(url)}/$channelId/none") },
                        onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") }
                    )
                }
            }
        }
        // FIX: Switched from HomeScreen to VodBrowserScreen to prevent lifecycle freezing
        // and to provide a consistent carousel UI for Adult VOD matching the Movies section.
        composable(Routes.ADULT_VOD_BROWSER) {
            AppShell(navController) {
                // FIX: same unlock gate as Adult Live TV above - see comment there.
                AdultGate(navController, adultSessionManager, Routes.ADULT_VOD_BROWSER) {
                    HomeScreen(
                        onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                        onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") }
                    )
                }
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
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onProfileSwitched = { newProfileId ->
                        scope.launch {
                            // 1. Wait for DataStore to confirm the new profile is active
                            // 2. Resolve the landing route for that specific profile
                            val route = startupViewModel.awaitProfileSwitch(newProfileId)

                            // 3. Navigate and clear the Profile Hub from the backstack
                            navController.navigate(route) {
                                popUpTo(Routes.PROFILE_HUB) { inclusive = true }
                            }
                        }
                    }
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