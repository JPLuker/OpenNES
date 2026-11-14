package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A bounded, pure negotiation payload builder for RomM 5.4.
 *
 * This builder does NOT register a device, contact a server, approve a
 * transfer, or grant access to files. The caller may explicitly submit the
 * generated JSON to start a comparison-only negotiation session. Callers must first authenticate the current account
 * and use the actual server-issued device UUID for that account.
 *
 * The protocol's content_hash is MD5, while OpenNES local archive integrity
 * uses SHA-256. Never exchange or compare these two digest types.
 */
internal object RommDeviceSyncNegotiationRequest {
    data class LocalSave(
        val romId: Int,
        val fileName: String,
        val slot: String?,
        val fingerprint: RommDeviceSyncProtocol.StableFileFingerprint,
        val updatedAt: String,
        // The libretro core id RomM's web player and RetroArch sync use.
        val emulator: String? = null,
    )

    private val deviceIdPattern =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val md5Pattern = Regex("[0-9a-f]{32}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val timestampPattern =
        Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")

    /**
     * Produces only a local JSON preview. The REST negotiation endpoint
     * creates a session, so this builder never sends it on its own.
     *
     * Only a subset of the user's verified NES ROMs may be queried. Unknown
     * ROM IDs, duplicate slots, unsafe paths, unverified account pins or
     * changed-file digests are rejected, not silently removed.
     */
    fun build(
        deviceId: String,
        requestedRomIds: Set<Int>,
        localSaves: List<LocalSave>,
        currentServerFingerprint: String?,
        currentCredentialRevision: String?,
        currentAccountId: Long?,
        pinnedServerFingerprint: String?,
        pinnedCredentialRevision: String?,
        pinnedAccountId: Long?,
    ): String {
        require(deviceId.matches(deviceIdPattern)) { "Registered RomM device required" }
        require(
            RommAccountIdentity.matchesPin(
                currentServerFingerprint,
                currentCredentialRevision,
                currentAccountId,
                pinnedServerFingerprint,
                pinnedCredentialRevision,
                pinnedAccountId,
            ),
        ) { "Current RomM account is not verified" }
        require(requestedRomIds.isNotEmpty() && requestedRomIds.size <= MAX_ROMS) {
            "Select a bounded set of verified NES games"
        }
        require(requestedRomIds.all { it > 0 }) { "Unverified RomM game ID" }
        require(localSaves.size <= MAX_SAVES) { "Too many local saves" }

        val slotKeys = HashSet<Pair<Int, String?>>()
        val saves = JSONArray()
        localSaves.forEach { save ->
            require(save.romId in requestedRomIds) { "Save does not belong to a selected ROM" }
            require(safeFileName(save.fileName)) { "Unsafe save filename" }
            require(save.slot == null || safeSlot(save.slot)) { "Unsafe RomM save slot" }
            require(slotKeys.add(save.romId to save.slot)) { "Duplicate save slot for ROM" }
            require(
                save.fingerprint.md5.matches(md5Pattern) &&
                    save.fingerprint.sha256.matches(sha256Pattern) &&
                    save.fingerprint.bytes in 1..MAX_SAVE_BYTES,
            ) { "A stable double-read save fingerprint is required" }
            require(validUtcTimestamp(save.updatedAt)) { "Invalid UTC save timestamp" }
            saves.put(
                JSONObject()
                    .put("rom_id", save.romId)
                    .put("file_name", save.fileName)
                    .put("slot", save.slot ?: JSONObject.NULL)
                    .put("emulator", save.emulator?.takeIf(::safeSlot) ?: JSONObject.NULL)
                    .put("content_hash", save.fingerprint.md5)
                    .put("updated_at", save.updatedAt)
                    .put("file_size_bytes", save.fingerprint.bytes),
            )
        }
        val ids = JSONArray()
        requestedRomIds.sorted().forEach { ids.put(it) }
        return JSONObject()
            .put("device_id", deviceId)
            .put("saves", saves)
            .put("rom_ids", ids)
            // Never treat an omitted local save as permission to delete or
            // to silently restore a remote account's unlisted saves.
            .put("restore_unlisted", false)
            .put("emulators", JSONObject.NULL)
            .toString()
    }

    private fun validUtcTimestamp(value: String): Boolean {
        if (!timestampPattern.matches(value)) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        val hour = value.substring(11, 13).toInt()
        val minute = value.substring(14, 16).toInt()
        val second = value.substring(17, 19).toInt()
        if (year !in 1..9999 || month !in 1..12 ||
            hour !in 0..23 || minute !in 0..59 || second !in 0..59
        ) {
            return false
        }
        val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days =
            when (month) {
                2 -> if (leapYear) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        return day in 1..days
    }

    private fun safeFileName(name: String): Boolean =
        name.length in 1..255 &&
            name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it.isISOControl() } &&
            name.lowercase(Locale.ROOT).endsWith(".srm")

    private fun safeSlot(slot: String): Boolean =
        slot.length in 1..128 &&
            slot.none { it == '/' || it == '\\' || it.isISOControl() }

    private const val MAX_ROMS = 128
    private const val MAX_SAVES = 128
    private const val MAX_SAVE_BYTES = 16L * 1024 * 1024
}
