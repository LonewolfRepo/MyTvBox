package com.itv.blockbuster.ui.navigation

import android.content.res.Configuration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
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
import com.itv.blockbuster.ui.vod.VodEpisodesScreen
import com.itv.blockbuster.ui.vod.VodDetailScreen
import com.itv.blockbuster.ui.adult.AdultHubScreen
import com.itv.blockbuster.data.session.AdultSessionManager
import kotlinx.coroutines.CoroutineScope
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
    const val VOD_EPISODES = "vod_episodes/{itemId}/{contentType}"
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
 * UPDATED (player-open latency): this used to await StreamValidator.isReachable(url)
 * - a full extra network round trip - BEFORE navigating, on every single
 * playback attempt, purely so a failure could show "Stream Not Available"
 * instead of a flash of an empty player screen. That meant every
 * SUCCESSFUL playback (the common case) also paid for that round trip
 * before the player even opened.
 *
 * Now navigation is immediate. The player screen itself shows a
 * "Connecting..." overlay while ExoPlayer buffers (see PlayerScreen's
 * DisposableEffect/Player.Listener.onPlayerError), and pops back out with
 * the same "Stream Not Available" toast if playback actually fails -
 * giving the same failure-case UX without taxing the success case with a
 * network call it doesn't need.
 */
