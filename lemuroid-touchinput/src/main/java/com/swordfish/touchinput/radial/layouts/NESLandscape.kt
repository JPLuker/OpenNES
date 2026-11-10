package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesCrossForeground
import com.swordfish.touchinput.radial.ui.NesFaceButtonForeground
import com.swordfish.touchinput.radial.ui.NesPillButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Real, independent landscape controls. The inherited radial layout shrank
 * the primary dial to fit secondary buttons around its circumference, leaving
 * most of a phone's left and right grip areas empty. These Box layouts give
 * the D-pad and A/B their own large hit regions, with secondary buttons placed
 * separately above/below. Every visible control is an actual PadKit handler.
 *
 * Used in BOTH Framed and NES Handheld presentations. Saved portrait geometry
 * and the existing overlay style remain untouched.
 */
@Composable
fun PadKitScope.NESLandscapeLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    onPrimaryBounds: ((Rect) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideWidth = maxWidth
        val sideHeight = maxHeight
        val dialSize = landscapeDialSize(sideWidth, sideHeight, settings.leftScale ?: settings.scale)
        val x =
            NesLandscapeDragLimits.horizontalTravelDp(sideWidth.value, dialSize.value).dp *
                settings.leftOffsetX.coerceIn(-1f, 1f)
        val y =
            NesLandscapeDragLimits.verticalTravelDp(sideHeight.value, dialSize.value).dp *
                settings.leftOffsetY.coerceIn(-1f, 1f)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LemuroidControlCross(
                modifier =
                    Modifier
                        .size(dialSize)
                        .offset(x = x, y = y)
                        .onGloballyPositioned { onPrimaryBounds?.invoke(it.boundsInRoot()) },
                id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD),
                allowDiagonals = true,
                expandedTouchArea = true,
                background = {},
                foreground = { NesCrossForeground(directionState = it) },
            )
            NESLandscapeSecondaryButton(
                modifier = Modifier.align(Alignment.Center),
                keyCode = KeyEvent.KEYCODE_BUTTON_SELECT,
                label = "SELECT",
                geometry = settings.selectButton,
                sideWidth = sideWidth,
                sideHeight = sideHeight,
                above = true,
                clusterOffsetX = x,
                clusterOffsetY = y,
            )
        }
    }
}

@Composable
fun PadKitScope.NESLandscapeRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    onPrimaryBounds: ((Rect) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideWidth = maxWidth
        val sideHeight = maxHeight
        val dialSize = landscapeDialSize(sideWidth, sideHeight, settings.rightScale ?: settings.scale)
        val x =
            NesLandscapeDragLimits.horizontalTravelDp(sideWidth.value, dialSize.value).dp *
                settings.rightOffsetX.coerceIn(-1f, 1f)
        val y =
            NesLandscapeDragLimits.verticalTravelDp(sideHeight.value, dialSize.value).dp *
                settings.rightOffsetY.coerceIn(-1f, 1f)
        val aId = remember { Id.Key(KeyEvent.KEYCODE_BUTTON_A) }
        val bId = remember { Id.Key(KeyEvent.KEYCODE_BUTTON_B) }
        val primaryAnchors =
            remember(settings.aButton, settings.bButton) {
                persistentListOf(
                    nesFaceAnchor(aId, 0.92f, settings.aButton),
                    nesFaceAnchor(bId, -0.92f, settings.bButton),
                )
            }
        val compositeAnchors =
            remember {
                val both = persistentSetOf(aId, bId)
                persistentListOf(
                    Anchor(androidx.compose.ui.geometry.Offset(0f, -0.85f), both, 0.1f),
                    Anchor(androidx.compose.ui.geometry.Offset(0f, 0.85f), both, 0.1f),
                )
            }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LemuroidControlFaceButtons(
                modifier =
                    Modifier
                        .size(dialSize)
                        .offset(x = x, y = y)
                        .onGloballyPositioned { onPrimaryBounds?.invoke(it.boundsInRoot()) },
                primaryAnchors = primaryAnchors,
                compositeAnchors = compositeAnchors,
                trackPointers = true,
                applyPadding = false,
                background = {},
                showCompositeForeground = false,
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        aId to { pressed -> NesFaceButtonForeground(pressed = pressed, label = "A") },
                        bId to { pressed -> NesFaceButtonForeground(pressed = pressed, label = "B") },
                    ),
            )
            NESLandscapeSecondaryButton(
                modifier = Modifier.align(Alignment.Center),
                keyCode = KeyEvent.KEYCODE_BUTTON_START,
                label = "START",
                geometry = settings.startButton,
                sideWidth = sideWidth,
                sideHeight = sideHeight,
                above = true,
                clusterOffsetX = x,
                clusterOffsetY = y,
            )
            NESLandscapeSecondaryButton(
                modifier = Modifier.align(Alignment.Center),
                keyCode = KeyEvent.KEYCODE_BUTTON_MODE,
                label = "MENU",
                geometry = settings.menuButton,
                sideWidth = sideWidth,
                sideHeight = sideHeight,
                above = false,
                clusterOffsetX = x,
                clusterOffsetY = y,
            )
        }
    }
}

/** Map old saved dial scales onto the available grip, not PadKit's radial ring. */
private fun landscapeDialSize(
    width: Dp,
    height: Dp,
    normalizedScale: Float,
): Dp {
    val available = minOf(width * 0.90f, height * 0.57f, 228.dp)
    val multiplier = 0.80f + 0.45f * normalizedScale.coerceIn(0f, 1f)
    return minOf(available * multiplier, width * 0.96f, height * 0.72f)
}

@Composable
private fun PadKitScope.NESLandscapeSecondaryButton(
    modifier: Modifier,
    keyCode: Int,
    label: String,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
    sideWidth: Dp,
    sideHeight: Dp,
    above: Boolean,
    clusterOffsetX: Dp,
    clusterOffsetY: Dp,
) {
    val buttonScale =
        geometry.size.coerceIn(
            TouchControllerSettingsManager.MIN_BUTTON_SIZE,
            TouchControllerSettingsManager.MAX_BUTTON_SIZE,
        )
    val buttonWidth = minOf(sideWidth * 0.60f, 112.dp) * buttonScale
    val buttonHeight = 46.dp
    val centerOffset = sideHeight * (if (above) -0.34f else 0.34f)
    val offsetX =
        TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X.dp *
            geometry.offsetX.coerceIn(-1f, 1f)
    val offsetY =
        TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y.dp *
            geometry.offsetY.coerceIn(-1f, 1f)
    // Secondary controls follow their group when it is dragged, but clamp
    // individually so Start/Select/Menu remain reachable at the grip edges.
    val safeX = (sideWidth - buttonWidth).coerceAtLeast(0.dp) / 2f - 6.dp
    val safeY = (sideHeight - buttonHeight).coerceAtLeast(0.dp) / 2f - 6.dp
    val positionedX = (clusterOffsetX + offsetX).coerceIn(-safeX.coerceAtLeast(0.dp), safeX.coerceAtLeast(0.dp))
    val positionedY =
        (centerOffset + clusterOffsetY + offsetY).coerceIn(
            -safeY.coerceAtLeast(0.dp),
            safeY.coerceAtLeast(0.dp),
        )
    ControlButton(
        modifier =
            modifier
                .offset(x = positionedX, y = positionedY)
                .size(buttonWidth, buttonHeight),
        id = Id.Key(keyCode),
        background = {},
        foreground = { NesPillButtonForeground(pressed = it, label = label, expanded = true, large = true) },
    )
}
