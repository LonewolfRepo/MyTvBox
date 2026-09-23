package com.itv.blockbuster.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * What should receive D-pad focus when a screen (or a region within one)
 * settles into a particular state.
 *
 * This is the single decision type behind every "where should focus land"
 * question in the app - the same four cases apply whether a screen is
 * loading for the first time, coming back empty, or restoring focus to a
 * specific remembered item (the last-played channel on TV Guide, or the
 * item you clicked before opening VOD Detail). Every consumer uses the same
 * push-based claimFocusEntry() below regardless of which case applies, so
 * there's one mechanism instead of several bespoke ones scattered per
 * screen.
 *
 * NOT YET WIRED INTO ANY SCREEN - this is Phase 1 of the D-pad focus
 * redesign: written and available, but no screen calls into it yet. See
 * the redesign plan for the phased rollout (Home first as the reference
 * implementation, then Live TV/TV Guide/Favorites, then VOD Detail restore).
 */
sealed class FocusEntry {
    /** Still loading, or otherwise not ready to decide yet - don't move
     *  focus anywhere. Leaves it wherever it already is (e.g. the filter
     *  row), which is a perfectly reasonable resting place while content
     *  loads - there's no need to actively park focus somewhere special
     *  just because nothing is ready yet. */
    object None : FocusEntry()

    /** Content loaded and non-empty - the first visible item claims focus. */
    object FirstItem : FocusEntry()

    /** Content loaded and came back empty - the screen's filter/category
     *  field claims focus instead of leaving focus stranded with nothing
     *  in the content area to move to. */
    object Fallback : FocusEntry()

    /** Land on a specific remembered item - the last-played channel on TV
     *  Guide, or the item that was clicked before opening a detail screen,
     *  once back. Equality is by itemId (data class), so a candidate item
     *  compares itself against the current entry by constructing its own
     *  RestoreItem(myId) and checking equality - no special-casing needed
     *  in claimFocusEntry() below. */
    data class RestoreItem(val itemId: String) : FocusEntry()
}

/**
 * Marks this composable as the thing that should claim D-pad focus the
 * moment `current == mine`.
 *
 * This is a PUSH, not a PULL: the item itself requests its own focus in a
 * LaunchedEffect keyed on `current`, rather than something external polling
 * to check whether the item exists yet. A LaunchedEffect inside a
 * composable cannot run before that composable has been placed in the
 * composition, so there is no "not ready yet" race to retry against - it
 * either isn't this target's turn yet (current != mine, effect does
 * nothing) or it is, and by the time the effect body runs the composable
 * (and its focusRequester modifier below) already exists.
 *
 * This is the one mechanism every FocusEntry case uses - a FirstItem
 * consumer passes `mine = FocusEntry.FirstItem`, a channel restoring focus
 * passes `mine = FocusEntry.RestoreItem(thisChannelId)`, etc. Replaces the
 * old route-level notifyContentReady()/focusFirstItem() polling loops
 * (repeat(N) { delay(50) }) that this same investigation already
 * identified as wasteful and hard to reason about.
 *
 * requestFocus() is wrapped in runCatching as a single defensive attempt
 * (not a retry loop) in case the requester is momentarily detached, e.g. a
 * mid-recomposition edge case - if it fails, focus simply stays where it
 * was rather than crashing.
 *
 * FIX (confirmed root cause of several reported bugs at once: focus
 * snapping back to the first item on the 3rd row of a grid, carousel items
 * oscillating under rapid D-pad Right, vertical pagination breaking):
 * FocusEntry.FirstItem/None/Fallback are singleton objects - the SAME
 * instance every time a screen re-enters that state, not a fresh value per
 * occurrence. "isFirstItem" (whichever item currently sits at the row's or
 * grid's scroll-anchor position) is recomputed continuously as the user
 * scrolls, so a DIFFERENT item satisfies it on every scroll step - and
 * since claimFocusEntry is applied conditionally to whichever item
 * currently qualifies, each newly-qualifying item mounts a BRAND NEW
 * LaunchedEffect(current) that has never run before, from ITS OWN
 * perspective. That fresh effect sees current == mine (still true, nothing
 * about the singleton changed) and fires requestFocus() again - yanking
 * focus back toward whatever item just became "first," fighting the user's
 * own navigation on every single scroll step.
 *
 * claimId is the fix: a value unique to THIS SPECIFIC transition, not just
 * this FocusEntry value - callers pass remember(current) { Any() } (or
 * equivalent), which mints a genuinely new identity every time `current`
 * changes, even if the new value happens to equal an earlier one (e.g.
 * None -> FirstItem -> None -> FirstItem again). scopeKey (typically the
 * screen's route) partitions consumption per screen, via
 * FocusRegistry.hasFocusEntryBeenClaimed/markFocusEntryClaimed, so only the FIRST item to see a given
 * claimId actually calls requestFocus() - every other item that later
 * qualifies for isFirstItem during the SAME transition finds it already
 * consumed and does nothing, however many times isFirstItem's target
 * shifts underneath continued scrolling.
 */
@Composable
fun Modifier.claimFocusEntry(
    current: FocusEntry,
    mine: FocusEntry,
    requester: FocusRequester,
    scopeKey: String?,
    claimId: Any
): Modifier {
    LaunchedEffect(claimId) {
        if (current == mine && scopeKey != null && !FocusRegistry.hasFocusEntryBeenClaimed(scopeKey, claimId)) {
            val result = runCatching { requester.requestFocus() }
            if (result.isSuccess) {
                FocusRegistry.markFocusEntryClaimed(scopeKey, claimId)
            }
            // FIX (see FocusRegistry.markFocusEntryClaimed's doc comment
            // for the full bug): deliberately NOT marking claimed on
            // failure - a failed attempt (e.g. the target's
            // FocusRequester genuinely not attached yet, a timing race)
            // leaves this transition open for a later item that also
            // qualifies to retry, instead of the transition being
            // permanently stuck unclaimed.
        }
    }
    return this.focusRequester(requester)
}