package com.swordfish.touchinput.radial.layouts

import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class NesFaceButtonLayoutTest {
    private val untouched = TouchControllerSettingsManager.ButtonGeometry()
    private val dial = 400f
    private val density = 2f

    private fun a(geometry: TouchControllerSettingsManager.ButtonGeometry = untouched) =
        NesFaceButtonLayout.button(dial, density, NesFaceButtonLayout.A_BASE_X, geometry, null)

    private fun b(geometry: TouchControllerSettingsManager.ButtonGeometry = untouched) =
        NesFaceButtonLayout.button(dial, density, NesFaceButtonLayout.B_BASE_X, geometry, null)

    @Test
    fun untouchedButtonsSitExactlyWhereThePairedDialPaintsThem() {
        // PadKit: center + anchor * (dial - button) / 2.
        val diameter = dial * 0.46f
        val travel = (dial - diameter) / 2f
        assertEquals(dial / 2f + 0.92f * travel, a().centerX, 0.01f)
        assertEquals(dial / 2f - 0.92f * travel, b().centerX, 0.01f)
        assertEquals(dial / 2f, a().centerY, 0.01f)
        assertEquals(diameter, a().diameter, 0.01f)
        assertFalse(NesFaceButtonLayout.isIndependent(untouched, untouched))
    }

    @Test
    fun aDragMovesTheButtonByThatManyRealPixelsWithNoDialLimit() {
        // The old layout divided by 96 and clipped at the dial edge, so a
        // 300 dp drag moved a button only as far as the dial rim.
        val moved = a(untouched.copy(dragX = -300f, dragY = 500f))
        assertEquals(a().centerX - 300f * density, moved.centerX, 0.01f)
        assertEquals(a().centerY + 500f * density, moved.centerY, 0.01f)
        assertTrue(moved.centerY > dial)
        assertTrue(NesFaceButtonLayout.isIndependent(untouched.copy(dragY = 500f), untouched))
    }

    @Test
    fun movingOneButtonLeavesTheOtherInPlace() {
        val movedA = a(untouched.copy(dragX = 80f, dragY = -40f))
        val arrangement = NesFaceButtonLayout.arrange(movedA, b())
        val placedB =
            when (arrangement) {
                is NesFaceButtonLayout.Separate -> arrangement.b
                is NesFaceButtonLayout.Cluster -> clusterButton(arrangement, first = false)
            }
        assertEquals(b().centerX, placedB.centerX, 0.5f)
        assertEquals(b().centerY, placedB.centerY, 0.5f)
    }

    @Test
    fun nearbyButtonsShareOneControlThatPaintsThemAtTheRequestedPixels() {
        val movedA = a(untouched.copy(dragX = 6f, dragY = -30f))
        val arrangement = NesFaceButtonLayout.arrange(movedA, b())
        assertTrue(arrangement is NesFaceButtonLayout.Cluster)
        val cluster = arrangement as NesFaceButtonLayout.Cluster
        val paintedA = clusterButton(cluster, first = true)
        val paintedB = clusterButton(cluster, first = false)
        assertEquals(movedA.centerX, paintedA.centerX, 0.5f)
        assertEquals(movedA.centerY, paintedA.centerY, 0.5f)
        assertEquals(movedA.diameter, paintedA.diameter, 0.5f)
        assertEquals(b().centerX, paintedB.centerX, 0.5f)
        assertEquals(b().centerY, paintedB.centerY, 0.5f)
    }

    @Test
    fun clusteredDiscsStayInsideTheTouchCircleInEveryDirection() {
        // PadKit ignores touches outside the circle inscribed in a control.
        for (dragX in listOf(-70f, -30f, 0f, 20f)) {
            for (dragY in listOf(-60f, -25f, 25f, 60f)) {
                val movedA = a(untouched.copy(dragX = dragX, dragY = dragY, size = 1.25f))
                val arrangement = NesFaceButtonLayout.arrange(movedA, b())
                if (arrangement !is NesFaceButtonLayout.Cluster) continue
                val centerX = arrangement.left + arrangement.side / 2f
                val centerY = arrangement.top + arrangement.side / 2f
                for (button in listOf(movedA, b())) {
                    val reach = hypot(button.centerX - centerX, button.centerY - centerY) + button.diameter / 2f
                    assertTrue("disc leaves touch circle at $dragX,$dragY", reach <= arrangement.side / 2f + 0.5f)
                }
            }
        }
    }

    @Test
    fun combinedPressZonesStayBesideTheLineBetweenTheButtons() {
        // Untouched: A right of B, zones above and below as in the dial.
        val level = NesFaceButtonLayout.arrange(a(untouched.copy(dragY = 0.001f)), b()) as NesFaceButtonLayout.Cluster
        assertEquals(0f, level.compositeX, 0.01f)
        assertEquals(0.85f, level.compositeY, 0.01f)
        // A stacked above B: zones move to the left and right.
        val stacked =
            NesFaceButtonLayout.arrange(
                a(untouched.copy(dragX = -50f, dragY = -45f)),
                b(untouched.copy(dragX = 50f, dragY = 45f)),
            ) as NesFaceButtonLayout.Cluster
        assertEquals(0.85f, hypot(stacked.compositeX, stacked.compositeY), 0.01f)
        assertTrue(kotlin.math.abs(stacked.compositeX) > 0.8f)
    }

    @Test
    fun distantButtonsBecomeSeparateTargetsWithNoLiveAreaBetweenThem() {
        val farA = a(untouched.copy(dragX = 0f, dragY = -200f))
        val arrangement = NesFaceButtonLayout.arrange(farA, b())
        assertTrue(arrangement is NesFaceButtonLayout.Separate)
        val separate = arrangement as NesFaceButtonLayout.Separate
        assertEquals(farA, separate.a)
        assertEquals(b(), separate.b)
    }

    @Test
    fun savedPositionsAreNeverPlacedOutsideTheWindow() {
        // Dial at (600, 900) in a 1080 x 1920 window.
        val window = NesFaceButtonLayout.Bounds(left = -600f, top = -900f, right = 480f, bottom = 1020f)
        val thrown =
            NesFaceButtonLayout.button(
                dial,
                density,
                NesFaceButtonLayout.A_BASE_X,
                untouched.copy(dragX = 4000f, dragY = -4000f),
                window,
            )
        assertEquals(480f - thrown.diameter / 2f, thrown.centerX, 0.01f)
        assertEquals(-900f + thrown.diameter / 2f, thrown.centerY, 0.01f)
        // A position already inside the window is not altered.
        val inside =
            NesFaceButtonLayout.button(
                dial,
                density,
                NesFaceButtonLayout.A_BASE_X,
                untouched.copy(dragX = -100f, dragY = -100f),
                window,
            )
        assertEquals(a().centerX - 200f, inside.centerX, 0.01f)
    }

    @Test
    fun invalidSavedValuesFallBackToTheUntouchedLayout() {
        val broken =
            TouchControllerSettingsManager.ButtonGeometry(
                size = Float.NaN,
                offsetX = Float.POSITIVE_INFINITY,
                offsetY = Float.NaN,
                dragX = Float.NaN,
                dragY = Float.NEGATIVE_INFINITY,
            )
        assertEquals(a(), a(broken))
        assertFalse(NesFaceButtonLayout.isIndependent(broken, untouched))
        assertEquals(0f, NesFaceButtonLayout.button(Float.NaN, density, 0.92f, untouched, null).diameter, 0f)
    }

    /** Reproduce PadKit's anchor placement inside a cluster control. */
    private fun clusterButton(
        cluster: NesFaceButtonLayout.Cluster,
        first: Boolean,
    ): NesFaceButtonLayout.Button {
        val size = if (first) cluster.aSize else cluster.bSize
        val anchorX = if (first) cluster.aAnchorX else cluster.bAnchorX
        val anchorY = if (first) cluster.aAnchorY else cluster.bAnchorY
        val diameter = cluster.side * size
        val travel = (cluster.side - diameter) / 2f
        return NesFaceButtonLayout.Button(
            centerX = cluster.left + cluster.side / 2f + anchorX * travel,
            centerY = cluster.top + cluster.side / 2f + anchorY * travel,
            diameter = diameter,
        )
    }
}
