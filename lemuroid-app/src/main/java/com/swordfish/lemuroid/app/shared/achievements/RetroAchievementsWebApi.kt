package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Read-only RetroAchievements website API. This is not an emulator login or
 * an achievement unlock engine. Requests are explicit and never award points.
 *
 * Official endpoints:
 *  - API_GetUserProfile.php
 *  - API_GetGameList.php?i=7&f=1&h=1
 *  - API_GetGameInfoAndUserProgress.php
 *
 * A public hash-to-game index is cached for seven days to avoid repeated
 * large API requests. No API key, username, or user progress is cached.
 */
class RetroAchievementsWebApi(context: Context) {
    data class Progress(
        val gameId: Int,
        val username: String,
        val romMd5: String,
        val achievements: List<VerifiedAchievement>,
    )

    private val indexFile =
        AtomicFile(File(context.applicationContext.cacheDir, "opennes-ra-nes-hashes-v1.json"))

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(50, TimeUnit.SECONDS)
            // The official web API uses a query key; never send it to redirects.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun validateWebAccount(account: RetroAchievementsWebAccount): String =
        withContext(Dispatchers.IO) {
            val response =
                JSONObject(
                    getBody(
                        "API_GetUserProfile.php",
                        account.apiKey,
                        listOf("u" to account.username),
                        MAX_PROFILE_BYTES,
                    ),
                )
            val user = response.optString("User")
            if (user.isBlank() || response.optInt("ID", 0) <= 0) {
                throw IOException("Could not verify this RetroAchievements web profile")
            }
            user
        }

