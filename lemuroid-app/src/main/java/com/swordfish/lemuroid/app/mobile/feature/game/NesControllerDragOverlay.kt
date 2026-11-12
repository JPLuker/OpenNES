package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.layouts.NesDragBounds
import com.swordfish.touchinput.radial.layouts.NesEditableControl
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.roundToInt
import com.swordfish.touchinput.controller.R as TouchInputR

/**
 * One drag handle per actual input: D-pad, A, B, Start, Select and Menu.
 * Real PadKit controls report their layout bounds through a CompositionLocal.
 * This editor only handles gestures; PadKit still owns gameplay input.
 */
@Composable
internal fun NesControllerDragOverlay(
    leftBounds: Rect?,
    rightBounds: Rect?,
    controlBounds: Map<NesEditableControl, Rect>,
    settings: TouchControllerSettingsManager.Settings,
    landscapeSidePanels: Boolean = false,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onCommit: (TouchControllerSettingsManager.Settings) -> Unit,
    onDone: () -> Unit,
    onFineTune: () -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    val editingBounds = remember { mutableStateOf<Rect?>(null) }
    val confirmRestore = remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0x330B0D12))
                .onGloballyPositioned { editingBounds.value = it.boundsInRoot() },
    ) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val safeDrawing = WindowInsets.safeDrawing
        val safeLeftPx = safeDrawing.getLeft(density, layoutDirection).toFloat()
        val safeRightPx = safeDrawing.getRight(density, layoutDirection).toFloat()
        val safeTopPx = safeDrawing.getTop(density).toFloat()
        val safeBottomPx = safeDrawing.getBottom(density).toFloat()
        val compactActions = maxWidth < 360.dp || density.fontScale > 1.2f

        // Use individual real-control measurements, not the old two radial
        // group rectangles. D-pad fallback exists for older PadKit layouts.
        NesEditableControl.entries.forEach { control ->
            val bounds =
                controlBounds[control]
                    ?: if (control == NesEditableControl.DPAD) leftBounds else null
            if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
                NesDragTarget(
                    control = control,
                    bounds = bounds,
                    editingBounds = editingBounds.value,
                    settings = settings,
                    safeLeftPx = safeLeftPx,
                    safeRightPx = safeRightPx,
                    safeTopPx = safeTopPx,
                    safeBottomPx = safeBottomPx,
                    onPreview = onPreview,
                    onCommit = onCommit,
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 420.dp),
            color = Color(0xFF25272D),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF9A394A)),
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_controls_drag_hint),
                    fontSize = 12.sp,
                    maxLines = 3,
                    textAlign = TextAlign.Center,
                )
                if (compactActions) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onFineTune, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.opennes_controls_drag_sliders))
                        }
                        TextButton(onClick = { confirmRestore.value = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    TouchInputR.string.touch_customize_button_reset,
                                ),
                            )
                        }
                        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(TouchInputR.string.touch_customize_button_done))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onFineTune) {
                            Text(stringResource(R.string.opennes_controls_drag_sliders))
                        }
                        TextButton(onClick = { confirmRestore.value = true }) {
                            Text(
                                stringResource(
                                    TouchInputR.string.touch_customize_button_reset,
                                ),
                            )
                        }
                        TextButton(onClick = onDone) {
                            Text(stringResource(TouchInputR.string.touch_customize_button_done))
                        }
                    }
                }
            }
        }
    }
    if (confirmRestore.value) {
        AlertDialog(
            onDismissRequest = { confirmRestore.value = false },
            title = { Text("Restore controller defaults?") },
            text = {
                Text(
                    "Reset positions and sizes for this orientation only. " +
                        "Controller colors and the other orientation will be preserved.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore.value = false
                        onRestoreDefaults()
                    },
                ) {
                    Text("Restore defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NesDragTarget(
    control: NesEditableControl,
    bounds: Rect,
    editingBounds: Rect?,
    settings: TouchControllerSettingsManager.Settings,
    safeLeftPx: Float,
    safeRightPx: Float,
    safeTopPx: Float,
    safeBottomPx: Float,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onCommit: (TouchControllerSettingsManager.Settings) -> Unit,
) {
    val density = LocalDensity.current
    val currentSettings = rememberUpdatedState(settings)
    val currentBounds = rememberUpdatedState(bounds)
    val currentViewport = rememberUpdatedState(editingBounds)
    val currentPreview = rememberUpdatedState(onPreview)
    val currentCommit = rememberUpdatedState(onCommit)
    val width = with(density) { bounds.width.toDp().coerceAtLeast(48.dp) }
    val height = with(density) { bounds.height.toDp().coerceAtLeast(48.dp) }
    val edgePx = with(density) { 8.dp.toPx() }
    val corner = RoundedCornerShape(12.dp)
    val label =
        when (control) {
            NesEditableControl.DPAD -> "D-PAD"
            NesEditableControl.DPAD_UP -> "UP"
            NesEditableControl.DPAD_DOWN -> "DOWN"
            NesEditableControl.DPAD_LEFT -> "LEFT"
            NesEditableControl.DPAD_RIGHT -> "RIGHT"
            NesEditableControl.A -> "A"
            NesEditableControl.B -> "B"
            NesEditableControl.START -> "START"
            NesEditableControl.SELECT -> "SELECT"
            NesEditableControl.MENU -> "MENU"
        }

    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        (bounds.left - (editingBounds?.left ?: 0f)).roundToInt(),
                        (bounds.top - (editingBounds?.top ?: 0f)).roundToInt(),
                    )
                }
                .size(width, height)
                .background(Color(0x244B5462), corner)
                .border(2.dp, Color(0xFFC74356), corner)
                .pointerInput(control, density.density) {
                    var original = currentSettings.value
                    var originalBounds = currentBounds.value
                    var viewport = currentViewport.value
                    var drag = Offset.Zero
                    var latest: TouchControllerSettingsManager.Settings? = null
                    detectDragGestures(
                        onDragStart = {
                            original = currentSettings.value
                            originalBounds = currentBounds.value
                            viewport = currentViewport.value
                            drag = Offset.Zero
                            latest = null
                        },
                        onDragEnd = {
                            latest?.let { currentCommit.value(it) }
                            latest = null
                        },
                        onDragCancel = {
                            currentPreview.value(original)
                            latest = null
                        },
                        onDrag = { change, movement ->
                            change.consume()
                            drag += movement
                            val dx =
                                viewport?.let {
                                    NesDragBounds.clamp(
                                        drag.x,
                                        originalBounds.left,
                                        originalBounds.right,
                                        it.left,
                                        it.right,
                                        edgePx,
                                        safeLeftPx,
                                        safeRightPx,
                                    )
                                } ?: drag.x
                            val dy =
                                viewport?.let {
                                    NesDragBounds.clamp(
                                        drag.y,
                                        originalBounds.top,
                                        originalBounds.bottom,
                                        it.top,
                                        it.bottom,
                                        edgePx,
                                        safeTopPx,
                                        safeBottomPx,
                                    )
                                } ?: drag.y
                            val revised =
                                NesControlDragSettings.move(
                                    original,
                                    control,
                                    dx / density.density,
                                    dy / density.density,
                                )
                            currentPreview.value(revised)
                            latest = revised
                        },
                    )
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = label,
            modifier =
                Modifier
                    .background(Color(0xFF25272D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
