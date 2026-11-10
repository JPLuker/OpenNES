package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesCoverResolutionCacheTest {
    @Test
    fun reuseAConfirmedFallbackAcrossShelfAndDetails() {
        val primary = "https://images.example.com/old-cover.png"
        val fallback = "https://images.example.com/rom-filename.png"
        assertNull(NesCoverResolutionCache.preferred(primary, fallback))
        NesCoverResolutionCache.recordSuccess(primary, fallback, fallback)
        assertEquals(fallback, NesCoverResolutionCache.preferred(primary, fallback))
    }

    @Test
    fun coverChangesCannotUseStaleSuccessfulArtwork() {
        val original = "https://images.example.com/catalogue.png"
        val alternate = "https://images.example.com/alternate.png"
        val edited = "content://covers/user-selected.png"
        NesCoverResolutionCache.recordSuccess(original, alternate, alternate)
        assertNull(NesCoverResolutionCache.preferred(edited, alternate))
        assertNull(NesCoverResolutionCache.preferred(original, edited))
    }

    @Test
    fun unknownAndUnrelatedUrlsCannotBecomeKnownGood() {
        val primary = "https://example.org/primary.png"
        val alternate = "https://example.org/fallback.png"
        NesCoverResolutionCache.recordSuccess(primary, alternate, "https://example.org/another.png")
        assertNull(NesCoverResolutionCache.preferred(primary, alternate))
        NesCoverResolutionCache.recordSuccess(primary, null, primary)
        assertNull(NesCoverResolutionCache.preferred(primary, null))
    }
}
