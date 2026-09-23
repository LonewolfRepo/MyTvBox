package com.itv.blockbuster.util

import android.util.Log
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "DpadFocus"

// UPDATED (performance): every focus event, retry-loop iteration, and
// carousel item registration used to call Log.d(TAG, "...") directly with a
// pre-built string template. Kotlin evaluates and allocates that
// interpolated string as a normal function argument BEFORE Log.d is even
// entered - so the cost was paid on every single call regardless of whether
// the log line would actually be useful, and this system logs on every rail
// focus change, every carousel item's registration, and every 50ms of every
// retry loop (notifyContentReady alone retries up to 200 times). Flip this
// to true when actively debugging focus behavior; leave it false otherwise
// so none of that string-building work happens at all in normal use - the
// `log` helper below takes a lambda specifically so the interpolation
// inside it is never even evaluated when this is false.
private const val FOCUS_LOGGING_ENABLED = true
private inline fun log(message: () -> String) {
    if (FOCUS_LOGGING_ENABLED) Log.d(TAG, message())
}

/**
 * App-wide focus memory:
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
 * Every state change and focus attempt logs through the log() helper above
 * (tag "DpadFocus") when FOCUS_LOGGING_ENABLED is flipped to true - filter
 * logcat on that tag to trace the exact sequence of events if focus
 * behavior looks wrong again.
 */
object FocusRegistry {
    private val railRequesters = HashMap<String, FocusRequester>()
    private val firstItemRequesters = HashMap<String, FocusRequester>()

    // FIX (confirmed via device logcat: a deferred focus-return attempt,
    // launched via rememberCoroutineScope() inside a dropdown's onClick,
    // sometimes never ran at all - no log, no requestFocus() call): the
    // SAME state change that the deferred attempt is trying to react to
    // (a category selection, which reloads content and can swap the whole
    // layout) can itself dispose the composable that scope belongs to
    // before the delay finishes, silently cancelling the coroutine before
    // it ever gets a chance to run. This scope is tied to FocusRegistry
    // itself - a process-wide singleton - so a deferred focus-return
    // launched here survives any single screen's recomposition, layout
    // swap, or disposal. SupervisorJob so one cancelled/failed launch here
    // never affects any other.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Requests focus on [requester] after [delayMs] - on FocusRegistry's own
     * process-wide scope (see its doc comment above), not the caller's own
     * composable-scoped one, specifically so this survives whatever state
     * change triggered the need for a delayed focus-return in the first
     * place (a category/genre selection, which reloads content and can
     * dispose the very composable that would otherwise have hosted this
     * coroutine before the delay completes).
     */
    fun deferredRequestFocus(requester: FocusRequester, delayMs: Long = 100, shouldClaim: () -> Boolean = { true }) {
        scope.launch {
            delay(delayMs)
            // FIX (confirmed on Live TV: selecting a category is entirely
            // client-side there, so a reload can settle to FirstItem and
            // have claimFocusEntry already, correctly, move focus onto the
            // actual first channel well within this delay - unlike Home,
            // where a real network round-trip means the reload is still in
            // flight when this fires). An unconditional claim here would
            // then yank focus straight back to the trigger, undoing a
            // focus placement that was already correct. shouldClaim is
            // evaluated fresh right here, not captured back at call time,
            // so a caller can check its own latest state (e.g. via
            // rememberUpdatedState) rather than a stale snapshot from
            // before this delay even started.
            if (shouldClaim()) {
                runCatching { requester.requestFocus() }
            }
        }
    }

    /** Rail key for the pinned Profile rail row (not an AppSection route). */
    const val PROFILE_KEY = "rail_profile"

    private const val DEFAULT_RAIL_KEY = "home"

    // ── Rail item focus ─────────────────────────────────────────────
    fun registerRail(key: String, requester: FocusRequester) {
        log { "registerRail: key=$key requester=$requester" }
        railRequesters[key] = requester
    }

    fun requestRail(key: String): Boolean {
        val requester = railRequesters[key]
        val success = requester?.runCatching { requestFocus() }?.isSuccess == true
        log { "requestRail: key=$key requesterPresent=${requester != null} success=$success" }
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
        log { "reportRailFocus: key=$key isFocused=$isFocused (was currentlyFocused=$currentlyFocusedRailKey)" }
        if (isFocused) currentlyFocusedRailKey = key
        else if (currentlyFocusedRailKey == key) currentlyFocusedRailKey = null
    }
    fun currentRailFocusKey(): String? = currentlyFocusedRailKey

