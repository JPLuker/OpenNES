package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Dedicated HTTPS transport for future native rc_client server callbacks.
 *
 * The official runtime supplies URL and optional form POST data. Do not
 * substitute the read-only public API key or make requests to arbitrary
 * hosts. No credentials, responses or endpoint URLs are logged.
 *
 * This transport is not connected to JNI yet and cannot earn awards alone.
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
    ): Reply =
        withContext(Dispatchers.IO) {
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
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) throw IOException("RetroAchievements redirect rejected")
                val stream = response.body?.byteStream() ?: throw IOException("Empty RetroAchievements response")
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                stream.use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > MAX_RESPONSE_BYTES) {
                            throw IOException("RetroAchievements response exceeds safe size")
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
                Reply(response.code, bytes.toByteArray())
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
            val url = raw.toHttpUrlOrNull()
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
