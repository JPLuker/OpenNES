package com.swordfish.lemuroid.app.mobile.feature.gamemenu.tilt

import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadTiltControlTest {
    @Test
    fun onlyShowsSwitchWhenNesDpadTiltIsSupported() {
        assertTrue(DpadTiltControl.isAvailable(listOf(TiltConfiguration.Disabled, TiltConfiguration.Cross(0))))
        assertFalse(DpadTiltControl.isAvailable(listOf(TiltConfiguration.Disabled)))
        assertFalse(DpadTiltControl.isAvailable(listOf(TiltConfiguration.Cross(1))))
    }

    @Test
    fun switchRoundTripsBetweenDisabledAndDpad() {
        assertFalse(DpadTiltControl.isEnabled(TiltConfiguration.Disabled))
        assertTrue(DpadTiltControl.isEnabled(TiltConfiguration.Cross(0)))
        assertFalse(DpadTiltControl.isEnabled(TiltConfiguration.Cross(1)))
        assertEquals(TiltConfiguration.Cross(0), DpadTiltControl.configurationFor(true))
        assertEquals(TiltConfiguration.Disabled, DpadTiltControl.configurationFor(false))
    }
}
