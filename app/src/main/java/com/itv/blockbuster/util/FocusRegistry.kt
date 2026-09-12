package com.itv.blockbuster.util

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

private const val TAG = "DpadFocus"

/**
 * App-wide focus memory:
 *  - rememberEntry[route] = id of the last focused item in that section
 *  - restoreRequesters[id] = requester able to re-focus that item
 *  - railRequesters[key] = requester for a rail item (AppSection route, the
 *    Settings item's own Routes.SETTINGS, or PROFILE_KEY for the pinned
 *    Profile row, which has no route of its own since it navigates to the
 *    separate, full-screen profile picker)
 *  - firstItemRequesters[route] = requester for the first carousel item of a
 *    screen, used to auto-advance focus off the rail once that screen loads
 *
 * This is a process-wide singleton, used as the coordination point between
 * the rail (in AppShell.kt) and each screen's content (deep inside the
 * NavHost, in a completely separate part of the composition tree) - neither
 * has a direct reference to the other's focus targets otherwise.
 *
 * Every state change and focus attempt logs through android.util.Log with
 * tag "DpadFocus" - filter logcat on that tag to trace the exact sequence of
 * events if focus behavior looks wrong again.
 */
object FocusRegistry {
    private val restoreRequesters = HashMap<String, FocusRequester>()
    private val rememberEntry = HashMap<String, String>()
    private val railRequesters = HashMap<String, FocusRequester>()
    private val firstItemRequesters = HashMap<String, FocusRequester>()

    /** Rail key for the pinned Profile rail row (not an AppSection route). */
    const val PROFILE_KEY = "rail_profile"

    private const val DEFAULT_RAIL_KEY = "home"

    // ── Rail item focus ─────────────────────────────────────────────
    fun registerRail(key: String, requester: FocusRequester) {
        Log.d(TAG, "registerRail: key=$key requester=$requester")
        railRequesters[key] = requester
    }

    fun requestRail(key: String): Boolean {
        val requester = railRequesters[key]
        val success = requester?.runCatching { requestFocus() }?.isSuccess == true
        Log.d(TAG, "requestRail: key=$key requesterPresent=${requester != null} success=$success")
        return success
    }

    /**
     * True if `route` corresponds to an actual top-level rail item (a
     * RailSections entry, Settings, or Profile) - false for any sub-route
     * reached by clicking something WITHIN a screen rather than a rail item
     * (e.g. Adult Live/VOD, opened from AdultHubScreen's cards). Used by
     * those sub-screens to know whether they can rely on the rail
     * legitimately holding focus while they load (true for a real rail
     * route - the normal, correct "rail shows focus while its content
     * loads" behavior) or whether they need to proactively claim focus
     * onto something of their own immediately on mount instead (false -
     * requestRail(route) can never succeed for a route with no rail item to
     * begin with, so without this, focus that was on whatever UI element
     * was clicked to navigate here simply falls through to Android's own
     * default-focus-search once that element is disposed, which - since
     * the rail is still composed and adjacent - visibly lands there and
     * stays until the destination's own content finishes loading).
     */
    fun isRailRegistered(route: String): Boolean = railRequesters.containsKey(route)

    // Ground truth for "which rail item currently actually has focus",
    // reported by each RailItem's own onFocusChanged. Not used to gate any
    // decision any more (that caused issue #1 - see notifyContentReady) but
    // kept purely as a diagnostic signal, logged on every change.
    private var currentlyFocusedRailKey: String? = null
    fun reportRailFocus(key: String, isFocused: Boolean) {
        Log.d(TAG, "reportRailFocus: key=$key isFocused=$isFocused (was currentlyFocused=$currentlyFocusedRailKey)")
        if (isFocused) currentlyFocusedRailKey = key
        else if (currentlyFocusedRailKey == key) currentlyFocusedRailKey = null
    }
    fun currentRailFocusKey(): String? = currentlyFocusedRailKey

    // The rail item the user last focused/selected. Restored when D-pad Left
    // is pressed from the leftmost item in the content area, or Back is
    // pressed while focus is in the content area.
    private var lastRailKey: String = DEFAULT_RAIL_KEY
    fun saveLastRailFocus(key: String) {
        Log.d(TAG, "saveLastRailFocus: key=$key")
        lastRailKey = key
    }
    fun focusRail(fallbackKey: String = DEFAULT_RAIL_KEY) {
        Log.d(TAG, "focusRail: lastRailKey=$lastRailKey fallbackKey=$fallbackKey")
        if (!requestRail(lastRailKey)) requestRail(fallbackKey)
    }

