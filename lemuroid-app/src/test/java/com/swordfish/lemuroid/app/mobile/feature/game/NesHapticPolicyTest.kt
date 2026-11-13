package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesHapticPolicyTest {
    @Test
    fun noneNeverVibrates() {
        assertNull(NesHapticPolicy.onPress(HapticFeedbackMode.NONE))
        assertNull(NesHapticPolicy.onRelease(HapticFeedbackMode.NONE))
    }

    @Test
    fun pressVibratesOnlyOnPress() {
        assertEquals(NesHapticPolicy.Effect.PRESS, NesHapticPolicy.onPress(HapticFeedbackMode.PRESS))
        assertNull(NesHapticPolicy.onRelease(HapticFeedbackMode.PRESS))
    }

    @Test
    fun pressAndReleaseVibratesBothWaysWithALighterRelease() {
        assertEquals(NesHapticPolicy.Effect.PRESS, NesHapticPolicy.onPress(HapticFeedbackMode.PRESS_RELEASE))
        assertEquals(NesHapticPolicy.Effect.RELEASE, NesHapticPolicy.onRelease(HapticFeedbackMode.PRESS_RELEASE))
    }

    @Test
    fun unknownSavedValueFallsBackToTheDefaultPressMode() {
        // Matches the Settings default, so a damaged preference never silences the pad.
        assertEquals(HapticFeedbackMode.PRESS, HapticFeedbackMode.parse("garbage"))
        assertEquals(NesHapticPolicy.Effect.PRESS, NesHapticPolicy.onPress(HapticFeedbackMode.parse("garbage")))
    }
}
