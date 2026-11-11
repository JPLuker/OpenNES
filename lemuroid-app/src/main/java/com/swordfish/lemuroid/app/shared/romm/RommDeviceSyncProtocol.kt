package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Strict, read-only handling for the documented RomM 5.4 device-sync
 * negotiation shape. It deliberately does not register a device, negotiate
 * over HTTP, confirm server downloads, or perform ANY sync operation.
 *
 * The local archive journal uses SHA-256 for integrity. RomM's newer device
 * protocol separately requires an MD5 content_hash for negotiation. Never
 * substitute one digest for the other or trust a server-supplied file path.
 *
 * Reference: https://docs.romm.app/5.4.0/developers/device-sync-protocol/
 */
internal object RommDeviceSyncProtocol {
    private const val MAX_RESPONSE_CHARS = 128 * 1024
    private const val MAX_OPERATIONS = 256
    private const val MAX_SAVE_BYTES = 16L * 1024 * 1024
    private val MD5_PATTERN = Regex("[0-9a-f]{32}")

    enum class Action {
        UPLOAD,
        DOWNLOAD,
        CONFLICT,
        DELETE,
        NO_OP,
        ;

        /** Even matching operations are never permission to write. */
        val requiresExplicitExecution: Boolean
            get() = this != NO_OP
    }

    enum class Rejection {
        UNSUPPORTED_SCHEMA,
        UNVERIFIED_ROM,
        INVALID_FILE_NAME,
        MALFORMED_RESPONSE,
    }

    data class Operation(
        val action: Action,
        val romId: Int,
        val saveId: Long?,
        val fileName: String,
        val slot: String?,
        val serverMd5: String?,
    )

    data class Negotiation(
        val sessionId: Long,
        val operations: List<Operation>,
    ) {
        /** Destructive directions remain display-only until full approval. */
        val needsUserReview: Boolean
            get() = operations.any { it.action.requiresExplicitExecution }
    }

    sealed interface ParseResult {
        data class Accepted(val negotiation: Negotiation) : ParseResult

        data class Rejected(val reason: Rejection) : ParseResult
    }

    fun parseNegotiation(
        json: String,
        allowedRomIds: Set<Int>,
    ): ParseResult {
        if (json.length !in 2..MAX_RESPONSE_CHARS || allowedRomIds.isEmpty()) {
            return ParseResult.Rejected(Rejection.UNSUPPORTED_SCHEMA)
        }
        return runCatching { parse(JSONObject(json), allowedRomIds) }
            .getOrElse { ParseResult.Rejected(Rejection.MALFORMED_RESPONSE) }
    }

