package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class NesCheatResponseReaderTest {
    @Test
    fun boundedReaderAcceptsValidAndExactlySizedResponse() {
        val payload = ByteArray(8192) { it.toByte() }
        assertArrayEquals(payload, NesCheatResponseReader.readBounded(ByteArrayInputStream(payload), 8192))
        assertArrayEquals(byteArrayOf(), NesCheatResponseReader.readBounded(ByteArrayInputStream(byteArrayOf()), 1))
    }

    @Test
    fun cannotReadOversizedIndexIntoMemory() {
        assertThrows(IOException::class.java) {
            NesCheatResponseReader.readBounded(ByteArrayInputStream(ByteArray(8193)), 8192)
        }
    }

    @Test
    fun failsOnPermanentlyStalledResponse() {
        assertThrows(IOException::class.java) {
            NesCheatResponseReader.readBounded(
                object : InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = 0
                },
                8192,
            )
        }
    }

    @Test
    fun transientZeroReadPreservesPayload() {
        val payload = byteArrayOf(5, 4, 3, 2, 1)
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
        assertArrayEquals(payload, NesCheatResponseReader.readBounded(input, 5))
    }
}
