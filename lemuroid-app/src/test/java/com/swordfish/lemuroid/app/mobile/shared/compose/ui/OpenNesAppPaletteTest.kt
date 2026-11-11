package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.swordfish.touchinput.radial.NesControllerPresets
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenNesAppPaletteTest {
    @Test
    fun presetThemesProduceDistinctApplicationSurfaces() {
        val classic = openNesAppPalette(NesControllerPresets.forId("classic").palette)
        val midnight = openNesAppPalette(NesControllerPresets.forId("midnight").palette)
        assertNotEquals(classic.panel, midnight.panel)
        assertNotEquals(classic.red, midnight.red)
    }

    @Test
    fun everyPresetMaintainsReadableTextOnPrimaryButtons() {
        NesControllerPresets.all.forEach { preset ->
            val colors = openNesAppPalette(preset.palette)
            val primaryLuminance = colors.red.luminance()
            val textLuminance = colors.onAccent.luminance()
            val high = maxOf(primaryLuminance, textLuminance)
            val low = minOf(primaryLuminance, textLuminance)
            assertTrue(preset.id, (high + 0.05f) / (low + 0.05f) >= 4.5f)
            assertNotEquals(Color.Transparent, colors.background)
        }
    }
}
