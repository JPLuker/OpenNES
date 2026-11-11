package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Metadata-only and private: never store token, ROM bytes, save content,
 * passwords or account names. The credential revision changes on every login,
 * so an offline catalogue cannot leak across RomM accounts on one server.
 */
internal class RommCatalogueCache(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "romm-nes-catalog-v1.json"))

    @Synchronized
    fun read(
        serverFingerprint: String,
        credentialRevision: String,
    ): RommCatalogueCodec.Snapshot? {
        if (!file.baseFile.isFile || file.baseFile.length() !in 1L..RommCatalogueCodec.MAX_BYTES.toLong()) {
            return null
        }
        return try {
            val bytes =
                file.openRead().use { input ->
                    input.readBytes().takeIf { it.size <= RommCatalogueCodec.MAX_BYTES } ?: return null
                }
            RommCatalogueCodec.decode(bytes, serverFingerprint, credentialRevision)
        } catch (_: Exception) {
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
        val payload = RommCatalogueCodec.encode(games, serverFingerprint, credentialRevision, fetchedAt) ?: return false
        return try {
            val stream = file.startWrite()
            try {
                stream.write(payload)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
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
}

/** Pure bounded codec, exercised by local JVM tests without Android storage. */
internal object RommCatalogueCodec {
    data class Snapshot(
        val games: List<RommApiClient.Rom>,
        val fetchedAt: Long,
    )

    const val MAX_BYTES = 12 * 1024 * 1024
    private const val VERSION = 1
    private const val MAX_GAMES = 10_000
    private const val MAX_SUMMARY = 8_000
    private const val MAX_ROM_BYTES = 96 * 1024 * 1024L
    private const val CLOCK_SKEW_MS = 5L * 60 * 1000

    fun encode(
        games: List<RommApiClient.Rom>,
        source: String,
        revision: String,
        timestamp: Long,
    ): ByteArray? {
        if (!RommServerIdentity.validFingerprint(source) ||
            revision.isBlank() || timestamp <= 0L || games.size > MAX_GAMES
        ) {
            return null
        }
        return runCatching {
            val array = JSONArray()
            val seen = HashSet<Int>()
            for (rom in games) {
                require(validRom(rom) && seen.add(rom.id))
                array.put(
                    JSONObject()
                        .put("id", rom.id)
                        .put("platform", rom.platformId)
                        .put("name", rom.name)
                        .put("filename", rom.fileName)
                        .put("size", rom.sizeBytes)
                        .put("md5", rom.md5.orEmpty())
                        .put("description", rom.description.orEmpty().take(MAX_SUMMARY)),
                )
            }
            val payload =
                JSONObject()
                    .put("version", VERSION)
                    .put("source", source)
                    .put("revision", revision)
                    .put("fetched_at", timestamp)
                    .put("games", array)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            payload.takeIf { it.size <= MAX_BYTES }
        }.getOrNull()
    }

    fun decode(
        payload: ByteArray,
        source: String,
        revision: String,
        now: Long = System.currentTimeMillis(),
    ): Snapshot? {
        if (payload.isEmpty() || payload.size > MAX_BYTES ||
            !RommServerIdentity.validFingerprint(source) || revision.isBlank()
        ) {
            return null
        }
        return runCatching {
            val root = JSONObject(String(payload, Charsets.UTF_8))
            require(
                root.optInt("version", -1) == VERSION &&
                    root.optString("source") == source &&
                    root.optString("revision") == revision,
            )
            val timestamp = root.getLong("fetched_at")
            require(timestamp > 0L && timestamp <= now + CLOCK_SKEW_MS)
            val array = root.getJSONArray("games")
            require(array.length() <= MAX_GAMES)
            val seen = HashSet<Int>()
            val games =
                (0 until array.length()).map { index ->
                    val row = array.getJSONObject(index)
                    val md5 = row.getString("md5")
                    require(md5.isEmpty() || md5.matches(Regex("[a-f0-9]{32}")))
                    val summary = row.getString("description")
                    require(summary.length <= MAX_SUMMARY)
                    val rom =
                        RommApiClient.Rom(
                            id = row.getInt("id"),
                            platformId = row.getInt("platform"),
                            name = row.getString("name"),
                            fileName = row.getString("filename"),
                            sizeBytes = row.getLong("size"),
                            md5 = md5.ifBlank { null },
                            description = summary.ifBlank { null },
                        )
                    require(validRom(rom) && seen.add(rom.id))
                    rom
                }
            Snapshot(games, timestamp)
        }.getOrNull()
    }

    private fun validRom(rom: RommApiClient.Rom): Boolean =
        rom.id > 0 &&
            rom.platformId > 0 &&
            rom.name.isNotBlank() &&
            rom.name.length <= 500 &&
            rom.sizeBytes in 0..MAX_ROM_BYTES &&
            RommApiClient.safeRomFileName(rom.fileName)
}
