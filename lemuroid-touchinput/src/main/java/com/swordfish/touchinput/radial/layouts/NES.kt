package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesCrossForeground
import com.swordfish.touchinput.radial.ui.NesPillButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope
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
    val report = LocalNesControlBoundsReporter.current
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            val padModifier =
                Modifier.onGloballyPositioned { report(NesEditableControl.DPAD, it.boundsInRoot()) }
            if (settings.individualDpadHitboxes || LocalNesDpadEditMode.current) {
                NesDirectionalDpad(modifier = padModifier, settings = settings)
            } else {
                LemuroidControlCross(
                    modifier = padModifier,
                    id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD),
                    allowDiagonals = true,
                    expandedTouchArea = true,
                    // Original hit regions remain unchanged until the player
                    // opts into independently adjusted directional targets.
                    background = {},
                    foreground = { NesCrossForeground(directionState = it) },
                )
            }
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
    val report = LocalNesControlBoundsReporter.current
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            NesFaceButtons(
                modifier =
                    Modifier.onGloballyPositioned {
                        report(NesEditableControl.FACE_BUTTONS, it.boundsInRoot())
                    },
                settings = settings,
            )
        },
        secondaryDials = {
            NesSecondaryStart(settings)
            NesSecondaryMenu(settings)
        },
    )
}

/**
 * Anchor for the untouched, paired A/B dial in PadKit's normalized (-1..1)
 * coordinates. Editor drags are not applied here: an anchor cannot leave its
 * dial, so dragged buttons are laid out by [NesFaceButtonLayout] instead.
 */
internal fun nesFaceAnchor(
    id: Id.Key,
    baseX: Float,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
): Anchor<Id.Key> =
    Anchor(
        position =
            Offset(
                NesFaceButtonLayout.anchorX(baseX, geometry),
                NesFaceButtonLayout.anchorY(geometry),
            ),
        buttons = persistentSetOf(id),
        size = NesFaceButtonLayout.sizeFraction(geometry),
    )

/**
 * Cancel the parent PadKit dial translation for secondary controls, leaving
 * Start/Select/Menu free to move independently with their real touch target.
 */
private fun independentSecondaryOffset(
    settings: TouchControllerSettingsManager.Settings,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
    isLeft: Boolean,
): Pair<Dp, Dp> {
    val groupX =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X *
            (if (isLeft) settings.leftOffsetX else settings.rightOffsetX) +
            (if (isLeft) settings.leftDragX else settings.rightDragX)
    val groupY =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y *
            (if (isLeft) settings.leftOffsetY else settings.rightOffsetY) +
            (if (isLeft) settings.leftDragY else settings.rightDragY)
    val x = TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X * geometry.offsetX + geometry.dragX - groupX
    val y = TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y * geometry.offsetY + geometry.dragY - groupY
    return x.dp to y.dp
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondarySelect(settings: TouchControllerSettingsManager.Settings) {
    val handheld = LocalNesLandscapeHandheld.current
    val scale =
        when {
            handheld -> 0.90f
            LocalNesPortraitLayout.current -> 1.15f
            else -> 1.05f
        }
    val geometry = settings.selectButton
    val report = LocalNesControlBoundsReporter.current
    val (buttonX, buttonY) =
        independentSecondaryOffset(
            settings,
            geometry,
            isLeft = true,
        )
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
                .offset(x = buttonX, y = buttonY)
                .onGloballyPositioned { report(NesEditableControl.SELECT, it.boundsInRoot()) },
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        foreground = { NesPillButtonForeground(pressed = it, label = "SELECT", expanded = true) },
        background = {},
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
private fun NesSecondaryStart(settings: TouchControllerSettingsManager.Settings) {
    val handheld = LocalNesLandscapeHandheld.current
    val scale =
        when {
            handheld -> 0.90f
            LocalNesPortraitLayout.current -> 1.15f
            else -> 1.05f
        }
    val geometry = settings.startButton
    val report = LocalNesControlBoundsReporter.current
    val (buttonX, buttonY) =
        independentSecondaryOffset(
            settings,
            geometry,
            isLeft = false,
        )
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
                .offset(x = buttonX, y = buttonY + if (LocalNesPortraitLayout.current) 4.dp else 0.dp)
                .onGloballyPositioned { report(NesEditableControl.START, it.boundsInRoot()) },
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
    val scale =
        when {
            handheld -> 0.90f
            LocalNesPortraitLayout.current -> 1.15f
            else -> 1.05f
        }
    val geometry = settings.menuButton
    val report = LocalNesControlBoundsReporter.current
    val (buttonX, buttonY) =
        independentSecondaryOffset(
            settings,
            geometry,
            isLeft = false,
        )
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
                .radialScale(scale * buttonScale)
                .offset(x = buttonX, y = buttonY)
                .onGloballyPositioned { report(NesEditableControl.MENU, it.boundsInRoot()) },
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        foreground = { NesPillButtonForeground(pressed = it, label = "MENU", expanded = true) },
        background = {},
    )
}
