package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RommRomFingerprintTest {
    private fun md5(bytes: ByteArray) =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun zip(vararg members: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            members.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val rom = ByteArray(4096) { (it % 251).toByte() }

    @Test
    fun aPlainRomIsHashedWholeIncludingItsHeaderLikeRomm() {
        assertEquals(md5(rom), RommRomFingerprint.md5(ByteArrayInputStream(rom), isZip = false))
    }

    @Test
    fun aZippedRomIsHashedByItsLargestMemberLikeRomm() {
        val archive = zip("readme.txt" to "hello".toByteArray(), "Zelda (USA).nes" to rom)
        assertEquals(md5(rom), RommRomFingerprint.md5(ByteArrayInputStream(archive), isZip = true))
    }

    @Test
    fun emptyInputsAreRejected() {
        assertThrows(IOException::class.java) {
            RommRomFingerprint.md5(ByteArrayInputStream(ByteArray(0)), isZip = false)
        }
        assertThrows(IOException::class.java) {
            RommRomFingerprint.md5(ByteArrayInputStream(zip()), isZip = true)
        }
    }
}