    suspend fun lookupProgress(
        account: RetroAchievementsWebAccount,
        romMd5: String,
    ): Progress? =
        withContext(Dispatchers.IO) {
            require(romMd5.matches(Regex("[a-fA-F0-9]{32}"))) { "Invalid local NES fingerprint" }
            val gameId =
                loadGameIndex(account.apiKey)[romMd5.lowercase(Locale.ROOT)]
                    ?: return@withContext null
            val raw =
                getBody(
                    "API_GetGameInfoAndUserProgress.php",
                    account.apiKey,
                    listOf("g" to gameId.toString(), "u" to account.username),
                    MAX_PROFILE_BYTES,
                )
            val game = JSONObject(raw)
            require(game.optInt("ID", 0) == gameId && game.optInt("ConsoleID", -1) == NES_SYSTEM_ID) {
                "RetroAchievements game association changed"
            }
            val entries =
                game.optJSONObject("Achievements")
                    ?: throw IOException("RetroAchievements did not return an achievement list")
            val achievements = mutableListOf<Pair<Int, VerifiedAchievement>>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = entries.optJSONObject(key) ?: continue
                val id = item.optInt("ID", -1)
                val title = item.optString("Title")
                if (id <= 0 || title.isBlank()) continue
                val earned =
                    item.has("DateEarned") &&
                        !item.isNull("DateEarned") &&
                        item.optString("DateEarned").isNotBlank()
                achievements += item.optInt("DisplayOrder", Int.MAX_VALUE) to
                    VerifiedAchievement(
                        id = id,
                        title = title.take(200),
                        description = item.optString("Description").take(500),
                        points = item.optInt("Points", 0).coerceIn(0, 10000),
                        unlocked = earned,
                    )
            }
            Progress(
                gameId = gameId,
                username = account.username,
                romMd5 = romMd5.lowercase(Locale.ROOT),
                achievements = achievements.sortedBy { it.first }.map { it.second },
            )
        }

    private suspend fun loadGameIndex(apiKey: String): Map<String, Int> {
        val cache = readIndex()
        if (cache != null) return cache
        // One rate-limited request only, explicitly initiated by the player.
        // The website specifically recommends caching this large endpoint.
        val raw =
            getBody(
                "API_GetGameList.php",
                apiKey,
                listOf("i" to NES_SYSTEM_ID.toString(), "f" to "1", "h" to "1"),
                MAX_INDEX_BYTES,
            )
        val list = JSONArray(raw)
        require(list.length() in 1..20000) { "Unexpected NES game index size" }
        val entries = JSONObject()
        var total = 0
        for (index in 0 until list.length()) {
            coroutineContext.ensureActive()
            val game = list.optJSONObject(index) ?: continue
            if (game.optInt("ConsoleID", -1) != NES_SYSTEM_ID) continue
            val gameId = game.optInt("ID", 0)
            if (gameId <= 0) continue
            val hashes = game.optJSONArray("Hashes") ?: continue
            for (i in 0 until hashes.length()) {
                val md5 = hashes.optString(i).lowercase(Locale.ROOT)
                if (md5.matches(Regex("[a-f0-9]{32}"))) {
                    entries.put(md5, gameId)
                    total++
                }
            }
        }
        require(total > 0) { "RetroAchievements returned an empty NES hash index" }
        val payload =
            JSONObject()
                .put("updated", System.currentTimeMillis())
                .put("system", NES_SYSTEM_ID)
                .put("hashes", entries)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
        require(payload.size <= MAX_CACHE_BYTES) { "NES game index is too large to cache" }
        // A failed cache write does not invalidate a successfully downloaded index.
        try {
            val stream = indexFile.startWrite()
            try {
                stream.write(payload)
                indexFile.finishWrite(stream)
            } catch (e: Exception) {
                indexFile.failWrite(stream)
            }
        } catch (_: Exception) {
            // Cache is optional; no credentials are written here.
        }
        return mapHashes(entries)
    }

    private fun readIndex(): Map<String, Int>? {
        try {
            if (!indexFile.baseFile.isFile || indexFile.baseFile.length() > MAX_CACHE_BYTES) return null
            val json = JSONObject(String(indexFile.readFully(), StandardCharsets.UTF_8))
            val updated = json.optLong("updated", 0)
            if (json.optInt("system") != NES_SYSTEM_ID ||
                updated <= 0 ||
                System.currentTimeMillis() - updated !in 0L..INDEX_TTL_MS
            ) {
                return null
            }
            val hashes = json.optJSONObject("hashes") ?: return null
            return mapHashes(hashes)
        } catch (_: Exception) {
            // Corrupt or expired cache is disposable public metadata.
            return null
        }
    }

    private fun mapHashes(hashes: JSONObject): Map<String, Int> {
        val map = HashMap<String, Int>()
        val keys = hashes.keys()
        while (keys.hasNext()) {
            val hash = keys.next()
            val id = hashes.optInt(hash, 0)
            if (id > 0 && hash.matches(Regex("[a-f0-9]{32}"))) map[hash] = id
        }
        return map
    }

    private suspend fun getBody(
        endpoint: String,
        apiKey: String,
        query: List<Pair<String, String>>,
        limit: Int,
    ): String {
        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host("retroachievements.org")
                .addPathSegment("API")
                .addPathSegment(endpoint)
                .apply {
                    addQueryParameter("y", apiKey)
                    query.forEach { (key, value) -> addQueryParameter(key, value) }
                }
                .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        // Never log exception messages: an OkHttp exception can contain its URL,
        // and the official web API requires the key in its URL query.
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("RetroAchievements returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("RetroAchievements returned no data")
                if (body.contentLength() > limit) throw IOException("RetroAchievements response too large")
                body.byteStream().use { input ->
                    val result = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) {
                            val one = input.read()
                            if (one < 0) break
                            result.write(one)
                        } else {
                            result.write(buffer, 0, read)
                        }
                        if (result.size() > limit) throw IOException("RetroAchievements response too large")
                    }
                    return result.toString(StandardCharsets.UTF_8.name())
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw IOException("RetroAchievements web request failed. Check your API key and connection.")
        }
    }

    companion object {
        private const val NES_SYSTEM_ID = 7
        private const val MAX_INDEX_BYTES = 12 * 1024 * 1024
        private const val MAX_CACHE_BYTES = 5 * 1024 * 1024
        private const val MAX_PROFILE_BYTES = 1024 * 1024
        private const val INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
