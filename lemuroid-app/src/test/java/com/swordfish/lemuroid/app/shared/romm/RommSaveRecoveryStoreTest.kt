package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class RommSaveRecoveryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun createsVerifiedNewCopyWithoutTouchingLiveSave() {
        val current = folder.newFile("running.sav")
        val original = ByteArray(4096) { (it * 3).toByte() }
        current.writeBytes(original)
        val backups = RommVersionedSaveBackupStore(folder.newFolder("backups"))
        val backup = backups.createVerifiedBackup(42, current)
        current.writeBytes(ByteArray(4096) { 0x5A.toByte() })

        val recovery =
            RommSaveRecoveryStore(backups)
                .stageRecovery(backup, folder.newFolder("recovered"))

        assertArrayEquals(original, recovery.file.readBytes())
        assertArrayEquals(ByteArray(4096) { 0x5A.toByte() }, current.readBytes())
        assertTrue(backups.verify(backup))
        assertEquals(backup.sha256, recovery.sha256)
        assertEquals(42, recovery.romId)
    }

    @Test
    fun corruptBackupMustNotProduceRecoveredFile() {
        val live = folder.newFile("save.sav")
        live.writeBytes(ByteArray(128) { it.toByte() })
        val backups = RommVersionedSaveBackupStore(folder.newFolder("immutable"))
        val backup = backups.createVerifiedBackup(73, live)
        backup.file.appendBytes(byteArrayOf(1))
        val recovered = folder.newFolder("recovered")
        assertThrows(IOException::class.java) {
            RommSaveRecoveryStore(backups).stageRecovery(backup, recovered)
        }
        assertTrue(recovered.listFiles().isNullOrEmpty())
    }

    @Test
    fun refusesToWriteNewRecoveryIntoBackupDirectory() {
        val live = folder.newFile("save.sav")
        live.writeBytes(ByteArray(32) { 1 })
        val immutable = folder.newFolder("immutable")
        val backups = RommVersionedSaveBackupStore(immutable)
        val backup = backups.createVerifiedBackup(12, live)
        assertThrows(IOException::class.java) {
            RommSaveRecoveryStore(backups).stageRecovery(backup, immutable)
        }
        // A committed backup now has two immutable files: data + SHA-256 manifest.
        assertEquals(2, immutable.listFiles()?.size)
        assertTrue(java.io.File(immutable, backup.file.name + ".sha256").isFile)
    }

    @Test
    fun twoRecoveriesNeverOverwriteEachOther() {
        val live = folder.newFile("save.sav")
        live.writeBytes(ByteArray(64) { 3 })
        val backups = RommVersionedSaveBackupStore(folder.newFolder("immutable"))
        val backup = backups.createVerifiedBackup(9, live)
        val recovered = folder.newFolder("recovered")
        val first = RommSaveRecoveryStore(backups).stageRecovery(backup, recovered)
        val second = RommSaveRecoveryStore(backups).stageRecovery(backup, recovered)
        assertFalse(first.file == second.file)
        assertTrue(first.file.isFile)
        assertTrue(second.file.isFile)
    }
}
