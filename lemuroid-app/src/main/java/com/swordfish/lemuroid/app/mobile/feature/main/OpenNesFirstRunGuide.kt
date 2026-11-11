package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.feature.game.NesLandscapeStyle
import com.swordfish.lemuroid.app.mobile.feature.game.NesLandscapeStylePicker
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.ControllerAppearancePreview
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.NesColorWheelDialog
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
import com.swordfish.touchinput.radial.NesControllerPresets
import com.swordfish.touchinput.radial.rememberNesControllerAppearance
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlinx.coroutines.launch

/** A first-launch guide; network sign-ins remain inside existing encrypted settings flows. */
class OpenNesFirstRunStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("opennes_first_run_v1", Context.MODE_PRIVATE)
    private val oldOnboarding =
        context.applicationContext.getSharedPreferences("opennes_controller_onboarding_v1", Context.MODE_PRIVATE)

    fun shouldShow(): Boolean =
        !preferences.getBoolean("completed", false) &&
            !oldOnboarding.getBoolean("completed", false)

    fun complete() {
        preferences.edit().putBoolean("completed", true).apply()
    }

    fun requestAgain() {
        preferences.edit().putBoolean("completed", false).apply()
    }
}

@Composable
fun OpenNesFirstRunGuide(
    onOpenSettings: (MainRoute) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val appearanceStore = remember(context) { NesControllerAppearanceStore(context) }
    val appearance = rememberNesControllerAppearance()
    val manager =
        remember(context) {
            TouchControllerSettingsManager(SharedPreferencesHelper.getSharedPreferences(context))
        }
    val density = LocalDensity.current
    val insets = WindowInsets.displayCutout
    val portrait by remember(manager, density, insets) {
        manager.observeSettings(
            TouchControllerID.NES,
            TouchControllerSettingsManager.Orientation.PORTRAIT,
            density,
            insets,
        )
    }.collectAsState(TouchControllerSettingsManager.Settings())
    val landscape by remember(manager, density, insets) {
        manager.observeSettings(
            TouchControllerID.NES,
            TouchControllerSettingsManager.Orientation.LANDSCAPE,
            density,
            insets,
        )
    }.collectAsState(TouchControllerSettingsManager.Settings())
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var customColors by rememberSaveable { mutableStateOf(false) }
    var selectedPart by remember { mutableStateOf<NesColorPart?>(null) }
    val headers = listOf("Controller layout", "Controller theme", "ROM library", "RetroAchievements", "RomM library")
    val explanations =
        listOf(
            "Choose how the controls look in portrait and landscape. Each orientation has its own layout.",
            "Choose a theme or select Custom to fine-tune individual controller and app colors.",
            "Choose your NES ROM folder. This step can be completed later in Settings.",
            "Connect a RetroAchievements account to track achievements. Signing in is optional.",
            "Connect your own RomM server for NES browsing and imports. This step is optional.",
        )
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "OPENNES SETUP · ${step + 1} OF ${headers.size}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(headers[step], style = MaterialTheme.typography.headlineSmall)
        Text(
            explanations[step],
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    Text("Portrait", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Standard" to 0.57f, "Compact" to 0.42f).forEach { (title, scale) ->
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        manager.storeSettings(
                                            TouchControllerID.NES,
                                            TouchControllerSettingsManager.Orientation.PORTRAIT,
                                            portrait.copy(scale = scale, leftScale = null, rightScale = null),
                                        )
                                    }
                                },
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    NesPortraitPreview(
                                        appearance.palette.panel,
                                        appearance.palette.action,
                                        scale < 0.50f,
                                    )
                                    Text(if (kotlin.math.abs(portrait.scale - scale) < 0.02f) "✓ $title" else title)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Landscape", style = MaterialTheme.typography.titleMedium)
                    NesLandscapeStylePicker(
                        selected = NesLandscapeStyle.fromSettings(landscape),
                        palette = appearance.palette,
                        onSelect = { style ->
                            scope.launch {
                                manager.storeSettings(
                                    TouchControllerID.NES,
                                    TouchControllerSettingsManager.Orientation.LANDSCAPE,
                                    style.apply(landscape),
                                )
                            }
                        },
                    )
                }
                1 -> {
                    ControllerAppearancePreview(appearance.palette)
                    Text("Preset themes", style = MaterialTheme.typography.titleMedium)
                    NesControllerPresets.all.chunked(2).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { preset ->
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        appearanceStore.selectPreset(preset.id)
                                        customColors = false
                                    },
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(24.dp).background(preset.palette.action, CircleShape),
                                        )
                                        Text(
                                            if (appearance.preset.id == preset.id && !customColors) {
                                                "✓ ${preset.title}"
                                            } else {
                                                preset.title
                                            },
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    FilterChip(
                        selected = customColors,
                        onClick = { customColors = !customColors },
                        label = { Text("Custom colors") },
                    )
                    if (customColors) {
                        NesColorPart.values().forEach { part ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedPart = part },
                            ) {
                                Box(
                                    Modifier.size(20.dp).background(appearance.palette.color(part), CircleShape),
                                )
                                Text(part.label, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
                2 -> {
                    Button(
                        onClick = { onOpenSettings(MainRoute.SETTINGS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose ROM folder in Settings") }
                    Text("Return here after choosing your folder, or skip this step.")
                }
                3 -> {
                    Button(
                        onClick = { onOpenSettings(MainRoute.SETTINGS_RETROACHIEVEMENTS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open RetroAchievements sign-in") }
                    Text("Your account token is stored securely by the existing sign-in screen.")
                }
                4 -> {
                    Button(
                        onClick = { onOpenSettings(MainRoute.SETTINGS_ROMM) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect RomM") }
                    Text("Use your own HTTPS server and per-user account token. Nothing is imported automatically.")
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step > 0) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { step-- },
                ) { Text("Back") }
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (step == headers.lastIndex) onFinish() else step++
                },
            ) { Text(if (step == headers.lastIndex) "Skip and finish" else "Skip") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (step == headers.lastIndex) onFinish() else step++
                },
            ) { Text(if (step == headers.lastIndex) "Finish setup" else "Next") }
        }
    }
    selectedPart?.let { part ->
        NesColorWheelDialog(
            part = part,
            initialColor = appearance.palette.color(part),
            onDismiss = { selectedPart = null },
            onApply = { hex ->
                appearanceStore.setColor(part, hex)
                selectedPart = null
            },
        )
    }
}

@Composable
private fun NesPortraitPreview(
    housing: Color,
    buttons: Color,
    compact: Boolean,
) {
    Canvas(modifier = Modifier.width(75.dp).height(125.dp)) {
        val width = size.width
        val height = size.height
        drawRoundRect(
            color = housing,
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        drawRect(
            color = Color(0xFF11131A),
            topLeft = Offset(width * 0.06f, height * 0.07f),
            size = Size(width * 0.88f, height * 0.43f),
        )
        val radius = width * if (compact) 0.065f else 0.09f
        val cy = height * 0.79f
        val left = width * 0.25f
        val right = width * 0.73f
        drawRect(
            color = Color(0xFF303137),
            topLeft = Offset(left - radius * 0.5f, cy - radius * 2f),
            size = Size(radius, radius * 4f),
        )
        drawRect(
            color = Color(0xFF303137),
            topLeft = Offset(left - radius * 2f, cy - radius * 0.5f),
            size = Size(radius * 4f, radius),
        )
        drawCircle(buttons, radius, Offset(right - radius, cy))
        drawCircle(buttons, radius, Offset(right + radius, cy))
    }
}
