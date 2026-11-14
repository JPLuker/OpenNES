package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * What two-way save sync remembers between runs, for one RomM server and
 * account. Holds no token, save bytes or file paths: only hashes, ids, the
 * player's on/off choice and the outcome of the last run.
 *
 * [owner] binds the state to a server fingerprint and account id. A different
 * owner (another server, account or reconnect with a new account) starts from
 * a clean, disabled state, so one account's history is never used for another.
 */
internal data class RommTwoWaySyncState(
    val owner: String? = null,
    val enabled: Boolean = false,
    /** RomM ROM id → MD5 of the save last known to be identical on both sides. */
    val baselines: Map<Int, String> = emptyMap(),
    val conflicts: List<Conflict> = emptyList(),
    val lastRun: LastRun? = null,
) {
    data class Conflict(
        val romId: Int,
        val gameId: Int,
        val title: String,
        val saveId: Long?,
        val localMd5: String?,
        val serverMd5: String?,
        val reason: RommTwoWaySyncPlan.ConflictReason,
        val detectedAtMillis: Long,
    )

    data class LastRun(
        val atMillis: Long,
        val uploaded: Int,
        val downloaded: Int,
        val conflicts: Int,
        val failed: Int,
        /** A short, user-safe message; never a server response or path. */
        val problem: String? = null,
        /** Games on this phone that were paired with a game on RomM. */
        val matched: Int = 0,
    )

    fun forOwner(currentOwner: String): RommTwoWaySyncState =
        if (owner == currentOwner) this else RommTwoWaySyncState(owner = currentOwner)

    fun toJson(): String {
        val root = JSONObject().put("version", VERSION).put("enabled", enabled)
        owner?.let { root.put("owner", it) }
        val base = JSONObject()
        baselines.toSortedMap().forEach { (romId, md5) -> base.put(romId.toString(), md5) }
        root.put("baselines", base)
        val rows = JSONArray()
        conflicts.forEach { c ->
            rows.put(
                JSONObject()
                    .put("rom_id", c.romId)
                    .put("game_id", c.gameId)
                    .put("title", c.title)
                    .put("save_id", c.saveId ?: JSONObject.NULL)
                    .put("local_md5", c.localMd5 ?: JSONObject.NULL)
                    .put("server_md5", c.serverMd5 ?: JSONObject.NULL)
                    .put("reason", c.reason.name)
                    .put("detected_at", c.detectedAtMillis),
            )
        }
        root.put("conflicts", rows)
        lastRun?.let { run ->
            root.put(
                "last_run",
                JSONObject()
                    .put("at", run.atMillis)
                    .put("uploaded", run.uploaded)
                    .put("downloaded", run.downloaded)
                    .put("conflicts", run.conflicts)
                    .put("failed", run.failed)
                    .put("matched", run.matched)
                    .put("problem", run.problem ?: JSONObject.NULL),
            )
        }
        return root.toString()
    }

    companion object {
        private const val VERSION = 1
        private const val MAX_TITLE = 120
        private const val MAX_PROBLEM = 200
        const val MAX_CONFLICTS = 64
        const val MAX_BASELINES = 4096
        private val md5Pattern = Regex("[0-9a-f]{32}")
        private val ownerPattern = Regex("[0-9a-f]{64}:[1-9][0-9]{0,18}")

        fun owner(
            serverFingerprint: String,
            accountId: Long,
        ): String = "$serverFingerprint:$accountId"

        /** Anything unreadable becomes the clean, disabled default. */
        fun fromJson(text: String): RommTwoWaySyncState =
            runCatching { parse(JSONObject(text)) }.getOrDefault(RommTwoWaySyncState())

        private fun parse(root: JSONObject): RommTwoWaySyncState {
            if (root.optInt("version") != VERSION) return RommTwoWaySyncState()
            val owner = root.optString("owner").takeIf { it.matches(ownerPattern) }
            val baselines = linkedMapOf<Int, String>()
            root.optJSONObject("baselines")?.let { base ->
                base.keys().asSequence().take(MAX_BASELINES).forEach { key ->
                    val romId = key.toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                    val md5 = base.optString(key).takeIf { it.matches(md5Pattern) } ?: return@forEach
                    baselines[romId] = md5
                }
            }
            val conflicts = mutableListOf<Conflict>()
            val rows = root.optJSONArray("conflicts") ?: JSONArray()
            for (i in 0 until minOf(rows.length(), MAX_CONFLICTS)) {
                val row = rows.optJSONObject(i) ?: continue
                val romId = row.optInt("rom_id").takeIf { it > 0 } ?: continue
                val reason =
                    RommTwoWaySyncPlan.ConflictReason.entries.firstOrNull { it.name == row.optString("reason") }
                        ?: continue
                conflicts +=
                    Conflict(
                        romId = romId,
                        gameId = row.optInt("game_id"),
                        title = row.optString("title").take(MAX_TITLE),
                        saveId = row.optLong("save_id", -1L).takeIf { !row.isNull("save_id") && it > 0 },
                        localMd5 = row.optString("local_md5").takeIf { it.matches(md5Pattern) },
                        serverMd5 = row.optString("server_md5").takeIf { it.matches(md5Pattern) },
                        reason = reason,
                        detectedAtMillis = row.optLong("detected_at"),
                    )
            }
            val lastRun =
                root.optJSONObject("last_run")?.let { run ->
                    LastRun(
                        atMillis = run.optLong("at"),
                        uploaded = run.optInt("uploaded").coerceAtLeast(0),
                        downloaded = run.optInt("downloaded").coerceAtLeast(0),
                        conflicts = run.optInt("conflicts").coerceAtLeast(0),
                        failed = run.optInt("failed").coerceAtLeast(0),
                        matched = run.optInt("matched").coerceAtLeast(0),
                        problem =
                            if (run.isNull("problem")) null else run.optString("problem").take(MAX_PROBLEM),
                    )
                }
            return RommTwoWaySyncState(
                owner = owner,
                // Never enabled without a known owner.
                enabled = owner != null && root.optBoolean("enabled"),
                baselines = baselines,
                conflicts = conflicts.distinctBy { it.romId },
                lastRun = lastRun,
            )
        }
    }
}

/** Reads and atomically replaces the state file. Bounded; never throws on read. */
internal class RommTwoWaySyncStateFile(private val file: File) {
    fun read(): RommTwoWaySyncState {
        if (!file.isFile || file.length() !in 2..MAX_BYTES) return RommTwoWaySyncState()
        val text =
            runCatching {
                file.inputStream().use { RommSafeStreamRead.readBounded(it, MAX_BYTES.toInt()) }
            }.getOrNull() ?: return RommTwoWaySyncState()
        return RommTwoWaySyncState.fromJson(String(text, Charsets.UTF_8))
    }

    fun write(state: RommTwoWaySyncState) {
        val bytes = state.toJson().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Sync state too large")
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not save sync state")
        }
    }

    companion object {
        private const val MAX_BYTES = 512L * 1024L
    }
}
