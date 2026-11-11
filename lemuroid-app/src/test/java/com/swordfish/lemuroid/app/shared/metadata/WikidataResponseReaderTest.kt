package com.swordfish.lemuroid.app.shared.metadata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class WikidataResponseReaderTest {
    @Test
    fun exactLimitIsAcceptedButOversizedResponseIsRejected() {
        val payload = ByteArray(1024) { it.toByte() }
        assertArrayEquals(payload, WikidataResponseReader.readBounded(ByteArrayInputStream(payload), 1024))
        assertThrows(IOException::class.java) {
            WikidataResponseReader.readBounded(ByteArrayInputStream(payload), 1023)
        }
    }

    @Test
    fun stalledProviderFailsInsteadOfSpinning() {
        assertThrows(IOException::class.java) {
            WikidataResponseReader.readBounded(
                object : InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = 0
                },
                2048,
            )
        }
    }

    @Test
    fun transientNoProgressDoesNotLoseData() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val input =
            object : ByteArrayInputStream(payload) {
                private var first = true

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (first) {
                        first = false
                        return 0
                    }
                    return super.read(bytes, offset, length)
                }
            }
        assertArrayEquals(payload, WikidataResponseReader.readBounded(input, 4))
    }
}
