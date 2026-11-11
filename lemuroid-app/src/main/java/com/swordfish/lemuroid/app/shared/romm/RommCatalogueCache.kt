package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Best-effort, metadata-only cache. Never writes ROM content, bearer tokens,
 * private save data, or account identifiers. In noBackupFilesDir so a restored
 * install cannot inherit another device's cached game catalogue.
 *
 * A cache is usable only with the exact server fingerprint AND credential
 * revision that created it. Old accounts are never surfaced after reconnect.
 */
internal class RommCatalogueCache(context: Context) {
    data class Snapshot(
        val games: List<RommApiClient.Rom>,
        val fetchedAt: Long,
    )

    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "romm-nes-catalog-v1.json"))

    @Synchronized
    fun read(
        serverFingerprint: String,
        credentialRevision: String,
    ): Snapshot? {
        if (!RommServerIdentity.validFingerprint(serverFingerprint) ||
            credentialRevision.isBlank() || !file.baseFile.isFile
        ) {
            return null
        }
        return try {
            if (file.baseFile.length() !in 1..MAX_FILE_BYTES) return null
            val bytes = file.openRead().use { stream ->
                stream.readBytes().takeIf { it.size <= MAX_FILE_BYTES } ?: return null
            }
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optInt("version", -1) != VERSION ||
                root.optString("source") != serverFingerprint ||
                root.optString("revision") != credentialRevision
            ) {
                return null
            }
            val fetchedAt = root.optLong("fetched_at", 0)
            if (fetchedAt <= 0L ||
                fetchedAt > System.currentTimeMillis() + CLOCK_SKEW_MS
            ) {
                return null
            }
            val rows = root.optJSONArray("games") ?: return null
            if (rows.length() > MAX_GAMES) return null
            val seen = HashSet<Int>()
            val games =
                (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    val id = row.getInt("id")
                    val platform = row.getInt("platform")
                    val name = row.getString("name")
                    val filename = row.getString("filename")
                    val size = row.optLong("size", 0)
                    require(id > 0 && platform > 0 && id !in seen)
                    require(name.isNotBlank() && name.length <= 500)
                    require(RommApiClient.safeRomFileName(filename))
                    require(size in 0..MAX_ROM_SIZE)
                    seen += id
                    RommApiClient.Rom(
                        id = id,
                        platformId = platform,
                        name = name,
                        fileName = filename,
                        sizeBytes = size,
                        md5 = row.optString("md5").takeIf { it.matches(Regex("[0-9a-f]{32}")) },
                        description = row.optString("description").takeIf { it.isNotBlank() }?.take(MAX_SUMMARY),
                    )
                }
            Snapshot(games, fetchedAt)
        } catch (_: Exception) {
            // Cache corruption is not an authentication or library failure.
            null
        }
    }

    @Synchronized
    fun write(
        serverFingerprint: String,
        credentialRevision: String,
        games: List<RommApiClient.Rom>,
        fetchedAt: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!RommServerIdentity.validFingerprint(serverFingerprint) ||
            credentialRevision.isBlank() || games.size > MAX_GAMES || fetchedAt <= 0L
        ) {
            return false
        }
        return try {
            val items = JSONArray()
            for (rom in games) {
                if (rom.id <= 0 || rom.platformId <= 0 ||
                    rom.name.isBlank() || !RommApiClient.safeRomFileName(rom.fileName)
                ) {
                    return false
                }
                items.put(
                    JSONObject()
                        .put("id", rom.id)
                        .put("platform", rom.platformId)
                        .put("name", rom.name.take(500))
                        .put("filename", rom.fileName)
                        .put("size", rom.sizeBytes)
                        .put("md5", rom.md5.orEmpty())
                        .put("description", rom.description.orEmpty().take(MAX_SUMMARY)),
                )
            }
            val payload =
                JSONObject()
                    .put("version", VERSION)
                    .put("source", serverFingerprint)
                    .put("revision", credentialRevision)
                    .put("fetched_at", fetchedAt)
                    .put("games", items)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            if (payload.size > MAX_FILE_BYTES) return false
            val output = file.startWrite()
            try {
                output.write(payload)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    companion object {
        private const val VERSION = 1
        private const val MAX_GAMES = 10_000
        private const val MAX_SUMMARY = 8_000
        private const val MAX_FILE_BYTES = 12 * 1024 * 1024L
        private const val MAX_ROM_SIZE = 96 * 1024 * 1024L
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
    }
}
