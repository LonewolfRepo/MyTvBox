package com.itv.blockbuster.data.remote

import com.itv.blockbuster.data.session.ReauthManager
import com.itv.blockbuster.data.session.StalkerSessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.nio.charset.Charset

class StalkerInterceptor(
    private val sessionManager: StalkerSessionManager,
    private val reauthManager: ReauthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val portal = sessionManager.activePortal.value
            ?: return chain.proceed(originalRequest)

        val url = originalRequest.url
        val urlPath = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")

        // Loop prevention: skip reauth for auth endpoints
        val isAuthEndpoint = urlPath.contains("action=handshake") ||
                urlPath.contains("action=get_profile") ||
                urlPath.contains("type=watchdog")

        val authenticatedRequest = buildRequest(originalRequest)
        val response = chain.proceed(authenticatedRequest)

        // Capture Set-Cookie
        response.headers("Set-Cookie").forEach { setCookieHeader ->
            sessionManager.appendCookie(setCookieHeader.substringBefore(";").trim())
        }

        if (isAuthEndpoint) {
            return response
        }

        // Handle standard HTTP 401/403
        if (response.code == 401 || response.code == 403) {
            response.close()
            val success = runBlocking { reauthManager.reauth() }
            if (success) {
                return chain.proceed(buildRequest(originalRequest))
            }
            return Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(ResponseBody.create(null, ""))
                .build()
        }

        // Peek body for Stalker-specific auth errors (HTTP 200 but JSON contains error)
        val source = response.body?.source()
        if (source != null) {
            try {
                source.request(Long.MAX_VALUE) // Buffer the entire body
                val buffer = source.buffer
                val bodyString = buffer.clone().readString(Charset.forName("UTF-8"))

                if (isStalkerAuthError(bodyString)) {
                    response.close()
                    val success = runBlocking { reauthManager.reauth() }
                    if (success) {
                        return chain.proceed(buildRequest(originalRequest))
                    }
                    // Reconstruct response body since we closed it
                    val newBody = ResponseBody.create(
                        response.body?.contentType(),
                        buffer.clone().readString(Charset.forName("UTF-8"))
                    )
                    return response.newBuilder().body(newBody).build()
                }
            } catch (e: Exception) {
                // Ignore peek errors
            }
        }

        return response
    }

    private fun isStalkerAuthError(body: String): Boolean {
        return body.contains("Authorization failed", ignoreCase = true) ||
                body.contains("\"error\":\"Authorization", ignoreCase = true)
    }

    private fun buildRequest(request: Request): Request {
        val portal = sessionManager.activePortal.value ?: return request
        val url = request.url

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

        val builder = request.newBuilder()
            .header("User-Agent", userAgent)
            .header("X-User-Agent", xUserAgent)
            .header("Accept", "*/*")
            .header("Cookie", cookieHeader)
            .header("Referer", referer)

        sessionManager.bearerToken.value?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        return builder.build()
    }
}