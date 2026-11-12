package com.swordfish.lemuroid.app.shared.romm

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * A broken content provider may return zero bytes forever from a bulk read.
 * Accept a few transient zero reads, but never spin without making progress.
 */
internal object RommSafeStreamRead {
    fun next(
        input: InputStream,
        buffer: ByteArray,
    ): Int {
        require(buffer.isNotEmpty())
        var emptyReads = 0
        while (true) {
            val read = input.read(buffer)
            if (read != 0) return read
            if (++emptyReads >= MAX_EMPTY_READS) {
                throw IOException("RomM save source stopped making progress")
            }
        }
    }

    /**
     * Never call InputStream.readBytes() for a file whose size can change
     * between the size check and the actual read.
     */
    fun readBounded(
        input: InputStream,
        maxBytes: Int,
    ): ByteArray {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = next(input, buffer)
            if (count < 0) return output.toByteArray()
            if (count > maxBytes - output.size()) {
                throw IOException("RomM catalogue cache exceeds safe size")
            }
            output.write(buffer, 0, count)
        }
    }

    private const val MAX_EMPTY_READS = 8
}