    private fun parse(
        root: JSONObject,
        allowedRomIds: Set<Int>,
    ): ParseResult {
        // RomM 4.x documented a UUID session and "type"/URL operations.
        // Never try to execute that format through this 5.x parser.
        val sessionId =
            positiveLong(root.opt("session_id"))
                ?: return ParseResult.Rejected(Rejection.UNSUPPORTED_SCHEMA)
        val rows =
            root.optJSONArray("operations")
                ?: return ParseResult.Rejected(Rejection.UNSUPPORTED_SCHEMA)
        if (rows.length() > MAX_OPERATIONS) {
            return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
        }
        val operations = mutableListOf<Operation>()
        val operationKeys = mutableSetOf<Triple<Int, String?, String>>()
        val totals = mutableMapOf<Action, Int>()
        for (i in 0 until rows.length()) {
            val row =
                rows.optJSONObject(i)
                    ?: return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
            val action =
                when (row.optString("action")) {
                    "upload" -> Action.UPLOAD
                    "download" -> Action.DOWNLOAD
                    "conflict" -> Action.CONFLICT
                    "delete" -> Action.DELETE
                    "no_op" -> Action.NO_OP
                    else -> return ParseResult.Rejected(Rejection.UNSUPPORTED_SCHEMA)
                }
            // No remote-supplied download URLs or installation paths.
            if (row.has("source") || row.has("destination") || row.has("dest_path")) {
                return ParseResult.Rejected(Rejection.UNSUPPORTED_SCHEMA)
            }
            val romId =
                positiveInt(row.opt("rom_id"))
                    ?: return ParseResult.Rejected(Rejection.UNVERIFIED_ROM)
            if (romId !in allowedRomIds) return ParseResult.Rejected(Rejection.UNVERIFIED_ROM)
            val file = row.optString("file_name").trim()
            if (!validFileName(file)) return ParseResult.Rejected(Rejection.INVALID_FILE_NAME)
            val slot =
                if (!row.has("slot") || row.isNull("slot")) {
                    null
                } else {
                    row.optString("slot").takeIf(::validSlot)
                        ?: return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
                }
            val rawSaveId = row.opt("save_id")
            val saveId =
                if (!row.has("save_id") || row.isNull("save_id")) {
                    null
                } else {
                    positiveLong(rawSaveId)
                        ?: return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
                }
            if (action in listOf(Action.DOWNLOAD, Action.CONFLICT) && saveId == null) {
                return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
            }
            if (action in listOf(Action.UPLOAD, Action.DELETE) && saveId != null) {
                return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
            }
            val serverMd5 =
                if (!row.has("server_content_hash") || row.isNull("server_content_hash")) {
                    null
                } else {
                    row.optString("server_content_hash")
                        .takeIf { it.matches(MD5_PATTERN) }
                        ?: return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
                }
            val uniqueKey = Triple(romId, slot, file)
            if (!operationKeys.add(uniqueKey)) {
                return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
            }
            totals[action] = (totals[action] ?: 0) + 1
            operations.add(Operation(action, romId, saveId, file, slot, serverMd5))
        }
        val countNames =
            mapOf(
                Action.UPLOAD to "total_upload",
                Action.DOWNLOAD to "total_download",
                Action.CONFLICT to "total_conflict",
                Action.DELETE to "total_delete",
                Action.NO_OP to "total_no_op",
            )
        countNames.forEach { (action, field) ->
            val amount = root.opt(field)
            if (nonnegativeInt(amount) != (totals[action] ?: 0)) {
                return ParseResult.Rejected(Rejection.MALFORMED_RESPONSE)
            }
        }
        return ParseResult.Accepted(Negotiation(sessionId, operations))
    }

    // Never truncate fractional/exponential JSON values to identifiers.
    // Exact integer syntax is required before any future account binding.
    private fun positiveLong(raw: Any?): Long? = (raw as? Number)?.toString()?.toLongOrNull()?.takeIf { it > 0 }

    private fun positiveInt(raw: Any?): Int? = (raw as? Number)?.toString()?.toIntOrNull()?.takeIf { it > 0 }

    private fun nonnegativeInt(raw: Any?): Int? = (raw as? Number)?.toString()?.toIntOrNull()?.takeIf { it >= 0 }

    private fun validFileName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= 255 &&
            name != "." &&
            name != ".." &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }

    private fun validSlot(slot: String): Boolean =
        slot.isNotBlank() &&
            slot.length <= 128 &&
            slot.none { it == '/' || it == '\\' || it.isISOControl() }

    data class StableFileFingerprint(
        val md5: String,
        val sha256: String,
        val bytes: Long,
    )

    /** Double-read a user-selected file; close both streams on all paths. */
    fun fingerprint(open: () -> InputStream?): StableFileFingerprint {
        val first = open()?.use(::fingerprintOneRead) ?: throw IOException("Save unavailable")
        val second = open()?.use(::fingerprintOneRead) ?: throw IOException("Save unavailable")
        if (first != second) throw IOException("Save changed while hashing for RomM")
        return first
    }

    private fun fingerprintOneRead(input: InputStream): StableFileFingerprint {
        val md5 = MessageDigest.getInstance("MD5")
        val sha256 = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = RommSafeStreamRead.next(input, buffer)
            if (read < 0) break
            if (read == 0) continue
            bytes += read
            if (bytes > MAX_SAVE_BYTES) throw IOException("Save exceeds safe size")
            md5.update(buffer, 0, read)
            sha256.update(buffer, 0, read)
        }
        if (bytes == 0L) throw IOException("Empty save")
        return StableFileFingerprint(hex(md5.digest()), hex(sha256.digest()), bytes)
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
