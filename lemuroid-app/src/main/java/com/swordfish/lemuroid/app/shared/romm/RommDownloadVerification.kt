package com.swordfish.lemuroid.app.shared.romm

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Local verification before a downloaded NES ROM or RomM save is committed.
 * No archive contents are extracted to the filesystem.
 */
object RommDownloadVerification {
    private const val MAX_ROM_UNCOMPRESSED = 64L * 1024L * 1024L
    private const val MIN_INES_HEADER_BYTES = 16L
    private const val MAX_SAVE_UNCOMPRESSED = 16L * 1024L * 1024L
    private const val MAX_ENTRIES = 256
    private val MD5_PATTERN = Regex("[a-f0-9]{32}")
    private val SHA256_PATTERN = Regex("[a-f0-9]{64}")

    /** Fail closed on malformed NES files, multi-ROM ZIPs and ZIP bombs. */
    fun checkImportedRom(
        file: File,
        name: String,
    ) {
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "nes" -> {
                if (file.length() < MIN_INES_HEADER_BYTES || file.length() > MAX_ROM_UNCOMPRESSED) {
                    throw IOException("Downloaded NES file has an invalid size")
                }
                file.inputStream().use { stream ->
                    val signature = ByteArray(4)
                    val amount = stream.read(signature)
                    if (amount != 4 ||
                        signature[0] != 0x4E.toByte() ||
                        signature[1] != 0x45.toByte() ||
                        signature[2] != 0x53.toByte() ||
                        signature[3] != 0x1A.toByte()
                    ) {
                        throw IOException("Downloaded NES file has no valid iNES header")
                    }
                }
            }
            "fds" -> {
                if (file.length() < 128) throw IOException("Downloaded FDS file is too small")
            }
            "zip" -> checkNesZip(file)
            else -> throw IOException("RomM download is not a supported NES file")
        }
    }

    private fun checkNesZip(file: File) {
        ZipInputStream(file.inputStream().buffered()).use { archive ->
            var files = 0
            val entryNames = HashSet<String>()
            var roms = 0
            var total = 0L
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val entry = archive.nextEntry ?: break
                files++
                if (files > MAX_ENTRIES) throw IOException("NES ZIP has too many entries")
                requireSafeEntryName(entry.name)
                if (!entryNames.add(entry.name.replace('\\', '/'))) {
                    throw IOException("NES ZIP contains duplicate entries")
                }
                if (!entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext == "nes" || ext == "fds") roms++
                    if (roms > 1) throw IOException("NES ZIP contains multiple ROMs")
                    // A .nes filename alone is not proof of a valid ROM.
                    // Verify the embedded iNES header while streaming rather
                    // than accepting a renamed or truncated archive entry.
                    val header = ByteArray(4)
                    var headerLength = 0
                    var entryLength = 0L
                    while (true) {
                        val count = archive.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (headerLength < header.size) {
                            val bytes = minOf(header.size - headerLength, count)
                            buffer.copyInto(header, headerLength, 0, bytes)
                            headerLength += bytes
                        }
                        entryLength += count
                        total += count
                        if (total > MAX_ROM_UNCOMPRESSED) {
                            throw IOException("NES ZIP expands beyond safe size limit")
                        }
                    }
                    if (ext == "nes" && (
                            entryLength < MIN_INES_HEADER_BYTES ||
                                headerLength != 4 ||
                                header[0] != 0x4E.toByte() ||
                                header[1] != 0x45.toByte() ||
                                header[2] != 0x53.toByte() ||
                                header[3] != 0x1A.toByte()
                        )
                    ) {
                        throw IOException("NES ZIP contains an invalid iNES header")
                    }
                    if (ext == "fds" && entryLength < 128L) {
                        throw IOException("NES ZIP contains a truncated FDS file")
                    }
                }
                archive.closeEntry()
            }
            if (roms != 1) throw IOException("NES ZIP must contain exactly one .nes or .fds file")
        }
    }

    /**
     * RomM 5.x content_hash is an MD5 digest. ZIP saves are hashed from sorted
     * entry-name:entry-MD5 lines, not from ZIP container bytes. Older or future
     * 64-character SHA-256 values are checked against the complete file.
     */
    fun checkRemoteSaveHash(
        file: File,
        knownHash: String?,
    ) {
        val expected = knownHash?.lowercase(Locale.ROOT) ?: return
        val actual =
            when {
                expected.matches(MD5_PATTERN) ->
                    if (isZipFile(file)) zipSaveContentMd5(file) else digestFile(file, "MD5")
                expected.matches(SHA256_PATTERN) -> digestFile(file, "SHA-256")
                else -> return // Unknown server hash format; never claim verified.
            }
        if (actual != expected) throw IOException("RomM save content hash mismatch")
    }

    private fun zipSaveContentMd5(file: File): String {
        ZipFile(file).use { archive ->
            val entries = archive.entries().asSequence().toList()
            if (entries.size > MAX_ENTRIES) throw IOException("RomM save ZIP has too many entries")
            val names = mutableSetOf<String>()
            val hashes = mutableListOf<String>()
            var total = 0L
            val buffer = ByteArray(16 * 1024)
            for (entry in entries.sortedBy { it.name }) {
                requireSafeEntryName(entry.name)
                if (entry.isDirectory) continue
                if (!names.add(entry.name)) throw IOException("RomM save ZIP has duplicate entries")
                val hash = MessageDigest.getInstance("MD5")
                archive.getInputStream(entry).use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > MAX_SAVE_UNCOMPRESSED) {
                            throw IOException("RomM save ZIP expands beyond safe size limit")
                        }
                        hash.update(buffer, 0, count)
                    }
                }
                hashes += entry.name + ":" + hex(hash.digest())
            }
            return hex(
                MessageDigest.getInstance("MD5")
                    .digest(hashes.joinToString("\n").toByteArray(Charsets.UTF_8)),
            )
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (file.length() < 4) return false
        val signature = ByteArray(4)
        file.inputStream().use { it.read(signature) }
        val zipPrefix = signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte()
        val knownSignature =
            signature[2] == 0x03.toByte() ||
                signature[2] == 0x05.toByte() ||
                signature[2] == 0x07.toByte()
        return zipPrefix && knownSignature
    }

    private fun requireSafeEntryName(name: String) {
        val normalized = name.replace('\\', '/')
        val unsafe =
            normalized.isBlank() ||
                normalized.startsWith("/") ||
                Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
                normalized.trimEnd('/').split('/').any { it == ".." || it.isEmpty() } ||
                name.any { it.isISOControl() } ||
                name.length > 512
        if (unsafe) {
            throw IOException("Unsafe archive entry name")
        }
    }

    private fun digestFile(
        file: File,
        algorithm: String,
    ): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
