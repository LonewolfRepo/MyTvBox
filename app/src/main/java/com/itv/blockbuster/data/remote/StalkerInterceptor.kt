package com.itv.blockbuster.data.remote

import com.itv.blockbuster.data.session.StalkerSessionManager
import okhttp3.Interceptor
import okhttp3.Response

class StalkerInterceptor(
    private val sessionManager: StalkerSessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val portal = sessionManager.activePortal.value
            ?: return chain.proceed(originalRequest)

        val url = originalRequest.url

        val userAgent = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 " +
                "(KHTML, like Gecko) MAG200 stbapp ver: 4 rev: 2116 Mobile Safari/533.3"
        val xUserAgent = "Model: MAG254; Link: Ethernet"

        val cookieHeader = buildString {
            append("PHPSESSID=null; ")
            append("timezone=${portal.timezoneId}; ")
            append("mac=${portal.mac}; ")
            append("stb_lang=English; language=en")

            val extra = sessionManager.cookies.value.joinToString("; ")
            if (extra.isNotEmpty()) {
                append("; ")
                append(extra)
            }
        }

        val hostHeader = if (
            (url.scheme == "http" && url.port == 80) ||
            (url.scheme == "https" && url.port == 443)
        ) url.host else "${url.host}:${url.port}"

        val referer = "${url.scheme}://$hostHeader${sessionManager.portalDir.value}"

        val builder = originalRequest.newBuilder()
            .header("User-Agent", userAgent)
            .header("X-User-Agent", xUserAgent)
            .header("Accept", "*/*")
            .header("Cookie", cookieHeader)
            .header("Referer", referer)

        sessionManager.bearerToken.value?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())

        response.headers("Set-Cookie").forEach { setCookieHeader ->
            sessionManager.appendCookie(setCookieHeader.substringBefore(";").trim())
        }

        return response
    }
}