package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.ControllerAppearancePreview
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
import com.swordfish.touchinput.radial.NesControllerPresets
import com.swordfish.touchinput.radial.rememberNesControllerAppearance

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

private const val STEP_WELCOME = 0
private const val STEP_GAMES = 1
private const val STEP_CONTROLLER = 2
private const val STEP_ONLINE = 3
private const val STEP_COUNT = 4

/**
 * First launch: welcome, choose the game folder (in place, no detour through
 * Settings), pick controller colors, then optional online services. Every
 * step can be skipped; nothing here signs in or downloads anything itself.
 */
@Composable
fun OpenNesFirstRunGuide(
    onOpenSettings: (MainRoute) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenNesAppColors.current
    var step by rememberSaveable { mutableStateOf(STEP_WELCOME) }
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val folderPrefs = remember(context) { SharedPreferencesHelper.getLegacySharedPreferences(context) }
    val folderKey =
        remember(context) { context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder) }
    var folder by remember { mutableStateOf(folderPrefs.getString(folderKey, null)) }
    // The folder picker is its own screen; read the choice when we come back.
    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) folder = folderPrefs.getString(folderKey, null)
    }
    val folderName =
        remember(folder) {
            folder?.takeIf { it.isNotBlank() }?.let {
                runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() ?: it
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        StepDots(current = step, colors = colors)
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                STEP_WELCOME -> WelcomeStep()
                STEP_GAMES ->
                    GamesStep(
                        folderName = folderName,
                        onChooseFolder = { StorageFrameworkPickerLauncher.pickFolder(context) },
                    )
                STEP_CONTROLLER -> ControllerStep()
                else -> OnlineStep(onOpenSettings = onOpenSettings)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = colors.onAccent),
            onClick = { if (step == STEP_COUNT - 1) onFinish() else step++ },
        ) {
            Text(
                stringResource(
                    when (step) {
                        STEP_WELCOME -> R.string.opennes_welcome_start
                        STEP_GAMES ->
                            if (folderName == null) R.string.opennes_welcome_skip else R.string.onboarding_next
                        STEP_ONLINE -> R.string.opennes_welcome_finish
                        else -> R.string.onboarding_next
                    },
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (step > STEP_WELCOME) {
                TextButton(onClick = { step-- }) {
                    Text(stringResource(R.string.onboarding_back), color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun StepDots(
    current: Int,
    colors: com.swordfish.lemuroid.app.mobile.shared.compose.ui.OpenNesAppPalette,
) {
    val label = stringResource(R.string.opennes_welcome_progress, current + 1, STEP_COUNT)
    Row(
        modifier = Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(STEP_COUNT) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) 24.dp else 8.dp, height = 8.dp)
                    .background(if (index <= current) colors.red else colors.border, CircleShape),
            )
        }
    }
}

@Composable
private fun StepTitle(
    title: String,
    body: String,
) {
    val colors = LocalOpenNesAppColors.current
    Text(title, color = colors.foreground, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    Text(body, color = colors.muted, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun WelcomeStep() {
    val colors = LocalOpenNesAppColors.current
    Spacer(Modifier.height(24.dp))
    Text("OpenNES", color = colors.foreground, fontSize = 44.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.opennes_welcome_tagline), color = colors.muted, fontSize = 17.sp, lineHeight = 24.sp)
    Spacer(Modifier.height(8.dp))
    FeatureRow(Icons.Outlined.SportsEsports, stringResource(R.string.opennes_welcome_feature_controller))
    FeatureRow(Icons.Outlined.Save, stringResource(R.string.opennes_welcome_feature_saves))
    FeatureRow(Icons.Outlined.Cloud, stringResource(R.string.opennes_welcome_feature_online))
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    text: String,
) {
    val colors = LocalOpenNesAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.panel, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.red)
        }
        Text(text, color = colors.foreground, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun GamesStep(
    folderName: String?,
    onChooseFolder: () -> Unit,
) {
    val colors = LocalOpenNesAppColors.current
    StepTitle(stringResource(R.string.opennes_welcome_games_title), stringResource(R.string.opennes_welcome_games_body))
    if (folderName == null) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            border = BorderStroke(1.dp, colors.red),
            onClick = onChooseFolder,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = colors.red)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.opennes_welcome_choose_folder), color = colors.foreground, fontSize = 16.sp)
        }
    } else {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.red)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folderName,
                        color = colors.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.opennes_welcome_folder_chosen), color = colors.muted, fontSize = 13.sp)
                }
                TextButton(onClick = onChooseFolder) {
                    Text(stringResource(R.string.opennes_welcome_change), color = colors.red)
                }
            }
        }
    }
    Text(stringResource(R.string.opennes_welcome_games_romm_hint), color = colors.muted, fontSize = 13.sp)
}

@Composable
private fun ControllerStep() {
    val colors = LocalOpenNesAppColors.current
    val context = LocalContext.current.applicationContext
    val store = remember(context) { NesControllerAppearanceStore(context) }
    val appearance = rememberNesControllerAppearance()
    StepTitle(
        stringResource(R.string.opennes_welcome_controller_title),
        stringResource(R.string.opennes_welcome_controller_body),
    )
    ControllerAppearancePreview(appearance.palette)
    NesControllerPresets.all.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { preset ->
                val selected = appearance.preset.id == preset.id
                Surface(
                    onClick = { store.selectPreset(preset.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.panel,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.red else colors.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Swatch(preset.palette.panel)
                        Swatch(preset.palette.action)
                        Text(
                            preset.title,
                            color = colors.foreground,
                            fontSize = 14.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Palette, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.opennes_welcome_controller_more), color = colors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun Swatch(color: Color) {
    val colors = LocalOpenNesAppColors.current
    Box(
        Modifier
            .size(18.dp)
            .background(color, CircleShape)
            .border(1.dp, colors.border, CircleShape),
    )
}

@Composable
private fun OnlineStep(onOpenSettings: (MainRoute) -> Unit) {
    StepTitle(
        stringResource(R.string.opennes_welcome_online_title),
        stringResource(R.string.opennes_welcome_online_body),
    )
    ServiceCard(
        icon = Icons.Outlined.EmojiEvents,
        title = stringResource(R.string.opennes_ra_settings_title),
        body = stringResource(R.string.opennes_welcome_ra_body),
        onSetUp = { onOpenSettings(MainRoute.SETTINGS_RETROACHIEVEMENTS) },
    )
    ServiceCard(
        icon = Icons.Outlined.Cloud,
        title = stringResource(R.string.opennes_welcome_romm_title),
        body = stringResource(R.string.opennes_welcome_romm_body),
        onSetUp = { onOpenSettings(MainRoute.SETTINGS_ROMM) },
    )
}

@Composable
private fun ServiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    onSetUp: () -> Unit,
) {
    val colors = LocalOpenNesAppColors.current
    Card {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = colors.red)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedButton(onClick = onSetUp, border = BorderStroke(1.dp, colors.border)) {
                    Text(stringResource(R.string.opennes_welcome_set_up), color = colors.foreground)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = LocalOpenNesAppColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}
