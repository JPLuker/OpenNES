package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_CROSS
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_DISABLED
import com.swordfish.touchinput.radial.settings.TouchControllerID

/** NES controls are the only on-screen controller provided by OpenNES. */
object ControllerConfigs {
    val NES =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NES,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )
}
