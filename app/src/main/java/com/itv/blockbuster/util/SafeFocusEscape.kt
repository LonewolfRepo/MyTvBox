package com.itv.blockbuster.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Safely intercepts a single directional D-pad key and attempts to move
 * focus to [target] when pressed, INSTEAD OF using
 * Modifier.focusProperties { down/right/up/left = target }.
 *
 * Compose's own focus-search, when it navigates using a focusProperties
 * directional override, throws an UNCATCHABLE IllegalStateException
 * ("FocusRequester is not initialized") if that FocusRequester isn't
 * CURRENTLY attached to a composed node - and this can happen even for a
 * requester that WAS successfully attached moments earlier: if the item
 * holding it sits inside a virtualizing list (LazyRow/LazyColumn/LazyGrid -
 * e.g. NetflixStyleCarousel's internal LazyRow), it can be scrolled/laid
 * out away from that exact position and temporarily leave composition -
 * for example during a window resize triggered by the on-screen keyboard
 * opening or closing - while the list itself (and therefore
 * FocusRegistry's registration for it) is still very much alive. That's a
 * DIFFERENT failure mode than a stale registration from a fully-disposed
 * composable (see FocusRegistry.unregisterFirstItem for that one) and isn't
 * fixed by it.
 *
 * Handling the requestFocus() call ourselves inside runCatching sidesteps
 * the whole problem: on failure, this just returns false (event not
 * consumed), falling through to Compose's own default focus search instead
 * of crashing the app outright.
 */
fun Modifier.safeFocusEscape(key: Key, target: FocusRequester): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == key) {
        runCatching { target.requestFocus() }.isSuccess
    } else {
        false
    }
}

/**
 * Same as above, but looks [target] up FRESH at the moment the key is
 * pressed rather than using a value captured earlier - for targets like
 * FocusRegistry.firstItemTarget(route), which is meant to reflect whichever
 * item is CURRENTLY registered, not whatever it was when this modifier was
 * built.
 */
fun Modifier.safeFocusEscape(key: Key, target: () -> FocusRequester): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == key) {
        runCatching { target().requestFocus() }.isSuccess
    } else {
        false
    }
}
