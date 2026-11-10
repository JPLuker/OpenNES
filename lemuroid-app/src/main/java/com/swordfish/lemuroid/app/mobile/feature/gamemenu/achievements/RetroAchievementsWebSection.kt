package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuStyle
import com.swordfish.lemuroid.app.shared.achievements.NesRetroAchievementsHash
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsWebAccount
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsWebAccountStore
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsWebApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional, manually refreshed website lookup. No network request is made
 * simply by pausing a game. The personal web API key stays encrypted and
 * never becomes an emulator login token or a source of local unlock awards.
 */
@Composable
internal fun RetroAchievementsWebSection(
    romMd5: String?,
    console: NesRetroAchievementsHash.Console?,
    accountOnly: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { RetroAchievementsWebAccountStore(context) }
    val api = remember(context) { RetroAchievementsWebApi(context) }
    val scope = rememberCoroutineScope()
    var configured by remember(context) { mutableStateOf(store.isConfigured()) }
    var usernameInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusId by remember { mutableStateOf<Int?>(null) }
    var progress by remember(romMd5, console) { mutableStateOf<RetroAchievementsWebApi.Progress?>(null) }

    // Credentials are only ever read and decrypted inside a user-initiated
    // operation, and the key is not copied into any Compose state after save.
    val run: (suspend () -> Unit) -> Unit = { action ->
        if (!busy) {
            scope.launch {
                busy = true
                statusId = null
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    statusId = R.string.opennes_ra_web_error
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.opennes_ra_web_heading),
            color = GameMenuStyle.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        Text(
            text =
                stringResource(
                    if (configured) {
                        R.string.opennes_ra_web_connected_hint
                    } else {
                        R.string.opennes_ra_web_explanation
                    },
                ),
            color = GameMenuStyle.muted,
            fontSize = 12.sp,
        )

        if (!configured) {
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it.take(64) },
                label = { Text(stringResource(R.string.opennes_ra_web_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it.take(128) },
                label = { Text(stringResource(R.string.opennes_ra_web_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && usernameInput.isNotBlank() && apiKeyInput.isNotBlank(),
                onClick = {
                    run {
                        val candidate = RetroAchievementsWebAccount(usernameInput, apiKeyInput)
                        val profileName = api.validateWebAccount(candidate)
                        withContext(Dispatchers.IO) {
                            store.save(profileName, candidate.apiKey)
                        }
                        usernameInput = ""
                        apiKeyInput = ""
                        configured = true
                        statusId = R.string.opennes_ra_web_saved
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.opennes_ra_web_connect))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!accountOnly) {
                    Button(
                        enabled = !busy && romMd5 != null && console != null,
                        onClick = {
                            val hash = romMd5
                            val selectedConsole = console
                            if (hash != null && selectedConsole != null) {
                                run {
                                    val account =
                                        withContext(Dispatchers.IO) {
                                            store.load() ?: error("Missing website account")
                                        }
                                    progress = api.lookupProgress(account, hash, selectedConsole)
                                    statusId =
                                        if (progress == null) {
                                            R.string.opennes_ra_web_no_match
                                        } else {
                                            R.string.opennes_ra_web_loaded
                                        }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.opennes_ra_web_refresh))
                    }
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        run {
                            withContext(Dispatchers.IO) { store.disconnect() }
                            configured = false
                            progress = null
                            usernameInput = ""
                            apiKeyInput = ""
                            statusId = R.string.opennes_ra_web_disconnected
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_ra_web_disconnect))
                }
            }
            if (!accountOnly && (romMd5 == null || console == null)) {
                Text(
                    text = stringResource(R.string.opennes_ra_web_need_hash),
                    color = GameMenuStyle.muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.opennes_ra_web_loading),
                    color = GameMenuStyle.muted,
                    fontSize = 12.sp,
                )
            }
        }
        statusId?.let { message ->
            Text(
                text = stringResource(message),
                color = GameMenuStyle.muted,
                fontSize = 12.sp,
            )
        }
        val expectedSystem =
            when (console) {
                NesRetroAchievementsHash.Console.NES -> 7
                NesRetroAchievementsHash.Console.FDS -> 81
                null -> null
            }
        progress?.takeIf {
            !accountOnly &&
                romMd5 != null &&
                expectedSystem != null &&
                it.romMd5.equals(romMd5, ignoreCase = true) &&
                it.systemId == expectedSystem
        }
            ?.let { value ->
                VerifiedAchievementList(
                    username = value.username,
                    romMd5 = value.romMd5,
                    achievements = value.achievements,
                    readOnlyWeb = true,
                )
            }
    }
}
