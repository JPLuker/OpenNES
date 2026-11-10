package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.NesControllerPalette
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

internal enum class NesLandscapeStyle {
    OVERLAY,
    FRAMED,
    HANDHELD,
    ;

    fun apply(settings: TouchControllerSettingsManager.Settings) =
        settings.copy(
            landscapeOverlay = this == OVERLAY,
            landscapeHandheld = this == HANDHELD,
        )

    companion object {
        fun fromSettings(
            settings: TouchControllerSettingsManager.Settings?,
            defaultOverlay: Boolean = true,
        ): NesLandscapeStyle =
            when {
                settings?.landscapeHandheld == true -> HANDHELD
                settings?.landscapeOverlay == false -> FRAMED
                settings?.landscapeOverlay == null && !defaultOverlay -> FRAMED
                else -> OVERLAY
            }
    }
}

@Composable
private fun NesLandscapeStyle.title(): String =
    stringResource(
        when (this) {
            NesLandscapeStyle.OVERLAY -> R.string.opennes_landscape_overlay_name
            NesLandscapeStyle.FRAMED -> R.string.opennes_landscape_framed_name
            NesLandscapeStyle.HANDHELD -> R.string.opennes_landscape_handheld_name
        },
    )

/** Visual choices available in first-run onboarding, Settings, and the in-game editor. */
@Composable
internal fun NesLandscapeStylePicker(
    selected: NesLandscapeStyle,
    palette: NesControllerPalette,
    onSelect: (NesLandscapeStyle) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NesLandscapeStyle.values().forEach { style ->
                    FilterChip(
                        selected = selected == style,
                        onClick = { onSelect(style) },
                        label = { Text(style.title()) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NesLandscapePreview(selected, palette)
                Text(stringResource(R.string.opennes_landscape_style_preview_hint), modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NesLandscapeStyle.values().forEach { style ->
                OutlinedButton(onClick = { onSelect(style) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NesLandscapePreview(style, palette)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(style.title() + if (style == selected) " ✓" else "")
                            Text(
                                stringResource(
                                    when (style) {
                                        NesLandscapeStyle.OVERLAY -> R.string.opennes_landscape_overlay_description
                                        NesLandscapeStyle.FRAMED -> R.string.opennes_landscape_framed_description
                                        NesLandscapeStyle.HANDHELD -> R.string.opennes_landscape_handheld_description
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Simplified original gameplay/control diagrams; no licensed screenshots. */
@Composable
internal fun NesLandscapePreview(
    style: NesLandscapeStyle,
    palette: NesControllerPalette,
) {
    Canvas(modifier = Modifier.width(112.dp).height(60.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = palette.panel,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
        val gameLeft = if (style == NesLandscapeStyle.OVERLAY) w * 0.04f else w * 0.225f
        val gameRight = if (style == NesLandscapeStyle.OVERLAY) w * 0.96f else w * 0.775f
        drawRect(
            color = Color(0xFF10121A),
            topLeft = Offset(gameLeft, h * 0.10f),
            size = Size(gameRight - gameLeft, h * 0.80f),
        )
        drawRect(
            color = Color(0xFF2854BD),
            topLeft = Offset(gameLeft + 2f, h * 0.13f),
            size = Size(gameRight - gameLeft - 4f, h * 0.44f),
        )
        drawRect(
            color = Color(0xFF425A40),
            topLeft = Offset(gameLeft + 2f, h * 0.77f),
            size = Size(gameRight - gameLeft - 4f, h * 0.10f),
        )
        val leftX = if (style == NesLandscapeStyle.OVERLAY) w * 0.20f else w * 0.13f
        val rightX = if (style == NesLandscapeStyle.OVERLAY) w * 0.80f else w * 0.87f
        val cy = h * 0.56f
        val arm = h * 0.045f
        val reach = h * 0.17f
        drawRect(
            color = palette.dpad,
            topLeft = Offset(leftX - arm, cy - reach),
            size = Size(arm * 2f, reach * 2f),
        )
        drawRect(
            color = palette.dpad,
            topLeft = Offset(leftX - reach, cy - arm),
            size = Size(reach * 2f, arm * 2f),
        )
        val radius = h * 0.09f
        drawCircle(color = palette.action, radius = radius, center = Offset(rightX - radius, cy))
        drawCircle(color = palette.action, radius = radius, center = Offset(rightX + radius, cy))
        if (style == NesLandscapeStyle.HANDHELD) {
            drawRoundRect(
                color = palette.accent,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size(w - 6.dp.toPx(), h - 6.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
