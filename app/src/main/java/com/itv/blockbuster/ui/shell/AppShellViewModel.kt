package com.itv.blockbuster.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.data.session.WatchdogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    val sessionManager: StalkerSessionManager,
    val watchdogManager: WatchdogManager,
    private val serverRepository: ServerRepository,
    private val connectionRepository: ConnectionRepository
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

        // GLOBAL BOOTSTRAP: Auto-connect to the active server whenever it changes.
        // This guarantees the session is ready before any screen tries to load data.
        viewModelScope.launch {
            serverRepository.getActiveServer().collect { server ->
                if (server != null) {
                    val needsConnect = sessionManager.ajaxLoader.value.isEmpty() ||
                            sessionManager.activePortal.value?.serverId != server.id
                    if (needsConnect) {
                        connectionRepository.connectToServer(server)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        watchdogManager.stopWatchdog()
    }
}