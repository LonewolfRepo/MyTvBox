package com.itv.blockbuster.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lightweight reachability check for a media stream URL, run right before
 * navigating into the Player - see the callers in AppNavigation.kt (applies
 * to both Live TV and VOD, since every entry point funnels through the same
 * handful of `navController.navigate("player/...")` call sites there).
 *
 * Uses its own short-timeout OkHttpClient rather than the app's shared
 * Stalker-portal client - that one attaches portal-auth headers/interceptors
 * meant for the Stalker JSON API, not raw CDN/media-server stream URLs, and
 * its timeouts are tuned for slow portal API responses rather than a quick
 * "is this even up" probe. Redirects are followed manually rather than by
 * OkHttp itself (followRedirects/followSslRedirects = false) so a 3xx is
 * seen and treated as "reachable" directly, instead of OkHttp silently
 * chasing it to some final status we'd otherwise have to unwind.
 */
object StreamValidator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Returns true the first time [url] responds with an informational,
     * successful, or redirection status (100-399 - see
     * https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status),
     * retrying up to [attempts] times, [delayBetweenMs] apart, on failure/
     * an invalid status/timeout.
     *
     * Uses GET rather than HEAD - some IPTV/Stalker media servers don't
     * implement HEAD correctly for live stream endpoints - but only
     * inspects the response status before closing the connection; the body
     * (the actual stream data) is never read, so this doesn't download
     * anything beyond the response headers.
     */
    suspend fun isReachable(
        url: String,
        attempts: Int = 2,
        delayBetweenMs: Long = 500
    ): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        repeat(attempts) { attempt ->
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 100..399) return@withContext true
                }
            } catch (e: Exception) {
                // Network error/timeout on this attempt - fall through and retry.
            }
            if (attempt < attempts - 1) delay(delayBetweenMs)
        }
        false
    }
}