package com.swordfish.lemuroid.app.shared.romm

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The phone side of two-way save sync: where a game's battery save and
 * autosave live, and how a downloaded save replaces the local one safely.
 * Plain JVM file code so the ordering rules are unit tested.
 */
internal object RommLocalSaveFiles {
    data class Snapshot(
        val fingerprint: RommDeviceSyncProtocol.StableFileFingerprint,
        /** UTC, whole seconds, in the form RomM's negotiation expects. */
        val modifiedUtc: String,
    ) {
        val md5: String get() = fingerprint.md5
    }

    /** Same name SavesManager uses, which is also RetroArch's. */
    fun saveFileName(gameFileName: String): String = gameFileName.substringBeforeLast(".") + ".srm"

    fun saveFile(
        savesDirectory: File,
        gameFileName: String,
    ): File = File(savesDirectory, saveFileName(gameFileName))

    /**
     * The casual-profile autosave state and its metadata, as StatesManager
     * names them under states/fceumm. A save state contains battery RAM, so
     * it must not be restored over a newer downloaded save.
     */
    fun autosaveFiles(
        statesDirectory: File,
        gameFileName: String,
    ): List<File> {
        val core = File(statesDirectory, "fceumm")
        val state = "$gameFileName.state"
        return listOf(File(core, state), File(core, "$state.metadata"))
    }

    /** Null when there is no save yet. Double-read, so a changing file fails. */
    fun snapshot(file: File): Snapshot? {
        if (!file.isFile || file.length() == 0L) return null
        val print = RommDeviceSyncProtocol.fingerprint { file.inputStream() }
        val modified = Instant.ofEpochMilli(file.lastModified()).truncatedTo(ChronoUnit.SECONDS)
        return Snapshot(print, modified.toString())
    }

    fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    /** Reads the save for upload and proves it is still the negotiated content. */
    fun readForUpload(
        file: File,
        expectedMd5: String,
    ): ByteArray {
        val bytes =
            file.inputStream().use {
                RommSafeStreamRead.readBounded(it, RommApiClient.MAX_SYNC_SAVE_BYTES)
            }
        if (bytes.isEmpty() || md5(bytes) != expectedMd5) throw IOException("Save changed during sync")
        return bytes
    }

    /**
     * Replaces [target] with [downloaded], in an order that can always be
     * undone from the backup:
     * 1. the download must hash to [expectedMd5];
     * 2. the local save must still be what sync saw ([currentMd5], or absent);
     * 3. an existing local save is handed to [backup], which must succeed;
     * 4. the autosave state is moved into [asideDirectory], never deleted;
     * 5. the new save is written to a temporary file and renamed into place;
     * 6. the result is read back and verified.
     */
    fun replaceWithDownload(
        target: File,
        downloaded: ByteArray,
        expectedMd5: String,
        currentMd5: String?,
        autosaveFiles: List<File>,
        asideDirectory: File,
        nowMillis: Long,
        backup: (File) -> Unit,
    ) {
        if (downloaded.isEmpty() || md5(downloaded) != expectedMd5) {
            throw IOException("Downloaded save failed its check")
        }
        val before = snapshot(target)?.md5
        if (before != currentMd5) throw IOException("Save changed during sync")
        if (before != null) backup(target)

        val existingStates = autosaveFiles.filter { it.isFile }
        if (existingStates.isNotEmpty()) {
            if (!asideDirectory.isDirectory && !asideDirectory.mkdirs()) {
                throw IOException("Could not set the autosave aside")
            }
            existingStates.forEach { state ->
                val aside = File(asideDirectory, "${state.name}.$nowMillis.aside")
                if (!state.renameTo(aside)) {
                    state.copyTo(aside, overwrite = false)
                    if (!state.delete()) throw IOException("Could not set the autosave aside")
                }
            }
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.romm-download")
        FileOutputStream(temp).use { out ->
            ByteArrayInputStream(downloaded).copyTo(out)
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not write the downloaded save")
        }
        if (snapshot(target)?.md5 != expectedMd5) throw IOException("Downloaded save failed its check")
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