private fun NavHostController.navigateToPlayerIfReachable(
    scope: CoroutineScope,
    context: android.content.Context,
    url: String,
    channelId: String = "none",
    videoId: String = "none"
) {
    navigate("player/${encodeUrl(url)}/$channelId/$videoId")
}

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
    val context = LocalContext.current
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

    // FIX: AppShell (the rail) now wraps the WHOLE NavHost exactly once, instead
    // of being instantiated separately inside every composable(route) { } block
    // below. That old per-destination-wrapper pattern meant every navigation
    // created a brand-new AppShell/RailShell subtree - and since
    // navigateToSection() uses the standard popUpTo(startDestination){saveState}
    // + restoreState bottom-nav pattern, which always collapses the back stack
    // down to [Home, target] and keeps the outgoing AND incoming destinations
    // composed simultaneously for the crossfade, that meant a genuinely fresh
    // "Home" RailShell instance was alive at the same moment as the real
    // target's RailShell instance on every single tab switch. Both instances
    // wrote to the same singleton FocusRegistry state (pendingContentFocusRoute
    // in particular), and whichever one's LaunchedEffect happened to finish
    // last won - which is exactly what caused the focus hand-off to
    // intermittently get silently overwritten back to "home".
    //
    // With a single persistent RailShell for the app's lifetime, its
    // LaunchedEffect(currentRoute) restarts exactly once per real navigation,
    // with no possibility of a duplicate "ghost" instance racing it. AppShell
    // decides internally (via NoRailRoutes) whether to actually render the
    // rail for the current route - full-screen routes like the profile picker
    // and video player render only their own content.
    AppShell(navController) {
        // FIX ("going from browsing to VOD details feels like waiting for
        // the rail to collapse, then snapping to the new screen"): NavHost
        // previously had no transition at all, so the actual content swap
        // was instant - the only thing that visibly animated during that
        // navigation was the rail itself shrinking from RailCollapsedWidth
        // to 0dp (VOD detail/episodes are NoRailRoutes, so showChrome flips
        // false and railWidth's default spring animates it away). One
        // animated thing plus one instant thing reads as two sequential
        // steps rather than one motion. A plain crossfade, tuned to roughly
        // the same duration as the rail's own default spring settle time,
        // means both the rail collapsing and the new screen arriving happen
        // together instead of the content swap looking like an abrupt
        // afterthought once the rail finishes.
        val screenTransitionSpec = tween<Float>(durationMillis = 260)
        NavHost(
            navController = navController,
            // CHANGE 2: when the picker is skipped, open the configured section directly
            startDestination = if (startAtPicker) Routes.PROFILE_PICKER else landingRoute,
            enterTransition = { fadeIn(animationSpec = screenTransitionSpec) },
            exitTransition = { fadeOut(animationSpec = screenTransitionSpec) },
            popEnterTransition = { fadeIn(animationSpec = screenTransitionSpec) },
            popExitTransition = { fadeOut(animationSpec = screenTransitionSpec) }
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
                HomeScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") },
                    route = Routes.HOME,
                    contentType = "home"
                )
            }
            // NEW: Movies and TV Shows now reuse HomeScreen/HomeViewModel
            // directly (same hero banner, 2-row viewport, search, filters,
            // pagination reachability fixes, etc.) instead of the separate
            // VodBrowserScreen/VodBrowserViewModel, which had fallen behind
            // on all of that. contentType is the only thing that actually
            // differs - HomeViewModel filters fetched items by isSeries and
            // reads the SAME "order_vod"/"order_series" category
            // sort/visibility settings VodBrowserViewModel already used, so
            // existing user customization carries over unchanged. VodBrowserScreen/
            // VodBrowserViewModel are left in place but unused rather than
            // deleted, in case of an issue that needs a quick revert.
            composable(Routes.MOVIES) {
                HomeScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") },
                    route = Routes.MOVIES,
                    contentType = "vod"
                )
            }
            composable(Routes.TV_SHOWS) {
                HomeScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") },
                    route = Routes.TV_SHOWS,
                    contentType = "series"
                )
            }
            composable(Routes.LIVE_TV) {
                LiveTvScreen(
                    onPlayChannel = { url, channelId -> navController.navigateToPlayerIfReachable(scope, context, url, channelId = channelId) },
                    onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") }
                )
            }
            composable(Routes.TV_GUIDE) {
                TvGuideScreen(
                    onPlayLive = { url, channelId -> navController.navigateToPlayerIfReachable(scope, context, url, channelId = channelId) },
                    onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") }
                )
            }
            composable(
                route = Routes.CATCHUP,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) {
                CatchupScreen(onPlay = { url -> navController.navigateToPlayerIfReachable(scope, context, url) })
            }
            composable(Routes.MY_LIST) {
                FavoritesHubScreen(
                    onPlayLive = { url, channelId ->
                        navController.navigateToPlayerIfReachable(scope, context, url, channelId = channelId)
                    },
                    onOpenVod = { itemId, type ->
                        navController.navigate("vod_detail/$itemId/$type")
                    }
                )
            }
            composable(Routes.RECENT) {
                RecentsHubScreen(
                    onPlayLive = { url, channelId ->
                        navController.navigateToPlayerIfReachable(scope, context, url, channelId = channelId)
                    },
                    onOpenVod = { itemId, type ->
                        navController.navigate("vod_detail/$itemId/$type")
                    }
                )
            }
            composable(Routes.ADULT) {
                AdultHubScreen(
                    onNavigateToLive = { navController.navigate(Routes.ADULT_LIVE_TV) },
                    onNavigateToVod = { navController.navigate(Routes.ADULT_VOD_BROWSER) }
                )
            }
            composable(Routes.ADULT_LIVE_TV) {
                // FIX: Guard against the password prompt being bypassed. Because Adult
                // uses the same save/restoreState bottom-nav pattern as the other tabs,
                // navigating away and back can restore this destination directly
                // (skipping AdultHubScreen entirely). This gate re-checks the unlock
                // state every time this destination is (re)composed and bounces back
                // to the password screen if it isn't unlocked.
                AdultGate(navController, adultSessionManager, Routes.ADULT_LIVE_TV) {
                    LiveTvScreen(
                        onPlayChannel = { url, channelId -> navController.navigateToPlayerIfReachable(scope, context, url, channelId = channelId) },
                        onOpenCatchup = { channelId -> navController.navigate("catchup/$channelId") },
                        route = Routes.ADULT_LIVE_TV
                    )
                }
            }
            // NOTE: uses HomeScreen (not a separate VodBrowserScreen) so Adult VOD
            // gets the same hero/carousel UI as the rest of the app - same pattern
            // Movies/TV Shows now use too (see Routes.MOVIES/TV_SHOWS above).
            composable(Routes.ADULT_VOD_BROWSER) {
                // FIX: same unlock gate as Adult Live TV above - see comment there.
                AdultGate(navController, adultSessionManager, Routes.ADULT_VOD_BROWSER) {
                    HomeScreen(
                        onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                        onOpenVodDetail = { itemId, type -> navController.navigate("vod_detail/$itemId/$type") },
                        route = Routes.ADULT_VOD_BROWSER
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
                ServersScreen()
            }
            composable(Routes.SEARCH) {
                SectionPlaceholder(AppSection.SEARCH)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenPortals = { navController.navigateToSection(Routes.SERVERS) },
                    onLogout = {
                        navController.navigate(Routes.PROFILE_PICKER) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.PROFILE_HUB) {
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
            composable(
                route = Routes.VOD_DETAIL,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("contentType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val contentType = backStackEntry.arguments?.getString("contentType") ?: "vod"
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                VodDetailScreen(
                    onPlay = { url -> navController.navigateToPlayerIfReachable(scope, context, url) },
                    onOpenEpisodes = {
                        navController.navigate("vod_episodes/$itemId/$contentType")
                    }
                )
            }
            composable(
                route = Routes.VOD_EPISODES,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("contentType") { type = NavType.StringType }
                )
            ) {
                VodEpisodesScreen(
                    onPlay = { url -> navController.navigateToPlayerIfReachable(scope, context, url) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}