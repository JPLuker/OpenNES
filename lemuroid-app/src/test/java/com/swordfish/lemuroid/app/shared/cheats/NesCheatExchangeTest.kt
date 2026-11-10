package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class NesCheatExchangeTest {
    @Test
    fun exportAndImportKeepNamesAndCodesButNeverEnableThem() {
        val original = listOf(NesCheatDefinition("one", "Extra lives", "APZLGI"))
        val encoded = NesCheatExchange.encode(original)
        val text = String(encoded, Charsets.UTF_8)
        assertFalse(text.contains("enabled"))
        assertTrue(text.contains("OpenNES-cheats-v1"))
        val imported = NesCheatExchange.decode(encoded)
        assertNotNull(imported)
        assertEquals(1, imported?.size)
        assertEquals("Extra lives", imported?.first()?.name)
        assertEquals("APZLGI", imported?.first()?.code)
        assertTrue(imported?.first()?.id != "one")
    }

    @Test
    fun importRejectsInvalidAndStateBearingDocuments() {
        val invalid =
            listOf(
                "{}",
                """{"format":"OpenNES-cheats-v1","entries":[{"name":"Bad","code":"FFFF:XX"}]}""",
                """{"format":"OpenNES-cheats-v1","entries":[{"name":"Bad","code":"APZLGI","enabled":true}]}""",
                """{"format":"OpenNES-cheats-v1","entries":[{"name":"Bad","code":"APZLGI"},{"name":"Duplicate","code":"APZLGI"}]}""",
                """{"format":"another-format","entries":[]}""",
            )
        invalid.forEach { assertNull(NesCheatExchange.decode(it.toByteArray())) }
    }

    @Test
    fun boundedReadsRejectOversizedFiles() {
        assertNull(NesCheatExchange.readBounded(ByteArrayInputStream(ByteArray(65 * 1024))))
        assertNotNull(NesCheatExchange.readBounded(ByteArrayInputStream(ByteArray(20))))
    }

    @Test
    fun mergingDoesNotDuplicateOrOverwriteExistingCodes() {
        val current = listOf(NesCheatDefinition("old", "Existing name", "APZLGI"))
        val imported =
            listOf(
                NesCheatDefinition("different", "Changed name", "APZLGI"),
                NesCheatDefinition("new", "New code", "00FF:1A"),
            )
        val merged = NesCheatExchange.merge(current, imported)
        assertEquals(2, merged?.size)
        assertEquals("Existing name", merged?.first()?.name)
        assertEquals("New code", merged?.last()?.name)
        assertEquals("old", merged?.first()?.id)
    }
}
