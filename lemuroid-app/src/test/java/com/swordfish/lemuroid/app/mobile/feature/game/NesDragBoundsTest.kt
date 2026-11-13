package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesDragBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class NesDragBoundsTest {
    @Test
    fun clampsMovementToVisibleViewport() {
        assertEquals(50f, drag(100f), 0.001f)
        assertEquals(-120f, drag(-300f), 0.001f)
    }

    @Test
    fun notchAndNavigationInsetsRestrictUnsafeEdges() {
        assertEquals(18f, drag(100f, safeEnd = 32f), 0.001f)
        assertEquals(-68f, drag(-300f, safeStart = 52f), 0.001f)
    }

    @Test
    fun oversizedGroupHasNoInvalidReversedClamp() {
        val actual = NesDragBounds.clamp(27f, 10f, 300f, 0f, 200f, 10f, 20f, 20f)
        assertEquals(20f, actual, 0.001f)
    }

    @Test
    fun oversizedControlCannotBeDraggedFurtherBeyondEitherSafeEdge() {
        assertEquals(-140f, NesDragBounds.clamp(-300f, 10f, 300f, 0f, 200f, 10f, 20f, 20f), 0.001f)
        assertEquals(20f, NesDragBounds.clamp(27f, 10f, 300f, 0f, 200f, 10f, 20f, 20f), 0.001f)
        assertEquals(20f, NesDragBounds.clamp(300f, 10f, 300f, 0f, 200f, 10f, 20f, 20f), 0.001f)
    }

    @Test
    fun invalidMeasurementsNeverPropagateNaN() {
        val actual =
            NesDragBounds.clamp(
                Float.NaN,
                0f,
                40f,
                0f,
                400f,
                10f,
                0f,
                0f,
            )
        assertEquals(0f, actual, 0.001f)
    }

    private fun drag(
        delta: Float,
        safeStart: Float = 0f,
        safeEnd: Float = 0f,
    ): Float = NesDragBounds.clamp(delta, 130f, 240f, 0f, 300f, 10f, safeStart, safeEnd)
}
