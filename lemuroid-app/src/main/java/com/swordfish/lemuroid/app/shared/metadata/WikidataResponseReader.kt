package com.swordfish.lemuroid.app.shared.metadata

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** A third-party metadata service must not control response memory or spin forever. */
internal object WikidataResponseReader {
    fun readBounded(
        input: InputStream,
        maxBytes: Int,
    ): ByteArray {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var emptyReads = 0
        while (true) {
            val amount = input.read(buffer)
            if (amount < 0) return output.toByteArray()
            if (amount == 0) {
                if (++emptyReads >= 8) throw IOException("Metadata stream stopped making progress")
                continue
            }
            emptyReads = 0
            if (amount > maxBytes - output.size()) {
                throw IOException("Metadata response exceeds safe limit")
            }
            output.write(buffer, 0, amount)
        }
    }
}
