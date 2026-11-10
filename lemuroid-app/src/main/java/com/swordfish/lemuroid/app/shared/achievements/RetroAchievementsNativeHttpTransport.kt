package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bounded HTTPS transport intended for official native rc_client callbacks.
 *
 * The runtime supplies an endpoint and optional POST form body. Only the
 * official origin is allowed, no redirect is followed, credentials and
 * responses are never logged, and cancelled game sessions cancel the actual
 * OkHttp call instead of leaving a blocking request alive.
 *
 * Game-owned JNI rc_client callbacks and short-lived token verification use
 * this transport. It never creates client-side achievement awards.
 */
internal class RetroAchievementsNativeHttpTransport(
    private val client: OkHttpClient = createClient(),
) {
    data class Reply(
        val statusCode: Int,
        val body: ByteArray,
    )

    suspend fun execute(
        rawUrl: String,
        formPostData: String?,
    ): Reply {
        val target = validatedEndpoint(rawUrl)
        require(formPostData == null || formPostData.length <= MAX_POST_CHARACTERS) {
            "RetroAchievements request is too large"
        }
        val builder =
            Request.Builder()
                .url(target)
                .header("User-Agent", "OpenNES/0.1.0 (Android) rcheevos/12.5.0")
                .header("Accept", "application/json")
        val request =
            if (formPostData != null) {
                builder.post(formPostData.toRequestBody(FORM_CONTENT_TYPE)).build()
            } else {
                builder.get().build()
            }

        // Async OkHttp avoids blocking the caller's coroutine thread and,
        // critically, cancellation tears down the in-flight socket.
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            // Do not expose e.message: it can contain a URL
                            // with a login token or password query value.
                            continuation.resumeWithException(IOException("RetroAchievements transport unavailable"))
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            response.use {
                                if (response.code in 300..399) {
                                    throw IOException("RetroAchievements redirect rejected")
                                }
                                val body =
                                    response.body
                                        ?: throw IOException("Empty RetroAchievements response")
                                if (body.contentLength() > MAX_RESPONSE_BYTES) {
                                    throw IOException("RetroAchievements response exceeds safe size")
                                }
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0
                                body.byteStream().use { input ->
                                    while (true) {
                                        if (!continuation.isActive) {
                                            throw IOException("RetroAchievements request cancelled")
                                        }
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        if (read == 0) continue
                                        total += read
                                        if (total > MAX_RESPONSE_BYTES) {
                                            throw IOException("RetroAchievements response exceeds safe size")
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                }
                                if (continuation.isActive) {
                                    continuation.resume(Reply(response.code, output.toByteArray()))
                                }
                            }
                        } catch (_: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException("RetroAchievements request failed or exceeded size limit"),
                                )
                            }
                        } catch (_: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("RetroAchievements transport unavailable"))
                            }
                        }
                    }
                },
            )
        }
    }

    companion object {
        private fun createClient() =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_POST_CHARACTERS = 64 * 1024
        private val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded".toMediaType()

        /** Reject unsafe URLs before a native callback can send secrets. */
        fun validatedEndpoint(raw: String): HttpUrl {
            val url =
                raw.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Invalid RetroAchievements endpoint")
            require(
                url.scheme == "https" &&
                    url.host == "retroachievements.org" &&
                    url.port == 443 &&
                    url.username.isEmpty() &&
                    url.password.isEmpty() &&
                    url.fragment == null,
            ) {
                "RetroAchievements calls require the official HTTPS endpoint"
            }
            return url
        }
    }
}
