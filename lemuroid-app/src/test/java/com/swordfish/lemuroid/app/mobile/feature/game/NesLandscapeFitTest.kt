package com.swordfish.lemuroid.app.mobile.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesLandscapeFitTest {
    @Test
    fun handheldReservesFourByThreeVideoWidth() {
        val required =
            NesLandscapeFit.minimumRequiredWidthDp(
                screenHeightDp = 400f,
                leftDialWidthDp = 160f,
                rightDialWidthDp = 160f,
                handheld = true,
            )
        assertEquals(400f * 4f / 3f + 48f + 320f * 0.72f, required, 0.01f)
    }

    @Test
    fun framedPreservesMinimumComfortWidth() {
        val required =
            NesLandscapeFit.minimumRequiredWidthDp(
                screenHeightDp = 280f,
                leftDialWidthDp = 120f,
                rightDialWidthDp = 120f,
                handheld = false,
            )
        assertEquals(640f, required, 0.01f)
    }

    @Test
    fun shortHandheldCanFitBelowOldArbitraryWidthThreshold() {
        val required =
            NesLandscapeFit.minimumRequiredWidthDp(
                screenHeightDp = 200f,
                leftDialWidthDp = 80f,
                rightDialWidthDp = 80f,
                handheld = true,
            )
        assertTrue(required < 640f)
        assertTrue(required > 400f)
    }

    @Test
    fun largerHandheldDialsRequireMoreSpace() {
        val compact =
            NesLandscapeFit.minimumRequiredWidthDp(360f, 100f, 100f, handheld = true)
        val enlarged =
            NesLandscapeFit.minimumRequiredWidthDp(360f, 200f, 200f, handheld = true)
        assertTrue(enlarged > compact)
    }
}