    // The rail item the user last focused/selected. Restored when D-pad Left
    // is pressed from the leftmost item in the content area, or Back is
    // pressed while focus is in the content area.
    private var lastRailKey: String = DEFAULT_RAIL_KEY
    fun saveLastRailFocus(key: String) {
        log { "saveLastRailFocus: key=$key" }
        lastRailKey = key
    }
    fun focusRail(fallbackKey: String = DEFAULT_RAIL_KEY) {
        log { "focusRail: lastRailKey=$lastRailKey fallbackKey=$fallbackKey" }
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
        log { "leftEscapeTarget: lastRailKey=$lastRailKey found=${target != null}" }
        return target ?: FocusRequester.Default
    }

    // FIX ("focus falls on the rail and then transitions to content" when
    // switching All Categories <-> an individual category): that switch
    // tears down and rebuilds an entire subtree (LargeHomeHeroLayout's
    // CarouselRow-based view vs. CategoryGridWithHeroLayout's PosterGrid-
    // based one - two genuinely different components, not just different
    // data within the same one), which can take longer than AppShell's
    // normal 120ms rail-expand debounce, especially if it involves a fresh
    // fetch for the newly-selected category. The debounce alone can't tell
    // "held briefly by accident" apart from "held for a while because this
    // transition is just slower" - both look identical to a timer, same
    // root issue as the cold-start case Phase 2 already fixed explicitly.
    //
    // Any screen sets this true for the duration of its own FocusEntry.None
    // state (still loading/transitioning - see FocusEntry.kt) and false
    // otherwise; AppShell checks it the moment the rail gains focus and, if
    // true, suppresses that claim's visual expansion outright regardless of
    // how long it lasts - the same explicit, duration-independent
    // mechanism as the cold-start claim, just triggered by a different
    // source. Plain var, not Compose state: nothing here needs to trigger
    // recomposition on its own, AppShell just reads the current value at
    // the moment it already needs to react to a focus change for its own
    // reasons.
    @Volatile
    private var contentIsTransitioning = false

    fun setContentTransitioning(isTransitioning: Boolean) {
        contentIsTransitioning = isTransitioning
    }

    fun isContentTransitioning(): Boolean = contentIsTransitioning

    // FIX (root cause of "focus snaps back to first item on scroll",
    // "carousel items oscillate under rapid Right", "vertical pagination
    // breaks" - see claimFocusEntry's doc comment in FocusEntry.kt for the
    // full mechanism): tracks, per scope (typically a screen's route), the
    // identity of the last focus-claim transition that was actually acted
    // on - so if a DIFFERENT item later qualifies as "the one to claim"
    // for the exact SAME transition (e.g. because isFirstItem's target
    // shifted under continued scrolling, not because a genuinely new
    // transition occurred), it finds this transition already consumed and
    // does nothing, instead of re-claiming focus out from under the user.
    private val consumedFocusClaims = HashMap<String, Any>()

    /**
     * Returns true if this exact [claimId] has already been successfully
     * claimed for [scopeKey] - a pure check, does not itself record
     * anything. See markFocusEntryClaimed for recording a successful
     * claim.
     */
    fun hasFocusEntryBeenClaimed(scopeKey: String, claimId: Any): Boolean =
        consumedFocusClaims[scopeKey] === claimId

    /**
     * Records [claimId] as successfully claimed for [scopeKey].
     *
     * FIX (rail catching focus well after content had already settled to
     * FirstItem - confirmed via device logcat: "Rail gained focus -
     * isContentTransitioning=false", happening seconds after the FirstItem
     * transition, not during it): the original single tryClaimFocusEntry()
     * marked a transition consumed the moment a claim was ATTEMPTED, before
     * knowing whether requestFocus() actually succeeded. If it failed
     * silently (e.g. the target's FocusRequester genuinely wasn't attached
     * yet at that exact moment - a timing race, not a logic bug), the
     * transition was permanently marked consumed anyway, so no later item
     * could ever retry it - focus stayed on whatever it was before (e.g.
     * the search field) until something else disposed that element,
     * finding nothing left to catch it. Splitting into a peek
     * (hasFocusEntryBeenClaimed, checked BEFORE attempting - still
     * prevents the original re-fire bug, since an already-succeeded claim
     * is never reattempted) and a commit (this function, called only
     * AFTER requestFocus() is confirmed to have succeeded) means a failed
     * attempt leaves the transition claimable, so a later item still
     * qualifying for isFirstItem can genuinely retry it.
     */
    fun markFocusEntryClaimed(scopeKey: String, claimId: Any) {
        consumedFocusClaims[scopeKey] = claimId
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
        log { "armInitialContentFocus: route=$route" }
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
        log { "registerFirstItem: route=$route requester=$requester" }
        firstItemRequesters[route] = requester
    }

