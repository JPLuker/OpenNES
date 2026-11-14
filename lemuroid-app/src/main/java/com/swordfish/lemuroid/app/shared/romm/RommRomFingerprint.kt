package com.swordfish.lemuroid.app.shared.romm

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The MD5 RomM 5.4 records for a ROM, so a game on the phone can be paired
 * with the same game on RomM even when the file names differ. RomM hashes a
 * plain file whole, and a zip by its largest member's uncompressed bytes.
 */
internal object RommRomFingerprint {
    private const val MAX_BYTES = 32L * 1024 * 1024

    fun md5(
        input: InputStream,
        isZip: Boolean,
    ): String = if (isZip) largestZipMemberMd5(input) else plainMd5(input)

    private fun plainMd5(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val total = feed(input, digest)
        if (total == 0L) throw IOException("Empty ROM")
        return hex(digest.digest())
    }

    private fun largestZipMemberMd5(input: InputStream): String {
        var best: Pair<Long, String>? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val digest = MessageDigest.getInstance("MD5")
                val size = feed(zip, digest)
                total += size
                if (total > MAX_BYTES) throw IOException("ROM archive too large")
                // RomM picks the largest member; the first wins a tie.
                if (best == null || size > best!!.first) best = size to hex(digest.digest())
            }
        }
        return best?.takeIf { it.first > 0 }?.second ?: throw IOException("Empty ROM archive")
    }

    private fun feed(
        input: InputStream,
        digest: MessageDigest,
    ): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > MAX_BYTES) throw IOException("ROM too large")
            digest.update(buffer, 0, read)
        }
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
