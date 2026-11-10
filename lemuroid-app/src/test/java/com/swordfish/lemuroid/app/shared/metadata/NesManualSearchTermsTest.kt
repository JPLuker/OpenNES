package com.swordfish.lemuroid.app.shared.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesManualSearchTermsTest {
    @Test
    fun removesPublisherQualifierOnlyWhenNeeded() {
        assertEquals("RoboCop", NesManualSearchTerms.fallback("RoboCop (Data East)"))
    }

    @Test
    fun removesBracketedRegionWithoutChangingTheGameName() {
        assertEquals("The Legend of Zelda", NesManualSearchTerms.fallback("The Legend of Zelda [USA]"))
    }

    @Test
    fun searchesMultipleTrailingQualifiersWithoutReorderingThem() {
        assertEquals(
            listOf("RoboCop (Data East) (USA)", "RoboCop (Data East)", "RoboCop"),
            NesManualSearchTerms.fallbacks("RoboCop (Data East) (USA) [Rev 1]"),
        )
        assertEquals(
            listOf("The Legend of Zelda (USA)", "The Legend of Zelda"),
            NesManualSearchTerms.fallbacks("The Legend of Zelda (USA) [Rev A]"),
        )
    }

    @Test
    fun preservesPlainTitlesAndShortUnknowns() {
        assertNull(NesManualSearchTerms.fallback("RoboCop"))
        assertNull(NesManualSearchTerms.fallback("X (J)"))
    }

    @Test
    fun refusesToDeleteAnythingExceptFinalQualifier() {
        assertNull(NesManualSearchTerms.fallback("RoboCop (Data East) Special Edition"))
        assertEquals("RoboCop (Data East)", NesManualSearchTerms.fallback("RoboCop (Data East) (USA)"))
    }
}
