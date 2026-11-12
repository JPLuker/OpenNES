package com.swordfish.lemuroid.app.mobile.feature.gamemenu.tilt

import com.swordfish.touchinput.radial.sensors.TiltConfiguration

/** NES tilt input has one supported mapping: tilt the phone to control the D-pad. */
internal object DpadTiltControl {
    private val configuration = TiltConfiguration.Cross(0)

    fun isAvailable(configurations: List<TiltConfiguration>): Boolean = configuration in configurations

    fun isEnabled(current: TiltConfiguration): Boolean = current == configuration

    fun configurationFor(enabled: Boolean): TiltConfiguration =
        if (enabled) configuration else TiltConfiguration.Disabled
}
