package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesLandscapeDragLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NesLandscapeFitTest {
    @Test
    fun bothSidePanelsKeepFourByThreeVideoCentered() {
        val width = 952f
        val height = 427f
        val grip = NesLandscapeFit.sideWidthDp(width, height)
        val gameLeft = grip
        val gameRight = width - grip
        assertEquals(width / 2f, (gameLeft + gameRight) / 2f, 0.01f)
        assertEquals(height * 4f / 3f, gameRight - gameLeft, 0.01f)
    }

    @Test
    fun typicalWidePhoneProvidesLargeGrips() {
        assertTrue(NesLandscapeFit.canShowSideControls(952f, 427f))
        assertTrue(NesLandscapeFit.sideWidthDp(952f, 427f) > 180f)
    }

    @Test
    fun narrowLandscapeFallsBackToOverlayWithoutBreakingVideo() {
        assertFalse(NesLandscapeFit.canShowSideControls(768f, 432f))
        assertEquals(96f, NesLandscapeFit.sideWidthDp(768f, 432f), 0.01f)
    }

    @Test
    fun fullLandscapeGripTravelExceedsOld72DpDragLimit() {
        val travel = NesLandscapeDragLimits.verticalTravelDp(sideHeightDp = 427f, dialHeightDp = 180f)
        assertTrue(travel > 100f)
        assertEquals(113.5f, travel, 0.01f)
    }

    @Test
    fun dragRangeNeverMovesEntireDialOffScreen() {
        assertEquals(5f, NesLandscapeDragLimits.horizontalTravelDp(210f, 180f), 0.01f)
        assertEquals(0f, NesLandscapeDragLimits.horizontalTravelDp(120f, 180f), 0f)
        assertEquals(0f, NesLandscapeDragLimits.verticalTravelDp(160f, 200f), 0f)
    }

    @Test
    fun invalidAndPortraitGeometryNeverEnablesSidePanels() {
        assertFalse(NesLandscapeFit.canShowSideControls(400f, 800f))
        assertFalse(NesLandscapeFit.canShowSideControls(0f, 400f))
        assertFalse(NesLandscapeFit.canShowSideControls(Float.NaN, 400f))
        assertEquals(0f, NesLandscapeFit.sideWidthDp(400f, 800f), 0f)
    }
}
