package com.swordfish.lemuroid.app.shared.cheats

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** GitHub cheat-index responses are untrusted and may not make read progress. */
internal object NesCheatResponseReader {
    fun readBounded(
        input: InputStream,
        limit: Int,
    ): ByteArray {
        require(limit > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var stalledReads = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (count == 0) {
                if (++stalledReads >= 8) throw IOException("Cheat database response stalled")
                continue
            }
            stalledReads = 0
            if (count > limit - output.size()) {
                throw IOException("Cheat database response exceeds safe size")
            }
            output.write(buffer, 0, count)
        }
    }
}
