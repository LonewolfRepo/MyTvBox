package com.itv.blockbuster.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itv.blockbuster.data.repository.ConnectionRepository
import com.itv.blockbuster.data.repository.ServerRepository
import com.itv.blockbuster.data.session.StalkerSessionManager
import com.itv.blockbuster.data.session.WatchdogManager
import com.itv.blockbuster.data.local.SettingsRepository
import com.itv.blockbuster.data.local.UserPreferencesRepository
import com.itv.blockbuster.data.session.AdultSessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    val sessionManager: StalkerSessionManager,
    val watchdogManager: WatchdogManager,
    private val serverRepository: ServerRepository,
    private val connectionRepository: ConnectionRepository,
    private val settings: SettingsRepository, // NEW
    private val prefs: UserPreferencesRepository, // NEW
    val adultSessionManager: AdultSessionManager // NEW
) : ViewModel() {
    // NEW: Expose adult content visibility based on profile settings
    val displayAdultContent: StateFlow<Boolean> = combine(
        prefs.activeProfileIdFlow,
        sessionManager.activePortal
    ) { p, sp -> Pair(p, sp?.serverId ?: 0) }.flatMapLatest { (p, s) ->
        settings.getBoolFlow(p, s, "display_adult", false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
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