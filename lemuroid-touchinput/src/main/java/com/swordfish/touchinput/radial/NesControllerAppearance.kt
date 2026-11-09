package com.swordfish.touchinput.radial

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Original, independent color palettes. No Nintendo assets or names. */
data class NesControllerPalette(
    val panel: Color,
    val accent: Color,
    val dpad: Color,
    val dpadPressed: Color,
    val action: Color,
    val actionPressed: Color,
    val secondary: Color,
    val secondaryPressed: Color,
    val label: Color,
    val outline: Color,
) {
    fun color(part: NesColorPart): Color =
        when (part) {
            NesColorPart.PANEL -> panel
            NesColorPart.ACCENT -> accent
            NesColorPart.DPAD -> dpad
            NesColorPart.DPAD_PRESSED -> dpadPressed
            NesColorPart.ACTION -> action
            NesColorPart.ACTION_PRESSED -> actionPressed
            NesColorPart.SECONDARY -> secondary
            NesColorPart.SECONDARY_PRESSED -> secondaryPressed
            NesColorPart.LABEL -> label
            NesColorPart.OUTLINE -> outline
        }

    fun withColor(
        part: NesColorPart,
        color: Color,
    ): NesControllerPalette =
        when (part) {
            NesColorPart.PANEL -> copy(panel = color)
            NesColorPart.ACCENT -> copy(accent = color)
            NesColorPart.DPAD -> copy(dpad = color)
            NesColorPart.DPAD_PRESSED -> copy(dpadPressed = color)
            NesColorPart.ACTION -> copy(action = color)
            NesColorPart.ACTION_PRESSED -> copy(actionPressed = color)
            NesColorPart.SECONDARY -> copy(secondary = color)
            NesColorPart.SECONDARY_PRESSED -> copy(secondaryPressed = color)
            NesColorPart.LABEL -> copy(label = color)
            NesColorPart.OUTLINE -> copy(outline = color)
        }
}

enum class NesColorPart(val label: String) {
    PANEL("Controller housing"),
    ACCENT("Accent trim"),
    DPAD("D-pad"),
    DPAD_PRESSED("D-pad pressed"),
    ACTION("A/B buttons"),
    ACTION_PRESSED("A/B pressed"),
    SECONDARY("Start / Select / Menu"),
    SECONDARY_PRESSED("Secondary pressed"),
    LABEL("Button lettering"),
    OUTLINE("Outlines"),
}

data class NesControllerPreset(
    val id: String,
    val title: String,
    val palette: NesControllerPalette,
)

