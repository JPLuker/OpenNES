package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RommDownloadVerificationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private var nextZip = 0

    @Test
    fun acceptsSingleNesWithValidHeader() {
        val rom = temporary.newFile("valid.nes")
        rom.writeBytes(nesBytes())
        RommDownloadVerification.checkImportedRom(rom, "valid.nes")
        RommDownloadVerification.checkImportedRom(
            zipOf("game.nes" to nesBytes(), "readme.txt" to "Notes".toByteArray()),
            "game.zip",
        )
    }

    @Test
    fun rejectsRenamedOrTruncatedNesInsideZip() {
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("fake.nes" to "not a ROM".toByteArray()),
                "fake.zip",
            )
        }
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("short.nes" to byteArrayOf(0x4E, 0x45)),
                "short.zip",
            )
        }
    }

    @Test
    fun rejectsMultipleRomsAndUnsafeZipPaths() {
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("one.nes" to nesBytes(), "two.nes" to nesBytes()),
                "multiple.zip",
            )
        }
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("../escape.nes" to nesBytes()),
                "traversal.zip",
            )
        }
    }

    @Test
    fun rejectsTruncatedFdsInsideZip() {
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("broken.fds" to ByteArray(32)),
                "broken.zip",
            )
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val zip = temporary.newFile("input-${nextZip++}.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return zip
    }

    private fun nesBytes(): ByteArray = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(16)
}
