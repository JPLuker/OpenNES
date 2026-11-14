package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesBoxArtNameGuessTest {
    @Test
    fun `plain titles get the USA tag`() {
        assertEquals("Excitebike (USA)", NesBoxArtNameGuess.usaStem("Excitebike"))
        assertEquals("Kirby's Adventure (USA)", NesBoxArtNameGuess.usaStem("Kirby's Adventure Rev PRG1"))
    }

    @Test
    fun `a leading The moves behind the name`() {
        assertEquals("Legend of Zelda, The (USA)", NesBoxArtNameGuess.usaStem("The Legend of Zelda"))
    }

    @Test
    fun `subtitles and forbidden characters follow the catalogue`() {
        assertEquals(
            "Tom _ Jerry - The Ultimate Game of Cat and Mouse! (USA)",
            NesBoxArtNameGuess.usaStem("Tom & Jerry: The Ultimate Game of Cat and Mouse!"),
        )
        assertEquals("Mike Tyson's Punch-Out!! (USA)", NesBoxArtNameGuess.usaStem("Mike Tyson's Punch-Out!!"))
        assertEquals(
            "Zelda II - The Adventure of Link (USA)",
            NesBoxArtNameGuess.usaStem("Zelda II: The Adventure of Link (Nintendo) [!]"),
        )
    }

    @Test
    fun `nothing to go on gives nothing`() {
        assertNull(NesBoxArtNameGuess.usaStem(" (USA) "))
    }
}
