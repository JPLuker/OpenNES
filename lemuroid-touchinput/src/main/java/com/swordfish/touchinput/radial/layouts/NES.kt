package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesCrossForeground
import com.swordfish.touchinput.radial.ui.NesFaceButtonForeground
import com.swordfish.touchinput.radial.ui.NesPillButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Only the portrait secondary controls receive the enlarged touch targets.
 * Landscape retains its existing arrangement until M4.
 */
val LocalNesPortraitLayout = compositionLocalOf { false }
val LocalNesLandscapeMinimal = compositionLocalOf { false }

/** NES-only touch presentation. Keep the existing PadKit input event IDs. */
@Composable
fun PadKitScope.NESLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlCross(
                id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD),
                allowDiagonals = true,
                expandedTouchArea = true,
                // Remove the inherited square backplate, not the hit target.
                background = {},
                foreground = { NesCrossForeground(directionState = it) },
            )
        },
        secondaryDials = {
            NesSecondarySelect()
            SecondaryButtonMenuPlaceholder(settings)
        },
    )
}

@Composable
fun PadKitScope.NESRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlFaceButtons(
                // Preserve independent A/B presses and the combined A+B
                // gesture; minimize padding around the actual touch targets.
                includeComposite = true,
                trackPointers = true,
                applyPadding = false,
                background = {},
                showCompositeForeground = false,
                ids =
                    persistentListOf(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                    ),
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A) to {
                            NesFaceButtonForeground(pressed = it, label = "A")
                        },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to {
                            NesFaceButtonForeground(pressed = it, label = "B")
                        },
                    ),
            )
        },
        secondaryDials = {
            NesSecondaryStart()
            NesSecondaryMenu(settings)
        },
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondarySelect() {
    val scale = if (LocalNesPortraitLayout.current) 1.15f else 1.05f
    ControlButton(
        modifier = Modifier.radialPosition(120f).radialScale(scale),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        foreground = { NesPillButtonForeground(pressed = it, label = "SELECT", expanded = true) },
        background = {},
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondaryStart() {
    val scale = if (LocalNesPortraitLayout.current) 1.15f else 1.05f
    ControlButton(
        modifier = Modifier.radialPosition(60f).radialScale(scale),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        foreground = { NesPillButtonForeground(pressed = it, label = "START", expanded = true) },
        background = {},
    )
}

/** Dedicated NES Menu pill in both orientations; 500ms hold is unchanged. */
context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondaryMenu(settings: TouchControllerSettingsManager.Settings) {
    ControlButton(
        modifier =
            Modifier
                .radialPosition(
                    -60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
                )
                .radialScale(if (LocalNesPortraitLayout.current) 1.15f else 1.05f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        foreground = { NesPillButtonForeground(pressed = it, label = "MENU", expanded = true) },
        background = {},
    )
}
