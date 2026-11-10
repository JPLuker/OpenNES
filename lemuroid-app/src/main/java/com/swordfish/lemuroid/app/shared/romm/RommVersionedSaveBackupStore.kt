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
        val sourceServerFingerprint: String? = null,
    )

    /** Counts only. Never removes files or repairs manifests automatically. */
    data class ArchiveAudit(
        val verified: Int,
        val unverifiedArchives: Int,
        val orphanedManifests: Int,
        val incompleteWrites: Int,
    )

    /**
     * Integrity inventory for archives that are otherwise silently filtered
     * out by listVerifiedBackups(). Unknown or incomplete files remain intact
     * for manual recovery; none is treated as safe for synchronization.
     */
    @Synchronized
    fun audit(): ArchiveAudit {
        val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
        val trusted = listVerifiedBackups().map { it.file.name }.toSet()
        val archiveNames =
            files.filter { it.name.startsWith("romm-") && it.name.endsWith(".savebak") }
                .map { it.name }
        val missingFiles =
            files.count { manifest ->
                manifest.name.startsWith("romm-") &&
                    manifest.name.endsWith(".savebak.sha256") &&
                    !File(directory, manifest.name.removeSuffix(".sha256")).isFile
            }
        val partialFiles =
            files.count { file ->
                file.name.endsWith(".partial") &&
                    (file.name.startsWith("opennes-romm-") ||
                        file.name.startsWith("opennes-manifest-"))
            }
        return ArchiveAudit(
            verified = trusted.size,
            unverifiedArchives = archiveNames.count { it !in trusted },
            orphanedManifests = missingFiles,
            incompleteWrites = partialFiles,
        )
    }

    fun createVerifiedBackup(
        romId: Int,
        original: File,
        sourceServerFingerprint: String? = null,
    ): Backup {
        require(romId > 0) { "Verified RomM ROM ID required" }
        require(
            sourceServerFingerprint == null ||
                RommServerIdentity.validFingerprint(sourceServerFingerprint),
        ) {
            "Invalid source-server fingerprint"
        }
        if (!original.isFile) throw IOException("Save source unavailable")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot initialize backup directory")
        }
        if (original.canonicalFile.parentFile == directory.canonicalFile) {
            throw IOException("Refusing to back up an existing backup")
        }

        // A future local-save backup must never silently capture bytes while
        // SRAM is changing. Check the same source before and after staging;
        // every copy must match the verified stable source fingerprint.
        val sourceBefore = RommSaveSnapshotter.fromFile(original)
        val staging = File.createTempFile("opennes-romm-", ".partial", directory)
        var committed: File? = null
        var manifest: File? = null
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
            if (total != sourceBefore.bytes ||
                fingerprint != sourceBefore.sha256 ||
                RommSaveSnapshotter.fromFile(original) != sourceBefore
            ) {
                throw IOException("Save source changed while creating backup")
            }
            val filename =
                buildString {
                    append("romm-")
                    append(romId)
                    append("-")
                    append(System.currentTimeMillis())
                    append("-")
                    append(UUID.randomUUID().toString())
                    append(".savebak")
                }
            val destination = File(directory, filename)
            if (destination.exists() || !staging.renameTo(destination)) {
                throw IOException("Cannot commit a new versioned backup")
            }
            committed = destination
            val record = Backup(destination, romId, fingerprint, total, sourceServerFingerprint)
            if (!verify(record)) throw IOException("Versioned backup failed SHA-256 verification")

            // The manifest makes integrity verification possible after an
            // Android process restart. A hash computed only at listing time
            // cannot detect corruption of the original immutable snapshot.
            manifest = File(directory, filename + MANIFEST_SUFFIX)
            val stagingManifest = File.createTempFile("opennes-manifest-", ".partial", directory)
            try {
                FileOutputStream(stagingManifest).use { output ->
                    val provenance = sourceServerFingerprint?.let { " $it" }.orEmpty()
                    output.write(("$fingerprint $total$provenance\n").toByteArray(Charsets.US_ASCII))
                    output.fd.sync()
                }
                if (manifest.exists() || !stagingManifest.renameTo(manifest)) {
                    throw IOException("Cannot commit backup integrity manifest")
                }
            } finally {
                stagingManifest.delete()
            }
            if (!verify(record)) throw IOException("Manifested backup failed verification")
            return record
        } catch (e: Exception) {
            committed?.delete()
            manifest?.delete()
            throw e
        } finally {
            staging.delete()
        }
    }

    /**
     * Enumerate only previously committed backups with matching immutable
     * checksum manifests. Old unmanifested files remain untouched but cannot
     * be mistaken for verified archive entries after process death.
     */
    fun listVerifiedBackups(romId: Int? = null): List<Backup> {
        if (romId != null) require(romId > 0)
        val files = directory.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && it.name.matches(ARCHIVE_FILE_PATTERN) }
            .mapNotNull { file ->
                val match = ARCHIVE_FILE_PATTERN.matchEntire(file.name) ?: return@mapNotNull null
                val archiveRomId =
                    match.groupValues[1].toIntOrNull()
                        ?.takeIf { it > 0 } ?: return@mapNotNull null
                if (romId != null && archiveRomId != romId) return@mapNotNull null
                val manifest = File(directory, file.name + MANIFEST_SUFFIX)
                if (!manifest.isFile || manifest.length() !in 1..200) return@mapNotNull null
                val fields =
                    runCatching { manifest.readText(Charsets.US_ASCII).trim().split(" ") }
                        .getOrNull() ?: return@mapNotNull null
                if (fields.size !in 2..3 || !fields[0].matches(SHA256_PATTERN)) {
                    return@mapNotNull null
                }
                val size = fields[1].toLongOrNull() ?: return@mapNotNull null
                val origin = fields.getOrNull(2)
                if (origin != null && !RommServerIdentity.validFingerprint(origin)) {
                    return@mapNotNull null
                }
                val record = Backup(file, archiveRomId, fields[0], size, origin)
                if (runCatching { verify(record) }.getOrDefault(false)) record else null
            }
            .sortedByDescending { it.file.name }
            .toList()
    }

    /** Recheck the current manifest, expected server and content on disk. */
    fun isCurrentlyArchived(backup: Backup): Boolean {
        if (backup.romId <= 0) return false
        return listVerifiedBackups(backup.romId).any { entry ->
            entry.file.canonicalFile == backup.file.canonicalFile &&
                entry.sha256 == backup.sha256 &&
                entry.bytes == backup.bytes &&
                entry.sourceServerFingerprint == backup.sourceServerFingerprint
        }
    }

    /**
     * Delete only a currently verified app-private archive and its manifest.
     * A supplied File reference is never sufficient authorization: validate
     * canonical containment, filename, ROM ID, manifest and current digest.
     *
     * The manifest is removed first so an interrupted deletion cannot leave
     * an unverified copy listed as safe to restore or export.
     */
    @Synchronized
    fun deleteVerifiedBackup(backup: Backup): Boolean {
        val current =
            listVerifiedBackups(backup.romId).firstOrNull {
                it.file.canonicalFile == backup.file.canonicalFile &&
                    it.sha256 == backup.sha256 &&
                    it.bytes == backup.bytes &&
                    it.sourceServerFingerprint == backup.sourceServerFingerprint
            } ?: return false
        if (!verify(current)) return false
        val manifest = File(directory, current.file.name + MANIFEST_SUFFIX)
        if (!manifest.isFile || !manifest.delete()) return false
        return current.file.delete()
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
        private const val MANIFEST_SUFFIX = ".sha256"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val ARCHIVE_FILE_PATTERN =
            Regex("romm-([1-9][0-9]*)-[0-9]{13}-[0-9a-fA-F-]{36}\\.savebak")
    }
}
