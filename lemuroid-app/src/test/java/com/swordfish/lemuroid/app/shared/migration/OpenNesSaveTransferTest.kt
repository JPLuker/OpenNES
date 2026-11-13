package com.swordfish.lemuroid.app.shared.migration

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class OpenNesSaveTransferTest {
    private lateinit var workspace: File
    private lateinit var source: File
    private lateinit var target: File
    private lateinit var staging: File

    @Before
    fun setUp() {
        workspace = Files.createTempDirectory("opennes-migration-test-").toFile()
        source = File(workspace, "source").apply { mkdirs() }
        target = File(workspace, "target").apply { mkdirs() }
        staging = File(workspace, "cache").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        workspace.deleteRecursively()
    }

    private fun sourceFile(
        path: String,
        data: ByteArray,
    ): File =
        File(source, path).also {
            it.parentFile?.mkdirs()
            it.writeBytes(data)
        }

    private fun exportBytes(): ByteArray =
        ByteArrayOutputStream().also { OpenNesSaveTransfer.export(source, it) }.toByteArray()

    @Test
    fun preservesNesSramAndFceummStatesButNeverIncludesRomsOrTokens() {
        val sram = byteArrayOf(1, 2, 3, 4)
        val nativeState = byteArrayOf(10, 11, 12)
        sourceFile("saves/The Legend of Zelda.srm", sram)
        sourceFile("states/fceumm/Zelda.nes.state", nativeState)
        sourceFile("states/fceumm/Zelda.nes.state.metadata", "{}".toByteArray())
        sourceFile("roms/Game.nes", "proprietary".toByteArray())
        sourceFile("romm-client-token.enc", "private".toByteArray())
        sourceFile("saves/hardcore/Other.srm", "hardcore".toByteArray())

        val backup = exportBytes()
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(backup)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.closeEntry()
            }
        }
        assertTrue("saves/The Legend of Zelda.srm" in names)
        assertTrue("states/fceumm/Zelda.nes.state" in names)
        assertTrue("opennes-save-manifest.json" in names)
        assertFalse(names.any { it.contains("roms/") || it.contains("hardcore") || it.contains("token") })

        val prepared = OpenNesSaveTransfer.prepare(ByteArrayInputStream(backup), staging)
        assertEquals(3, prepared.items.size)
        assertEquals(OpenNesSaveTransfer.ImportResult(3, 0), prepared.apply(target))
        assertArrayEquals(sram, File(target, "saves/The Legend of Zelda.srm").readBytes())
        assertArrayEquals(nativeState, File(target, "states/fceumm/Zelda.nes.state").readBytes())
    }

    @Test
    fun existingSavesArePreservedWithoutReplacement() {
        sourceFile("saves/Zelda.srm", byteArrayOf(1, 2, 3))
        val existing = File(target, "saves/Zelda.srm").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(99, 98))
        }
        val imported =
            OpenNesSaveTransfer.prepare(ByteArrayInputStream(exportBytes()), staging).apply(target)
        assertEquals(0, imported.imported)
        assertEquals(1, imported.existingPreserved)
        assertArrayEquals(byteArrayOf(99, 98), existing.readBytes())
    }

    @Test
    fun stageCorruptionFailsBeforeWritingAnyDestination() {
        sourceFile("saves/Zelda.srm", byteArrayOf(1, 2, 3))
        val prepared = OpenNesSaveTransfer.prepare(ByteArrayInputStream(exportBytes()), staging)
        val staged = staging.listFiles().orEmpty().single()
        File(staged, "saves/Zelda.srm").writeBytes(byteArrayOf(55, 56, 57))
        assertThrows(IOException::class.java) { prepared.apply(target) }
        assertFalse(File(target, "saves/Zelda.srm").exists())
    }

    @Test
    fun invalidOrTraversalArchiveNeverCreatesAnySave() {
        val attacks =
            listOf(
                "saves/../escape.srm",
                "states/fceumm/../../escape.state",
                "roms/secret.nes",
                "saves/other.srm/extra",
                "saves/\\foreign.srm",
            )
        for (path in attacks) {
            val malicious =
                ByteArrayOutputStream().also { bytes ->
                    ZipOutputStream(bytes).use { zip ->
                        zip.putNextEntry(ZipEntry(path))
                        zip.write(byteArrayOf(1))
                        zip.closeEntry()
                    }
                }.toByteArray()
            assertThrows(IOException::class.java) {
                OpenNesSaveTransfer.prepare(ByteArrayInputStream(malicious), staging)
            }
            assertTrue(target.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun duplicateArchiveEntryAndMissingManifestFailClosed() {
        val missing =
            ByteArrayOutputStream().also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry("saves/Zelda.srm"))
                    zip.write(byteArrayOf(1, 2))
                    zip.closeEntry()
                }
            }.toByteArray()
        assertThrows(IOException::class.java) {
            OpenNesSaveTransfer.prepare(ByteArrayInputStream(missing), staging)
        }
    }

    @Test
    fun discardingUnconfirmedImportDoesNotTouchLiveSaves() {
        sourceFile("saves/Zelda.srm", byteArrayOf(1))
        val prepared = OpenNesSaveTransfer.prepare(ByteArrayInputStream(exportBytes()), staging)
        prepared.discard()
        assertFalse(File(target, "saves/Zelda.srm").exists())
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
}
