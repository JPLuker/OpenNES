package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class RommSaveSnapshotterTest {
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
    fun rejectsEmptyNonexistentAndOversizedFiles() {
        val empty = folder.newFile("empty.sav")
        assertThrows(IOException::class.java) { RommSaveSnapshotter.fromFile(empty) }
        assertThrows(IOException::class.java) {
            RommSaveSnapshotter.fromFile(java.io.File(folder.root, "absent.sav"))
        }
        val huge = folder.newFile("huge.sav")
        huge.outputStream().use { it.channel.truncate(16L * 1024L * 1024L + 1) }
        // FileChannel.truncate never extends a file; RandomAccessFile does.
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(16L * 1024L * 1024L + 1) }
        assertTrue(huge.length() > 16L * 1024L * 1024L)
        assertThrows(IOException::class.java) { RommSaveSnapshotter.fromFile(huge) }
    }
}
