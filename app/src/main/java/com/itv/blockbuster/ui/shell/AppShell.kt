package com.itv.blockbuster.ui.shell

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.itv.blockbuster.ui.navigation.AppSection
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.navigateToSection
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry
import kotlinx.coroutines.delay

private const val TAG = "DpadFocus"

// Rail order (Profile and Settings are pinned separately, top/bottom):
// Profile, Favorites, Recents, Home, Movies, TV Show, Live TV, TV Guide, Adult, Settings.
// Search is intentionally excluded - no Search icon on the rail.
private val RailSections = listOf(
    AppSection.MY_LIST,
    AppSection.RECENT,
    AppSection.HOME,
    AppSection.MOVIES,
    AppSection.TV_SHOWS,
    AppSection.LIVE_TV,
    AppSection.TV_GUIDE,
    AppSection.ADULT
)

// Portrait overlay menu order: Favorites, Recents, Home, Movies, TV Show, Live TV, TV Guide, Adult.
private val MenuSections = listOf(
    AppSection.MY_LIST,
    AppSection.RECENT,
    AppSection.HOME,
    AppSection.MOVIES,
    AppSection.TV_SHOWS,
    AppSection.LIVE_TV,
    AppSection.TV_GUIDE,
    AppSection.ADULT
)

/**
 * Routes that render with no chrome (rail or bottom-nav) at all - the
 * profile picker, the video player, catchup playback, and the VOD detail/
 * episodes screens. See the showChrome computation in AppShell below for
 * how this is applied without disposing/recreating content() itself.
 */
private val NoRailRoutes = setOf(
    Routes.PROFILE_PICKER, Routes.PLAYER, Routes.CATCHUP, Routes.VOD_DETAIL, Routes.VOD_EPISODES
)

/**
 * Routes reached as a "detour" from a browser item click - a VOD poster
 * opening detail/episodes, a channel/poster opening playback directly, or
 * the Adult hub's Live/VOD cards opening their own browser screens - rather
 * than by clicking a rail item. Used by AppShell to detect, on every route
 * change, whether we're landing on a chrome-visible route because we just
 * came from one of these - that detection lives in FocusRegistry (a
 * process-wide singleton) rather than a composable's local remembered
 * state, so it survives regardless of which shell composable happens to be
 * mounted (or, historically, disposed and recreated) at any given moment.
 * When true, RailShell arms the focus-restoration path
 * (FocusRegistry.armRestoreFocus/restoreClickedItemFocus) instead of the
 * usual rail-focus-then-first-item handoff, so focus lands back on the
 * exact browser item (or Adult Live/VOD card) that was clicked to start the
 * detour - whether that detour was one hop (a channel straight into
 * Player) or two (a VOD poster into its detail screen, then Play into
 * Player).
 *
 * Deliberately excludes Routes.PROFILE_PICKER: returning from there is a
 * profile switch, not "back to what I was just looking at", so the normal
 * rail-focus-then-first-item handoff is what's wanted.
 */
private val DetourRoutes = setOf(
    Routes.VOD_DETAIL, Routes.VOD_EPISODES, Routes.PLAYER, Routes.CATCHUP,
    Routes.ADULT_LIVE_TV, Routes.ADULT_VOD_BROWSER
)

/**
 * Routes where Back should behave like ordinary navigation - popping the
 * back stack - rather than RailShell's usual redirect-to-rail, even though
 * (unlike NoRailRoutes) the rail chrome stays visible here: Adult Live/VOD
 * are full browser screens in their own right (channel grids, VOD
 * carousels), so hiding the rail would be inconsistent with Home/Movies/
 * Live TV, but per the requirement, Back from either should return to the
 * Adult hub's Live/VOD selection screen, not just refocus the rail while
 * staying put. VOD_DETAIL/EPISODES/PLAYER/CATCHUP don't need a separate
 * entry here since showChrome (false for all of them, being NoRailRoutes)
 * already disables the BackHandler entirely.
 */
private val BackPopsToParentRoutes = setOf(Routes.ADULT_LIVE_TV, Routes.ADULT_VOD_BROWSER)

