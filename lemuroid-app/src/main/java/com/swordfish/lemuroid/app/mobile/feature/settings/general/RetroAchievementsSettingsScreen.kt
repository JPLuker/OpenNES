package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements.RetroAchievementsWebSection
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsEmulatorLogin
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsLoginFlow
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsNativeAccountStore
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsNativeHttpTransport
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsNativeTokenVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web API history and authenticated emulator login are deliberately separate.
 * The emulator token is encrypted in noBackupFilesDir; password text never
 * goes into saved instance state or preferences. The native rc_client game
 * lifecycle is not yet connected, so login does not claim earning is enabled.
 */
@Composable
fun RetroAchievementsSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val nativeStore = remember(context) { RetroAchievementsNativeAccountStore(context) }
    val loginTransport = remember { RetroAchievementsNativeHttpTransport() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedInAs by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var nativeVerified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(nativeStore) {
        signedInAs =
            try {
                withContext(Dispatchers.IO) { nativeStore.load()?.username }
            } catch (_: Exception) {
                status = context.getString(R.string.opennes_ra_native_reconnect)
                null
            }
        checking = false
    }

    OpenNesSettingsPage(modifier = modifier.imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.opennes_ra_native_login_title),
                color = OpenNesSettingsColors.foreground,
            )
            Text(
                text = stringResource(R.string.opennes_ra_native_login_explanation),
                color = OpenNesSettingsColors.muted,
            )
            when {
                checking -> Text(stringResource(R.string.opennes_ra_native_checking))
                signedInAs != null -> {
                    Text(
                        text = stringResource(R.string.opennes_ra_native_signed_in, signedInAs.orEmpty()),
                        color = OpenNesSettingsColors.foreground,
                    )
                    Text(
                        text =
                            stringResource(
                                if (nativeVerified) {
                                    R.string.opennes_ra_native_verified_state
                                } else {
                                    R.string.opennes_ra_native_saved_state
                                },
                            ),
                        color = OpenNesSettingsColors.muted,
                    )
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                status = ""
                                try {
                                    val saved = withContext(Dispatchers.IO) { nativeStore.load() }
                                    val result =
                                        if (saved != null) {
                                            RetroAchievementsNativeTokenVerifier().verify(saved)
                                        } else {
                                            RetroAchievementsNativeTokenVerifier.Result.Unavailable
                                        }
                                    nativeVerified =
                                        result is RetroAchievementsNativeTokenVerifier.Result.Verified &&
                                        result.username.equals(saved?.username, ignoreCase = true)
                                    status =
                                        if (nativeVerified) {
                                            context.getString(
                                                R.string.opennes_ra_native_session_verified,
                                                saved?.username.orEmpty(),
                                            )
                                        } else {
                                            context.getString(R.string.opennes_ra_native_session_failed)
                                        }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    status = context.getString(R.string.opennes_ra_native_session_failed)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_ra_native_verify_session))
                    }
                    Text(
                        text = stringResource(R.string.opennes_ra_native_session_explanation),
                        color = OpenNesSettingsColors.muted,
                    )
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    withContext(Dispatchers.IO) { nativeStore.disconnect() }
                                    signedInAs = null
                                    nativeVerified = false
                                    username = ""
                                    password = ""
                                    status = context.getString(R.string.opennes_ra_native_disconnected)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_ra_native_sign_out))
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(64) },
                        singleLine = true,
                        enabled = !busy,
                        label = { Text(stringResource(R.string.opennes_ra_native_username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(512) },
                        enabled = !busy,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                            ),
                        label = { Text(stringResource(R.string.opennes_ra_native_password)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                        onClick = {
                            if (!busy) {
                                val submittedName = username
                                val submittedPassword = password
                                // Clear the UI copy before starting network work,
                                // and never surface raw exception messages that
                                // may contain endpoint or credential information.
                                password = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                busy = true
                                status = context.getString(R.string.opennes_ra_native_signing_in)
                                scope.launch {
                                    try {
                                        val result =
                                            withContext(Dispatchers.IO) {
                                                RetroAchievementsLoginFlow(
                                                    passwordSignIn = { user, secret ->
                                                        RetroAchievementsEmulatorLogin(
                                                            transport = loginTransport,
                                                            saveConfirmedToken = nativeStore::saveConfirmedToken,
                                                        ).signIn(user, secret)
                                                    },
                                                    readSavedCredentials = nativeStore::load,
                                                    nativeVerify = { saved ->
                                                        RetroAchievementsNativeTokenVerifier().verify(saved)
                                                    },
                                                ).signInAndVerify(submittedName, submittedPassword)
                                            }
                                        signedInAs =
                                            when (result) {
                                                is RetroAchievementsLoginFlow.Result.Verified -> result.username
                                                is RetroAchievementsLoginFlow.Result.SavedButUnverified ->
                                                    result.username
                                            }
                                        nativeVerified = result is RetroAchievementsLoginFlow.Result.Verified
                                        username = ""
                                        status =
                                            if (nativeVerified) {
                                                context.getString(
                                                    R.string.opennes_ra_native_session_verified,
                                                    signedInAs.orEmpty(),
                                                )
                                            } else {
                                                context.getString(R.string.opennes_ra_native_saved_unverified)
                                            }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        status = context.getString(R.string.opennes_ra_native_login_failed)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_ra_native_sign_in_verify))
                    }
                }
            }
            if (status.isNotEmpty()) {
                Text(status, color = OpenNesSettingsColors.muted)
            }
            Text(
                text = stringResource(R.string.opennes_ra_settings_native_pending),
                color = OpenNesSettingsColors.muted,
            )
            Text(
                text = stringResource(R.string.opennes_ra_settings_explanation),
                color = OpenNesSettingsColors.foreground,
            )
            RetroAchievementsWebSection(
                romMd5 = null,
                console = null,
                accountOnly = true,
            )
        }
    }
}
