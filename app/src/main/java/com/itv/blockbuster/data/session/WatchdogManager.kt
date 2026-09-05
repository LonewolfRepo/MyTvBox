package com.itv.blockbuster.data.session

import com.itv.blockbuster.data.repository.StalkerPortalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchdogManager @Inject constructor(
    private val portalService: StalkerPortalService,
) {
    private var watchdogJob: Job? = null
    private val watchdogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startWatchdog() {
        stopWatchdog()
        watchdogJob = watchdogScope.launch {
            while (isActive) {
                delay(59_000) // 59 seconds
                try {
                    portalService.sendWatchdog()
                } catch (_: Exception) {
                    // Silently ignore errors — watchdog must never impact user experience
                }
            }
        }
    }

    fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}