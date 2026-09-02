package com.itv.blockbuster.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class ActivePortal(
    val serverId: Int,
    val name: String,
    val host: String,
    val mac: String,
    val username: String = "",
    val password: String = "",
    val useCredentials: Boolean = false,
    val timezoneId: String = TimeZone.getDefault().id
)

@Singleton
class StalkerSessionManager @Inject constructor() {

    private val _activePortal = MutableStateFlow<ActivePortal?>(null)
    val activePortal: StateFlow<ActivePortal?> = _activePortal.asStateFlow()

    private val _bearerToken = MutableStateFlow<String?>(null)
    val bearerToken: StateFlow<String?> = _bearerToken.asStateFlow()

    private val _portalDir = MutableStateFlow(DEFAULT_PORTAL_DIR)
    val portalDir: StateFlow<String> = _portalDir.asStateFlow()

    private val _ajaxLoader = MutableStateFlow("")
    val ajaxLoader: StateFlow<String> = _ajaxLoader.asStateFlow()

    private val _cookies = MutableStateFlow<Set<String>>(emptySet())
    val cookies: StateFlow<Set<String>> = _cookies.asStateFlow()

    fun setActivePortal(portal: ActivePortal) {
        _activePortal.value = portal
    }

    fun setBearerToken(token: String) {
        _bearerToken.value = token
    }

    fun setPortalDir(dir: String) {
        _portalDir.value = dir
    }

    fun setAjaxLoader(url: String) {
        _ajaxLoader.value = url
    }

    fun appendCookie(cookie: String) {
        val cleaned = cookie.trim()
        if (cleaned.isEmpty()) return
        _cookies.update { it + cleaned }
    }

    fun clearSession() {
        _bearerToken.value = null
        _ajaxLoader.value = ""
        _cookies.value = emptySet()
    }

    fun clearAll() {
        _activePortal.value = null
        _portalDir.value = DEFAULT_PORTAL_DIR
        clearSession()
    }

    private companion object {
        const val DEFAULT_PORTAL_DIR = "/stalker_portal/"
    }
}