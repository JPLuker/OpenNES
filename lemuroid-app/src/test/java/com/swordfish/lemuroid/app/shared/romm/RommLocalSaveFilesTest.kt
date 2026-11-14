package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RommLocalSaveFilesTest {
    private val root = Files.createTempDirectory("romm-local").toFile()
    private val saves = File(root, "saves").apply { mkdirs() }
    private val states = File(root, "states").apply { mkdirs() }
    private val aside = File(root, "aside")
    private val game = "Zelda (USA).nes"
    private val oldSave = byteArrayOf(1, 2, 3)
    private val newSave = byteArrayOf(9, 8, 7, 6)

    private fun autosaves() = RommLocalSaveFiles.autosaveFiles(states, game)

    private fun writeAutosave() {
        autosaves().forEach {
            it.parentFile.mkdirs()
            it.writeBytes(byteArrayOf(5))
        }
    }

    @Test
    fun usesTheSameNamesAsTheEmulator() {
        assertEquals("Zelda (USA).srm", RommLocalSaveFiles.saveFileName(game))
        assertEquals(
            listOf("fceumm/Zelda (USA).nes.state", "fceumm/Zelda (USA).nes.state.metadata"),
            autosaves().map { it.relativeTo(states).path },
        )
    }

    @Test
    fun aMissingOrEmptySaveHasNoSnapshot() {
        val file = RommLocalSaveFiles.saveFile(saves, game)
        assertNull(RommLocalSaveFiles.snapshot(file))
        file.writeBytes(ByteArray(0))
        assertNull(RommLocalSaveFiles.snapshot(file))
    }

    @Test
    fun aDownloadBacksUpTheOldSaveSetsTheAutosaveAsideAndWritesTheNewOne() {
        val file = RommLocalSaveFiles.saveFile(saves, game).apply { writeBytes(oldSave) }
        writeAutosave()
        var backedUp: ByteArray? = null
        RommLocalSaveFiles.replaceWithDownload(
            target = file,
            downloaded = newSave,
            expectedMd5 = RommLocalSaveFiles.md5(newSave),
            currentMd5 = RommLocalSaveFiles.md5(oldSave),
            autosaveFiles = autosaves(),
            asideDirectory = aside,
            nowMillis = 1000,
        ) { backedUp = it.readBytes() }
        assertArrayEquals(oldSave, backedUp)
        assertArrayEquals(newSave, file.readBytes())
        // The autosave can no longer be restored over the new save, but is kept.
        assertTrue(autosaves().none { it.exists() })
        assertEquals(2, aside.listFiles()!!.size)
        assertFalse(File(saves, ".Zelda (USA).srm.romm-download").exists())
    }

    @Test
    fun aFirstDownloadNeedsNoBackup() {
        val file = RommLocalSaveFiles.saveFile(saves, game)
        RommLocalSaveFiles.replaceWithDownload(
            file,
            newSave,
            RommLocalSaveFiles.md5(newSave),
            null,
            autosaves(),
            aside,
            1000,
        ) { throw AssertionError("nothing to back up") }
        assertArrayEquals(newSave, file.readBytes())
    }

    @Test
    fun aCorruptDownloadChangesNothing() {
        val file = RommLocalSaveFiles.saveFile(saves, game).apply { writeBytes(oldSave) }
        writeAutosave()
        assertThrows(IOException::class.java) {
            RommLocalSaveFiles.replaceWithDownload(
                file,
                newSave,
                "0".repeat(32),
                RommLocalSaveFiles.md5(oldSave),
                autosaves(),
                aside,
                1000,
            ) {}
        }
        assertArrayEquals(oldSave, file.readBytes())
        assertTrue(autosaves().all { it.exists() })
    }

    @Test
    fun aSaveWrittenDuringSyncIsNeverReplaced() {
        // The player saved again after negotiation: sync saw old, disk has newer.
        val file = RommLocalSaveFiles.saveFile(saves, game).apply { writeBytes(byteArrayOf(4, 4)) }
        assertThrows(IOException::class.java) {
            RommLocalSaveFiles.replaceWithDownload(
                file,
                newSave,
                RommLocalSaveFiles.md5(newSave),
                RommLocalSaveFiles.md5(oldSave),
                autosaves(),
                aside,
                1000,
            ) {}
        }
        assertArrayEquals(byteArrayOf(4, 4), file.readBytes())
    }

    @Test
    fun aFailedBackupStopsTheReplacement() {
        val file = RommLocalSaveFiles.saveFile(saves, game).apply { writeBytes(oldSave) }
        writeAutosave()
        assertThrows(IOException::class.java) {
            RommLocalSaveFiles.replaceWithDownload(
                file,
                newSave,
                RommLocalSaveFiles.md5(newSave),
                RommLocalSaveFiles.md5(oldSave),
                autosaves(),
                aside,
                1000,
            ) { throw IOException("disk full") }
        }
        assertArrayEquals(oldSave, file.readBytes())
        assertTrue(autosaves().all { it.exists() })
    }

    @Test
    fun anUploadIsRefusedIfTheSaveChangedSinceNegotiation() {
        val file = RommLocalSaveFiles.saveFile(saves, game).apply { writeBytes(newSave) }
        assertArrayEquals(newSave, RommLocalSaveFiles.readForUpload(file, RommLocalSaveFiles.md5(newSave)))
        assertThrows(IOException::class.java) {
            RommLocalSaveFiles.readForUpload(file, RommLocalSaveFiles.md5(oldSave))
        }
    }
}
