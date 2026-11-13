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
    fun rejectsBareNesWithTruncatedHeader() {
        val rom = temporary.newFile("truncated.nes")
        rom.writeBytes(byteArrayOf(0x4E, 0x45, 0x53, 0x1A))
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(rom, "truncated.nes")
        }
    }

    @Test
    fun rejectsBareNesAboveSafeSizeLimit() {
        val rom = temporary.newFile("oversized.nes")
        java.io.RandomAccessFile(rom, "rw").use { file ->
            file.setLength(64L * 1024L * 1024L + 1L)
        }
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(rom, "oversized.nes")
        }
    }

    @Test
    fun rejectsZipNesWithSignatureButIncompleteHeader() {
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(
                zipOf("truncated.nes" to byteArrayOf(0x4E, 0x45, 0x53, 0x1A)),
                "truncated.zip",
            )
        }
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
    fun rejectsWindowsDriveAndEmptyArchiveSegments() {
        for (entry in listOf("C:/game.nes", "folder//game.nes", "/game.nes")) {
            assertThrows(IOException::class.java) {
                RommDownloadVerification.checkImportedRom(
                    zipOf(entry to nesBytes()),
                    "unsafe.zip",
                )
            }
        }
    }

    @Test
    fun rejectsDuplicateArchiveEntryNames() {
        val archive = zipOf("game.nes" to nesBytes(), "a.txt" to byteArrayOf(1), "b.txt" to byteArrayOf(2))
        val bytes = archive.readBytes()
        val original = "b.txt".toByteArray()
        val duplicate = "a.txt".toByteArray()
        var changed = 0
        for (index in 0..(bytes.size - original.size)) {
            if (original.indices.all { offset -> bytes[index + offset] == original[offset] }) {
                duplicate.copyInto(bytes, index)
                changed++
            }
        }
        org.junit.Assert.assertEquals(2, changed)
        archive.writeBytes(bytes)
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkImportedRom(archive, "duplicate.zip")
        }
    }

    @Test
    fun acceptsNormalDirectoryEntriesInRomZip() {
        RommDownloadVerification.checkImportedRom(
            zipOf("games/" to byteArrayOf(), "games/game.nes" to nesBytes()),
            "nested.zip",
        )
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

    @Test
    fun verifiesStandaloneSaveHashAndRejectsMismatch() {
        val save = temporary.newFile("game.sav")
        save.writeBytes(byteArrayOf(1, 2, 3, 4))
        val md5 =
            java.security.MessageDigest.getInstance("MD5")
                .digest(save.readBytes())
                .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        RommDownloadVerification.checkRemoteSaveHash(save, md5)
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkRemoteSaveHash(save, "0".repeat(32))
        }
    }

    @Test
    fun verifiesZipSaveHashFromUncompressedEntryContents() {
        val content = byteArrayOf(10, 20, 30)
        val archive = zipOf("save.sav" to content)
        val entryMd5 =
            java.security.MessageDigest.getInstance("MD5")
                .digest(content)
                .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val expected =
            java.security.MessageDigest.getInstance("MD5")
                .digest("save.sav:$entryMd5".toByteArray(Charsets.UTF_8))
                .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        RommDownloadVerification.checkRemoteSaveHash(archive, expected)
        assertThrows(IOException::class.java) {
            RommDownloadVerification.checkRemoteSaveHash(archive, "0".repeat(32))
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
