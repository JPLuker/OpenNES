package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesCheatCodesTest {
    @Test
    fun acceptsSixAndEightCharacterGameGenieCodes() {
        assertEquals("APZLGI", NesCheatCodes.normalize("apz-lgi"))
        assertEquals("APZLGITY", NesCheatCodes.normalize("apzl gity"))
        assertEquals("GZUXNGEI", NesCheatCodes.normalize("GZUXNGEI"))
    }

    @Test
    fun acceptsSupportedRawMemoryCodes() {
        assertEquals("00FF:1A", NesCheatCodes.normalize("00ff:1a"))
        assertEquals("80AB?04:FF", NesCheatCodes.normalize("80ab?04:ff"))
    }

    @Test
    fun rejectsMalformedOrUnsupportedNativeInput() {
        listOf(
            "",
            "ABCDEFGH",
            "APZLG",
            "APZLGIT",
            "APZLGITYXYZ",
            "APZLGI+APZLGI",
            "0:1",
            "FFFF:FF:FF",
            "FFFF?G1:00",
            "1234:XX",
            "APZLGI\\n",
        ).forEach { assertNull(NesCheatCodes.normalize(it)) }
    }
}
