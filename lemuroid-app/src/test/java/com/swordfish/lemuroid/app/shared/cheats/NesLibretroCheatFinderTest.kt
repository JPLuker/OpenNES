package com.swordfish.lemuroid.app.shared.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NesLibretroCheatFinderTest {
    @Test
    fun cheatSourceIsPinnedToAnImmutableCommitAndOneFile() {
        val revision = "a".repeat(40)
        val path = NesLibretroCheatFinder.pinnedCheatUrl(revision, "Duck Hunt (USA).cht")
        assertTrue(path.contains("/$revision/cht/Nintendo%20-%20Nintendo%20Entertainment%20System/"))
        assertTrue(path.endsWith("Duck%20Hunt%20%28USA%29.cht"))
        assertFalse(path.contains("/master/"))
    }

    @Test
    fun cheatUrlCannotUseAMutableBranchOrPathTraversal() {
        val revision = "b".repeat(40)
        for (invalid in listOf("master", "../master", "a".repeat(39), "A".repeat(40))) {
            assertThrows(IllegalArgumentException::class.java) {
                NesLibretroCheatFinder.pinnedCheatUrl(invalid, "Game.cht")
            }
        }
        for (invalid in listOf("../Other.cht", "Other\\\\Dir.cht", "Game.nes", "/Game.cht")) {
            assertThrows(IllegalArgumentException::class.java) {
                NesLibretroCheatFinder.pinnedCheatUrl(revision, invalid)
            }
        }
    }

    @Test
    fun libretroGameGenieCodesAreParsedButCombinedCodesAreExcluded() {
        val source =
            """
            cheats = 4
            cheat0_desc = "Infinite Ammo"
            cheat0_code = "SZNIPPVG"
            cheat0_enable = true
            cheat1_desc = "Two-code combination"
            cheat1_code = "AASIAUZA+ALSIPLEI"
            cheat2_desc = "Unsupported action replay"
            cheat2_code = "12345+ABCDE"
            cheat3_desc = "Extra Ammo"
            cheat3_code = "SZNIPPVG"
            """.trimIndent()

        val result = NesLibretroCheatFinder.parseCheatText(source, "Duck Hunt (World)")
        assertEquals(1, result.size)
        assertEquals("Infinite Ammo", result.single().title)
        assertEquals("SZNIPPVG", result.single().code)
        assertFalse(result.any { it.code.contains('+') })
    }

    @Test
    fun titleMatchingHandlesArticlesAndRegionLabels() {
        assertEquals(
            NesLibretroCheatFinder.normalizeTitle("Legend of Zelda, The (USA) (Game Genie).cht"),
            NesLibretroCheatFinder.normalizeTitle("The Legend of Zelda"),
        )
        assertEquals(
            NesLibretroCheatFinder.normalizeTitle("Super Mario Bros. + Duck Hunt"),
            NesLibretroCheatFinder.normalizeTitle("Super Mario Bros. / Duck Hunt"),
        )
        assertTrue(
            NesLibretroCheatFinder.normalizeTitle("The Legend of Zelda") !=
                NesLibretroCheatFinder.normalizeTitle("Zelda II"),
        )
    }

    @Test
    fun oversizedUntrustedSourceIsRejected() {
        assertTrue(NesLibretroCheatFinder.parseCheatText("X".repeat(250_001), "Any").isEmpty())
    }
}
