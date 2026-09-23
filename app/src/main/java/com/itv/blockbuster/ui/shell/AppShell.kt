package com.itv.blockbuster.ui.shell

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
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
import com.itv.blockbuster.R
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.ui.theme.RailCollapsedWidth
import com.itv.blockbuster.util.FocusRegistry
import com.itv.blockbuster.util.safeFocusEscape
import kotlinx.coroutines.delay

private const val TAG = "DpadFocus"
// Same rationale as FocusRegistry's own log() helper (see its doc comment):
// lambda-gated so the string interpolation itself is skipped when disabled,
// not just the log write. Kept as a separate local helper rather than
// sharing FocusRegistry's (which is private to that file) to avoid adding
// public API surface just for logging.
private const val RAIL_LOGGING_ENABLED = false
private inline fun log(message: () -> String) {
    if (RAIL_LOGGING_ENABLED) Log.d(TAG, message())
}

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

    // FIX (root cause of the mobile fullscreen-landscape video bug, found via
    // your own bisection to this file): rememberFormFactor() derives from
    // Configuration.orientation. On mobile, entering the player deliberately
    // forces the device into landscape (see PlayerScreen's
    // SCREEN_ORIENTATION_SENSOR_LANDSCAPE) - which flips formFactor from
    // MOBILE_PORTRAIT to MOBILE_LANDSCAPE mid-session. The `when (formFactor)`
    // branch below picks between PortraitShell and RailShell - two entirely
    // different composable functions - so that flip DISPOSES PortraitShell's
    // whole subtree (which contains content(): the entire NavHost, therefore
    // PlayerScreen, therefore its AndroidView/PlayerView/SurfaceView) and
    // mounts RailShell fresh, which calls content() again from scratch. That
    // is a real Activity-level teardown-and-rebuild of the video surface
    // happening seconds into playback, every single time a mobile session
    // starts in portrait and opens the player - completely independent of
    // anything in PlayerScreen or PlayerView itself. It explains every
    // symptom that was chased there: a genuinely fresh SurfaceView each
    // time, a clean decoder attach each time, audio uninterrupted (the
    // player instance itself lives in the ViewModel-scoped PlaybackManager,
    // not the View layer, so it survives the shell swap) - and no video,
    // because the surface backing it never survives the swap.
    //
    // This mirrors, one level up, the exact class of bug the showChrome
    // refactor above already fixed WITHIN a single shell (content() moving
    // between call sites when currentRoute crossed the NoRailRoutes
    // boundary) - except here it's the CHOICE OF SHELL ITSELF moving
    // content() between two entirely different call sites.
    //
    // Fix: freeze which shell is active while the player (or catchup, which
    // forces the same orientation lock) is the current route, instead of
    // reacting to formFactor live. The player's own forced orientation
    // change should never be able to swap shells out from under it - only a
    // REAL form-factor change (e.g. actually rotating while browsing, not
    // triggered by entering the player) should do that.
    val liveFormFactor = rememberFormFactor()
    var formFactor by remember { mutableStateOf(liveFormFactor) }
    LaunchedEffect(liveFormFactor, currentRoute) {
        if (currentRoute != Routes.PLAYER && currentRoute != Routes.CATCHUP) {
            formFactor = liveFormFactor
        }
    }

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
    // FIX ("navigating back from VOD detail: rail comes back expanded,
    // then immediately collapses"): when a detail screen's focused item is
    // disposed on Back, Compose needs SOMETHING focused immediately and its
    // own fallback focus search was landing on the rail for a moment -
    // genuinely gaining real focus, so railExpanded flipped true - before
    // FocusRegistry.restoreClickedItemFocus's retry loop (started
    // separately, from the screen's own ON_RESUME) finished resolving and
    // moved focus to the actual previously-clicked item a beat later. That
    // transient landing was real focus, not a bug in the handoff itself,
    // but it's not a genuine "user is browsing the rail" moment either.
    //
    // railHasRawFocus tracks the actual hasFocus state every time it
    // changes; railExpanded only follows it into TRUE after a short
    // debounce confirms focus is still there - if it's stolen away again
    // (as in the transient case above) before the debounce elapses, this
    // LaunchedEffect gets cancelled and restarted (its key changed) before
    // ever setting railExpanded, so the rail never visibly flashes at all.
    // Losing focus always collapses immediately, with no debounce - only
    // the expand reaction is delayed.
    //
    // PHASE 2 FOLLOW-UP ("rail still opens up as expanded when the app is
    // launched and content is loading"): the debounce above assumes
    // whatever claimed the rail will be superseded quickly - true for the
    // cameFromDetail transient touch it was built for (a Compose fallback
    // landing there for a moment), but NOT true for the one-time cold-start
    // claim (see the LaunchedEffect(Unit) below): HomeViewModel's initial
    // connect+load genuinely takes SECONDS, far outlasting any reasonable
    // debounce window, so the debounce's own logic correctly (by its own
    // rules) judged that long-held focus as "not transient" and expanded
    // the rail anyway - for the entire loading duration.
    //
    // A duration-based heuristic can't distinguish "held briefly by
    // accident" from "held for a while because loading is just slow" - both
    // look identical to a timer. So this claim needs to be suppressed
    // EXPLICITLY instead, regardless of how long it lasts: railSuppressExpansion
    // is set true immediately before the cold-start claim's own
    // requestFocus() call (a claim we make ourselves and can mark
    // accordingly), and forces railExpanded to stay false the whole time
    // that focus persists, however long that takes - the debounce timer
    // below is simply skipped while this is set. It's cleared the moment
    // focus next leaves the rail (whether content steals it away, or
    // anything else), so it never suppresses any FUTURE, genuine focus
    // event - only this one specific claim.
    var railSuppressExpansion by remember { mutableStateOf(false) }
    var railHasRawFocus by remember { mutableStateOf(false) }
    LaunchedEffect(railHasRawFocus) {
        if (railHasRawFocus) {
            if (railSuppressExpansion) {
                railExpanded = false
            } else {
                delay(120)
                if (railHasRawFocus && !railSuppressExpansion) railExpanded = true
            }
        } else {
            railExpanded = false
        }
    }
    val railWidth by animateDpAsState(
        targetValue = if (!showChrome) 0.dp else if (railExpanded) 280.dp else RailCollapsedWidth,
        // FIX (Grok-identified: "VOD detail open looks stuck mid-way, then
        // snaps full-screen"): was the default spring, uncoordinated with
        // NavHost's own 260ms crossfade (see AppNavigation.kt) - the rail
        // and the screen transition were settling at different, unrelated
        // times. An explicit tween matching that same duration means both
        // finish together instead of one lagging the other.
        animationSpec = tween(260),
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
        // PHASE 2 (D-pad focus redesign - "app should start on the landing
        // page in a TRUE collapsed state, not fake-opening and immediately
        // collapsing"): this LaunchedEffect(Unit) fires exactly once for
        // the whole app session (this composable stays mounted the entire
        // time - see the FIX comment above). Android's D-pad needs
        // SOMETHING focused to have anywhere to send key events at all,
        // and at this exact moment nothing else has composed yet - so the
        // rail's own first section item claims focus once, here, as the
        // purely functional cold-start anchor. This is the ONLY place the
        // rail ever proactively claims focus now - LaunchedEffect(
        // currentRoute) below no longer does, for any subsequent
        // navigation - so there's no repeated flash to suppress, only this
        // one, single, genuinely-necessary claim.
        //
        // railSuppressExpansion is set true here, immediately before the
        // claim, so railExpanded stays false for however long this
        // specific claim persists - see its doc comment above for why the
        // debounce ALONE isn't enough here (an earlier version of this fix
        // assumed content would supersede this within a frame or two, but
        // cold-start loading genuinely takes seconds, so the rail was
        // visibly expanding for the whole loading window before content
        // finally claimed focus away).
        // FIX ("focus temporarily lands on Favorites while content loads,
        // instead of the landing page's rail item"): this used to claim
        // RailSections.firstOrNull() unconditionally - but RailSections is
        // ordered for rail DISPLAY/navigation purposes (Favorites, Recent,
        // Home, Movies, ...), not "which section is the actual landing
        // page". AppSection.MY_LIST (Favorites) being first in that list
        // meant the cold-start claim always landed there, regardless of
        // what the app actually opens to. Matching against currentRoute
        // instead finds whichever section genuinely corresponds to the
        // real landing destination - falling back to HOME specifically
        // (the overwhelmingly common landing page) if that route doesn't
        // exactly match any static section route for some reason, and only
        // as an absolute last resort to the first list entry so this can
        // never end up with no target at all.
        val landingSection = RailSections.firstOrNull { it.route == currentRoute }
            ?: RailSections.firstOrNull { it.route == Routes.HOME }
            ?: RailSections.firstOrNull()
        railSuppressExpansion = true
        landingSection?.let { section ->
            sectionFocusRequesters[section]?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    // PHASE 2 (D-pad focus redesign): every time currentRoute changes - cold
    // start's landing page, OR any later rail-click navigation - arms that
    // route for content's own auto-advance-into-first-item once loaded (see
    // FocusRegistry.notifyContentReady, called from each screen). It does
    // NOT request rail focus itself anymore for this case - see this
    // block's body for why. LaunchedEffect(currentRoute) only restarts when
    // the route value actually changes, so this fires exactly once per real
    // navigation.
    //
    // Skipped entirely when !showChrome: NoRailRoutes screens have no
    // corresponding rail item to save focus for, and doing so anyway used
    // to pollute lastRailKey with a bogus route string.
    //
    // EXCEPTION: if cameFromDetail (see param doc above), skip the arm-for-
    // content step entirely and arm the focus-restoration path instead (see
    // FocusRegistry.armRestoreFocus/restoreClickedItemFocus) - the browser
    // screen's own ON_RESUME observer picks this up and puts focus back on
    // the exact item that was clicked to open the detail screen.
    LaunchedEffect(currentRoute) {
        if (!showChrome) return@LaunchedEffect
        val route = currentRoute ?: Routes.HOME
        log { "RailShell: mounted for route=$route (cameFromDetail=$cameFromDetail)" }
        FocusRegistry.saveLastRailFocus(route)
        if (cameFromDetail) {
            FocusRegistry.armRestoreFocus(route)
            return@LaunchedEffect
        }
        // Still armed here - screens' own notifyContentReady()/
        // focusFirstItem() calls are hard-gated on this (see
        // FocusRegistry.notifyContentReady's pendingContentFocusRoute
        // check) and haven't been migrated to the new FocusEntry mechanism
        // yet (Phase 3+ of the D-pad focus redesign) - removing this call
        // would silently break every screen's existing focus-on-load
        // behavior, not just the rail's.
        FocusRegistry.armInitialContentFocus(route)
        // PHASE 2 (D-pad focus redesign - "never use rail as a fallback
        // mechanism"): previously this fell through to a grace-period wait
        // and then a repeat(20) x 50ms retry loop explicitly requesting
        // rail focus on EVERY navigation, even to an already-cached
        // destination with nothing to wait for - which is exactly the
        // "rail as fallback" pattern this phase eliminates. The rail no
        // longer proactively competes for focus here at all: it already
        // holds focus from the one-time cold-start claim (see the
        // LaunchedEffect(Unit) below) or from a previous screen, and
        // content's own notifyContentReady()/focusFirstItem() calls (armed
        // just above, called from each screen's own effects) take it over
        // the moment they're ready - with no race to referee and nothing
        // for the rail to do on a normal navigation.
    }

    // FIX (Grok-identified: same root cause as railWidth's animationSpec
    // above): this used to be `if (showChrome) RailCollapsedWidth else
    // 0.dp` with no animation at all - an instant jump the moment showChrome
    // flipped, completely decoupled from railWidth's own animated value.
    // Content would snap to full width immediately while the rail was
    // still visually present/shrinking, which is exactly the "new screen
    // already expanded while the old one is still fading" mid-transition
    // look. Deriving this from railWidth itself (already animated, now on
    // the same tween(260) as above) means content's reserved space shrinks
    // in lockstep with the rail, not ahead of it.
    val contentStartPadding = railWidth.coerceAtMost(RailCollapsedWidth)

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        // NEW: content is declared FIRST (drawn behind the rail, which
        // comes after it below) and reserves a FIXED left offset equal to
        // the rail's COLLAPSED width always - not the live/animating
        // railWidth. Previously this was a Row with the rail and a
        // weight(1f) content Box as siblings, so content's own available
        // width shrank every time the rail expanded, causing the whole
        // screen to visibly reflow. With a fixed offset instead, content
        // never resizes at all; when the rail expands (see the Column
        // below, drawn on top), it simply draws OVER the leftmost portion
        // of that already-stable content area instead of pushing it aside.
        //
        // FIX: this Box - and therefore content() - is now UNCONDITIONAL,
        // always rendered at this exact same position in the composition
        // tree regardless of showChrome. See AppShell's showChrome doc
        // comment for why that's essential (content() must never move
        // between different composable call sites).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding)
        ) {
            content()
        }

        // FIX (Grok-identified, same investigation as above): was gated on
        // the raw `showChrome` boolean, so this Column - and railWidth's
        // whole animation - vanished from composition the INSTANT
        // showChrome flipped false, before the shrink animation above ever
        // had a chance to actually play. Gating on railWidth > 0.dp instead
        // keeps it composed (and visibly shrinking) for the full duration
        // of the animation, only actually disappearing once it's genuinely
        // reached zero.
        if (showChrome || railWidth > 0.dp) {
            // FIX: computed once per recomposition and passed to every RailItem
            // below - see RailItem's rightEscapeTarget doc comment for why this
            // explicit override is now needed (the rail overlays content rather
            // than resizing it, so content's first item can be spatially
            // covered by the expanded rail's wider bounds).
            val rightEscapeTarget = FocusRegistry.firstItemTarget(currentRoute ?: Routes.HOME)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(railWidth)
                    .fillMaxHeight()
                    // NEW: translucent instead of a solid fill, so whatever's
                    // playing/showing behind the rail (e.g. the hero banner on
                    // Home) subtly shows through rather than being fully
                    // obscured by a flat panel.
                    .background(BbSurface.copy(alpha = 0.72f))
                    .onFocusChanged {
                        railHasRawFocus = it.hasFocus
                        if (it.hasFocus) {
                            // TEMPORARY DIAGNOSTIC (remove once the remaining
                            // "focus falls on rail after searching + switching
                            // to All Categories" investigation is resolved):
                            // logs every time the rail actually gains focus,
                            // and whether isContentTransitioning() was true at
                            // that exact moment - directly answers whether the
                            // suppression flag is being set/read correctly, or
                            // whether the rail is catching focus through some
                            // OTHER path this investigation hasn't found yet.
                            android.util.Log.d(
                                "DpadFocus",
                                "Rail gained focus - isContentTransitioning=${FocusRegistry.isContentTransitioning()}"
                            )
                        }
                        if (it.hasFocus && FocusRegistry.isContentTransitioning()) {
                            // FIX ("focus falls on rail then transitions to
                            // content" switching All Categories <-> an
                            // individual category): whatever screen currently
                            // has chrome has marked itself as transitioning
                            // (see FocusRegistry.setContentTransitioning) - the
                            // rail happening to catch focus during that window
                            // is the same "Compose needs something focused, the
                            // old item was just disposed" mechanism as the
                            // cold-start claim, just from a different source.
                            // Same fix: suppress explicitly, regardless of how
                            // long this transition takes, rather than relying
                            // on the debounce below to guess right from timing
                            // alone.
                            railSuppressExpansion = true
                        }
                        if (!it.hasFocus) {
                            // Whatever this claim was (the cold-start anchor or
                            // otherwise), it's over now that focus has moved
                            // away. Clear the suppression so the NEXT time the
                            // rail gains focus, it's judged fresh by the normal
                            // debounce logic above instead of still being
                            // treated as the suppressed cold-start claim.
                            railSuppressExpansion = false
                        }
                    }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // FIX: was the adaptive-icon foreground placeholder (a
                // generic Android-robot-style asset with a large transparent
                // safe-zone margin, needing a scale(1.6f) zoom hack just to
                // read as a badge) plus a "BLOCKBUSTER" text wordmark. Now uses
                // the real logo artwork directly: the square mark
                // (logo_square) alone when collapsed, and a dedicated
                // logo_rail_wide lockup (cropped tightly to the logo's own
                // aspect ratio, unlike logo_wide/tv_banner which are built for
                // a much wider canvas) when expanded - no separate Text
                // wordmark needed any more, since "STREAM SMART" is baked into
                // the image itself. Both sit on a white pill/rounded-rect so
                // the logo's own white background reads cleanly against the
                // dark rail; the expanded lockup only needs a sliver of extra
                // padding beyond what's already built into the asset.
                if (railExpanded) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_rail_wide),
                        contentDescription = "Stream Smart logo",
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.logo_square),
                        contentDescription = "Stream Smart logo",
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(4.dp)
                    )
                }

                // Profile / Change profile
                RailItem(
                    icon = Icons.Default.AccountCircle,
                    label = "Change Profile",
                    expanded = railExpanded,
                    selected = false,
                    focusRequester = profileFocusRequester,
                    focusKey = FocusRegistry.PROFILE_KEY,
                    rightEscapeTarget = rightEscapeTarget,
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
                                rightEscapeTarget = rightEscapeTarget,
                                onClick = {
                                    // NEW: clicking the rail item for the page that's
                                    // ALREADY open just collapses the rail (sends focus
                                    // into content) instead of re-navigating to the
                                    // same route, which was previously a confusing
                                    // no-op click.
                                    if (section.route == currentRoute) {
                                        runCatching { FocusRegistry.firstItemTarget(section.route).requestFocus() }
                                    } else {
                                        // FIX: Removed destructive popUpTo(inclusive=true) which corrupted the backstack.
                                        // Now uses standard navigation and triggers a reset signal for the password dialog.
                                        log { "RailItem click: section=${section.route}" }
                                        FocusRegistry.saveLastRailFocus(section.route)
                                        // The next screen's content should auto-advance focus into it
                                        // once loaded, same as the initial landing page does.
                                        FocusRegistry.armInitialContentFocus(section.route)
                                        navController.navigateToSection(section.route)
                                        if (section == AppSection.ADULT) {
                                            shellViewModel.adultSessionManager.triggerUnlockPrompt()
                                        }
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
                    rightEscapeTarget = rightEscapeTarget,
                    onClick = {
                        // NEW: see the matching comment on the section RailItem
                        // above - clicking Settings while it's already open just
                        // collapses the rail instead of re-navigating.
                        if (currentRoute == Routes.SETTINGS) {
                            runCatching { FocusRegistry.firstItemTarget(Routes.SETTINGS).requestFocus() }
                        } else {
                            FocusRegistry.saveLastRailFocus(Routes.SETTINGS)
                            navController.navigateToSection(Routes.SETTINGS)
                        }
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
        // success. The boundary-blocking for Up/Down escaping to the rail
        // is handled per-item instead (see CarouselRow's isLastRow,
        // PosterGrid, ChannelCarouselRow, etc.).
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
        // Give any screen-installed interceptor (e.g. Home clearing an
        // active search) first refusal - see FocusRegistry.consumeBackPressInterceptor.
        if (FocusRegistry.consumeBackPressInterceptor()) return@BackHandler
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000L) {
            log { "RailShell: rapid double Back - exiting app" }
            // DISABLED (cold start - see MainActivity's onUserLeaveHint for
            // the full reasoning): killProcess() removed. finishAndRemoveTask()
            // stays, since this IS a deliberate "exit the app" gesture and
            // should still close/remove it from Recents as expected -
            // dropping just the forced kill means Android's own process
            // lifecycle decides whether to keep this process warm in memory
            // afterward, rather than guaranteeing every next launch pays the
            // full JIT/class-verification tax again.
            (context as? Activity)?.finishAndRemoveTask()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            log { "RailShell: Back pressed - returning focus to rail (railExpanded=$railExpanded)" }
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
    focusKey: String? = null,
    // FIX: explicit Right-direction target into content, rather than
    // relying on Compose's default spatial focus search. Now that the rail
    // OVERLAYS content instead of resizing it (see the Box restructure
    // above), content's first item sits at a FIXED x position that the
    // EXPANDED rail's wider bounds can spatially cover/overlap - so a
    // default "nearest item to the right" search could skip right past the
    // (now visually/spatially overlapped) first item and land on the
    // SECOND one instead. An explicit override bypasses that ambiguity
    // entirely: Right from any rail item always jumps to exactly the
    // registered first-item target for the current screen, regardless of
    // whatever the rail's current width/overlap happens to be.
    rightEscapeTarget: FocusRequester? = null
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
            // FIX: this outer+inner padding pair (12dp + 16dp = 28dp per
            // side) was tuned for the rail's OLD 84dp collapsed width,
            // leaving a comfortable ~28dp for the 24dp icon. At the newer,
            // narrower RailCollapsedWidth (68dp), that same fixed padding
            // left almost no room for the icon at all, squeezing it down
            // visually - shrinking padding specifically for the collapsed
            // (icon-only) state restores the icon's real 24dp size without
            // reverting the rail width change.
            .padding(horizontal = if (expanded) 12.dp else 4.dp)
            .clip(RoundedCornerShape(50))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                // FIX: was Modifier.focusProperties { right = rightEscapeTarget }.
                // That hands the raw FocusRequester to Compose's own internal
                // focus-search, which throws an UNCATCHABLE exception if the
                // registered first-item requester happens to be transiently
                // unattached (e.g. virtualized out of a LazyRow during the
                // keyboard's window-resize churn) - exactly the repro that
                // crashed: open search, Back out without typing, land back
                // on the rail, press Right. safeFocusEscape intercepts the
                // key ourselves and wraps requestFocus() in runCatching, so
                // a failure just falls through to normal focus search
                // instead of crashing. See SafeFocusEscape.kt.
                if (rightEscapeTarget != null) Modifier.safeFocusEscape(Key.DirectionRight, rightEscapeTarget)
                else Modifier
            )
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
            .padding(horizontal = if (expanded) 16.dp else 8.dp, vertical = 12.dp),
        // Collapsed (icon-only): center the icon in the row regardless of
        // exact padding/slack, rather than relying on precise padding math
        // to land it visually centered under "start" alignment. Expanded
        // (icon + label) keeps the original start alignment unchanged.
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
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
                // FIX: was a plain "BLOCKBUSTER" text wordmark - matches the
                // rail's own branding update (see its FIX comment) using the
                // same square logo mark on a white badge.
                Image(
                    painter = painterResource(id = R.drawable.logo_square),
                    contentDescription = "Stream Smart logo",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
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