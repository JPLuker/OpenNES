package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

class RommSaveSnapshotterTest {
    @Test
    fun permanentlyStalledDocumentProviderCannotHangSyncReview() {
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromReopenableStream {
                object : java.io.InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = 0
                }
            }
        }
    }

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun snapshotsReflectBytesNotFileNames() {
        val first = folder.newFile("one.sav")
        val second = folder.newFile("two.sav")
        val content = ByteArray(1024) { (it * 5).toByte() }
        first.writeBytes(content)
        second.writeBytes(content)
        val left = RommSaveSnapshotter.fromFile(first)
        val right = RommSaveSnapshotter.fromFile(second)
        assertEquals(left, right)
        assertEquals(content.size.toLong(), left.bytes)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(content).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            },
            left.sha256,
        )
    }

    @Test
    fun aChangedSaveProducesDifferentFingerprint() {
        val file = folder.newFile("current.sav")
        file.writeBytes(ByteArray(128) { 1 })
        val before = RommSaveSnapshotter.fromFile(file)
        file.writeBytes(ByteArray(128) { 2 })
        val after = RommSaveSnapshotter.fromFile(file)
        assertNotEquals(before, after)
    }

    @Test
    fun documentStreamComparisonUsesTwoIndependentContentFingerprints() {
        val saved = ByteArray(512) { (it * 7).toByte() }
        val source = folder.newFile("archived.sav")
        source.writeBytes(saved)
        var opens = 0
        val selected =
            RommSaveSnapshotter.fromReopenableStream {
                opens += 1
                ByteArrayInputStream(saved)
            }
        assertEquals(2, opens)
        assertEquals(RommSaveSnapshotter.fromFile(source), selected)
    }

    @Test
    fun changedOrUnavailableDocumentFailsClosed() {
        var opens = 0
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromReopenableStream {
                opens += 1
                ByteArrayInputStream(ByteArray(32) { opens.toByte() })
            }
        }
        assertEquals(2, opens)
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromReopenableStream { null }
        }
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromReopenableStream { ByteArrayInputStream(byteArrayOf()) }
        }
    }

    @Test
    fun streamEnforcesTheSameSixteenMibSizeLimitAsRegularSnapshots() {
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromReopenableStream {
                object : java.io.InputStream() {
                    private var remaining = 16 * 1024 * 1024 + 1

                    override fun read(): Int {
                        if (remaining <= 0) return -1
                        remaining -= 1
                        return 0
                    }

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        if (remaining <= 0) return -1
                        val count = minOf(length, remaining)
                        java.util.Arrays.fill(bytes, offset, offset + count, 0.toByte())
                        remaining -= count
                        return count
                    }
                }
            }
        }
    }

    @Test
    fun rejectsEmptyNonexistentAndOversizedFiles() {
        val empty = folder.newFile("empty.sav")
        assertThrows(IOException::class.java) { RommSaveSnapshotter.fromFile(empty) }
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromFile(java.io.File(folder.root, "absent.sav"))
        }
        val huge = folder.newFile("huge.sav")
        // setLength creates a sparse file without allocating 16 MB of test data.
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(16L * 1024L * 1024L + 1) }
        assertTrue(huge.length() > 16L * 1024L * 1024L)
        assertThrows(IOException::class.java) { RommSaveSnapshotter.fromFile(huge) }
    }
}
