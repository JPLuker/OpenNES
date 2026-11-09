package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Local-only NES/FDS game identification for the future native rc_client.
 *
 * RetroAchievements hashes the unheadered NES/FDS payload, not the full
 * original iNES or FDS header. Only identification is implemented here:
 * this does not perform a login, contact RA servers, unlock achievements
 * or substitute for frame-accurate emulated-memory evaluation.
 */
object NesRetroAchievementsHash {
    private const val MAX_ROM_BYTES = 64L * 1024L * 1024L
    // Includes ignored archive members. A malformed ZIP must not make
    // a pause-menu identification request inflate unlimited data.
    private const val MAX_ZIP_UNCOMPRESSED_BYTES = 96L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 256
    private val HEX = "0123456789abcdef".toCharArray()

    enum class Console {
        NES,
        FDS,
    }

    data class Identity(
        val md5: String,
        val console: Console,
    )

    /** Never uploads ROM bytes. Uses the exact locally indexed file URI. */
    suspend fun forGame(
        context: Context,
        game: Game,
    ): Identity =
        withContext(Dispatchers.IO) {
            require(game.systemId == "nes") { "Only indexed NES games are supported" }
            val uri = Uri.parse(game.fileUri)
            require(uri.scheme == "content" || uri.scheme == "file") {
                "ROM identification requires a local Android URI"
            }
            val stream =
                context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Could not open the selected NES ROM")
            stream.use { calculate(it, game.fileName) }
        }

    /**
     * Stream data instead of buffering the ROM in memory. ZIP archives must
     * have exactly one .nes or .fds member; guessing among multiple games
     * would risk associating another user's progress with the wrong title.
     */
    fun calculate(
        input: InputStream,
        fileName: String,
    ): Identity {
        return when (extension(fileName)) {
            "nes", "fds" -> hashRaw(BufferedInputStream(input), extension(fileName))
            "zip" -> hashZip(input)
            else -> throw IllegalArgumentException(
                "Unsupported NES game format for achievement identification",
            )
        }
    }

    private fun hashZip(input: InputStream): Identity {
        ZipInputStream(BufferedInputStream(input)).use { archive ->
            var result: Identity? = null
            var count = 0
            var remainingBudget = MAX_ZIP_UNCOMPRESSED_BYTES
            val scratch = ByteArray(16 * 1024)
            while (true) {
                val entry = archive.nextEntry ?: break
                count++
                if (count > MAX_ZIP_ENTRIES) throw IOException("NES archive has too many entries")
                val format = extension(entry.name)
                val supported = !entry.isDirectory && (format == "nes" || format == "fds")
                if (supported && result != null) throw IOException("NES archive contains multiple games")
                if (supported && entry.size > MAX_ROM_BYTES) {
                    throw IOException("NES archive entry exceeds safe size limit")
                }
                if (entry.size > remainingBudget) {
                    throw IOException("NES archive exceeds safe decompression size")
                }

                // Hashing or deliberately draining every entry enforces one
                // aggregate uncompressed-size budget, including ignored files.
                val bounded = BoundedEntryStream(archive, remainingBudget)
                if (supported) {
                    result = hashRaw(bounded, format)
                } else {
                    while (true) {
                        val read = bounded.read(scratch)
                        if (read < 0) break
                    }
                }
                remainingBudget -= bounded.bytesRead
                archive.closeEntry()
            }
            return result ?: throw IOException("Archive does not contain a supported NES game")
        }
    }

    /**
     * A non-closing wrapper for one ZIP member; reading one byte past the
     * configured ceiling raises an error rather than silently truncating
     * content and producing a misleading ROM hash.
     */
    private class BoundedEntryStream(
        private val delegate: InputStream,
        private val budget: Long,
    ) : InputStream() {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            val result = delegate.read()
            if (result >= 0) {
                bytesRead++
                if (bytesRead > budget) throw IOException("NES archive exceeds safe decompression size")
            }
            return result
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (length == 0) return 0
            val remaining = budget - bytesRead
            // Allow one extra byte to detect overflow at the exact boundary.
            val allowed = minOf(length.toLong(), remaining + 1L).coerceAtLeast(1L).toInt()
            val result = delegate.read(buffer, offset, allowed)
            if (result > 0) {
                bytesRead += result
                if (bytesRead > budget) throw IOException("NES archive exceeds safe decompression size")
            }
            return result
        }
    }

    private fun hashRaw(
        stream: InputStream,
        extension: String,
    ): Identity {
        val md5 = MessageDigest.getInstance("MD5")
        val prefix = ByteArray(16)
        var prefixSize = 0
        while (prefixSize < prefix.size) {
            val read = stream.read(prefix, prefixSize, prefix.size - prefixSize)
            if (read < 0) break
            if (read == 0) {
                val one = stream.read()
                if (one < 0) break
                prefix[prefixSize++] = one.toByte()
            } else {
                prefixSize += read
            }
        }

        val hasNesHeader =
            prefixSize == 16 &&
                prefix[0] == 0x4E.toByte() &&
                prefix[1] == 0x45.toByte() &&
                prefix[2] == 0x53.toByte() &&
                prefix[3] == 0x1A.toByte()
        val hasFdsHeader =
            prefixSize == 16 &&
                prefix[0] == 0x46.toByte() &&
                prefix[1] == 0x44.toByte() &&
                prefix[2] == 0x53.toByte() &&
                prefix[3] == 0x1A.toByte()
        val hasHeader = hasNesHeader || hasFdsHeader
        val skip = if (hasHeader) 16 else 0
        val console = if (extension == "fds") Console.FDS else Console.NES
        var payloadBytes = (prefixSize - skip).toLong()
        if (payloadBytes > 0) md5.update(prefix, skip, payloadBytes.toInt())

        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read == 0) {
                // Some Android content providers legally return zero without
                // indicating EOF. Read one byte so this loop cannot spin.
                val value = stream.read()
                if (value < 0) break
                payloadBytes++
                if (payloadBytes > MAX_ROM_BYTES) throw IOException("NES ROM exceeds safe hashing size limit")
                md5.update(value.toByte())
                continue
            }
            payloadBytes += read.toLong()
            if (payloadBytes > MAX_ROM_BYTES) {
                throw IOException("NES ROM exceeds safe hashing size limit")
            }
            md5.update(buffer, 0, read)
        }
        if (payloadBytes == 0L) throw IOException("NES ROM contains no game payload")

        val digest = md5.digest()
        val text = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            text.append(HEX[value ushr 4])
            text.append(HEX[value and 0x0F])
        }
        return Identity(text.toString(), console)
    }

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}
