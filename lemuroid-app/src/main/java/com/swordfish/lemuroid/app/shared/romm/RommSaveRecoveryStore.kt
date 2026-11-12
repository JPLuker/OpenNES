package com.swordfish.lemuroid.app.shared.romm

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline recovery from a *verified immutable backup* to a NEW file.
 *
 * This component intentionally has no method for replacing the emulator's
 * active save, deleting a save, or sending content to RomM. A caller must
 * display the recovered file and separately obtain the player's approval
 * before any live save import is implemented.
 */
internal class RommSaveRecoveryStore(
    private val backups: RommVersionedSaveBackupStore,
) {
    data class Recovery(
        val file: File,
        val romId: Int,
        val sha256: String,
        val bytes: Long,
    )

    fun stageRecovery(
        backup: RommVersionedSaveBackupStore.Backup,
        recoveryDirectory: File,
    ): Recovery {
        if (!backups.isCurrentlyArchived(backup)) {
            throw IOException("Save backup or its integrity manifest is no longer verified")
        }
        if (!recoveryDirectory.isDirectory && !recoveryDirectory.mkdirs()) {
            throw IOException("Cannot initialize save recovery directory")
        }
        if (recoveryDirectory.canonicalFile == backup.file.canonicalFile.parentFile) {
            throw IOException("Cannot stage a recovery in the immutable backup directory")
        }
        val temporary = File.createTempFile("opennes-recovery-", ".partial", recoveryDirectory)
        var completed: File? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            backup.file.inputStream().buffered().use { source ->
                FileOutputStream(temporary).use { sink ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = RommSafeStreamRead.next(source, buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        bytes += count
                        if (bytes > MAX_SAVE_BYTES || bytes > backup.bytes) {
                            throw IOException("Recovery size differs from verified backup")
                        }
                        digest.update(buffer, 0, count)
                        sink.write(buffer, 0, count)
                    }
                    sink.fd.sync()
                }
            }
            val sourceHash = hex(digest.digest())
            if (bytes != backup.bytes || sourceHash != backup.sha256 || !backups.isCurrentlyArchived(backup)) {
                throw IOException("Save backup changed during recovery")
            }
            val destination =
                File(
                    recoveryDirectory,
                    "romm-${backup.romId}-${UUID.randomUUID()}.recovered.sav",
                )
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw IOException("Cannot commit staged save recovery")
            }
            completed = destination
            if (!matches(destination, bytes, backup.sha256)) {
                throw IOException("Staged save recovery failed verification")
            }
            return Recovery(destination, backup.romId, backup.sha256, backup.bytes)
        } catch (e: Exception) {
            completed?.delete()
            throw e
        } finally {
            temporary.delete()
        }
    }

    private fun matches(
        file: File,
        expectedSize: Long,
        expectedHash: String,
    ): Boolean {
        if (!file.isFile || file.length() != expectedSize) return false
        return file.inputStream().buffered().use { source ->
            verifyStream(source, expectedSize, expectedHash)
        }
    }

    /**
     * Reverify a committed recovery without trusting a file-length check:
     * the file can grow after it was inspected. Failure never deletes or
     * overwrites an unrelated save.
     */
    internal fun verifyStream(
        source: InputStream,
        expectedSize: Long,
        expectedHash: String,
    ): Boolean {
        if (expectedSize !in 1..MAX_SAVE_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = RommSafeStreamRead.next(source, buffer)
            if (count < 0) break
            if (count > expectedSize - total) return false
            total += count
            digest.update(buffer, 0, count)
        }
        return total == expectedSize && hex(digest.digest()) == expectedHash
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    companion object {
        private const val MAX_SAVE_BYTES = 16L * 1024L * 1024L
    }
}
