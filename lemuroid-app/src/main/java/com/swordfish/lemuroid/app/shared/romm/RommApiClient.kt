package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Read-only RomM API adapter. Uses a scoped bearer token and HTTPS exclusively.
 * Every import is explicitly initiated by the player, staged in app cache,
 * and committed into the already selected NES ROM document tree.
 */
class RommApiClient {
    data class Rom(
        val id: Int,
        val platformId: Int,
        val name: String,
        val fileName: String,
        val sizeBytes: Long,
        val md5: String?,
    )

    data class Page(
        val items: List<Rom>,
        val total: Int,
        val offset: Int,
        val limit: Int,
    )

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Do not forward a bearer token to a redirected origin.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun checkAccount(connection: RommConnection): String =
        withContext(Dispatchers.IO) {
            val data = getJson(connection, listOf("api", "users", "me"))
            data.optString("username").takeIf { it.isNotBlank() }
                ?: throw IOException("RomM did not return a signed-in user")
        }

    suspend fun listNesRoms(
        connection: RommConnection,
        offset: Int = 0,
        limit: Int = 40,
    ): Page = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..100) { "Invalid RomM page" }
        val ids = findNesPlatformIds(connection)
        if (ids.isEmpty()) return@withContext Page(emptyList(), 0, offset, limit)
        val params =
            mutableListOf(
                "offset" to offset.toString(),
                "limit" to limit.toString(),
                "with_total" to "true",
                "with_char_index" to "false",
                "with_filter_values" to "false",
            )
        ids.forEach { id -> params += "platform_ids" to id.toString() }
        val document = getJson(connection, listOf("api", "roms"), params)
        val items = document.optJSONArray("items") ?: JSONArray()
        val result =
            (0 until items.length()).mapNotNull { index ->
                val row = items.optJSONObject(index) ?: return@mapNotNull null
                val platformId = row.optInt("platform_id", -1)
                if (platformId !in ids) return@mapNotNull null
                val id = row.optInt("id", -1)
                val fileName = row.optString("fs_name")
                if (id <= 0 || !safeRomFileName(fileName)) return@mapNotNull null
                Rom(
                    id = id,
                    platformId = platformId,
                    name = row.optString("name").takeIf { it.isNotBlank() } ?: fileName,
                    fileName = fileName,
                    sizeBytes = row.optLong("fs_size_bytes", 0L),
                    md5 = row.optString("md5_hash")
                        .lowercase(Locale.ROOT)
                        .takeIf { it.matches(Regex("[a-f0-9]{32}")) },
                )
            }
        Page(
            items = result,
            total = document.optInt("total", offset + items.length()),
            offset = offset,
            limit = limit,
        )
    }

    /** A RomM import never overwrites an existing local file or save. */
    suspend fun importIntoSelectedNesFolder(
        context: Context,
        connection: RommConnection,
        rom: Rom,
    ): Uri = withContext(Dispatchers.IO) {
        require(rom.id > 0 && safeRomFileName(rom.fileName)) {
            "This RomM game is not a supported standalone NES file"
        }
        @Suppress("DEPRECATION")
        val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val treeKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        val treeString = legacyPrefs.getString(treeKey, null)
            ?: throw IOException("Choose an OpenNES ROM folder in Settings first")
        val folder =
            DocumentFile.fromTreeUri(context, Uri.parse(treeString))
                ?: throw IOException("The selected NES ROM folder is unavailable")
        if (!folder.isDirectory || !folder.canWrite()) {
            throw IOException("The selected NES ROM folder is not writable")
        }
        if (folder.findFile(rom.fileName) != null) {
            throw IOException("A local ROM with this filename already exists; nothing was overwritten")
        }

        val temporary = File.createTempFile("opennes-romm-", ".part", context.cacheDir)
        try {
            downloadToTemp(connection, rom, temporary)
            coroutineContext.ensureActive()
            if (folder.findFile(rom.fileName) != null) {
                throw IOException("The local ROM appeared during download; import cancelled")
            }
            val newFile =
                folder.createFile("application/octet-stream", rom.fileName)
                    ?: throw IOException("Could not create a file in the selected NES folder")
            try {
                val stream = context.contentResolver.openOutputStream(newFile.uri, "w")
                    ?: throw IOException("Cannot write to selected NES folder")
                stream.use { output ->
                    temporary.inputStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count > 0) output.write(buffer, 0, count)
                        }
                    }
                    output.flush()
                }
            } catch (e: Exception) {
                // This file was freshly created by OpenNES, never an old ROM.
                newFile.delete()
                throw e
            }
            LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
            newFile.uri
        } finally {
            temporary.delete()
        }
    }

    private fun findNesPlatformIds(connection: RommConnection): Set<Int> {
        // RomM's GET /api/platforms returns a bare JSON array, unlike
        // the paginated {items, total} response of GET /api/roms.
        val payload = fetchJsonText(connection, listOf("api", "platforms"))
        val array =
            if (payload.trimStart().startsWith("[")) {
                JSONArray(payload)
            } else {
                val page = JSONObject(payload)
                page.optJSONArray("items") ?: page.optJSONArray("platforms") ?: JSONArray()
            }
        val matching = mutableSetOf<Int>()
        for (index in 0 until array.length()) {
            val platform = array.optJSONObject(index) ?: continue
            val slug = platform.optString("slug").lowercase(Locale.ROOT)
            val fsSlug = platform.optString("fs_slug").lowercase(Locale.ROOT)
            if (slug in NES_SLUGS || fsSlug in NES_SLUGS) {
                platform.optInt("id").takeIf { it > 0 }?.let { matching += it }
            }
        }
        return matching
    }

    private fun getJson(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): JSONObject = JSONObject(fetchJsonText(connection, path, params))

    private fun fetchJsonText(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): String {
        val url = buildUrl(connection, path, params)
        val request =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + connection.token)
                .header("Accept", "application/json")
                .header("User-Agent", "OpenNES/0.1 RomM read-only client")
                .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty RomM response")
            val bytes = body.byteStream().use { readBounded(it, MAX_JSON_BYTES) }
            String(bytes, Charsets.UTF_8)
        }
    }

    private suspend fun downloadToTemp(connection: RommConnection, rom: Rom, target: File) {
        val request =
            Request.Builder()
                .url(buildUrl(connection, listOf("api", "roms", rom.id.toString(), "content", rom.fileName)))
                .get()
                .header("Authorization", "Bearer " + connection.token)
                .header("User-Agent", "OpenNES/0.1 RomM read-only client")
                .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty RomM ROM response")
            if (body.contentLength() > MAX_ROM_BYTES) {
                throw IOException("RomM ROM exceeds import size limit")
            }
            val digest = MessageDigest.getInstance("MD5")
            var countTotal = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        countTotal += count
                        if (countTotal > MAX_ROM_BYTES) {
                            throw IOException("RomM ROM exceeds import size limit")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (countTotal == 0L) throw IOException("RomM returned an empty ROM")
            rom.md5?.let { expected ->
                val actual =
                    digest.digest().joinToString("") { byte ->
                        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                    }
                if (actual != expected) throw IOException("RomM ROM checksum mismatch")
            }
        }
    }

    private fun buildUrl(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): HttpUrl {
        val root = validatedServerUrl(connection.serverUrl).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid RomM HTTPS address")
        return root.newBuilder().apply {
            path.forEach { addPathSegment(it) }
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
    }

    private fun httpError(code: Int): IOException =
        IOException(
            when (code) {
                401 -> "RomM token expired or invalid (401)"
                403 -> "RomM token lacks required read scope (403)"
                404 -> "RomM API endpoint unavailable (404); check server version"
                in 300..399 -> "RomM redirected the request; a token was not forwarded"
                else -> "RomM server returned HTTP " + code
            },
        )

    companion object {
        private const val MAX_JSON_BYTES = 3 * 1024 * 1024L
        private const val MAX_ROM_BYTES = 96 * 1024 * 1024L
        private val NES_SLUGS = setOf("nes", "famicom", "fds", "famicom-disk-system")

        /** HTTPS-only: never expose bearer tokens over cleartext HTTP. */
        fun validatedServerUrl(raw: String): String {
            val candidate = raw.trim()
            val parsed = candidate.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid RomM URL")
            require(parsed.scheme == "https" && parsed.username.isEmpty() &&
                parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
                "RomM requires an HTTPS URL without login details or query"
            }
            // Accept both https://host and https://host/subpath.
            // The trailing slash is needed before appending /api endpoints.
            return if (parsed.encodedPath.endsWith("/")) {
                parsed.toString()
            } else {
                parsed.newBuilder().addPathSegment("").build().toString()
            }
        }

        fun safeRomFileName(fileName: String): Boolean {
            if (fileName.isBlank() || fileName.length > 230 || fileName.startsWith(".")) return false
            if (fileName.any { it == '/' || it == '\\' || it.isISOControl() }) return false
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return ext in setOf("nes", "fds", "zip")
        }

        private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val amount = input.read(buffer)
                if (amount < 0) break
                total += amount
                if (total > maxBytes) throw IOException("RomM JSON response exceeds limit")
                result.write(buffer, 0, amount)
            }
            return result.toByteArray()
        }
    }
}
