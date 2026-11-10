package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NesRetroAchievementsHashTest {
    @Test
    fun nesHeaderIsNotPartOfAchievementHash() {
        val payload = ByteArray(32) { (it * 7).toByte() }
        val nesHeader = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12)
        val actual = NesRetroAchievementsHash.calculate(ByteArrayInputStream(nesHeader + payload), "test.nes")
        assertEquals(md5(payload), actual.md5)
        assertEquals(NesRetroAchievementsHash.Console.NES, actual.console)
    }

    @Test
    fun fdsHeaderIsNotPartOfAchievementHash() {
        val payload = ByteArray(32) { it.toByte() }
        val fdsHeader = byteArrayOf(0x46, 0x44, 0x53, 0x1A) + ByteArray(12)
        val actual = NesRetroAchievementsHash.calculate(ByteArrayInputStream(fdsHeader + payload), "test.fds")
        assertEquals(md5(payload), actual.md5)
        assertEquals(NesRetroAchievementsHash.Console.FDS, actual.console)
    }

    @Test
    fun headerlessGameHashesEntirePayload() {
        val payload = ByteArray(64) { it.toByte() }
        val actual = NesRetroAchievementsHash.calculate(ByteArrayInputStream(payload), "game.nes")
        assertEquals(md5(payload), actual.md5)
    }

    @Test
    fun singleRomInZipHasSameIdentityAsRawRom() {
        val payload = ByteArray(32) { (it * 11).toByte() }
        val header = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12)
        val raw = NesRetroAchievementsHash.calculate(ByteArrayInputStream(header + payload), "game.nes")
        val zipped =
            NesRetroAchievementsHash.calculate(
                ByteArrayInputStream(
                    zipOf("notes.txt" to "OpenNES".toByteArray(), "game.nes" to (header + payload)),
                ),
                "game.zip",
            )
        assertEquals(raw, zipped)
    }

    @Test
    fun multipleNesGamesAreRejectedInsteadOfGuessing() {
        assertThrows(IOException::class.java) {
            NesRetroAchievementsHash.calculate(
                ByteArrayInputStream(zipOf("one.nes" to ByteArray(20), "two.nes" to ByteArray(20))),
                "many.zip",
            )
        }
    }

    @Test
    fun zipWithNoNesGamesIsRejected() {
        assertThrows(IOException::class.java) {
            NesRetroAchievementsHash.calculate(
                ByteArrayInputStream(zipOf("notes.txt" to "not a ROM".toByteArray())),
                "notes.zip",
            )
        }
    }

    @Test
    fun headerOnlyNesIsRejectedAsEmpty() {
        val header = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12)
        assertThrows(IOException::class.java) {
            NesRetroAchievementsHash.calculate(ByteArrayInputStream(header), "empty.nes")
        }
    }

    @Test
    fun unsupportedFileExtensionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NesRetroAchievementsHash.calculate(ByteArrayInputStream(ByteArray(24)), "wrong.sfc")
        }
    }

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (filename, bytes) ->
                zip.putNextEntry(ZipEntry(filename))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
