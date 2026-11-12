package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class RommSafeStreamReadTest {
    @Test
    fun boundedReadAcceptsExactLimitAndEmptyFile() {
        val bytes = ByteArray(8192) { it.toByte() }
        assertArrayEquals(bytes, RommSafeStreamRead.readBounded(ByteArrayInputStream(bytes), 8192))
        assertArrayEquals(byteArrayOf(), RommSafeStreamRead.readBounded(ByteArrayInputStream(byteArrayOf()), 32))
    }

    @Test
    fun expandingCacheCannotAllocatePastBound() {
        val bytes = ByteArray(8193)
        assertThrows(IOException::class.java) {
            RommSafeStreamRead.readBounded(ByteArrayInputStream(bytes), 8192)
        }
    }

    @Test
    fun stalledStreamTerminatesAfterBoundedAttempts() {
        val stalled =
            object : InputStream() {
                override fun read(): Int = 0

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = 0
            }
        assertThrows(IOException::class.java) { RommSafeStreamRead.readBounded(stalled, 4096) }
    }

    @Test
    fun transientZeroReadPreservesCorrectBytes() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val occasionallyEmpty =
            object : ByteArrayInputStream(bytes) {
                private var first = true

                override fun read(
                    data: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (first) {
                        first = false
                        return 0
                    }
                    return super.read(data, offset, length)
                }
            }
        assertArrayEquals(bytes, RommSafeStreamRead.readBounded(occasionallyEmpty, 4))
    }
}
