package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun verifiedArchiveSurvivesReopeningStoreAndFiltersByRomId() {
        val source = temporary.newFile("remote.sav")
        source.writeBytes(ByteArray(200) { it.toByte() })
        val folder = temporary.newFolder("versions")
        val first = RommVersionedSaveBackupStore(folder)
        val backup = first.createVerifiedBackup(45, source)
        first.createVerifiedBackup(88, source)

        val reloaded = RommVersionedSaveBackupStore(folder)
        val entries = reloaded.listVerifiedBackups(45)
        assertEquals(1, entries.size)
        assertEquals(backup.sha256, entries.single().sha256)
        assertEquals(backup.bytes, entries.single().bytes)
        assertEquals(2, reloaded.listVerifiedBackups().size)
    }

    @Test
    fun corruptedArchiveAndManifestCannotAppearAsVerified() {
        val source = temporary.newFile("remote.sav")
        source.writeBytes(ByteArray(80) { 7 })
        val folder = temporary.newFolder("versions")
        val store = RommVersionedSaveBackupStore(folder)
        val first = store.createVerifiedBackup(45, source)
        assertEquals(1, store.listVerifiedBackups(45).size)
        first.file.writeBytes(ByteArray(80) { 9 })
        assertTrue(store.listVerifiedBackups(45).isEmpty())

        val second = store.createVerifiedBackup(45, source)
        val manifest = java.io.File(folder, second.file.name + ".sha256")
        manifest.writeText("0".repeat(64) + " 80\\n")
        assertTrue(store.listVerifiedBackups(45).isEmpty())
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
    fun boundArchivesRetainServerIdentityWhileLegacyExportsRemainUsable() {
        val source = temporary.newFile("source.sav")
        source.writeBytes(ByteArray(200) { it.toByte() })
        val folder = temporary.newFolder("versions")
        val store = RommVersionedSaveBackupStore(folder)
        val server = RommServerIdentity.fingerprint("https://romm.example.com")
        val bound = store.createVerifiedBackup(34, source, server)
        val legacy = store.createVerifiedBackup(34, source)
        val reopened = RommVersionedSaveBackupStore(folder).listVerifiedBackups(34)
        assertEquals(2, reopened.size)
        assertEquals(server, reopened.first { it.file == bound.file }.sourceServerFingerprint)
        assertEquals(null, reopened.first { it.file == legacy.file }.sourceServerFingerprint)
        assertFalse(store.deleteVerifiedBackup(bound.copy(sourceServerFingerprint = null)))
        assertTrue(store.deleteVerifiedBackup(bound))
        assertTrue(store.verify(legacy))
    }

    @Test
    fun confirmedDeletionRemovesOnlySelectedArchiveAndManifest() {
        val original = temporary.newFile("live.sav")
        val payload = ByteArray(256) { it.toByte() }
        original.writeBytes(payload)
        val folder = temporary.newFolder("versions")
        val store = RommVersionedSaveBackupStore(folder)
        val first = store.createVerifiedBackup(7, original)
        val second = store.createVerifiedBackup(7, original)
        val another = store.createVerifiedBackup(8, original)

        assertTrue(store.deleteVerifiedBackup(first))
        assertFalse(first.file.exists())
        assertFalse(java.io.File(folder, first.file.name + ".sha256").exists())
        assertFalse(store.deleteVerifiedBackup(first))
        assertTrue(store.verify(second))
        assertTrue(store.verify(another))
        assertEquals(2, store.listVerifiedBackups().size)
        assertArrayEquals(payload, original.readBytes())
    }

    @Test
    fun deletionRefusesStaleCorruptAndExternalBackups() {
        val original = temporary.newFile("live.sav")
        original.writeBytes(byteArrayOf(1, 2, 3))
        val folder = temporary.newFolder("versions")
        val store = RommVersionedSaveBackupStore(folder)
        val archive = store.createVerifiedBackup(12, original)
        val external =
            RommVersionedSaveBackupStore.Backup(
                original,
                archive.romId,
                archive.sha256,
                archive.bytes,
            )
        assertFalse(store.deleteVerifiedBackup(external))
        assertTrue(original.exists())
        val forged = archive.copy(sha256 = "0".repeat(64))
        assertFalse(store.deleteVerifiedBackup(forged))
        archive.file.writeBytes(byteArrayOf(0, 0, 0))
        assertFalse(store.deleteVerifiedBackup(archive))
        assertTrue(archive.file.exists())
        assertTrue(original.exists())
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