    /**
     * The FocusRequester D-pad Left should jump to from the leftmost item of
     * any content carousel - i.e. the rail item the user actually entered
     * this screen from, not whichever rail row happens to sit closest on
     * screen. Used with Modifier.focusProperties { left = ... }, which is
     * queried live by Compose's own focus-search at the moment Left is
     * pressed (not cached at composition time), so this always reflects the
     * current lastRailKey. FocusRequester.Default tells Compose "no custom
     * override, fall back to normal spatial search" if we don't have a
     * requester registered for some reason.
     */
    fun leftEscapeTarget(): FocusRequester {
        val target = railRequesters[lastRailKey]
        Log.d(TAG, "leftEscapeTarget: lastRailKey=$lastRailKey found=${target != null}")
        return target ?: FocusRequester.Default
    }

    // ── Rail -> content focus sequencing ────────────────────────────
    // Every time RailShell mounts for a route - cold start landing page OR
    // any later rail-click navigation, since Compose Navigation gives each
    // destination its own fresh RailShell subtree - it explicitly requests
    // focus on that route's rail item (see AppShell.kt), then arms this flag
    // so the screen can auto-advance focus into its first carousel item once
    // loaded.
    //
    // IMPORTANT: there used to be a "only advance if focus is still on the
    // rail" guard here (via currentlyFocusedRailKey). It was removed - it
    // couldn't tell "user already navigated into content" apart from
    // "nothing has been focused yet" (both look like null), and when content
    // loaded fast (e.g. cached data) it fired before the rail's own focus
    // request had even completed, permanently aborting the handoff and
    // leaving focus stuck on the rail forever. Simpler and correct: if this
    // route is armed, always try to advance.
    private var pendingContentFocusRoute: String? = null
    fun armInitialContentFocus(route: String) {
        Log.d(TAG, "armInitialContentFocus: route=$route")
        pendingContentFocusRoute = route
    }
    // ── Route transition tracking (survives any single composable's own
    // mount/dispose lifecycle) ──────────────────────────────────────
    // Used by AppShell to detect "did we just come from a detail screen"
    // across the FULL sequence of route changes, including ones where the
    // rail (or any other shell composable) wasn't even mounted in between -
    // e.g. Detail -> Player -> back -> Detail -> back -> browser. A plain
    // `remember` var inside a composable that itself gets disposed partway
    // through that sequence (as RailShell used to, since Player is a
    // NoRailRoutes screen) can't reliably track this; a singleton can.
    private var lastNavigatedRoute: String? = null
    fun swapLastNavigatedRoute(newRoute: String): String? {
        val old = lastNavigatedRoute
        lastNavigatedRoute = newRoute
        return old
    }

    fun isPendingFor(route: String): Boolean = pendingContentFocusRoute == route
    fun registerFirstItem(route: String, requester: FocusRequester) {
        Log.d(TAG, "registerFirstItem: route=$route requester=$requester")
        firstItemRequesters[route] = requester
    }

    /**
     * The FocusRequester D-pad Down should jump to from a screen's top bar
     * controls (search field, genre/category dropdowns, sort button) - i.e.
     * whichever item is CURRENTLY registered as that route's first item,
     * looked up live at the moment Down is pressed (not cached at
     * composition time, same idea as leftEscapeTarget), so it always
     * reflects the current carousel/grid state even as filters change which
     * row or item that actually is. FocusRequester.Default falls back to
     * normal spatial search if nothing is registered for this route yet
     * (e.g. content still loading).
     */
    fun firstItemTarget(route: String): FocusRequester {
        val target = firstItemRequesters[route]
        Log.d(TAG, "firstItemTarget: route=$route found=${target != null}")
        return target ?: FocusRequester.Default
    }

    /**
     * Explicitly moves focus onto a route's registered first item right now,
     * independent of the rail->content handoff's pendingContentFocusRoute
     * gate above - used when a category/genre/search filter change refreshes
     * the row/grid data while the user is already on the content screen
     * (not navigating in from the rail), so the normal "only advance if
     * still pending" logic doesn't apply. Retries briefly since the new
     * first item may not be composed/registered yet the instant the
     * filtered data arrives.
     */
    suspend fun focusFirstItem(route: String) {
        Log.d(TAG, "focusFirstItem: route=$route starting attempts")
        repeat(20) { attempt ->
            val requester = firstItemRequesters[route]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            Log.d(TAG, "focusFirstItem: route=$route attempt=$attempt requesterPresent=${requester != null} moved=$moved")
            if (moved) return
            delay(50)
        }
        Log.d(TAG, "focusFirstItem: route=$route gave up after retries")
    }

