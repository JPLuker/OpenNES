package com.swordfish.lemuroid.app.shared.covers

import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NesFixMatchCoverTest {
    private fun entry(
        name: String,
        system: String = "nes",
        thumbnail: String? = "https://thumbnails.libretro.com/nes/$name.png",
    ) = GameMetadata(name = name, system = system, romName = null, developer = null, thumbnail = thumbnail)

    private val zelda = entry("Legend of Zelda, The (USA)")
    private val zelda2 = entry("Zelda II - The Adventure of Link (USA)")

    @Test
    fun correctingZeldaToZeldaTwoSelectsTheZeldaTwoCover() {
        // The reported case: the match changed but the Zelda cover stayed.
        val cover = NesFixMatchCover.choose("Zelda II: The Adventure of Link", listOf(zelda, zelda2))
        assertEquals(zelda2.thumbnail, cover)
    }

    @Test
    fun catalogueArticleOrderAndRegionTagsAreIgnored() {
        assertEquals(zelda.thumbnail, NesFixMatchCover.choose("The Legend of Zelda", listOf(zelda2, zelda)))
    }

    @Test
    fun aSimilarButDifferentTitleNeverReplacesTheCover() {
        assertNull(NesFixMatchCover.choose("Zelda II", listOf(zelda, zelda2)))
        assertNull(NesFixMatchCover.choose("Mega Man", listOf(entry("Mega Man 2 (USA)"), entry("Mega Man 3 (USA)"))))
        assertNull(NesFixMatchCover.choose("  ", listOf(zelda)))
    }

    @Test
    fun meaningfulParenthesizedSubtitlesAreNotDiscarded() {
        assertNull(NesFixMatchCover.choose("Batman (Return of the Joker)", listOf(entry("Batman (USA)"))))
        assertNull(NesFixMatchCover.choose("Batman", listOf(entry("Batman (Return of the Joker) (USA)"))))
        assertEquals(
            "https://thumbnails.libretro.com/nes/Batman (Return of the Joker) (USA).png",
            NesFixMatchCover.choose(
                "Batman (Return of the Joker)",
                listOf(entry("Batman (USA)"), entry("Batman (Return of the Joker) (USA)")),
            ),
        )
    }

    @Test
    fun plainUsaReleaseIsPreferredOverOtherRegionsAndRevisions() {
        val cover =
            NesFixMatchCover.choose(
                "Super Mario Bros. 3",
                listOf(
                    entry("Super Mario Bros. 3 (Japan)"),
                    entry("Super Mario Bros. 3 (USA) (Rev 1)"),
                    entry("Super Mario Bros. 3 (Europe)"),
                    entry("Super Mario Bros. 3 (USA)"),
                ),
            )
        assertEquals("https://thumbnails.libretro.com/nes/Super Mario Bros. 3 (USA).png", cover)
    }

    @Test
    fun otherSystemsAndEntriesWithoutArtAreIgnored() {
        assertNull(
            NesFixMatchCover.choose(
                "Tetris",
                listOf(entry("Tetris (USA)", system = "gb"), entry("Tetris (USA)", thumbnail = null)),
            ),
        )
    }

    @Test
    fun searchTermsReachCataloguePunctuationAndReversedArticles() {
        val terms = NesFixMatchCover.searchTerms("Zelda II: The Adventure of Link")
        // The catalogue stores "Zelda II - The Adventure of Link".
        assertTrue(terms.contains("The Adventure of Link"))
        assertTrue(terms.contains("Zelda II"))
        // The catalogue stores "Legend of Zelda, The".
        assertTrue(NesFixMatchCover.searchTerms("The Legend of Zelda").contains("Legend of Zelda"))
        assertTrue(NesFixMatchCover.searchTerms("Q").isEmpty())
    }
}
