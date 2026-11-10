package com.swordfish.lemuroid.app.shared.romm

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Local, immutable, versioned save backups for future opt-in RomM sync.
 *
 * This component NEVER alters a live save and does not know how to upload
 * anything. The caller must supply an app-private no-backup directory.
 * Each snapshot receives a fresh filename and is verified after closing.
 */
internal class RommVersionedSaveBackupStore(private val directory: File) {
    data class Backup(
        val file: File,
        val romId: Int,
        val sha256: String,
        val bytes: Long,
    )

    fun createVerifiedBackup(
        romId: Int,
        original: File,
    ): Backup {
        require(romId > 0) { "Verified RomM ROM ID required" }
        if (!original.isFile) throw IOException("Save source unavailable")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot initialize backup directory")
        }
        if (original.canonicalFile.parentFile == directory.canonicalFile) {
            throw IOException("Refusing to back up an existing backup")
        }

        val staging = File.createTempFile("opennes-romm-", ".partial", directory)
        var committed: File? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            original.inputStream().buffered().use { source ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        if (total > MAX_SAVE_BYTES) throw IOException("Save exceeds RomM backup limit")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            if (total <= 0) throw IOException("Cannot back up empty save")
            val fingerprint = hex(digest.digest())
            val filename = "romm-" + romId + "-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString() + ".savebak"
            val destination = File(directory, filename)
            if (destination.exists() || !staging.renameTo(destination)) {
                throw IOException("Cannot commit a new versioned backup")
            }
            committed = destination
            val record = Backup(destination, romId, fingerprint, total)
            if (!verify(record)) throw IOException("Versioned backup failed SHA-256 verification")
            return record
        } catch (e: Exception) {
            committed?.delete()
            throw e
        } finally {
            staging.delete()
        }
    }

    /** Revalidate before using a stored backup. A prior verification is not permanent. */
    fun verify(backup: Backup): Boolean {
        if (backup.romId <= 0 || backup.bytes <= 0 || backup.bytes > MAX_SAVE_BYTES) return false
        if (backup.file.canonicalFile.parentFile != directory.canonicalFile) return false
        if (!backup.file.name.startsWith("romm-" + backup.romId + "-") ||
            !backup.file.name.endsWith(".savebak")
        ) {
            return false
        }
        if (!backup.file.isFile || backup.file.length() != backup.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        backup.file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                if (length > 0) digest.update(buffer, 0, length)
            }
        }
        return hex(digest.digest()) == backup.sha256
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    companion object {
        private const val MAX_SAVE_BYTES = 16L * 1024L * 1024L
    }
}
