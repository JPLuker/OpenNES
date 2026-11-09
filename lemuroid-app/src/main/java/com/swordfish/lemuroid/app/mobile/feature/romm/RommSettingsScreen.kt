package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommConnection
import com.swordfish.lemuroid.app.shared.romm.RommConnectionStore
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional RomM NES-only remote library. Never imports automatically.
 * Bearer tokens are never displayed again after successful configuration.
 */
@Composable
fun RommSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { RommConnectionStore(context) }
    val client = remember { RommApiClient() }
    val scope = rememberCoroutineScope()

    @Suppress("DEPRECATION")
    val legacyFolderPrefs =
        remember(context) {
            SharedPreferencesHelper.getLegacySharedPreferences(context)
        }
    val folderKey =
        remember(context) {
            context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        }
    val hasRomFolder = !legacyFolderPrefs.getString(folderKey, null).isNullOrBlank()

    var serverUrl by remember { mutableStateOf(store.configuredServer().orEmpty()) }
    var tokenInput by remember { mutableStateOf("") }
    var configured by remember {
        mutableStateOf(store.configuredServer() != null && store.hasSavedToken())
    }
    var signedInAs by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<RommApiClient.Page?>(null) }
    var remoteSaves by remember { mutableStateOf<List<RommApiClient.RemoteSave>?>(null) }
    var selectedSaveGame by remember { mutableStateOf<RommApiClient.Rom?>(null) }
    var pendingSave by remember { mutableStateOf<RommApiClient.RemoteSave?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }

    val perform: (String, suspend () -> Unit) -> Unit = { title, action ->
        if (!busy) {
            scope.launch {
                busy = true
                operation = title
                status = ""
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = e.message?.take(240) ?: "RomM request failed"
                } finally {
                    busy = false
                    operation = ""
                }
            }
        }
    }

    val backupPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { destination ->
            val chosen = pendingSave
            pendingSave = null
            if (destination != null && chosen != null) {
                perform("Exporting save backup") {
                    val connection =
                        withContext(Dispatchers.IO) {
                            store.load() ?: error("Reconnect to RomM")
                        }
                    client.exportSaveBackup(context, connection, chosen, destination)
                    status = context.getString(R.string.opennes_romm_save_exported)
                }
            }
        }

    val browse: (Int) -> Unit = { offset ->
        perform("Browsing NES games") {
            val connection =
                withContext(Dispatchers.IO) {
                    store.load() ?: error("Connect to a RomM server first")
                }
            page = client.listNesRoms(connection, offset = offset)
            remoteSaves = null
            selectedSaveGame = null
        }
    }

    OpenNesSettingsPage(modifier = modifier) {
        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_romm_title)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.opennes_romm_explanation))
                if (!configured) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.opennes_romm_server)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text(stringResource(R.string.opennes_romm_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !busy && serverUrl.isNotBlank() && tokenInput.isNotBlank(),
                        onClick = {
                            perform("Connecting") {
                                val candidate = RommConnection(serverUrl.trim(), tokenInput.trim())
                                val user = client.checkAccount(candidate)
                                withContext(Dispatchers.IO) {
                                    store.save(candidate.serverUrl, candidate.token)
                                }
                                configured = true
                                signedInAs = user
                                serverUrl = store.configuredServer().orEmpty()
                                tokenInput = ""
                                status = "Connected"
                            }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_romm_connect))
                    }
                } else {
                    Text(stringResource(R.string.opennes_romm_connected, serverUrl))
                    signedInAs?.let {
                        Text(stringResource(R.string.opennes_romm_verified, it))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                perform("Checking connection") {
                                    val connection =
                                        withContext(Dispatchers.IO) {
                                            store.load() ?: error("Reconnect to RomM")
                                        }
                                    signedInAs = client.checkAccount(connection)
                                    status = "Connection verified"
                                }
                            },
                        ) { Text(stringResource(R.string.opennes_romm_check)) }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                perform("Disconnecting") {
                                    withContext(Dispatchers.IO) { store.disconnect() }
                                    configured = false
                                    tokenInput = ""
                                    signedInAs = null
                                    page = null
                                    remoteSaves = null
                                    selectedSaveGame = null
                                    pendingSave = null
                                    status = "Disconnected"
                                }
                            },
                        ) { Text(stringResource(R.string.opennes_romm_disconnect)) }
                    }
                    Button(enabled = !busy, onClick = { browse(0) }) {
                        Text(stringResource(R.string.opennes_romm_browse))
                    }
                }
                if (busy) {
                    Text(stringResource(R.string.opennes_romm_action_busy, operation))
                }
                if (status.isNotBlank()) Text(status)
            }
        }

        if (configured) {
            page?.let { loaded ->
                OpenNesSettingsGroup(
                    title = {
                        Text(stringResource(R.string.opennes_romm_results, loaded.total))
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.opennes_romm_import_note))
                        if (!hasRomFolder) {
                            Text(stringResource(R.string.opennes_romm_no_folder))
                        }
                        if (loaded.items.isEmpty()) {
                            Text(stringResource(R.string.opennes_romm_no_roms))
                        }
                        loaded.items.forEach { rom ->
                            RommGameRow(
                                game = rom,
                                busy = busy,
                                importAvailable = hasRomFolder,
                                onSaves = {
                                    perform("Viewing save backups") {
                                        val connection =
                                            withContext(Dispatchers.IO) {
                                                store.load() ?: error("Reconnect to RomM")
                                            }
                                        remoteSaves = client.listRemoteSaves(connection, rom.id)
                                        selectedSaveGame = rom
                                    }
                                },
                                onImport = {
                                    perform("Importing " + rom.name) {
                                        val connection =
                                            withContext(Dispatchers.IO) {
                                                store.load() ?: error("Reconnect to RomM")
                                            }
                                        client.importIntoSelectedNesFolder(context, connection, rom)
                                        status = context.getString(R.string.opennes_romm_imported, rom.name)
                                    }
                                },
                            )
                        }
                        if (loaded.offset + loaded.limit < loaded.total) {
                            Button(enabled = !busy, onClick = { browse(loaded.offset + loaded.limit) }) {
                                Text(stringResource(R.string.opennes_romm_more))
                            }
                        }
                    }
                }
            }
        }

        val saves = remoteSaves
        val game = selectedSaveGame
        if (configured && saves != null && game != null) {
            OpenNesSettingsGroup(
                title = { Text(stringResource(R.string.opennes_romm_save_backups_for, game.name)) },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.opennes_romm_save_backups_note))
                    if (saves.isEmpty()) Text(stringResource(R.string.opennes_romm_no_saves))
                    saves.forEach { save ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(save.fileName)
                                save.slot?.let { Text(it) }
                            }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    pendingSave = save
                                    backupPicker.launch(save.fileName)
                                },
                            ) {
                                Text(stringResource(R.string.opennes_romm_export_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keep game titles and action buttons usable on narrow/large-text phones. */
@Composable
private fun RommGameRow(
    game: RommApiClient.Rom,
    busy: Boolean,
    importAvailable: Boolean,
    onSaves: () -> Unit,
    onImport: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 380.dp || LocalDensity.current.fontScale > 1.2f
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RommGameName(game)
                RommGameActions(busy, importAvailable, onSaves, onImport)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) { RommGameName(game) }
                RommGameActions(busy, importAvailable, onSaves, onImport)
            }
        }
    }
}

@Composable
private fun RommGameName(game: RommApiClient.Rom) {
    Text(game.name)
    Text(game.fileName)
}

@Composable
private fun RommGameActions(
    busy: Boolean,
    importAvailable: Boolean,
    onSaves: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(enabled = !busy, onClick = onSaves) {
            Text(stringResource(R.string.opennes_romm_view_saves))
        }
        TextButton(enabled = !busy && importAvailable, onClick = onImport) {
            Text(stringResource(R.string.opennes_romm_import))
        }
    }
}