    // ── Content -> detail -> back focus restoration ─────────────────
    // Every focusable browser item (VOD poster, channel tile) registers a
    // STABLE requester here, keyed by (route, itemId) so it survives list
    // reordering/refocus - unlike firstItemRequesters above, which only
    // tracks ONE slot per route, this tracks every item ever composed for
    // that route. When an item is clicked to open a detail screen (VOD
    // detail, etc.), rememberClickedItem records which one. RailShell's own
    // route-change handling (see AppShell.kt) detects when the route we're
    // landing on was navigated to via Back FROM a detail screen, and arms
    // this path with armRestoreFocus INSTEAD of the usual rail-focus-then-
    // first-item handoff - the browser screen's own ON_RESUME observer then
    // calls restoreClickedItemFocus, which puts focus back on that exact
    // item rather than wherever the rail/first-item handoff would otherwise
    // send it.
    private val itemRequesters = HashMap<String, FocusRequester>()
    private val lastClickedItem = HashMap<String, String>()
    private var pendingRestoreRoute: String? = null

    private fun itemKey(route: String, itemId: String) = "$route\u0000$itemId"

    fun registerItemFocus(route: String, itemId: String, requester: FocusRequester) {
        itemRequesters[itemKey(route, itemId)] = requester
    }

    fun rememberClickedItem(route: String, itemId: String) {
        Log.d(TAG, "rememberClickedItem: route=$route itemId=$itemId")
        lastClickedItem[route] = itemId
    }

    fun armRestoreFocus(route: String) {
        Log.d(TAG, "armRestoreFocus: route=$route")
        pendingRestoreRoute = route
    }
    fun isPendingRestoreFor(route: String): Boolean = pendingRestoreRoute == route

    /**
     * Polls briefly for armRestoreFocus(route) to have been called, rather
     * than checking isPendingRestoreFor once and giving up immediately -
     * the ON_RESUME lifecycle callback a screen uses to call this (and
     * restoreClickedItemFocus below) isn't strictly ordered relative to
     * RailShell's own route-change LaunchedEffect that calls
     * armRestoreFocus (different parts of the composition tree, triggered
     * by the same navigation event), so the arm signal may not have
     * arrived yet the instant this is checked. Used by screens whose
     * sections can reorder (e.g. Recents promoting a just-played item to
     * the front) to decide whether it's safe/necessary to snap their own
     * row scroll states back to index 0 before attempting the restore -
     * skipped entirely for ordinary resumes (tab switches, rail clicks) so
     * those don't lose their scroll position for no reason.
     */
    suspend fun awaitPendingRestore(route: String): Boolean {
        repeat(20) {
            if (pendingRestoreRoute == route) return true
            delay(50)
        }
        return false
    }

    /**
     * Called when a browser screen becomes visible again (e.g. ON_RESUME
     * after Back pops a detail screen pushed from it). No-ops unless
     * armRestoreFocus was called for this exact route (see AppShell.kt),
     * which distinguishes "returning via Back from a detail screen" from an
     * ordinary rail-click navigation or tab-switch resume - this
     * deliberately does NOT act on every resume, only when explicitly
     * armed, so it can't race the normal rail-focus-then-first-item handoff
     * or fire on unrelated lifecycle churn.
     *
     * The ON_RESUME lifecycle callback that calls this and RailShell's own
     * route-change LaunchedEffect that calls armRestoreFocus aren't
     * strictly ordered relative to each other (different parts of the
     * composition tree, triggered by the same navigation event) - so rather
     * than checking pendingRestoreRoute once up front and bailing
     * immediately if it doesn't match yet, the retry loop below keeps
     * polling for it to appear, the same as it polls for the item's
     * requester to become available.
     */
    suspend fun restoreClickedItemFocus(route: String): Boolean {
        Log.d(TAG, "restoreClickedItemFocus: route=$route starting attempts")
        repeat(20) { attempt ->
            if (pendingRestoreRoute != route) {
                Log.d(TAG, "restoreClickedItemFocus: route=$route attempt=$attempt not (yet) armed, pendingRestoreRoute=$pendingRestoreRoute")
                delay(50)
                return@repeat
            }
            val itemId = lastClickedItem[route]
            if (itemId == null) {
                Log.d(TAG, "restoreClickedItemFocus: route=$route no remembered item, clearing pending")
                pendingRestoreRoute = null
                return false
            }
            val requester = itemRequesters[itemKey(route, itemId)]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            Log.d(TAG, "restoreClickedItemFocus: route=$route itemId=$itemId attempt=$attempt requesterPresent=${requester != null} moved=$moved")
            if (moved) {
                pendingRestoreRoute = null
                Log.d(TAG, "restoreClickedItemFocus: route=$route itemId=$itemId SUCCESS")
                return true
            }
            delay(50)
        }
        Log.d(TAG, "restoreClickedItemFocus: route=$route gave up after retries")
        return false
    }

