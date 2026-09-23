package com.itv.blockbuster.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.itv.blockbuster.ui.navigation.AppRoot
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BlockbusterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            BlockbusterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BbBackground
                ) {
                    AppRoot()
                }
            }
        }
    }

    // NEW: onUserLeaveHint() fires specifically when the user makes a
    // deliberate choice to leave the app to the foreground of something
    // else - pressing Home, or switching away via Recents - as opposed to
    // other things that also pause/stop an Activity (an incoming call,
    // pulling down the notification shade, the screen turning off, or this
    // app itself launching another of its own activities/screens). That
    // makes it the right, precise hook for "Home button pressed": rather
    // than letting the system do its normal thing (move the task to the
    // background so it can be resumed later, i.e. "minimize"), immediately
    // tear the task down and kill the process outright, so there's nothing
    // left running to resume - the app terminates instead of minimizing.
    // DISABLED (cold start): this used to finishAndRemoveTask() +
    // killProcess() here, meaning every single re-entry into the app after
    // pressing Home - not just the very first cold start after install -
    // paid the full JIT-compilation/class-verification tax again, since
    // ART's JIT cache lives in the process and there was never a warm
    // process left to reuse. Letting onUserLeaveHint() do nothing lets
    // Android background the task normally (kept warm in the LRU cache when
    // memory allows, only actually killed by the OS under real memory
    // pressure) - "user leaves and comes back" should now often resume a
    // warm process instead of forcing a full cold start every time.
    // Revisit if there's a hard requirement (DRM/licensing session
    // freshness, a TV store certification rule, deliberate memory hygiene)
    // that actually needs the process to die on every exit - flagged for a
    // follow-up decision, not removed because it was wrong outright.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }
}