    /**
     * FIX: counterpart to registerFirstItem, called from the registering
     * composable's DisposableEffect(onDispose). Without this, when the item
     * that registered itself leaves composition (filtered out by a search
     * query, category change, or scrolled/recomposed away during a layout
     * churn like the on-screen keyboard opening), firstItemRequesters[route]
     * keeps pointing at a FocusRequester that's no longer attached to any
     * node. firstItemTarget() would then hand that dangling requester out as
     * a `down = ...` focusProperties target - and unlike an explicit
     * .requestFocus() call (which we can runCatching), Compose's OWN
     * internal focus-search throws an uncatchable
     * IllegalStateException("FocusRequester is not initialized") the moment
     * real D-pad key dispatch tries to navigate into it, crashing the app
     * outright. Only clears the entry if it STILL matches the given
     * requester, so a stale dispose firing after a newer item has already
     * re-registered for this route can't wrongly wipe out the current one.
     */
    fun unregisterFirstItem(route: String, requester: FocusRequester) {
        if (firstItemRequesters[route] === requester) {
            log { "unregisterFirstItem: route=$route requester=$requester" }
            firstItemRequesters.remove(route)
        }
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
        log { "firstItemTarget: route=$route found=${target != null}" }
        return target ?: FocusRequester.Default
    }

    // FIX (D-pad-only, confirmed not reproducible via touch scroll: "focus
    // jumps to categories filter during rapid Up-scroll, even though more
    // rows exist above"): Compose Foundation's LazyColumn beyond-bounds
    // composition isn't tunable in this project's version (no
    // beyondBoundsItemCount parameter until a later Foundation release), so
    // rows composed long ago during an initial scroll-down can be disposed
    // by the time a fast, sustained Up-scroll reaches back up to them.
    // Every row except the very first has no explicit upEscapeTarget, so it
    // falls through to Compose's own default spatial focus search - which
    // can only find candidates that are ACTUALLY composed right now. If the
    // real row above isn't, that search keeps looking and lands on the
    // always-composed filter bar instead, since nothing else catches it
    // first.
    //
    // This registry sidesteps that entirely: every row (not just the first)
    // registers its own first-item requester here, keyed by a caller-chosen
    // key (route + row id, so different screens' rows never collide). The
    // row below explicitly targets "the row above's registered requester"
    // via rowFirstItemTarget() instead of leaving Up to Compose's live
    // search - so it works whether or not the row above happens to be
    // composed at that exact moment (as long as it's been composed at
    // least once and registered), no different from firstItemTarget's own
    // route-level equivalent above.
    //
    // Known tradeoff: this always lands on the row above's FIRST item,
    // not necessarily preserving the column/horizontal position you were
    // at - Compose's natural spatial search (when it works) tries to land
    // near the same column. Revisit if that's noticeable in practice -
    // remembering the last-focused item per row instead of always the
    // first would fix it, at the cost of tracking focus on every item
    // instead of just the first.
    private val rowFirstItemRequesters = HashMap<String, FocusRequester>()

    fun registerRowFirstItem(key: String, requester: FocusRequester) {
        log { "registerRowFirstItem: key=$key requester=$requester" }
        rowFirstItemRequesters[key] = requester
    }

    /** Counterpart to registerRowFirstItem - same dangling-requester
     *  reasoning as unregisterFirstItem's doc comment above. */
    fun unregisterRowFirstItem(key: String, requester: FocusRequester) {
        if (rowFirstItemRequesters[key] === requester) {
            log { "unregisterRowFirstItem: key=$key requester=$requester" }
            rowFirstItemRequesters.remove(key)
        }
    }

    /**
     * The FocusRequester D-pad Up should jump to from a row that isn't the
     * screen's first - looked up live at the moment Up is pressed (same
     * "always current, never cached at composition time" reasoning as
     * firstItemTarget). FocusRequester.Default falls back to normal spatial
     * search if the row above has genuinely never been composed/registered
     * yet (e.g. this really is being reached for the first time).
     */
    fun rowFirstItemTarget(key: String): FocusRequester {
        val target = rowFirstItemRequesters[key]
        log { "rowFirstItemTarget: key=$key found=${target != null}" }
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
        log { "focusFirstItem: route=$route starting attempts" }
        repeat(20) { attempt ->
            val requester = firstItemRequesters[route]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            log { "focusFirstItem: route=$route attempt=$attempt requesterPresent=${requester != null} moved=$moved" }
            if (moved) return
            delay(50)
        }
        log { "focusFirstItem: route=$route gave up after retries" }
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
        log { "rememberClickedItem: route=$route itemId=$itemId" }
        lastClickedItem[route] = itemId
    }

