package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.roundToInt
import com.swordfish.touchinput.controller.R as TouchInputR

/**
 * Editing-only touch layer positioned over the existing, real PadKit clusters.
 * It does not replace the gameplay input implementation. The ViewModel blocks
 * virtual gameplay events while this layer is active.
 */
@Composable
internal fun NesControllerDragOverlay(
    leftBounds: Rect?,
    rightBounds: Rect?,
    settings: TouchControllerSettingsManager.Settings,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onCommit: (TouchControllerSettingsManager.Settings) -> Unit,
    onDone: () -> Unit,
    onFineTune: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0x330B0D12))) {
        leftBounds?.let {
            NesDragTarget(
                bounds = it,
                label = stringResource(R.string.opennes_controls_left_group),
                isLeft = true,
                settings = settings,
                onPreview = onPreview,
                onCommit = onCommit,
            )
        }
        rightBounds?.let {
            NesDragTarget(
                bounds = it,
                label = stringResource(R.string.opennes_controls_right_group),
                isLeft = false,
                settings = settings,
                onPreview = onPreview,
                onCommit = onCommit,
            )
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            color = Color(0xFF25272D),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF9A394A)),
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.opennes_controls_drag_hint),
                    fontSize = 12.sp,
                    maxLines = 2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onFineTune) {
                        Text(stringResource(R.string.opennes_controls_drag_sliders))
                    }
                    TextButton(onClick = onDone) {
                        Text(stringResource(TouchInputR.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun NesDragTarget(
    bounds: Rect,
    label: String,
    isLeft: Boolean,
    settings: TouchControllerSettingsManager.Settings,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onCommit: (TouchControllerSettingsManager.Settings) -> Unit,
) {
    val density = LocalDensity.current
    val latestSettings = rememberUpdatedState(settings)
    val latestPreview = rememberUpdatedState(onPreview)
    val latestCommit = rememberUpdatedState(onCommit)
    val width = with(density) { bounds.width.toDp().coerceAtLeast(72.dp) }
    val height = with(density) { bounds.height.toDp().coerceAtLeast(72.dp) }
    val corner = RoundedCornerShape(16.dp)

    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                    )
                }
                .size(width, height)
                .background(Color(0x224B5462), corner)
                .border(2.dp, Color(0xFFC74356), corner)
                .pointerInput(isLeft, density.density) {
                    var startSettings = latestSettings.value
                    var totalDrag = Offset.Zero
                    var finalSettings: TouchControllerSettingsManager.Settings? = null
                    detectDragGestures(
                        onDragStart = {
                            startSettings = latestSettings.value
                            totalDrag = Offset.Zero
                            finalSettings = null
                        },
                        onDragEnd = {
                            finalSettings?.let { latestCommit.value(it) }
                            finalSettings = null
                        },
                        onDragCancel = {
                            latestPreview.value(startSettings)
                            finalSettings = null
                        },
                        onDrag = { change, movement ->
                            change.consume()
                            totalDrag += movement
                            val xDp = totalDrag.x / density.density
                            val yDp = totalDrag.y / density.density
                            val revised =
                                if (isLeft) {
                                    startSettings.copy(
                                        leftOffsetX =
                                            (
                                                startSettings.leftOffsetX +
                                                    xDp / TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X
                                            ).coerceIn(-1f, 1f),
                                        leftOffsetY =
                                            (
                                                startSettings.leftOffsetY +
                                                    yDp / TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y
                                            ).coerceIn(-1f, 1f),
                                    )
                                } else {
                                    startSettings.copy(
                                        rightOffsetX =
                                            (
                                                startSettings.rightOffsetX +
                                                    xDp / TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X
                                            ).coerceIn(-1f, 1f),
                                        rightOffsetY =
                                            (
                                                startSettings.rightOffsetY +
                                                    yDp / TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y
                                            ).coerceIn(-1f, 1f),
                                    )
                                }
                            latestPreview.value(revised)
                            finalSettings = revised
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
