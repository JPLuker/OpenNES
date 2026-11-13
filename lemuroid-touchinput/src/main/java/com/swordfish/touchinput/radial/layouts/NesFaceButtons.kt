package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.NesFaceButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.math.roundToInt

/**
 * The A and B buttons for every NES layout (portrait, overlay and landscape).
 *
 * Untouched layouts keep the original paired dial. Once A or B has been
 * dragged in the editor, each button is placed at real pixels so its painted
 * disc and its touch target move together, anywhere on the screen.
 *
 * [modifier] positions the original dial; dragged buttons are measured from it.
 */
@Composable
fun PadKitScope.NesFaceButtons(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    val aId = remember { Id.Key(KeyEvent.KEYCODE_BUTTON_A) }
    val bId = remember { Id.Key(KeyEvent.KEYCODE_BUTTON_B) }

    if (!NesFaceButtonLayout.isIndependent(settings.aButton, settings.bButton)) {
        val primaryAnchors =
            remember(settings.aButton, settings.bButton) {
                persistentListOf(
                    nesFaceAnchor(aId, NesFaceButtonLayout.A_BASE_X, settings.aButton),
                    nesFaceAnchor(bId, NesFaceButtonLayout.B_BASE_X, settings.bButton),
                )
            }
        val compositeAnchors =
            remember {
                val both = persistentSetOf(aId, bId)
                persistentListOf(
                    Anchor(Offset(0f, -0.85f), both, 0.1f),
                    Anchor(Offset(0f, 0.85f), both, 0.1f),
                )
            }
        NesFaceButtonCluster(
            modifier = modifier,
            aId = aId,
            bId = bId,
            primaryAnchors = primaryAnchors,
            compositeAnchors = compositeAnchors,
        )
        return
    }

    val density = LocalDensity.current.density
    val dialOrigin = remember { mutableStateOf<Offset?>(null) }
    val rootSize = remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(
        modifier =
            modifier
                .aspectRatio(1f)
                .onGloballyPositioned {
                    dialOrigin.value = it.positionInRoot()
                    rootSize.value = it.findRootCoordinates().size
                },
    ) {
        val dialPx = minOf(constraints.maxWidth, constraints.maxHeight)
        if (dialPx <= 0 || dialPx == Constraints.Infinity) return@BoxWithConstraints

        // Never leave a button outside the window, whatever was saved.
        val origin = dialOrigin.value
        val root = rootSize.value
        val bounds =
            if (origin != null && root.width > 0 && root.height > 0) {
                NesFaceButtonLayout.Bounds(
                    left = -origin.x,
                    top = -origin.y,
                    right = root.width - origin.x,
                    bottom = root.height - origin.y,
                )
            } else {
                null
            }
        val a =
            NesFaceButtonLayout.button(
                dialPx.toFloat(),
                density,
                NesFaceButtonLayout.A_BASE_X,
                settings.aButton,
                bounds,
            )
        val b =
            NesFaceButtonLayout.button(
                dialPx.toFloat(),
                density,
                NesFaceButtonLayout.B_BASE_X,
                settings.bButton,
                bounds,
            )

        when (val arrangement = NesFaceButtonLayout.arrange(a, b)) {
            is NesFaceButtonLayout.Cluster -> {
                val primaryAnchors =
                    remember(arrangement) {
                        persistentListOf(
                            Anchor(
                                Offset(arrangement.aAnchorX, arrangement.aAnchorY),
                                persistentSetOf(aId),
                                arrangement.aSize,
                            ),
                            Anchor(
                                Offset(arrangement.bAnchorX, arrangement.bAnchorY),
                                persistentSetOf(bId),
                                arrangement.bSize,
                            ),
                        )
                    }
                val compositeAnchors =
                    remember(arrangement) {
                        val both = persistentSetOf(aId, bId)
                        persistentListOf(
                            Anchor(Offset(arrangement.compositeX, arrangement.compositeY), both, 0.1f),
                            Anchor(Offset(-arrangement.compositeX, -arrangement.compositeY), both, 0.1f),
                        )
                    }
                NesFaceButtonCluster(
                    modifier =
                        Modifier.nesPlacedAt(
                            arrangement.left,
                            arrangement.top,
                            arrangement.side,
                        ),
                    aId = aId,
                    bId = bId,
                    primaryAnchors = primaryAnchors,
                    compositeAnchors = compositeAnchors,
                )
            }
            is NesFaceButtonLayout.Separate -> {
                NesSeparateFaceButton(id = aId, label = "A", button = arrangement.a)
                NesSeparateFaceButton(id = bId, label = "B", button = arrangement.b)
            }
        }
    }
}

@Composable
private fun PadKitScope.NesFaceButtonCluster(
    modifier: Modifier,
    aId: Id.Key,
    bId: Id.Key,
    primaryAnchors: PersistentList<Anchor<Id.Key>>,
    compositeAnchors: PersistentList<Anchor<Id.Key>>,
) {
    // PadKit anchors control BOTH the rendered buttons and their hit targets.
    LemuroidControlFaceButtons(
        modifier = modifier,
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
}

/**
 * One button on its own. The touch target is slightly larger than the disc,
 * replacing the generous nearest-button area the paired dial provides.
 */
@Composable
private fun PadKitScope.NesSeparateFaceButton(
    id: Id.Key,
    label: String,
    button: NesFaceButtonLayout.Button,
) {
    val hit = button.diameter * NesFaceButtonLayout.SEPARATE_HIT_SCALE
    ControlButton(
        modifier =
            Modifier.nesPlacedAt(
                button.centerX - hit / 2f,
                button.centerY - hit / 2f,
                hit,
            ),
        id = id,
        background = {},
        foreground = { pressed ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize(1f / NesFaceButtonLayout.SEPARATE_HIT_SCALE)) {
                    NesFaceButtonForeground(pressed = pressed, label = label)
                }
            }
        },
    )
}

/**
 * Place a square control at exact pixels relative to the parent's top-left.
 * A layout placement moves the PadKit touch rectangle with the drawing,
 * unlike a graphical translation, and may extend beyond the parent.
 */
private fun Modifier.nesPlacedAt(
    leftPx: Float,
    topPx: Float,
    sidePx: Float,
): Modifier =
    layout { measurable, _ ->
        val side = sidePx.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(0, 0) {
            placeable.place(leftPx.roundToInt(), topPx.roundToInt())
        }
    }