@Composable
fun AppShell(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    // Inject AppShellViewModel to activate watchdog lifecycle management
    // FIX: Assign the ViewModel to a variable instead of just calling it
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val showAdult by shellViewModel.displayAdultContent.collectAsState()

    val formFactor = rememberFormFactor()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // FIX: computed here, at the top of AppShell, rather than inside
    // RailShell - this runs for EVERY route change regardless of whether
    // the current or previous route was rail-visible or not, so it
    // correctly observes the FULL sequence of route transitions.
    // remember(route) makes this fire exactly once per distinct route
    // value, like a LaunchedEffect would, without needing a coroutine for a
    // plain synchronous var swap.
    val route = currentRoute ?: Routes.HOME
    val cameFromDetail = remember(route) {
        FocusRegistry.swapLastNavigatedRoute(route) in DetourRoutes
    }

    // FIX: MAJOR - this used to be `if (currentRoute in NoRailRoutes) {
    // content(); return }`, calling content() directly from AppShell for
    // those routes but from deep inside RailShell/PortraitShell's own Row/
    // Column/Box otherwise. That meant content() (the entire NavHost, and
    // therefore EVERY destination's own composition - remember state,
    // scroll positions, everything) was being invoked from a DIFFERENT
    // composable call site depending on the route. Compose can't preserve
    // a composition across a call-site change like that: crossing the
    // NoRailRoutes boundary in either direction silently disposed the
    // whole NavHost and recreated it from scratch, which is what caused
    // browser screens' scroll position resetting to the top and rail focus
    // registrations (built via remember{} inside RailShell) getting wiped
    // out, on every single visit to a NoRailRoutes screen like VOD detail.
    //
    // Fixed by always calling content() from the exact same position for a
    // given form factor (RailShell for landscape/TV, PortraitShell for
    // portrait - that branch is stable, since form factor doesn't change
    // mid-session). showChrome is now a plain boolean that only controls
    // whether the rail column / top+bottom bars render around content() -
    // it no longer determines which parent calls content() itself.
    val showChrome = currentRoute !in NoRailRoutes

    when (formFactor) {
        FormFactor.MOBILE_PORTRAIT -> PortraitShell(navController, currentRoute, showChrome, content, showAdult, shellViewModel)
        else -> RailShell(navController, currentRoute, cameFromDetail, showChrome, content, showAdult, shellViewModel)
    }
}

// =====================================================================
// TV / LANDSCAPE: auto-collapsing left rail
// =====================================================================

