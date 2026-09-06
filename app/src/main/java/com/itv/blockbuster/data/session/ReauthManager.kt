package com.itv.blockbuster.data.session

import com.itv.blockbuster.data.remote.StalkerApi
import com.itv.blockbuster.util.MacSerialUtils
import dagger.Lazy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReauthManager @Inject constructor(
    private val sessionManager: StalkerSessionManager,
    private val apiLazy: Lazy<StalkerApi>
) {
    private val mutex = Mutex()
    private var lastReauthTime = 0L

    suspend fun reauth(): Boolean = mutex.withLock {
        // Prevent spamming reauth if it just happened within the last 2 seconds
        if (System.currentTimeMillis() - lastReauthTime < 2000) {
            return@withLock true
        }

        val portal = sessionManager.activePortal.value ?: return@withLock false
        val portalDir = sessionManager.portalDir.value

        // If portalDir is empty, we can't reliably construct the URL
        if (portalDir.isEmpty()) return@withLock false

        val cleanHost = portal.host.trimEnd('/')
        val api = apiLazy.get()

        return@withLock try {
            // 1. Handshake
            val handshakeUrl = "$cleanHost${portalDir}server/load.php?action=handshake&type=stb&JsHttpRequest=1-xml"
            val handshakeResponse = api.handshake(handshakeUrl)
            val token = handshakeResponse.js.token.trim()
            if (token.isEmpty()) return@withLock false

            sessionManager.setBearerToken(token)

            // 2. Profile Refresh
            val serialNumber = MacSerialUtils.generateSerial(portal.mac)
            val profileUrl = "$cleanHost${portalDir}server/load.php" +
                    "?action=get_profile&type=stb&hd=1" +
                    "&ver=ImageDescription:%200.2.18-r14-pub-250" +
                    "&sn=$serialNumber&stb_type=MAG254&client_type=STB" +
                    "&device_id=&deviceid2=&JsHttpRequest=1-xml"

            val profileResponse = api.getProfile(profileUrl)
            val status = profileResponse.js.status
            val isSuccess = status.isEmpty() || status.equals("OK", ignoreCase = true) || status == "0"

            if (isSuccess) {
                sessionManager.setBearerToken(token)
                lastReauthTime = System.currentTimeMillis()
            }
            isSuccess
        } catch (e: Exception) {
            false
        }
    }
}