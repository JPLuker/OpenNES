package com.swordfish.lemuroid.app.shared.romm

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Produces verified, content-derived snapshots for the opt-in RomM sync
 * planner. Filename, modification time, and remote metadata are NOT evidence
 * that a save is unchanged.
 *
 * Read twice: this rejects the common case where a running emulator writes
 * SRAM during hashing. A caller must still pause emulation and recheck the
 * final hashes immediately before committing any future sync operation.
 */
internal object RommSaveSnapshotter {
    fun fromFile(file: File): RommSaveSyncPlanner.Snapshot {
        val first = fingerprint(file)
        val second = fingerprint(file)
        if (first != second) throw IOException("Save changed while being fingerprinted")
        return first
    }

    private fun fingerprint(file: File): RommSaveSyncPlanner.Snapshot {
        if (!file.isFile) throw IOException("Save file unavailable")
        val expectedSize = file.length()
        if (expectedSize !in 1L..MAX_SAVE_BYTES) {
            throw IOException("Save file is empty or exceeds safe size")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                bytes += read
                if (bytes > MAX_SAVE_BYTES) throw IOException("Save file grew beyond safe size")
                digest.update(buffer, 0, read)
            }
        }
        if (bytes != expectedSize || file.length() != expectedSize) {
            throw IOException("Save file changed during hashing")
        }
        val sha256 =
            digest.digest().joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
        return RommSaveSyncPlanner.Snapshot(sha256, bytes)
    }

    private const val MAX_SAVE_BYTES = 16L * 1024L * 1024L
}
