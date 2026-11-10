package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesCrossForeground
import com.swordfish.touchinput.radial.ui.NesFaceButtonForeground
import com.swordfish.touchinput.radial.ui.NesPillButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Only the portrait secondary controls receive the enlarged touch targets.
 * Landscape retains its existing arrangement until M4.
 */
val LocalNesPortraitLayout = compositionLocalOf { false }
val LocalNesLandscapeMinimal = compositionLocalOf { false }
val LocalNesLandscapeHandheld = compositionLocalOf { false }

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
            NesSecondarySelect(settings)
            if (!LocalNesLandscapeHandheld.current) SecondaryButtonMenuPlaceholder(settings)
        },
    )
}

@Composable
fun PadKitScope.NESRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    val aId = Id.Key(KeyEvent.KEYCODE_BUTTON_A)
    val bId = Id.Key(KeyEvent.KEYCODE_BUTTON_B)
    // PadKit anchors control BOTH the rendered buttons and their hit targets.
    // Avoid graphical offsets here: those would visually move buttons while
    // leaving the original invisible input regions behind.
    val primaryAnchors =
        remember(settings.aButton, settings.bButton) {
            persistentListOf(
                nesFaceAnchor(aId, 0.92f, settings.aButton),
                nesFaceAnchor(bId, -0.92f, settings.bButton),
            )
        }
    val compositeAnchors =
        remember(aId, bId) {
            val both = persistentSetOf(aId, bId)
            persistentListOf(
                Anchor(Offset(0f, -0.85f), both, 0.1f),
                Anchor(Offset(0f, 0.85f), both, 0.1f),
            )
        }

    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlFaceButtons(
                primaryAnchors = primaryAnchors,
                compositeAnchors = compositeAnchors,
                trackPointers = true,
                applyPadding = false,
                background = {},
                showCompositeForeground = false,
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        aId to { NesFaceButtonForeground(pressed = it, label = "A") },
                        bId to { NesFaceButtonForeground(pressed = it, label = "B") },
                    ),
            )
        },
        secondaryDials = {
            NesSecondaryStart(settings)
            NesSecondaryMenu(settings)
        },
    )
}

/**
 * Position and size are in PadKit's normalized (-1..1) dial coordinates.
 * Defaults approximate the existing paired layout, while independently
 * adjusting the anchor changes the painted disc and real touch hitbox alike.
 */
private fun nesFaceAnchor(
    id: Id.Key,
    baseX: Float,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
): Anchor<Id.Key> {
    val x = (baseX + geometry.offsetX.coerceIn(-1f, 1f) * 0.30f).coerceIn(-1f, 1f)
    val y = geometry.offsetY.coerceIn(-1f, 1f) * 0.40f
    val size = (0.46f * geometry.size.coerceIn(0.75f, 1.25f)).coerceIn(0.34f, 0.58f)
    return Anchor(
        position = Offset(x, y),
        buttons = persistentSetOf(id),
        size = size,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondarySelect(settings: TouchControllerSettingsManager.Settings) {
    val handheld = LocalNesLandscapeHandheld.current
    val scale = if (handheld) 0.90f else if (LocalNesPortraitLayout.current) 1.15f else 1.05f
    val geometry = settings.selectButton
    val buttonScale =
        geometry.size.coerceIn(
            TouchControllerSettingsManager.MIN_BUTTON_SIZE,
            TouchControllerSettingsManager.MAX_BUTTON_SIZE,
        )
    ControlButton(
        modifier =
            Modifier
                .radialPosition(if (handheld) 60f else 120f)
                .radialScale(scale * buttonScale)
                .offset(
                    x = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X * geometry.offsetX.coerceIn(-1f, 1f)).dp,
                    y = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y * geometry.offsetY.coerceIn(-1f, 1f)).dp,
                ),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        foreground = { NesPillButtonForeground(pressed = it, label = "SELECT", expanded = true) },
        background = {},
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondaryStart(settings: TouchControllerSettingsManager.Settings) {
    val handheld = LocalNesLandscapeHandheld.current
    val scale = if (handheld) 0.90f else if (LocalNesPortraitLayout.current) 1.15f else 1.05f
    val geometry = settings.startButton
    val buttonScale =
        geometry.size.coerceIn(
            TouchControllerSettingsManager.MIN_BUTTON_SIZE,
            TouchControllerSettingsManager.MAX_BUTTON_SIZE,
        )
    ControlButton(
        modifier =
            Modifier
                .radialPosition(if (handheld) 120f else 60f)
                .radialScale(scale * buttonScale)
                .offset(
                    x = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X * geometry.offsetX.coerceIn(-1f, 1f)).dp,
                    y = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y * geometry.offsetY.coerceIn(-1f, 1f)).dp,
                ),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        foreground = { NesPillButtonForeground(pressed = it, label = "START", expanded = true) },
        background = {},
    )
}

/** Dedicated NES Menu pill in both orientations; 500ms hold is unchanged. */
context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondaryMenu(settings: TouchControllerSettingsManager.Settings) {
    val handheld = LocalNesLandscapeHandheld.current
    val geometry = settings.menuButton
    val buttonScale =
        geometry.size.coerceIn(
            TouchControllerSettingsManager.MIN_BUTTON_SIZE,
            TouchControllerSettingsManager.MAX_BUTTON_SIZE,
        )
    ControlButton(
        modifier =
            Modifier
                .radialPosition(
                    (if (handheld) -120f else -60f) +
                        2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
                )
                .radialScale(
                    (if (handheld) 0.90f else if (LocalNesPortraitLayout.current) 1.15f else 1.05f) *
                        buttonScale,
                )
                .offset(
                    x = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X * geometry.offsetX.coerceIn(-1f, 1f)).dp,
                    y = (TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y * geometry.offsetY.coerceIn(-1f, 1f)).dp,
                ),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        foreground = { NesPillButtonForeground(pressed = it, label = "MENU", expanded = true) },
        background = {},
    )
}
