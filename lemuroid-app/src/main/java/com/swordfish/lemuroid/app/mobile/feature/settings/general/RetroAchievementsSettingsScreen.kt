package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * Single-purpose account screen. The enclosing Scaffold already owns the
 * keyboard-sized viewport: never add IME padding to this scrolling page,
 * which previously scrolled every field out of view on narrow devices.
 */
@Composable
fun RetroAchievementsSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val nativeStore = remember(context) { RetroAchievementsNativeAccountStore(context) }
    val loginTransport = remember { RetroAchievementsNativeHttpTransport() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedInAs by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var nativeVerified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var websiteExpanded by remember { mutableStateOf(false) }

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

    // The parent is already scrollable, and MainActivity resizes its content
    // area for the IME. Extra imePadding() would double-count the keyboard.
    OpenNesSettingsPage(modifier = modifier) {
        Surface(
            color = OpenNesSettingsColors.panel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, OpenNesSettingsColors.border),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = OpenNesSettingsColors.red,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.opennes_ra_native_login_title),
                            color = OpenNesSettingsColors.foreground,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.opennes_ra_account_subtitle),
                            color = OpenNesSettingsColors.muted,
                        )
                    }
                }
                when {
                    checking -> {
                        CircularProgressIndicator(color = OpenNesSettingsColors.red)
                    }
                    signedInAs != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (nativeVerified) verifiedColor else OpenNesSettingsColors.muted,
                            )
                            Column {
                                Text(
                                    text = signedInAs.orEmpty(),
                                    color = OpenNesSettingsColors.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            if (nativeVerified) {
                                                R.string.opennes_ra_account_verified
                                            } else {
                                                R.string.opennes_ra_account_saved
                                            },
                                        ),
                                    color = OpenNesSettingsColors.muted,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    status = ""
                                    scope.launch {
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
                                                    context.getString(R.string.opennes_ra_account_reverified)
                                                } else {
                                                    context.getString(R.string.opennes_ra_native_session_failed)
                                                }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            nativeVerified = false
                                            status = context.getString(R.string.opennes_ra_native_session_failed)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = OpenNesSettingsColors.red),
                            ) {
                                Text(stringResource(R.string.opennes_ra_account_verify_short))
                            }
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmSignOut = true },
                            ) {
                                Text(
                                    text = stringResource(R.string.opennes_ra_native_sign_out),
                                    color = OpenNesSettingsColors.foreground,
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.opennes_ra_account_login_hint),
                            color = OpenNesSettingsColors.muted,
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.take(64) },
                            enabled = !busy,
                            singleLine = true,
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
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                    },
                                ),
                            label = { Text(stringResource(R.string.opennes_ra_native_password)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = OpenNesSettingsColors.red),
                            onClick = {
                                if (!busy) {
                                    val submittedName = username
                                    val submittedPassword = password
                                    password = ""
                                    keyboard?.hide()
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
                                                    context.getString(R.string.opennes_ra_account_reverified)
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
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 10.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.opennes_ra_native_sign_in_verify))
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Text(text = status, color = OpenNesSettingsColors.muted)
                }
            }
        }

        Surface(
            color = OpenNesSettingsColors.panel,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = OpenNesSettingsColors.muted,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.opennes_ra_account_game_status_title),
                        fontWeight = FontWeight.SemiBold,
                        color = OpenNesSettingsColors.foreground,
                    )
                    Text(
                        text = stringResource(R.string.opennes_ra_account_game_status_body),
                        color = OpenNesSettingsColors.muted,
                    )
                }
            }
        }

        TextButton(onClick = { websiteExpanded = !websiteExpanded }) {
            Text(
                text =
                    stringResource(
                        if (websiteExpanded) {
                            R.string.opennes_ra_account_hide_web
                        } else {
                            R.string.opennes_ra_account_show_web
                        },
                    ),
                color = OpenNesSettingsColors.foreground,
            )
        }
        if (websiteExpanded) {
            Surface(
                color = OpenNesSettingsColors.panel,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    RetroAchievementsWebSection(
                        romMd5 = null,
                        console = null,
                        accountOnly = true,
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = OpenNesSettingsColors.panel,
            titleContentColor = OpenNesSettingsColors.foreground,
            textContentColor = OpenNesSettingsColors.muted,
            title = { Text(stringResource(R.string.opennes_ra_account_signout_title)) },
            text = { Text(stringResource(R.string.opennes_ra_account_signout_body)) },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.cancel), color = OpenNesSettingsColors.foreground)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmSignOut = false
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { nativeStore.disconnect() }
                                signedInAs = null
                                nativeVerified = false
                                username = ""
                                password = ""
                                status = context.getString(R.string.opennes_ra_native_disconnected)
                            } catch (_: Exception) {
                                status = context.getString(R.string.opennes_ra_account_signout_failed)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_ra_native_sign_out), color = OpenNesSettingsColors.red)
                }
            },
        )
    }
}

private val verifiedColor = Color(0xFF78C9A1)
