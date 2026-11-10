package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class RommVersionedSaveBackupStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun createsDistinctVerifiedBackupsWithoutChangingOriginal() {
        val original = temporary.newFile("live.sav")
        val bytes = ByteArray(8192) { it.toByte() }
        original.writeBytes(bytes)
        val store = RommVersionedSaveBackupStore(temporary.newFolder("versions"))
        val first = store.createVerifiedBackup(7, original)
        val second = store.createVerifiedBackup(7, original)
        assertNotEquals(first.file, second.file)
        assertTrue(first.file.exists())
        assertTrue(second.file.exists())
        assertTrue(store.verify(first))
        assertTrue(store.verify(second))
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test
    fun detectsBackupCorruptionWithoutTouchingLiveSave() {
        val original = temporary.newFile("local.sav")
        original.writeBytes("before".toByteArray())
        val store = RommVersionedSaveBackupStore(temporary.newFolder("versions"))
        val backup = store.createVerifiedBackup(10, original)
        backup.file.writeBytes("after".toByteArray())
        assertFalse(store.verify(backup))
        assertArrayEquals("before".toByteArray(), original.readBytes())
    }

    @Test
    fun refusesEmptyAndOversizedSnapshotsAndCleansPartials() {
        val folder = temporary.newFolder("versions")
        val store = RommVersionedSaveBackupStore(folder)
        val empty = temporary.newFile("empty.sav")
        assertThrows(IOException::class.java) { store.createVerifiedBackup(1, empty) }
        val tooLarge = temporary.newFile("oversized.sav")
        tooLarge.outputStream().use { output ->
            val piece = ByteArray(1024 * 1024)
            repeat(17) { output.write(piece) }
        }
        assertThrows(IOException::class.java) { store.createVerifiedBackup(1, tooLarge) }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rejectsUnverifiedRomIdentifiersAndExistingBackupsAsSources() {
        val original = temporary.newFile("local.sav")
        original.writeBytes(byteArrayOf(1))
        val store = RommVersionedSaveBackupStore(temporary.newFolder("versions"))
        assertThrows(IllegalArgumentException::class.java) { store.createVerifiedBackup(0, original) }
        val prior = store.createVerifiedBackup(3, original)
        assertThrows(IOException::class.java) { store.createVerifiedBackup(3, prior.file) }
    }
}
