package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Decides what to do with one of the four save-state slots of one game.
 *
 * RomM keeps a state as a plain file per ROM (no device negotiation and no
 * content hash in its listing), so each slot remembers what was last synced:
 * the phone copy's MD5 and the server copy's stamp (id, time, size). A side
 * that still matches that record is unchanged and can safely take the other
 * side's newer state. When both changed, the bytes are compared and, if they
 * differ, the more recently made state wins.
 */
internal object RommStateSlotPlan {
    const val SLOTS = 4

    data class Baseline(val md5: String, val remoteStamp: String?)

    data class Remote(
        val id: Long,
        val romId: Int,
        val slot: Int,
        val stamp: String,
        val updatedAtMillis: Long?,
    )

    enum class Action { NOTHING, UPLOAD, DOWNLOAD, COMPARE }

    fun plan(
        localMd5: String?,
        remoteStamp: String?,
        baseline: Baseline?,
    ): Action {
        val localChanged = localMd5 != baseline?.md5
        val remoteChanged = remoteStamp != baseline?.remoteStamp
        return when {
            localMd5 == null && remoteStamp == null -> Action.NOTHING
            // Removed on the server after a sync: never put it back by itself.
            remoteStamp == null -> if (localChanged) Action.UPLOAD else Action.NOTHING
            localMd5 == null -> if (remoteChanged) Action.DOWNLOAD else Action.NOTHING
            !localChanged && !remoteChanged -> Action.NOTHING
            !remoteChanged -> Action.UPLOAD
            !localChanged -> Action.DOWNLOAD
            else -> Action.COMPARE
        }
    }

    /** Both sides changed: identical bytes need nothing, otherwise the newer state wins. */
    fun resolve(
        localMd5: String,
        remoteMd5: String,
        localModifiedMillis: Long,
        remoteUpdatedMillis: Long?,
    ): Action =
        when {
            localMd5 == remoteMd5 -> Action.NOTHING
            remoteUpdatedMillis != null && remoteUpdatedMillis > localModifiedMillis -> Action.DOWNLOAD
            else -> Action.UPLOAD
        }

    /** The server file name for a slot: RetroArch's `Game.state1` … `Game.state4`. */
    fun remoteFileName(
        romFileName: String,
        slot: Int,
    ): String {
        require(slot in 1..SLOTS)
        return romFileName.substringAfterLast('/').substringBeforeLast('.') + ".state" + slot
    }

    private val slotSuffix = Regex("""\.state([1-4])$""", RegexOption.IGNORE_CASE)
    private val timestamp = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})""")

    /** The newest usable state per (ROM, slot) from RomM's `/api/states` list. */
    fun parseRemote(json: String): Map<Pair<Int, Int>, Remote> {
        val rows = JSONArray(json)
        val best = HashMap<Pair<Int, Int>, Remote>()
        for (i in 0 until rows.length()) {
            val remote = (rows.opt(i) as? JSONObject)?.let(::parseOne) ?: continue
            val key = remote.romId to remote.slot
            val current = best[key]
            if (current == null || (remote.updatedAtMillis ?: 0L) > (current.updatedAtMillis ?: 0L)) {
                best[key] = remote
            }
        }
        return best
    }

    fun parseOne(row: JSONObject): Remote? {
        val id = row.optLong("id", -1)
        val romId = row.optInt("rom_id", -1)
        val name = row.optString("file_name")
        val slot = slotSuffix.find(name)?.groupValues?.get(1)?.toInt() ?: return null
        val emulator = if (row.isNull("emulator")) null else row.optString("emulator")
        if (id <= 0 || romId <= 0 || row.optBoolean("missing_from_fs", false)) return null
        if (emulator != null && emulator.lowercase(Locale.ROOT) != RommApiClient.SYNC_EMULATOR) return null
        val updated = row.optString("updated_at")
        val size = row.optLong("file_size_bytes", -1)
        return Remote(id, romId, slot, "$id|$updated|$size", parseUtcMillis(updated))
    }

    /** RomM sends UTC timestamps; only whole seconds matter here. */
    fun parseUtcMillis(text: String): Long? {
        val match = timestamp.find(text) ?: return null
        val (year, month, day, hour, minute, second) = match.destructured
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
            clear()
            set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            timeInMillis
        }
    }
}
