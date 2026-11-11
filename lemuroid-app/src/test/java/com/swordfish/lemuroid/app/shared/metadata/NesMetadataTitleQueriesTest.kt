package com.swordfish.lemuroid.app.shared.metadata

import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NesMetadataTitleQueriesTest {
    @Test
    fun localCatalogueSearchHandlesReorderedAndBundledTitles() {
        assertTrue(NesGameTitle.searchVariants("Simpsons, The (USA)").contains("The Simpsons"))
        assertTrue(
            NesGameTitle.searchVariants("2-in-1 Super Mario Bros. + Duck Hunt")
                .contains("Super Mario Bros. / Duck Hunt"),
        )
        assertTrue(NesGameTitle.isCompilation("Super Mario Bros. + Duck Hunt"))
        assertEquals(
            NesGameTitle.canonicalReleaseName("Super Mario Bros. + Duck Hunt"),
            NesGameTitle.canonicalReleaseName("Super Mario Bros. / Duck Hunt"),
        )
        assertFalse(
            NesGameTitle.canonicalReleaseName("Super Mario Bros. + Duck Hunt") ==
                NesGameTitle.canonicalReleaseName("Super Mario Bros."),
        )
    }

    @Test
    fun reversedArticleMatchesNormalTitle() {
        assertTrue(NesMetadataTitleQueries.equivalent("Simpsons, The", "The Simpsons"))
        assertTrue(NesMetadataTitleQueries.equivalent("The Simpsons", "Simpsons, The"))
        assertTrue(NesMetadataTitleQueries.alternateQueries("The Simpsons").contains("Simpsons, The"))
    }

    @Test
    fun twoInOneCartridgeUsesEntireReleaseName() {
        val original = "2-in-1 Super Mario Bros. + Duck Hunt"
        assertTrue(NesMetadataTitleQueries.isCompilation(original))
        assertTrue(NesMetadataTitleQueries.equivalent(original, "Super Mario Bros. / Duck Hunt"))
        assertTrue(NesMetadataTitleQueries.compatibleCandidate(original, "Super Mario Bros. & Duck Hunt"))
        assertFalse(NesMetadataTitleQueries.compatibleCandidate(original, "Super Mario Bros."))
        assertFalse(NesMetadataTitleQueries.compatibleCandidate(original, "Duck Hunt"))
        assertTrue(NesMetadataTitleQueries.alternateQueries(original).contains("Super Mario Bros. / Duck Hunt"))
    }

    @Test
    fun suffixesPreserveIdentityAndPreventDifferentCombos() {
        assertTrue(
            NesMetadataTitleQueries.equivalent(
                "Super Mario Bros. + Duck Hunt (USA)",
                "Super Mario Bros. / Duck Hunt",
            ),
        )
        assertFalse(
            NesMetadataTitleQueries.compatibleCandidate(
                "Super Mario Bros. + Duck Hunt",
                "Super Mario Bros. / World Class Track Meet",
            ),
        )
        assertEquals("thesimpsons", NesMetadataTitleQueries.normalized("Simpsons, The (USA)"))
    }
}
