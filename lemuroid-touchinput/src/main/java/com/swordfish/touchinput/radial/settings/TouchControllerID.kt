package com.swordfish.touchinput.radial.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.layouts.NESLeft
import com.swordfish.touchinput.radial.layouts.NESRight
import gg.padkit.PadKitScope

/**
 * OpenNES has one virtual controller. New portrait and landscape skins
 * will share its underlying NES input mapping.
 */
enum class TouchControllerID {
    NES,
    ;

    class Config(
        val leftComposable: @Composable PadKitScope.(
            modifier: Modifier,
            settings: TouchControllerSettingsManager.Settings,
        ) -> Unit,
        val rightComposable: @Composable PadKitScope.(
            modifier: Modifier,
            settings: TouchControllerSettingsManager.Settings,
        ) -> Unit,
    )

    companion object {
        fun getConfig(id: TouchControllerID): Config {
            return when (id) {
                NES ->
                    Config(
                        { modifier, settings -> NESLeft(modifier, settings) },
                        { modifier, settings -> NESRight(modifier, settings) },
                    )
            }
        }
    }
}