@Composable
private fun RailShell(
    navController: NavHostController,
    currentRoute: String?,
    // FIX: computed once in AppShell (see the DetourRoutes doc comment
    // there for why it moved out of this composable's own local state) -
    // true when we're landing on currentRoute because Back just popped a
    // DetourRoutes screen (VOD detail/episodes, player, or catchup).
    cameFromDetail: Boolean,
    // FIX: whether the rail column/BackHandler should be active for the
    // CURRENT route (false for NoRailRoutes screens - player, VOD detail/
    // episodes, profile picker). This composable itself, and therefore
    // content() below, stays mounted continuously across EVERY route
    // change regardless of this flag - only the rail's own visual/
    // interactive presence toggles. See AppShell's showChrome doc comment
    // for why this must NOT be implemented as conditionally calling (or
    // not calling) this whole composable instead.
    showChrome: Boolean,
    content: @Composable () -> Unit,
    showAdult: Boolean,
    shellViewModel: AppShellViewModel
) {
    var railExpanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (!showChrome) 0.dp else if (railExpanded) 280.dp else 84.dp,
        label = "railWidth"
    )

    val visibleRailSections = RailSections.filter { it != AppSection.ADULT || showAdult }

    // D-pad focus: one requester per rail row, registered globally so content
    // screens (via FocusRegistry.focusRail/notifyContentReady) and the D-pad
    // Left/Back handling below can send focus to a specific rail item.
    // Registered in FocusRegistry (a singleton) rather than passed directly,
    // since content screens live in a completely separate part of the
    // composition tree (inside the NavHost) and have no direct reference to
    // these requesters otherwise.
    //
    // FIX: built from the FULL, static RailSections list (not the filtered
    // visibleRailSections), so toggling the Adult profile setting mid-session
    // can never discard and recreate these requesters out from under a rail
    // item that currently holds real focus. Also unconditional on showChrome -
    // this composable (and these remember{} blocks) now stays mounted the
    // whole time, so these requesters are stable and never get recreated out
    // from under anything, unlike before this fix.
    val profileFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val sectionFocusRequesters = remember { RailSections.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) {
        FocusRegistry.registerRail(FocusRegistry.PROFILE_KEY, profileFocusRequester)
        // FIX: keyed by the real route (Routes.SETTINGS), not the
        // FocusRegistry.SETTINGS_KEY constant - see the RailItem below.
        FocusRegistry.registerRail(Routes.SETTINGS, settingsFocusRequester)
        sectionFocusRequesters.forEach { (section, requester) ->
            FocusRegistry.registerRail(section.route, requester)
        }
    }

    // FIX: Every time currentRoute changes - cold start's landing page, OR any
    // later rail-click navigation - explicitly requests focus on that route's
    // rail item, then arms it for auto-advance into content once loaded (see
    // FocusRegistry.notifyContentReady, called from each screen).
    // LaunchedEffect(currentRoute) only restarts when the route value
    // actually changes, so this fires exactly once per real navigation.
    //
    // Retries briefly since the rail item may not be composed/registered yet
    // on the very first frame, then stops as soon as it succeeds once - it
    // deliberately does NOT keep re-asserting focus for the whole loading
    // window, since that would fight the user navigating the rail with the
    // D-pad while the page is still loading.
    //
    // Skipped entirely when !showChrome: NoRailRoutes screens have no
    // corresponding rail item to save/request focus for, and doing so
    // anyway used to pollute lastRailKey with a bogus route string.
    //
    // EXCEPTION: if cameFromDetail (see param doc above), skip the rail-
    // focus-then-first-item handoff entirely and arm the focus-restoration
    // path instead (see FocusRegistry.armRestoreFocus/restoreClickedItemFocus)
    // - the browser screen's own ON_RESUME observer picks this up and puts
    // focus back on the exact item that was clicked to open the detail
    // screen, rather than the rail stealing it away from underneath that
    // restoration attempt.
    LaunchedEffect(currentRoute) {
        if (!showChrome) return@LaunchedEffect
        val route = currentRoute ?: Routes.HOME
        Log.d(TAG, "RailShell: mounted for route=$route - requesting rail focus (cameFromDetail=$cameFromDetail)")
        FocusRegistry.saveLastRailFocus(route)
        if (cameFromDetail) {
            FocusRegistry.armRestoreFocus(route)
            return@LaunchedEffect
        }
        FocusRegistry.armInitialContentFocus(route)
        repeat(20) { attempt ->
            val success = FocusRegistry.requestRail(route)
            Log.d(TAG, "RailShell: requestRail(route=$route) attempt=$attempt success=$success")
            if (success) return@LaunchedEffect
            delay(50)
        }
        Log.d(TAG, "RailShell: gave up requesting focus for route=$route after retries")
    }

    Row(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (showChrome) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(BbSurface)
                .onFocusChanged {
                    if (railExpanded != it.hasFocus) {
                        Log.d(TAG, "RailShell: rail column hasFocus changed to ${it.hasFocus}")
                    }
                    railExpanded = it.hasFocus
                }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BLOCKBUSTER",
                color = BbAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (railExpanded) 18.sp else 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Profile / Change profile
            RailItem(
                icon = Icons.Default.AccountCircle,
                label = "Change Profile",
                expanded = railExpanded,
                selected = false,
                focusRequester = profileFocusRequester,
                focusKey = FocusRegistry.PROFILE_KEY,
                onClick = {
                    FocusRegistry.saveLastRailFocus(FocusRegistry.PROFILE_KEY)
                    navController.navigate(Routes.PROFILE_PICKER)
                }
            )

            // FIX: scrollable middle section so all rail items (now 10)
            // remain reachable on short TV viewports; profile stays pinned
            // at the top and Settings pinned at the bottom.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleRailSections.forEach { section ->
                    key(section.route) {
                        RailItem(
                            icon = section.icon,
                            label = section.label,
                            expanded = railExpanded,
                            selected = currentRoute == section.route,
                            focusRequester = sectionFocusRequesters[section],
                            focusKey = section.route,
                            onClick = {
                                // FIX: Removed destructive popUpTo(inclusive=true) which corrupted the backstack.
                                // Now uses standard navigation and triggers a reset signal for the password dialog.
                                Log.d(TAG, "RailItem click: section=${section.route}")
                                FocusRegistry.saveLastRailFocus(section.route)
                                // The next screen's content should auto-advance focus into it
                                // once loaded, same as the initial landing page does.
                                FocusRegistry.armInitialContentFocus(section.route)
                                navController.navigateToSection(section.route)
                                if (section == AppSection.ADULT) {
                                    shellViewModel.adultSessionManager.triggerUnlockPrompt()
                                }
                            }
                        )
                    }
                }
            }

            // Settings (pinned bottom)
            // FIX: registered/keyed under Routes.SETTINGS (its real route),
            // not the FocusRegistry.SETTINGS_KEY constant - RailShell's
            // per-mount effect below looks up rail items by currentRoute
            // directly, so a mismatched key here meant requestRail(route)
            // could never find this item, and D-pad focus would never land
            // here on navigating to Settings.
            RailItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                expanded = railExpanded,
                selected = currentRoute == Routes.SETTINGS || currentRoute == Routes.SERVERS,
                focusRequester = settingsFocusRequester,
                focusKey = Routes.SETTINGS,
                onClick = {
                    FocusRegistry.saveLastRailFocus(Routes.SETTINGS)
                    navController.navigateToSection(Routes.SETTINGS)
                }
            )
        }
        }

        // FIX: REVERTED - focusGroup()+exit here was a mistake. It doesn't
        // just block D-pad directional search (moveFocus) from escaping to
        // the rail as intended; it also silently interferes with the
        // explicit FocusRegistry.focusRail()/requestFocus() calls Back
        // triggers below. Logs confirmed requestRail() reporting
        // success=true on every Back press, yet the rail's own
        // onFocusChanged never fired until several presses later - i.e.
        // focus was never actually landing there despite the API claiming
        // success. Back to the plain content Box; the boundary-blocking for
        // Up/Down escaping to the rail is handled per-item instead (see
        // CarouselRow's isLastRow, PosterGrid, ChannelCarouselRow, etc.).
        //
        // FIX: this Box - and therefore content() - is now UNCONDITIONAL,
        // always rendered at this exact same position in the composition
        // tree regardless of showChrome. See AppShell's showChrome doc
        // comment for why that's essential (content() must never move
        // between different composable call sites).
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }

    // NEW: two rapid Back presses (within 2s) from any browser page or the
    // rail menu exits the app outright, matching the "Home button
    // terminates, doesn't minimize" behavior in MainActivity for
    // consistency - rather than just leaving the user bounced back to the
    // rail with no way to actually leave via Back. lastBackPressTime is a
    // plain remember{} (not rememberSaveable) - safe since RailShell now
    // stays mounted continuously for the whole session (see showChrome/
    // content() call-site-stability fix above), so it's never reset
    // out from under this by an unrelated navigation.
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // FIX: Back button, or D-pad Left from the leftmost item in the content
    // area (see NetflixStyleCarousel), returns focus to the rail instead of
    // popping the back stack - landing on whichever rail item was last
    // focused/selected, exactly like Left does.
    //
    // MUST be declared AFTER the Row/content() above, not before: since
    // AppShell now wraps the NavHost exactly once, NavHost's own internal
    // back-press handling is registered somewhere inside content() - i.e.
    // deeper in this composable's tree than this call. Compose's
    // OnBackPressedDispatcher gives priority to whichever BackHandler was
    // registered LAST, and registration follows composition/call order.
    // Placing this call before content() meant NavHost's own handler
    // registered after ours and won every time, silently popping the real
    // back stack (which, thanks to navigateToSection()'s standard
    // popUpTo(startDestination){saveState} + restoreState pattern, always
    // has Home sitting directly beneath whichever tab you're on) - landing
    // on Home regardless of which tab you were actually browsing, and
    // regardless of whether the rail already had focus. Declaring it after
    // content() instead makes this the LAST-registered handler, so it wins.
    //
    // Deliberately NOT gated by `!railExpanded`: calling focusRail() again
    // when the rail already has focus is a harmless no-op, so it's safe (and
    // necessary, per the above) to leave this unconditionally enabled for
    // every rail-visible route.
    //
    // Disabled whenever !showChrome (NoRailRoutes screens - player, catchup,
    // VOD detail/episodes, profile picker): there's no rail to redirect
    // focus to, and these screens should behave like ordinary navigation -
    // popping the back stack - which is exactly what happens when this
    // handler is disabled, removing it from the dispatcher's active set
    // entirely and letting NavHost's own (earlier-registered) back handling
    // take over.
    //
    // ALSO disabled on BackPopsToParentRoutes (Adult Live/VOD): these keep
    // the rail visible (showChrome stays true), but per the requirement,
    // Back from either should pop back to the Adult hub's selection screen
    // rather than just refocus the rail while staying put.
    BackHandler(enabled = showChrome && currentRoute !in BackPopsToParentRoutes) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000L) {
            Log.d(TAG, "RailShell: rapid double Back - exiting app")
            (context as? Activity)?.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "RailShell: Back pressed - returning focus to rail (railExpanded=$railExpanded)")
            FocusRegistry.focusRail()
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    // D-pad focus: this item's key in FocusRegistry, reported on every real
    // focus change so the initial-focus retry loop (and anything else) can
    // tell exactly which rail item currently has focus, rather than only
    // knowing "some rail item does."
    focusKey: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val pillColor by animateColorAsState(
        targetValue = when {
            focused -> BbAccent
            selected -> BbAccent.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        label = "pill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(50))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(pillColor)
            .then(
                if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (focusKey != null) FocusRegistry.reportRailFocus(focusKey, it.isFocused)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) BbTextPrimary else if (selected) BbAccent else BbTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = if (focused) BbTextPrimary else BbTextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =====================================================================
// PORTRAIT: top bar + overlay menu + bottom bar
// =====================================================================

@Composable
private fun PortraitShell(
    navController: NavHostController,
    currentRoute: String?,
    // FIX: see RailShell's showChrome param doc - same reasoning here, this
    // composable (and content() inside it) now always stays mounted;
    // showChrome only toggles the top bar and bottom nav around it.
    showChrome: Boolean,
    content: @Composable () -> Unit,
    showAdult: Boolean,
    shellViewModel: AppShellViewModel
) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentLabel = when (currentRoute) {
        Routes.PROFILE_HUB -> "Profile"
        Routes.SETTINGS -> "Settings"
        Routes.SERVERS -> "Portals"
        else -> AppSection.values()
            .firstOrNull { it.route == currentRoute }?.label ?: "Home"
    }

    Column(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        // Top bar
        if (showChrome) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BbBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BLOCKBUSTER",
                color = BbAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel,
                    color = BbTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open menu",
                    tint = BbTextPrimary
                )
            }
        }
        }

        // Content + overlay menu. FIX: unconditional - see showChrome doc
        // comment on RailShell for why content() must never move between
        // different composable call sites.
        Box(modifier = Modifier.weight(1f)) {
            content()
            if (menuOpen) {
                OverlayMenu(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        menuOpen = false
                        navController.navigateToSection(route)
                        if (route == Routes.ADULT) {
                            shellViewModel.adultSessionManager.triggerUnlockPrompt()
                        }
                    },
                    onClose = { menuOpen = false },
                    showAdult = showAdult
                )
            }
        }

        // Bottom bar: Home / Search / Profile
        if (showChrome) {
        NavigationBar(containerColor = BbSurface, modifier = Modifier.height(78.dp)) {
            NavigationBarItem(
                selected = currentRoute == Routes.HOME,
                onClick = { navController.navigateToSection(Routes.HOME) },
                icon = { Icon(AppSection.HOME.icon, "Home") },
                label = { Text("Home", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.SEARCH,
                onClick = { navController.navigateToSection(Routes.SEARCH) },
                icon = { Icon(Icons.Default.Search, "Search") },
                label = { Text("Search", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.PROFILE_HUB,
                onClick = { navController.navigateToSection(Routes.PROFILE_HUB) },
                icon = { Icon(Icons.Default.AccountCircle, "Profile") },
                label = { Text("Profile", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
        }
        }
    }
}

@Composable
private fun OverlayMenu(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    showAdult: Boolean
) {
    // NEW: Filter out Adult if setting is off
    val visibleMenuSections = MenuSections.filter { it != AppSection.ADULT || showAdult }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        visibleMenuSections.forEach { section ->
            val active = currentRoute == section.route
            Text(
                text = section.label,
                color = if (active) BbAccent else BbTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(section.route) }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClose)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "Close menu", tint = Color.White)
        }
    }
}
