package com.swordfish.touchinput.radial.layouts

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.layouts.radial.LayoutRadial
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope)
@Composable
fun BaseLayoutLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val interpolatedDialSize =
        remember(settings.leftScale, settings.scale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                (settings.leftScale ?: settings.scale).coerceIn(0f, 1f),
            )
        }
    val xOffsetDp =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X *
            settings.leftOffsetX.coerceIn(-1f, 1f) + settings.leftDragX
    val yOffsetDp =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y *
            settings.leftOffsetY.coerceIn(-1f, 1f) + settings.leftDragY

    val leftHanded = LocalNesLeftHanded.current
    val sideMargin = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX
    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    // The edge margin follows the side this cluster is on.
                    left = if (leftHanded) 0.dp else sideMargin,
                    right = if (leftHanded) sideMargin else 0.dp,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                )
                .offset(x = xOffsetDp.dp, y = yOffsetDp.dp)
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize =
            TouchControllerSettingsManager.BASE_DIAL_SIZE_DP.dp * interpolatedDialSize,
        secondaryDialsBaseRotationInDegrees =
            (if (leftHanded) -1f else 1f) * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}

context(PadKitScope)
@Composable
fun BaseLayoutRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val xOffsetDp =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X *
            settings.rightOffsetX.coerceIn(-1f, 1f) + settings.rightDragX
    val yOffsetDp =
        TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y *
            settings.rightOffsetY.coerceIn(-1f, 1f) + settings.rightDragY
    val leftHanded = LocalNesLeftHanded.current
    val sideMargin = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX
    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    left = if (leftHanded) sideMargin else 0.dp,
                    right = if (leftHanded) 0.dp else sideMargin,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                )
                .offset(x = xOffsetDp.dp, y = yOffsetDp.dp)
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize =
            TouchControllerSettingsManager.BASE_DIAL_SIZE_DP.dp *
                lerp(
                    TouchControllerSettingsManager.MIN_SCALE,
                    TouchControllerSettingsManager.MAX_SCALE,
                    (settings.rightScale ?: settings.scale).coerceIn(0f, 1f),
                ),
        secondaryDialsBaseRotationInDegrees =
            (if (leftHanded) 1f else -1f) * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}
