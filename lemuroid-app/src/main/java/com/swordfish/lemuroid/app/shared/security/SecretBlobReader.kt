package com.swordfish.lemuroid.app.shared.security

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Read private encrypted account blobs without allocating from an untrusted
 * file length or readBytes()/AtomicFile.readFully(). Never log payload data.
 */
internal object SecretBlobReader {
    private const val LIMIT = 4096
    private const val MAX_STALLED_READS = 8

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        var stalled = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (count == 0) {
                if (++stalled >= MAX_STALLED_READS) {
                    throw IOException("Encrypted account file stopped making progress")
                }
                continue
            }
            stalled = 0
            if (count > LIMIT - output.size()) {
                throw IOException("Encrypted account file exceeds maximum size")
            }
            output.write(buffer, 0, count)
        }
    }
}
