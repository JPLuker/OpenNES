package com.swordfish.lemuroid.app.shared.romm

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

    private const val MAX_EMPTY_READS = 8
}
