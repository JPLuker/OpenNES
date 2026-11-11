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
        val serverFingerprint: String,
        val romId: Int,
        val direction: RommSaveSyncPlanner.Direction,
        val observedLocal: RommSaveSyncPlanner.Snapshot,
        val observedRemote: RommSaveSyncPlanner.Snapshot,
        val requestedAtMillis: Long,
        val sourceAccountId: Long? = null,
    )

    sealed interface ReadResult {
        data class Entries(val pending: List<Pending>) : ReadResult

        data object Corrupt : ReadResult
    }

    /** Read-only check; never approves or executes an offline request. */
    enum class LocalReview {
        MATCHES_OBSERVED,
        CHANGED_SINCE_REQUEST,
        DIFFERENT_SERVER,
        REQUEST_CHANGED,
        JOURNAL_CORRUPT,
    }

    @Synchronized
    fun list(): ReadResult {
        // V1 entries have no source-server binding and may not be migrated
        // into executable future work. Fail closed rather than hide them.
        if (File(directory, LEGACY_FILE_NAME).exists()) return ReadResult.Corrupt
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
            val games = HashSet<Triple<String, Long?, Int>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.getString("id")
                val serverFingerprint = item.getString("serverFingerprint")
                val romId = item.getInt("romId")
                val direction = RommSaveSyncPlanner.Direction.valueOf(item.getString("direction"))
                val observedLocal = snapshot(item.getJSONObject("local"))
                val observedRemote = snapshot(item.getJSONObject("remote"))
                val timestamp = item.getLong("requestedAtMillis")
                val sourceAccountId =
                    if (item.has("sourceAccountId") && !item.isNull("sourceAccountId")) {
                        (item.opt("sourceAccountId") as? Number)
                            ?.toString()
                            ?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: return ReadResult.Corrupt
                    } else {
                        null
                    }
                if (!id.matches(ID_PATTERN) ||
                    !RommServerIdentity.validFingerprint(serverFingerprint) ||
                    !ids.add(id) || romId <= 0 ||
                    !games.add(Triple(serverFingerprint, sourceAccountId, romId)) ||
                    direction == RommSaveSyncPlanner.Direction.NONE || timestamp <= 0L
                ) {
                    return ReadResult.Corrupt
                }
                entries +=
                    Pending(
                        id = id,
                        serverFingerprint = serverFingerprint,
                        romId = romId,
                        direction = direction,
                        observedLocal = observedLocal,
                        observedRemote = observedRemote,
                        requestedAtMillis = timestamp,
                        sourceAccountId = sourceAccountId,
                    )
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
        serverFingerprint: String,
        sourceAccountId: Long? = null,
    ): Boolean {
        if (!RommServerIdentity.validFingerprint(serverFingerprint) ||
            romId <= 0 ||
            (sourceAccountId != null && sourceAccountId <= 0L) ||
            direction == RommSaveSyncPlanner.Direction.NONE
        ) {
            return false
        }
        val current = (list() as? ReadResult.Entries)?.pending ?: return false
        if (current.size >= MAX_PENDING ||
            current.any {
                it.romId == romId &&
                    it.serverFingerprint == serverFingerprint &&
                    it.sourceAccountId == sourceAccountId
            }
        ) {
            return false
        }
        val addition =
            Pending(
                id = UUID.randomUUID().toString(),
                serverFingerprint = serverFingerprint,
                romId = romId,
                direction = direction,
                observedLocal = local,
                observedRemote = remote,
                requestedAtMillis = System.currentTimeMillis(),
                sourceAccountId = sourceAccountId,
            )
        return persist(current + addition)
    }

    @Synchronized
    fun discard(id: String): Boolean {
        val current = (list() as? ReadResult.Entries)?.pending ?: return false
        if (current.none { it.id == id }) return false
        return persist(current.filterNot { it.id == id })
    }

    /**
     * Compare a freshly fingerprinted user-selected local document to a
     * CURRENT queued intent. The journal is reloaded before comparison so a
     * stale UI selection cannot be mistaken for a current request.
     *
     * A match means "unchanged since recording", never safe to synchronize:
     * remote state, identity, common ancestry and approval are not checked.
     */
    @Synchronized
    fun reviewLocal(
        pending: Pending,
        currentServerFingerprint: String?,
        currentLocal: RommSaveSyncPlanner.Snapshot,
    ): LocalReview {
        if (!RommServerIdentity.validFingerprint(currentServerFingerprint.orEmpty()) ||
            pending.serverFingerprint != currentServerFingerprint
        ) {
            return LocalReview.DIFFERENT_SERVER
        }
        val state = list()
        if (state is ReadResult.Corrupt) return LocalReview.JOURNAL_CORRUPT
        val live =
            (state as ReadResult.Entries).pending.firstOrNull { it.id == pending.id }
                ?: return LocalReview.REQUEST_CHANGED
        if (live != pending) return LocalReview.REQUEST_CHANGED
        return if (live.observedLocal == currentLocal) {
            LocalReview.MATCHES_OBSERVED
        } else {
            LocalReview.CHANGED_SINCE_REQUEST
        }
    }

    /**
     * Revalidate a current persisted intent, never an unattached or stale
     * object passed by a UI callback. This function still cannot write files.
     */
    @Synchronized
    fun evaluate(
        pending: Pending,
        request: RommSaveSyncPlanner.Request,
        serverFingerprint: String,
        activeAccountId: Long? = null,
    ): RommSaveSyncPlanner.Decision {
        if (!RommServerIdentity.validFingerprint(serverFingerprint) ||
            pending.serverFingerprint != serverFingerprint
        ) {
            return RommSaveSyncPlanner.Decision.UnverifiedIdentity
        }
        if (pending.sourceAccountId == null || pending.sourceAccountId != activeAccountId) {
            // An older server-only request can be reviewed or discarded,
            // but cannot become a candidate transfer under any account.
            return RommSaveSyncPlanner.Decision.UnverifiedIdentity
        }
        val fresh = (list() as? ReadResult.Entries)?.pending ?: return RommSaveSyncPlanner.Decision.Conflict
        if (fresh.none { it == pending }) return RommSaveSyncPlanner.Decision.Conflict
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
            val json =
                JSONObject()
                    .put("id", item.id)
                    .put("serverFingerprint", item.serverFingerprint)
                    .put("romId", item.romId)
                    .put("direction", item.direction.name)
                    .put("local", serialize(item.observedLocal))
                    .put("remote", serialize(item.observedRemote))
                    .put("requestedAtMillis", item.requestedAtMillis)
            item.sourceAccountId?.let { json.put("sourceAccountId", it) }
            values.put(json)
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
        private const val FILE_NAME = "romm-pending-sync-v2.json"
        private const val LEGACY_FILE_NAME = "romm-pending-sync-v1.json"
        private const val VERSION = 2
        private const val MAX_PENDING = 32
        private const val MAX_FILE_BYTES = 64 * 1024
        private val ID_PATTERN = Regex("[0-9a-f-]{36}")
    }
}
