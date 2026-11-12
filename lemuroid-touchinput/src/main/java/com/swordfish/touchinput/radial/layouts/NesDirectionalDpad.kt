package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesCrossForeground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Opt-in NES D-pad with independently positioned real PadKit key hitboxes.
 * Composite diagonal anchors activate both adjacent directions with one
 * thumb, while simultaneous multi-pointer face-button input is preserved.
 *
 * The default, untouched controller continues to use ControlCross instead.
 */
@Composable
fun PadKitScope.NesDirectionalDpad(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    val upId = remember { Id.Key(KeyEvent.KEYCODE_DPAD_UP) }
    val downId = remember { Id.Key(KeyEvent.KEYCODE_DPAD_DOWN) }
    val leftId = remember { Id.Key(KeyEvent.KEYCODE_DPAD_LEFT) }
    val rightId = remember { Id.Key(KeyEvent.KEYCODE_DPAD_RIGHT) }

    val up = nesDpadAnchor(upId, 0f, -0.72f, settings.dpadUp)
    val down = nesDpadAnchor(downId, 0f, 0.72f, settings.dpadDown)
    val left = nesDpadAnchor(leftId, -0.72f, 0f, settings.dpadLeft)
    val right = nesDpadAnchor(rightId, 0.72f, 0f, settings.dpadRight)
    val primaryAnchors = remember(up, down, left, right) { persistentListOf(up, down, left, right) }
    val composites =
        remember(up, down, left, right) {
            persistentListOf(
                nesDpadDiagonal(up, right),
                nesDpadDiagonal(right, down),
                nesDpadDiagonal(down, left),
                nesDpadDiagonal(left, up),
            )
        }
    val directionState =
        remember(upId, downId, leftId, rightId) {
            derivedStateOf {
                val keys = inputState.value
                val x =
                    (if (keys.getDigitalKey(rightId)) 1f else 0f) -
                        (if (keys.getDigitalKey(leftId)) 1f else 0f)
                // PadKit's foreground uses positive Y for up.
                val y =
                    (if (keys.getDigitalKey(upId)) 1f else 0f) -
                        (if (keys.getDigitalKey(downId)) 1f else 0f)
                Offset(x, y)
            }
        }

    LemuroidControlFaceButtons(
        modifier = modifier,
        primaryAnchors = primaryAnchors,
        compositeAnchors = composites,
        trackPointers = true,
        applyPadding = false,
        background = { NesCrossForeground(directionState = directionState) },
        showCompositeForeground = false,
        idsForegrounds =
            persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                upId to { NesDpadHitboxBounds(NesEditableControl.DPAD_UP) },
                downId to { NesDpadHitboxBounds(NesEditableControl.DPAD_DOWN) },
                leftId to { NesDpadHitboxBounds(NesEditableControl.DPAD_LEFT) },
                rightId to { NesDpadHitboxBounds(NesEditableControl.DPAD_RIGHT) },
            ),
    )
}

@Composable
private fun NesDpadHitboxBounds(control: NesEditableControl) {
    val report = LocalNesControlBoundsReporter.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { report(control, it.boundsInRoot()) },
    )
}

/** Clamp each hitbox within its visible cardinal arm, not the whole viewport. */
internal fun nesDpadAnchor(
    id: Id.Key,
    baseX: Float,
    baseY: Float,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
): Anchor<Id.Key> {
    val dx = (geometry.offsetX.coerceIn(-1f, 1f) * 0.16f + geometry.dragX.coerceIn(-32f, 32f) / 125f)
        .coerceIn(-0.30f, 0.30f)
    val dy = (geometry.offsetY.coerceIn(-1f, 1f) * 0.16f + geometry.dragY.coerceIn(-32f, 32f) / 125f)
        .coerceIn(-0.30f, 0.30f)
    val size = 0.38f * geometry.size.coerceIn(0.75f, 1.25f)
    return Anchor(
        position = Offset((baseX + dx).coerceIn(-1f, 1f), (baseY + dy).coerceIn(-1f, 1f)),
        buttons = persistentSetOf(id),
        size = size,
    )
}

private fun nesDpadDiagonal(
    first: Anchor<Id.Key>,
    second: Anchor<Id.Key>,
): Anchor<Id.Key> {
    val halfway = (first.position + second.position) * 0.75f
    return Anchor(
        position = Offset(halfway.x.coerceIn(-1f, 1f), halfway.y.coerceIn(-1f, 1f)),
        buttons = persistentSetOf(first.buttons.first(), second.buttons.first()),
        size = 0.20f,
    )
}
