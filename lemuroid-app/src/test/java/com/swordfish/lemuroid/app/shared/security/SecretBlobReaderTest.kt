package com.swordfish.lemuroid.app.shared.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class SecretBlobReaderTest {
    @Test
    fun acceptsSmallAndExactlyMaximumSizedEncryptedFiles() {
        val small = byteArrayOf(1, 2, 3)
        val exact = ByteArray(4096) { it.toByte() }
        assertArrayEquals(small, SecretBlobReader.readBounded(ByteArrayInputStream(small)))
        assertArrayEquals(exact, SecretBlobReader.readBounded(ByteArrayInputStream(exact)))
    }

    @Test
    fun rejectsOversizedFileBeforeWritingBeyondLimit() {
        assertThrows(IOException::class.java) {
            SecretBlobReader.readBounded(ByteArrayInputStream(ByteArray(4097)))
        }
    }

    @Test
    fun failsClosedWhenStreamNeverAdvances() {
        assertThrows(IOException::class.java) {
            SecretBlobReader.readBounded(
                object : InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = 0
                },
            )
        }
    }

    @Test
    fun transientZeroReadKeepsPayloadIntact() {
        val content = ByteArray(320) { it.toByte() }
        val source =
            object : ByteArrayInputStream(content) {
                private var zeroRead = true

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (zeroRead) {
                        zeroRead = false
                        return 0
                    }
                    return super.read(bytes, offset, length)
                }
            }
        assertArrayEquals(content, SecretBlobReader.readBounded(source))
    }
}
