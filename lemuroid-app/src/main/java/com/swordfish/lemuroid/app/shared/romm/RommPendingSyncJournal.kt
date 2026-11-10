package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Disk-backed, non-executing journal for future explicit RomM save transfers.
 *
 * Only ROM identities and content fingerprints are persisted. No credentials,
 * save bytes, network calls or approval flags are stored. On reconnect, every
 * entry MUST be reconsidered against fresh local and remote snapshots; an
 * offline request is never permission to overwrite anything.
 */
internal class RommPendingSyncJournal(private val directory: File) {
    data class Pending(
        val id: String,
        val romId: Int,
        val direction: RommSaveSyncPlanner.Direction,
        val observedLocal: RommSaveSyncPlanner.Snapshot,
        val observedRemote: RommSaveSyncPlanner.Snapshot,
        val requestedAtMillis: Long,
    )

    sealed interface ReadResult {
        data class Entries(val pending: List<Pending>) : ReadResult

        data object Corrupt : ReadResult
    }

    @Synchronized
    fun list(): ReadResult {
        val file = File(directory, FILE_NAME)
        if (!file.exists()) return ReadResult.Entries(emptyList())
        if (!file.isFile || file.length() !in 1..MAX_FILE_BYTES.toLong()) return ReadResult.Corrupt
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.getInt("version") != VERSION) return ReadResult.Corrupt
            val data = root.getString("data")
            if (sha256(data) != root.getString("sha256")) return ReadResult.Corrupt
            val array = JSONObject(data).getJSONArray("pending")
            if (array.length() > MAX_PENDING) return ReadResult.Corrupt
            val entries = ArrayList<Pending>()
            val ids = HashSet<String>()
            val games = HashSet<Int>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.getString("id")
                val romId = item.getInt("romId")
                val direction = RommSaveSyncPlanner.Direction.valueOf(item.getString("direction"))
                val observedLocal = snapshot(item.getJSONObject("local"))
                val observedRemote = snapshot(item.getJSONObject("remote"))
                val timestamp = item.getLong("requestedAtMillis")
                if (!id.matches(ID_PATTERN) || !ids.add(id) || romId <= 0 ||
                    !games.add(romId) || direction == RommSaveSyncPlanner.Direction.NONE || timestamp <= 0L
                ) {
                    return ReadResult.Corrupt
                }
                entries += Pending(id, romId, direction, observedLocal, observedRemote, timestamp)
            }
            ReadResult.Entries(entries)
        } catch (_: Exception) {
            ReadResult.Corrupt
        }
    }

    /**
     * A user-requested intent, NOT an executable job. Existing requests
     * cannot be silently replaced: resolve or discard them explicitly first.
     */
    @Synchronized
    fun enqueue(
        romId: Int,
        direction: RommSaveSyncPlanner.Direction,
        local: RommSaveSyncPlanner.Snapshot,
        remote: RommSaveSyncPlanner.Snapshot,
    ): Boolean {
        if (romId <= 0 || direction == RommSaveSyncPlanner.Direction.NONE) return false
        val current = (list() as? ReadResult.Entries)?.pending ?: return false
        if (current.size >= MAX_PENDING || current.any { it.romId == romId }) return false
        val addition = Pending(
            id = UUID.randomUUID().toString(),
            romId = romId,
            direction = direction,
            observedLocal = local,
            observedRemote = remote,
            requestedAtMillis = System.currentTimeMillis(),
        )
        return persist(current + addition)
    }

    @Synchronized
    fun discard(id: String): Boolean {
        val current = (list() as? ReadResult.Entries)?.pending ?: return false
        if (current.none { it.id == id }) return false
        return persist(current.filterNot { it.id == id })
    }

    /** Revalidation only; no copy/upload method is exposed by this journal. */
    fun evaluate(
        pending: Pending,
        request: RommSaveSyncPlanner.Request,
    ): RommSaveSyncPlanner.Decision {
        if (pending.romId != request.romId ||
            pending.direction != request.direction ||
            pending.observedLocal != request.local ||
            pending.observedRemote != request.remote
        ) {
            return RommSaveSyncPlanner.Decision.Conflict
        }
        return RommSaveSyncPlanner.plan(request)
    }

    private fun persist(pending: List<Pending>): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val values = JSONArray()
        pending.forEach { item ->
            values.put(
                JSONObject()
                    .put("id", item.id)
                    .put("romId", item.romId)
                    .put("direction", item.direction.name)
                    .put("local", serialize(item.observedLocal))
                    .put("remote", serialize(item.observedRemote))
                    .put("requestedAtMillis", item.requestedAtMillis),
            )
        }
        val data = JSONObject().put("pending", values).toString()
        val bytes =
            JSONObject().put("version", VERSION)
                .put("data", data)
                .put("sha256", sha256(data))
                .toString()
                .toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) return false
        val target = File(directory, FILE_NAME)
        val staging = File.createTempFile("pending-sync-", ".partial", directory)
        return try {
            FileOutputStream(staging).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            staging.renameTo(target)
        } catch (_: Exception) {
            false
        } finally {
            staging.delete()
        }
    }

    private fun serialize(value: RommSaveSyncPlanner.Snapshot): JSONObject =
        JSONObject().put("sha256", value.sha256).put("bytes", value.bytes)

    private fun snapshot(value: JSONObject): RommSaveSyncPlanner.Snapshot =
        RommSaveSyncPlanner.Snapshot(value.getString("sha256"), value.getLong("bytes"))

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    companion object {
        private const val FILE_NAME = "romm-pending-sync-v1.json"
        private const val VERSION = 1
        private const val MAX_PENDING = 32
        private const val MAX_FILE_BYTES = 64 * 1024
        private val ID_PATTERN = Regex("[0-9a-f-]{36}")
    }
}
