package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alorma.compose.settings.storage.memory.rememberMemoryIntSettingState
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.NesBoxArtwork
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import com.swordfish.lemuroid.app.shared.achievements.HardcoreSessionPolicy
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import kotlin.reflect.KFunction1

private enum class Confirmation {
    RESTART,
    QUIT,
}

/** One consistent pause screen. Save slots and core settings remain dedicated subpages. */
@Composable
fun GameMenuHomeScreen(
    navController: NavController,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    audioEnabled: Boolean,
    onAudioChanged: (Boolean) -> Unit,
    fastForwardEnabled: Boolean,
    onFastForwardChanged: (Boolean) -> Unit,
    rewindAvailable: Boolean,
    tiltEnabled: Boolean,
    onTiltChanged: (Boolean) -> Unit,
    onResult: KFunction1<Intent.() -> Unit, Unit>,
) {
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    val hardcoreActive = HardcoreRuntime.mode() == HardcoreSessionPolicy.Mode.HARDCORE

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NesBoxArtwork(game = gameMenuRequest.game, modifier = Modifier.width(66.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_pause_label),
                    color = GameMenuStyle.red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = gameMenuRequest.game.displayName(),
                    color = GameMenuStyle.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onResult { } }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = GameMenuStyle.foreground,
                )
            }
        }

        Button(
            onClick = { onResult { } },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = GameMenuStyle.red,
                    contentColor = GameMenuStyle.foreground,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.game_context_menu_resume))
        }

        if (gameMenuRequest.coreConfig.statesSupported) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GameMenuQuickAction(
                    label = stringResource(R.string.game_menu_save),
                    icon = R.drawable.ic_menu_save,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigateToRoute(GameMenuRoute.SAVE) },
                )
                if (!hardcoreActive) {
                    GameMenuQuickAction(
                        label = stringResource(R.string.game_menu_load),
                        icon = R.drawable.ic_menu_load,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigateToRoute(GameMenuRoute.LOAD) },
                    )
                }
            }
        }

        GameMenuAction(
            title = stringResource(R.string.opennes_ra_menu_title),
            icon = R.drawable.ic_menu_achievements,
            onClick = { navController.navigateToRoute(GameMenuRoute.ACHIEVEMENTS) },
        )

        if (!hardcoreActive) {
            GameMenuAction(
                title = stringResource(R.string.opennes_cheats_title),
                icon = R.drawable.ic_menu_cheats,
                onClick = { navController.navigateToRoute(GameMenuRoute.CHEATS) },
            )
        }

        GameMenuAction(
            title = stringResource(R.string.game_menu_edit_touch_controls),
            icon = R.drawable.ic_menu_controls,
            onClick = {
                onResult { putExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, true) }
            },
        )

        if (gameMenuRequest.coreOptions.isNotEmpty() || gameMenuRequest.advancedCoreOptions.isNotEmpty()) {
            GameMenuAction(
                title = stringResource(R.string.game_menu_settings),
                icon = R.drawable.ic_menu_settings,
                onClick = { navController.navigateToRoute(GameMenuRoute.OPTIONS) },
            )
        }

        GameMenuSwitchAction(
            title = stringResource(R.string.opennes_pause_game_audio),
            checked = audioEnabled,
            onCheckedChange = onAudioChanged,
        )

        // NES games normally do not expose multiple disks or tilt input.
        // Preserve the existing handlers only if the running core supports them.
        if (gameMenuRequest.numDisks > 1) {
            LemuroidSettingsList(
                title = { Text(stringResource(R.string.game_menu_change_disk_button)) },
                items =
                    (1..gameMenuRequest.numDisks).map {
                        stringResource(R.string.game_menu_change_disk_disk, it)
                    },
                useSelectedValueAsSubtitle = false,
                state = rememberMemoryIntSettingState(gameMenuRequest.currentDisk),
                onItemSelected = { index, _ ->
                    onResult { putExtra(GameMenuContract.RESULT_CHANGE_DISK, index) }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GameMenuQuickAction(
                label = stringResource(R.string.game_menu_restart),
                icon = R.drawable.ic_menu_restart,
                modifier = Modifier.weight(1f),
                onClick = { confirmation = Confirmation.RESTART },
            )
            GameMenuQuickAction(
                label = stringResource(R.string.game_menu_quit),
                icon = R.drawable.ic_menu_quit,
                modifier = Modifier.weight(1f),
                onClick = { confirmation = Confirmation.QUIT },
            )
        }
    }

    confirmation?.let { selection ->
        val restart = selection == Confirmation.RESTART
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    stringResource(
                        if (restart) R.string.opennes_confirm_restart_title else R.string.opennes_confirm_quit_title,
                    ),
                )
            },
            text = { Text(stringResource(R.string.opennes_confirm_progress_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        if (restart) {
                            onResult { putExtra(GameMenuContract.RESULT_RESET, true) }
                        } else {
                            onResult { putExtra(GameMenuContract.RESULT_QUIT, true) }
                        }
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                if (restart) R.string.game_menu_restart else R.string.game_menu_quit,
                            ),
                        color = GameMenuStyle.red,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = GameMenuStyle.surface,
            titleContentColor = GameMenuStyle.foreground,
            textContentColor = GameMenuStyle.muted,
        )
    }
}

@Composable
private fun GameMenuQuickAction(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GameMenuStyle.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameMenuStyle.foreground),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun GameMenuAction(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Surface(
        onClick = onClick,
        color = GameMenuStyle.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GameMenuStyle.border),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = GameMenuStyle.foreground,
            )
            Text(
                text = title,
                color = GameMenuStyle.foreground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
            )
            detail?.let {
                Text(text = it, fontSize = 12.sp, color = GameMenuStyle.muted)
            }
        }
    }
}

/**
 * The full row owns the accessibility toggle action. The Switch itself has
 * no separate click handler, avoiding duplicate switches and conflicting taps.
 * The setting is returned to gameplay when the menu closes, not immediately.
 */
@Composable
private fun GameMenuSwitchAction(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        color = GameMenuStyle.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GameMenuStyle.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = GameMenuStyle.foreground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = GameMenuStyle.foreground,
                        checkedTrackColor = GameMenuStyle.red,
                        uncheckedThumbColor = GameMenuStyle.muted,
                        uncheckedTrackColor = GameMenuStyle.surfaceRaised,
                        uncheckedBorderColor = GameMenuStyle.border,
                    ),
            )
        }
    }
}
