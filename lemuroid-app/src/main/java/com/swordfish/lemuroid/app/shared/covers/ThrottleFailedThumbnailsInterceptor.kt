package com.swordfish.lemuroid.app.shared.covers

import android.util.LruCache
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object ThrottleFailedThumbnailsInterceptor : Interceptor {
    // Only permanent misses are worth suppressing. 429 and 5xx responses
    // must remain retryable when the server/network recovers.
    private val missingThumbnails = LruCache<String, Int>(512)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestUrl = chain.request().url.toString()
        if (missingThumbnails[requestUrl] != null) {
            throw IOException("Thumbnail is missing from the catalogue: $requestUrl")
        }

        val response = chain.proceed(chain.request())
        if (response.code == 404 || response.code == 410) {
            missingThumbnails.put(requestUrl, response.code)
        }
        return response
    }
}
