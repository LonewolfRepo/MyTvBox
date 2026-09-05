package com.itv.blockbuster.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.data.session.WatchdogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    val sessionManager: StalkerSessionManager,
    val watchdogManager: WatchdogManager
) : ViewModel() {

    init {
        // Observe session state — start watchdog when portal becomes active,
        // stop it when session is cleared
        viewModelScope.launch {
            sessionManager.activePortal.collect { portal ->
                if (portal != null) {
                    watchdogManager.startWatchdog()
                } else {
                    watchdogManager.stopWatchdog()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        watchdogManager.stopWatchdog()
    }
}