object NesControllerPresets {
    val all =
        listOf(
            NesControllerPreset(
                "classic",
                "Classic Hardware",
                NesControllerPalette(
                    panel = Color(0xFFAAA69D),
                    accent = Color(0xFFA22B3F),
                    dpad = Color(0xFF303137),
                    dpadPressed = Color(0xFF17181C),
                    action = Color(0xFFB52E43),
                    actionPressed = Color(0xFF842437),
                    secondary = Color(0xFF494A50),
                    secondaryPressed = Color(0xFF25262B),
                    label = Color(0xFFF7F2E9),
                    outline = Color(0xFF1D1E23),
                ),
            ),
            NesControllerPreset(
                "arcade_duo",
                "Arcade Duo",
                NesControllerPalette(
                    panel = Color(0xFF2F4A9D),
                    accent = Color(0xFF52A46B),
                    dpad = Color(0xFF25315B),
                    dpadPressed = Color(0xFF151D3D),
                    action = Color(0xFFE6453B),
                    actionPressed = Color(0xFFB32B2A),
                    secondary = Color(0xFF418A50),
                    secondaryPressed = Color(0xFF275A35),
                    label = Color(0xFFFFFFFF),
                    outline = Color(0xFF172344),
                ),
            ),
            NesControllerPreset(
                "forest_wanderer",
                "Forest Wanderer",
                NesControllerPalette(
                    panel = Color(0xFF667C52),
                    accent = Color(0xFFE3BD60),
                    dpad = Color(0xFF273C2C),
                    dpadPressed = Color(0xFF1A2C20),
                    action = Color(0xFFD3A744),
                    actionPressed = Color(0xFFAD8230),
                    secondary = Color(0xFF49653E),
                    secondaryPressed = Color(0xFF2E472B),
                    label = Color(0xFFFFF1D2),
                    outline = Color(0xFF233A28),
                ),
            ),
            NesControllerPreset(
                "midnight",
                "Midnight Console",
                NesControllerPalette(
                    panel = Color(0xFF20242D),
                    accent = Color(0xFF6E849E),
                    dpad = Color(0xFF303746),
                    dpadPressed = Color(0xFF1A1F29),
                    action = Color(0xFF657B94),
                    actionPressed = Color(0xFF40546C),
                    secondary = Color(0xFF363E4D),
                    secondaryPressed = Color(0xFF1C2531),
                    label = Color(0xFFF0F4F7),
                    outline = Color(0xFF0E151D),
                ),
            ),
            NesControllerPreset(
                "crimson",
                "Crimson Arcade",
                NesControllerPalette(
                    panel = Color(0xFF241B1F),
                    accent = Color(0xFFCA3446),
                    dpad = Color(0xFF454047),
                    dpadPressed = Color(0xFF252227),
                    action = Color(0xFFD22B41),
                    actionPressed = Color(0xFF9B1F30),
                    secondary = Color(0xFF5B303D),
                    secondaryPressed = Color(0xFF35212A),
                    label = Color(0xFFFFE8EA),
                    outline = Color(0xFF120D11),
                ),
            ),
            NesControllerPreset(
                "ivory",
                "Ivory Minimal",
                NesControllerPalette(
                    panel = Color(0xFFD9D5CB),
                    accent = Color(0xFF746E64),
                    dpad = Color(0xFF3C3B3B),
                    dpadPressed = Color(0xFF232222),
                    action = Color(0xFF77716F),
                    actionPressed = Color(0xFF4B4544),
                    secondary = Color(0xFF74716D),
                    secondaryPressed = Color(0xFF4B4844),
                    label = Color(0xFFFFFFFF),
                    outline = Color(0xFF41403E),
                ),
            ),
        )

    val default = all.first()

    fun forId(id: String?): NesControllerPreset = all.firstOrNull { it.id == id } ?: default
}

data class NesControllerAppearance(
    val preset: NesControllerPreset,
    val palette: NesControllerPalette,
)

val LocalNesControllerPalette = compositionLocalOf { NesControllerPresets.default.palette }

/**
 * The theme is shared by portrait and landscape; position/scale remain
 * orientation-specific in TouchControllerSettingsManager.
 */
class NesControllerAppearanceStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("opennes_controller_appearance_v1", Context.MODE_PRIVATE)

    fun load(): NesControllerAppearance {
        val preset = NesControllerPresets.forId(preferences.getString(PRESET_KEY, null))
        var palette = preset.palette
        NesColorPart.values().forEach { part ->
            val stored = preferences.getString("color_" + part.name, null)
            if (stored != null && isValidHex(stored)) {
                palette = palette.withColor(part, Color(android.graphics.Color.parseColor(stored)))
            }
        }
        return NesControllerAppearance(preset, palette)
    }

    fun selectPreset(id: String) {
        val selected = NesControllerPresets.forId(id)
        preferences.edit().apply {
            putString(PRESET_KEY, selected.id)
            NesColorPart.values().forEach { remove("color_" + it.name) }
            apply()
        }
    }

    fun setColor(
        part: NesColorPart,
        value: String,
    ): Boolean {
        if (!isValidHex(value)) return false
        preferences.edit()
            .putString("color_" + part.name, value.uppercase(Locale.ROOT))
            .apply()
        return true
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PRESET_KEY = "preset_id"
        private val HEX_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

        fun isValidHex(value: String) = HEX_PATTERN.matches(value.trim())

        fun toHex(color: Color): String = "#%06X".format(Locale.ROOT, color.toArgb() and 0xFFFFFF)
    }
}

/** React to theme changes without restarting OpenNES or changing any input IDs. */
@Composable
fun rememberNesControllerAppearance(): NesControllerAppearance {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { NesControllerAppearanceStore(context) }
    var appearance by remember(store) { mutableStateOf(store.load()) }
    DisposableEffect(store) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                appearance = store.load()
            }
        store.register(listener)
        onDispose { store.unregister(listener) }
    }
    return appearance
}