    fun armRestoreFocus(route: String) {
        log { "armRestoreFocus: route=$route" }
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
        log { "restoreClickedItemFocus: route=$route starting attempts" }
        repeat(20) { attempt ->
            if (pendingRestoreRoute != route) {
                log { "restoreClickedItemFocus: route=$route attempt=$attempt not (yet) armed, pendingRestoreRoute=$pendingRestoreRoute" }
                delay(50)
                return@repeat
            }
            val itemId = lastClickedItem[route]
            if (itemId == null) {
                log { "restoreClickedItemFocus: route=$route no remembered item, clearing pending" }
                pendingRestoreRoute = null
                return false
            }
            val requester = itemRequesters[itemKey(route, itemId)]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            log { "restoreClickedItemFocus: route=$route itemId=$itemId attempt=$attempt requesterPresent=${requester != null} moved=$moved" }
            if (moved) {
                pendingRestoreRoute = null
                log { "restoreClickedItemFocus: route=$route itemId=$itemId SUCCESS" }
                return true
            }
            delay(50)
        }
        log { "restoreClickedItemFocus: route=$route gave up after retries" }
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
            log { "notifyContentReady: route=$route ignored, pendingContentFocusRoute=$pendingContentFocusRoute" }
            return
        }
        log { "notifyContentReady: route=$route starting attempts to focus first item" }
        // The first item may not be composed/registered yet the instant
        // loading finishes.
        repeat(200) { attempt ->
            if (pendingContentFocusRoute != route) {
                log { "notifyContentReady: route=$route aborted mid-retry, pendingContentFocusRoute changed to $pendingContentFocusRoute" }
                return
            }
            val requester = firstItemRequesters[route]
            val moved = requester?.runCatching { requestFocus() }?.isSuccess == true
            log { "notifyContentReady: route=$route attempt=$attempt requesterPresent=${requester != null} requesterId=${System.identityHashCode(requester)} moved=$moved" }
            if (moved) {
                pendingContentFocusRoute = null
                log { "notifyContentReady: route=$route SUCCESS - focus moved to first item" }
                return
            }
            delay(50)
        }
        log { "notifyContentReady: route=$route gave up after retries, leaving pendingContentFocusRoute set for a later retry" }
    }

    // ── Back-press interception ──────────────────────────────────────
    // AppShell's own BackHandler (in AppShell.kt) is deliberately the LAST
    // one registered in the composition (see its doc comment), specifically
    // so it always wins the OnBackPressedDispatcher's LIFO priority over
    // anything a screen further down in the tree could register - which
    // means a screen adding its OWN BackHandler for a screen-specific action
    // (e.g. Home wanting Back to clear an active search instead of
    // refocusing the rail) would simply never fire; AppShell's handler
    // always intercepts first regardless of registration order deeper in
    // the tree. This hook lets a screen ask AppShell's single BackHandler to
    // check with it FIRST, without introducing a second competing
    // BackHandler: the screen sets an interceptor while its special case is
    // active, AppShell's BackHandler consumes it (if present) before falling
    // through to its normal rail-refocus/exit-app behavior, and the screen
    // clears the interceptor once it's no longer needed (e.g. in a
    // DisposableEffect keyed on whatever "special case active" condition,
    // clearing it both when that condition ends AND on dispose so a stale
    // interceptor never lingers into an unrelated screen).
    private var backPressInterceptor: (() -> Boolean)? = null

    /** Screen-side: install (or clear, by passing null) the interceptor. */
    fun setBackPressInterceptor(interceptor: (() -> Boolean)?) {
        backPressInterceptor = interceptor
    }

    /**
     * AppShell-side: gives the current interceptor (if any) first refusal on
     * this back press. Returns true if it consumed the press (AppShell
     * should do nothing further), false if there's no interceptor or it
     * declined (AppShell should proceed with its normal handling).
     */
    fun consumeBackPressInterceptor(): Boolean {
        val interceptor = backPressInterceptor ?: run {
            android.util.Log.d("DpadFocus", "consumeBackPressInterceptor: no interceptor registered - falling through to focusRail()")
            return false
        }
        val consumed = interceptor()
        android.util.Log.d("DpadFocus", "consumeBackPressInterceptor: interceptor present, consumed=$consumed")
        return consumed
    }
}