    /**
     * Called by a top-level screen once its content has finished loading.
     * If this screen is the one an initial (or rail-triggered) focus shift is
     * still pending for, moves focus onto its registered first item and
     * clears the pending flag.
     *
     * Retries generously (several seconds) rather than giving up after ~1s:
     * previously-visited screens can keep recomposing (and re-registering
     * their own first-item requester) for a while after you've navigated
     * away under Navigation Compose's saveState/restoreState pattern, which
     * can starve a freshly-revisited screen's own first row of a chance to
     * (re-)register its requester before a short retry budget runs out -
     * leaving a stale, now-orphaned requester in the map that requestFocus()
     * keeps failing on. This is safe to make generous: the loop already
     * exits immediately the moment the user navigates elsewhere (the
     * pendingContentFocusRoute check below), so extra patience only matters
     * in the rare case it's actually needed.
     */
    suspend fun notifyContentReady(route: String) {
        if (pendingContentFocusRoute != route) {
            Log.d(TAG, "notifyContentReady: route=$route ignored, pendingContentFocusRoute=$pendingContentFocusRoute")
            return
        }
        Log.d(TAG, "notifyContentReady: route=$route starting attempts to focus first item")
        // The first item may not be composed/registered yet the instant
        // loading finishes.
        repeat(200) { attempt ->
            if (pendingContentFocusRoute != route) {
                Log.d(TAG, "notifyContentReady: route=$route aborted mid-retry, pendingContentFocusRoute changed to $pendingContentFocusRoute")
                return
            }
            val requester = firstItemRequesters[route]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            Log.d(TAG, "notifyContentReady: route=$route attempt=$attempt requesterPresent=${requester != null} requesterId=${System.identityHashCode(requester)} moved=$moved")
            if (moved) {
                pendingContentFocusRoute = null
                Log.d(TAG, "notifyContentReady: route=$route SUCCESS - focus moved to first item")
                return
            }
            delay(50)
        }
        Log.d(TAG, "notifyContentReady: route=$route gave up after retries, leaving pendingContentFocusRoute set for a later retry")
    }

    fun entryFor(route: String): String? = rememberEntry[route]

    internal fun saveRestore(id: String, requester: FocusRequester) { restoreRequesters[id] = requester }
    internal fun saveEntry(route: String, id: String) { rememberEntry[route] = id }
    internal fun tryFocus(id: String): Boolean =
        restoreRequesters[id]?.runCatching { requestFocus() }?.isSuccess == true
}

/**
 * One-stop focus behavior for any focusable item.
 * FIX: Changed to a Modifier extension function so it can be chained cleanly.
 */
@Composable
fun Modifier.restorableFocus(
    restoreKey: String?,
    route: String? = null,
    initialFocus: Boolean = false
): Modifier {
    val requester = remember { FocusRequester() }
    if (restoreKey != null) FocusRegistry.saveRestore(restoreKey, requester)

    LaunchedEffect(restoreKey, route, initialFocus) {
        if (restoreKey == null || route == null) return@LaunchedEffect
        val entry = FocusRegistry.entryFor(route)
        when {
            entry == restoreKey -> {
                // Restore: item may not be composed yet -> retry briefly
                repeat(20) {
                    if (FocusRegistry.tryFocus(restoreKey)) return@LaunchedEffect
                    delay(50)
                }
            }
            entry == null && initialFocus -> {
                // First visit: focus the first item
                requester.requestFocus()
            }
        }
    }

    return this
        .focusRequester(requester)
        .focusable()
        .onFocusChanged {
            if (it.isFocused && restoreKey != null && route != null) {
                FocusRegistry.saveEntry(route, restoreKey)
            }
        }
}
