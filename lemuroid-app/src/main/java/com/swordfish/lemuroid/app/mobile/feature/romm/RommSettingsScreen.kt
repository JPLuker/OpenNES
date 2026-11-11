package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.shared.diagnostics.OpenNesFailureCategory
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommCataloguePaging
import com.swordfish.lemuroid.app.shared.romm.RommConnection
import com.swordfish.lemuroid.app.shared.romm.RommConnectionPreferences
import com.swordfish.lemuroid.app.shared.romm.RommConnectionStore
import com.swordfish.lemuroid.app.shared.romm.RommPairing
import com.swordfish.lemuroid.app.shared.romm.RommPendingReviewSummary
import com.swordfish.lemuroid.app.shared.romm.RommPendingSyncJournal
import com.swordfish.lemuroid.app.shared.romm.RommSaveSyncPlanner
import com.swordfish.lemuroid.app.shared.romm.RommServerIdentity
import com.swordfish.lemuroid.app.shared.romm.RommVersionedSaveBackupStore
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optional RomM NES-only remote library. Never imports automatically.
 * Bearer tokens are never displayed again after successful configuration.
 */
@Composable
fun RommSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { RommConnectionStore(context) }
    val client = remember { RommApiClient() }
    LaunchedEffect(context) { RommConnectionPreferences.load(context) }
    val showDisconnectWarnings = RommConnectionPreferences.warnings.collectAsState().value
    val scope = rememberCoroutineScope()
    val syncJournal =
        remember(context) {
            RommPendingSyncJournal(File(context.noBackupFilesDir, "romm-offline-sync"))
        }

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
    var pairingCodeInput by remember { mutableStateOf("") }
    var pendingPairing by remember { mutableStateOf<RommPairing.Request?>(null) }
    var confirmPlainHttpManual by remember { mutableStateOf(false) }
    var configured by remember {
        mutableStateOf(store.configuredServer() != null && store.hasSavedToken())
    }
    var signedInAs by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<RommApiClient.Page?>(null) }
    var remoteSaves by remember { mutableStateOf<List<RommApiClient.RemoteSave>?>(null) }
    var selectedSaveGame by remember { mutableStateOf<RommApiClient.Rom?>(null) }
    var pendingSave by remember { mutableStateOf<RommApiClient.RemoteSave?>(null) }
    var pendingArchive by remember { mutableStateOf<RommVersionedSaveBackupStore.Backup?>(null) }
    var pendingArchiveDeletion by remember { mutableStateOf<RommVersionedSaveBackupStore.Backup?>(null) }
    var pendingArchiveComparison by remember { mutableStateOf<RommVersionedSaveBackupStore.Backup?>(null) }
    var prepareArchive by remember { mutableStateOf<RommVersionedSaveBackupStore.Backup?>(null) }
    var pendingTransferDocument by remember {
        mutableStateOf<Pair<RommVersionedSaveBackupStore.Backup, RommSaveSyncPlanner.Direction>?>(null)
    }
    var pendingIntentDeletion by remember { mutableStateOf<RommPendingSyncJournal.Pending?>(null) }
    var pendingIntentReview by remember { mutableStateOf<RommPendingSyncJournal.Pending?>(null) }
    var pendingIntentDetails by remember { mutableStateOf<RommPendingSyncJournal.Pending?>(null) }
    var localReviews by remember {
        mutableStateOf<Map<String, RommPendingSyncJournal.LocalReview>>(emptyMap())
    }
    var archivedSaves by remember { mutableStateOf<List<RommVersionedSaveBackupStore.Backup>>(emptyList()) }
    var archiveAudit by remember { mutableStateOf<RommVersionedSaveBackupStore.ArchiveAudit?>(null) }
    var syncQueue by remember {
        mutableStateOf<RommPendingSyncJournal.ReadResult>(RommPendingSyncJournal.ReadResult.Entries(emptyList()))
    }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val perform: (String, suspend () -> Unit) -> Unit = { title, action ->
        if (!busy) {
            activeJob =
                scope.launch {
                    busy = true
                    operation = title
                    status = ""
                    try {
                        action()
                    } catch (e: CancellationException) {
                        status = context.getString(R.string.opennes_romm_operation_cancelled)
                        throw e
                    } catch (e: Exception) {
                        // Network/SAF exceptions may embed full server URLs,
                        // bearer tokens or private paths in their messages.
                        status =
                            context.getString(
                                when (OpenNesFailureCategory.classify(e)) {
                                    OpenNesFailureCategory.ACCESS -> R.string.opennes_support_error_access
                                    OpenNesFailureCategory.IO -> R.string.opennes_support_error_io
                                    OpenNesFailureCategory.INVALID_INPUT ->
                                        R.string.opennes_support_error_input
                                    OpenNesFailureCategory.UNEXPECTED -> R.string.opennes_support_error_generic
                                },
                            )
                    } finally {
                        busy = false
                        operation = ""
                        activeJob = null
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

    val archivePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { destination ->
            val selected = pendingArchive
            pendingArchive = null
            if (destination != null && selected != null) {
                perform("Exporting local backup") {
                    client.exportOfflineSaveBackup(context, selected, destination)
                    status = context.getString(R.string.opennes_romm_save_exported)
                }
            }
        }

    val transferPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { document ->
            val request = pendingTransferDocument
            pendingTransferDocument = null
            if (document != null && request != null) {
                perform("Preparing offline transfer request") {
                    val prepared =
                        client.prepareOfflineSaveTransfer(
                            context = context,
                            backup = request.first,
                            localDocument = document,
                            direction = request.second,
                            serverUrl = store.configuredServer(),
                            journal = syncJournal,
                        )
                    syncQueue = withContext(Dispatchers.IO) { syncJournal.list() }
                    status =
                        context.getString(
                            if (prepared) {
                                R.string.opennes_romm_pending_prepared
                            } else {
                                R.string.opennes_romm_pending_prepare_failed
                            },
                        )
                }
            }
        }

    val reviewPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { localDocument ->
            val pending = pendingIntentReview
            pendingIntentReview = null
            if (localDocument != null && pending != null) {
                perform("Reviewing local save fingerprint") {
                    val result =
                        client.reviewPendingLocalSave(
                            context = context,
                            pending = pending,
                            localDocument = localDocument,
                            serverUrl = store.configuredServer(),
                            journal = syncJournal,
                        )
                    localReviews = localReviews + (pending.id to result)
                    syncQueue = withContext(Dispatchers.IO) { syncJournal.list() }
                    status = context.getString(R.string.opennes_romm_pending_local_reviewed)
                }
            }
        }

    val comparePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { source ->
            val chosen = pendingArchiveComparison
            pendingArchiveComparison = null
            if (source != null && chosen != null) {
                perform("Comparing save files") {
                    val same = client.compareOfflineSaveBackup(context, chosen, source)
                    status =
                        context.getString(
                            if (same) {
                                R.string.opennes_romm_compare_matching
                            } else {
                                R.string.opennes_romm_compare_different
                            },
                        )
                }
            }
        }

    val qrScanner =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { contents ->
                runCatching { RommPairing.parseQr(contents) }
                    .onSuccess { pendingPairing = it }
                    .onFailure { status = context.getString(R.string.opennes_romm_pair_invalid) }
            }
        }

    // Archives are local-only and must remain accessible without network,
    // connected RomM credentials, or an online save-list request.
    LaunchedEffect(context) {
        archivedSaves = client.listOfflineSaveBackups(context)
        syncQueue = withContext(Dispatchers.IO) { syncJournal.list() }
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

    val connectManually: () -> Unit = {
        perform("Connecting") {
            val candidate = RommConnection(serverUrl.trim(), tokenInput.trim())
            val user = client.checkAccountProfile(candidate)
            withContext(Dispatchers.IO) {
                store.save(candidate.serverUrl, candidate.token)
                store.pinVerifiedAccount(candidate, user)
            }
            configured = true
            signedInAs = user.username
            page = null
            remoteSaves = null
            selectedSaveGame = null
            serverUrl = store.configuredServer().orEmpty()
            tokenInput = ""
            pairingCodeInput = ""
            status = "Connected"
        }
    }

    OpenNesSettingsPage(modifier = modifier.then(if (busy) Modifier.blur(10.dp) else Modifier)) {
        OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_romm_notice_settings)) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.opennes_romm_notice_toggle))
                    Text(
                        stringResource(R.string.opennes_romm_notice_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = showDisconnectWarnings,
                    onCheckedChange = { RommConnectionPreferences.setWarnings(context, it) },
                )
            }
        }
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
                            if (RommApiClient.isPrivateLanHttp(serverUrl)) {
                                confirmPlainHttpManual = true
                            } else {
                                connectManually()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_romm_connect))
                    }
                    Text(stringResource(R.string.opennes_romm_pair_instructions))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            qrScanner.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(context.getString(R.string.opennes_romm_scan_prompt))
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.opennes_romm_scan_qr))
                    }
                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = { pairingCodeInput = it.take(12) },
                        label = { Text(stringResource(R.string.opennes_romm_pair_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        enabled = !busy && serverUrl.isNotBlank() && pairingCodeInput.isNotBlank(),
                        onClick = {
                            runCatching {
                                RommPairing.Request(
                                    RommApiClient.validatedServerUrl(serverUrl),
                                    RommPairing.normalizeCode(pairingCodeInput),
                                )
                            }.onSuccess { pendingPairing = it }
                                .onFailure {
                                    status = context.getString(R.string.opennes_romm_pair_code_invalid)
                                }
                        },
                    ) {
                        Text(stringResource(R.string.opennes_romm_pair_code_action))
                    }
                    if (RommApiClient.isPrivateLanHttp(serverUrl)) {
                        Text(stringResource(R.string.opennes_romm_http_warning))
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
                                    val profile = client.checkAccountProfile(connection)
                                    withContext(Dispatchers.IO) {
                                        store.pinVerifiedAccount(connection, profile)
                                    }
                                    signedInAs = profile.username
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
                        val nextOffset =
                            runCatching { RommCataloguePaging.nextOffset(loaded.offset, loaded) }.getOrNull()
                        if (nextOffset != null) {
                            Button(enabled = !busy, onClick = { browse(nextOffset) }) {
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
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    perform("Saving private offline backup") {
                                        client.keepRemoteSaveBackup(
                                            context,
                                            withContext(Dispatchers.IO) {
                                                store.load() ?: error("Reconnect to RomM")
                                            },
                                            save,
                                        )
                                        archivedSaves = client.listOfflineSaveBackups(context)
                                        status = context.getString(R.string.opennes_romm_archive_saved)
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.opennes_romm_archive_keep))
                            }
                        }
                    }
                }
            }
        }

        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_romm_archive_title)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.opennes_romm_archive_description))
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        perform("Checking offline save backups") {
                            archivedSaves = client.listOfflineSaveBackups(context)
                            archiveAudit = null
                            status = context.getString(R.string.opennes_romm_archive_checked)
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_romm_archive_refresh))
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        perform("Auditing offline backup integrity") {
                            archiveAudit = client.auditOfflineSaveBackups(context)
                            archivedSaves = client.listOfflineSaveBackups(context)
                            status = context.getString(R.string.opennes_romm_archive_audited)
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_romm_archive_audit))
                }
                archiveAudit?.let { audit ->
                    Text(
                        stringResource(
                            R.string.opennes_romm_archive_audit_results,
                            audit.verified,
                            audit.unverifiedArchives,
                            audit.orphanedManifests,
                            audit.incompleteWrites,
                        ),
                    )
                    if (audit.unverifiedArchives > 0 || audit.orphanedManifests > 0 ||
                        audit.incompleteWrites > 0
                    ) {
                        Text(stringResource(R.string.opennes_romm_archive_audit_warning))
                    }
                }
                if (archivedSaves.isEmpty()) {
                    Text(stringResource(R.string.opennes_romm_archive_empty))
                }
                archivedSaves.forEach { backup ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.opennes_romm_archive_entry, backup.romId))
                        Text(stringResource(R.string.opennes_romm_archive_size, backup.bytes))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    pendingArchive = backup
                                    archivePicker.launch("romm-${backup.romId}.savebak")
                                },
                            ) { Text(stringResource(R.string.opennes_romm_export_save)) }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    pendingArchiveComparison = backup
                                    comparePicker.launch(arrayOf("*/*"))
                                },
                            ) { Text(stringResource(R.string.opennes_romm_compare_action)) }
                            TextButton(
                                enabled = !busy,
                                onClick = { pendingArchiveDeletion = backup },
                            ) { Text(stringResource(R.string.opennes_romm_archive_delete)) }
                        }
                        val source = RommServerIdentity.fingerprint(store.configuredServer())
                        if (backup.sourceServerFingerprint == null) {
                            Text(stringResource(R.string.opennes_romm_pending_legacy_archive))
                        } else if (backup.sourceServerFingerprint != source) {
                            Text(stringResource(R.string.opennes_romm_pending_other_server))
                        } else {
                            TextButton(
                                enabled = !busy,
                                onClick = { prepareArchive = backup },
                            ) { Text(stringResource(R.string.opennes_romm_pending_prepare)) }
                        }
                    }
                }
            }
        }

        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_romm_pending_title)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.opennes_romm_pending_description))
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        perform("Reviewing pending transfers") {
                            syncQueue = withContext(Dispatchers.IO) { syncJournal.list() }
                            localReviews = emptyMap()
                            status = context.getString(R.string.opennes_romm_pending_refreshed)
                        }
                    },
                ) { Text(stringResource(R.string.opennes_romm_pending_refresh)) }
                when (val result = syncQueue) {
                    RommPendingSyncJournal.ReadResult.Corrupt ->
                        Text(stringResource(R.string.opennes_romm_pending_corrupt))
                    is RommPendingSyncJournal.ReadResult.Entries -> {
                        if (result.pending.isEmpty()) {
                            Text(stringResource(R.string.opennes_romm_pending_empty))
                        }
                        val currentServer = RommServerIdentity.fingerprint(store.configuredServer())
                        result.pending.forEach { pending ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                val direction =
                                    if (pending.direction == RommSaveSyncPlanner.Direction.UPLOAD) {
                                        stringResource(R.string.opennes_romm_pending_upload)
                                    } else {
                                        stringResource(R.string.opennes_romm_pending_download)
                                    }
                                Text(
                                    stringResource(
                                        R.string.opennes_romm_pending_entry,
                                        pending.romId,
                                        direction,
                                    ),
                                )
                                Text(stringResource(R.string.opennes_romm_pending_unapproved))
                                if (currentServer == null || pending.serverFingerprint != currentServer) {
                                    Text(stringResource(R.string.opennes_romm_pending_other_server))
                                }
                                localReviews[pending.id]?.let { review ->
                                    val label =
                                        when (review) {
                                            RommPendingSyncJournal.LocalReview.MATCHES_OBSERVED ->
                                                R.string.opennes_romm_pending_local_same
                                            RommPendingSyncJournal.LocalReview.CHANGED_SINCE_REQUEST ->
                                                R.string.opennes_romm_pending_local_changed
                                            RommPendingSyncJournal.LocalReview.DIFFERENT_SERVER ->
                                                R.string.opennes_romm_pending_other_server
                                            RommPendingSyncJournal.LocalReview.REQUEST_CHANGED ->
                                                R.string.opennes_romm_pending_request_stale
                                            RommPendingSyncJournal.LocalReview.JOURNAL_CORRUPT ->
                                                R.string.opennes_romm_pending_corrupt
                                        }
                                    Text(stringResource(label))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { pendingIntentDetails = pending },
                                    ) { Text(stringResource(R.string.opennes_romm_pending_details)) }
                                    if (currentServer != null && pending.serverFingerprint == currentServer) {
                                        TextButton(
                                            enabled = !busy,
                                            onClick = {
                                                pendingIntentReview = pending
                                                reviewPicker.launch(arrayOf("*/*"))
                                            },
                                        ) { Text(stringResource(R.string.opennes_romm_pending_recheck_local)) }
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { pendingIntentDeletion = pending },
                                    ) { Text(stringResource(R.string.opennes_romm_pending_discard)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (busy) {
        Dialog(
            onDismissRequest = {},
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xD0111317)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.84f),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF272B33),
                    border = BorderStroke(1.dp, Color(0xFF414650)),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color(0xFFB73439))
                        Text(
                            stringResource(R.string.opennes_romm_action_busy, operation),
                            color = Color(0xFFF2EDE6),
                        )
                        TextButton(onClick = { activeJob?.cancel() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    if (confirmPlainHttpManual) {
        AlertDialog(
            onDismissRequest = { confirmPlainHttpManual = false },
            title = { Text(stringResource(R.string.opennes_romm_http_confirm_title)) },
            text = { Text(stringResource(R.string.opennes_romm_http_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPlainHttpManual = false
                        connectManually()
                    },
                ) { Text(stringResource(R.string.opennes_romm_http_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPlainHttpManual = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingPairing?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingPairing = null },
            title = { Text(stringResource(R.string.opennes_romm_pair_confirm)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.opennes_romm_pair_explanation, selected.serverUrl))
                    if (RommApiClient.isPrivateLanHttp(selected.serverUrl)) {
                        Text(stringResource(R.string.opennes_romm_http_warning))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingPairing = null
                        perform("Pairing with RomM") {
                            val connection =
                                client.exchangePairingCode(selected.serverUrl, selected.code)
                            val user = client.checkAccountProfile(connection)
                            withContext(Dispatchers.IO) {
                                store.save(connection.serverUrl, connection.token)
                                store.pinVerifiedAccount(connection, user)
                            }
                            configured = true
                            signedInAs = user.username
                            page = null
                            remoteSaves = null
                            selectedSaveGame = null
                            serverUrl = store.configuredServer().orEmpty()
                            tokenInput = ""
                            pairingCodeInput = ""
                            status = "Connected"
                        }
                    },
                ) { Text(stringResource(R.string.opennes_romm_pair)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPairing = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    prepareArchive?.let { selected ->
        AlertDialog(
            onDismissRequest = { prepareArchive = null },
            title = { Text(stringResource(R.string.opennes_romm_pending_prepare)) },
            text = { Text(stringResource(R.string.opennes_romm_pending_prepare_explanation)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        prepareArchive = null
                        pendingTransferDocument = selected to RommSaveSyncPlanner.Direction.UPLOAD
                        transferPicker.launch(arrayOf("*/*"))
                    },
                ) { Text(stringResource(R.string.opennes_romm_pending_request_upload)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            prepareArchive = null
                            pendingTransferDocument = selected to RommSaveSyncPlanner.Direction.DOWNLOAD
                            transferPicker.launch(arrayOf("*/*"))
                        },
                    ) { Text(stringResource(R.string.opennes_romm_pending_request_download)) }
                    TextButton(onClick = { prepareArchive = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
    pendingArchiveDeletion?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingArchiveDeletion = null },
            title = { Text(stringResource(R.string.opennes_romm_archive_delete)) },
            text = {
                Text(stringResource(R.string.opennes_romm_archive_delete_confirm, selected.romId))
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingArchiveDeletion = null
                        perform("Deleting archived backup") {
                            val deleted = client.deleteOfflineSaveBackup(context, selected)
                            archivedSaves = client.listOfflineSaveBackups(context)
                            status =
                                context.getString(
                                    if (deleted) {
                                        R.string.opennes_romm_archive_deleted
                                    } else {
                                        R.string.opennes_romm_archive_delete_failed
                                    },
                                )
                        }
                    },
                ) { Text(stringResource(R.string.opennes_romm_archive_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchiveDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    pendingIntentDetails?.let { selected ->
        val summary =
            RommPendingReviewSummary.summarize(
                selected,
                RommServerIdentity.fingerprint(store.configuredServer()),
                localReviews[selected.id],
            )
        AlertDialog(
            onDismissRequest = { pendingIntentDetails = null },
            title = { Text(stringResource(R.string.opennes_romm_pending_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val message =
                        when (summary.status) {
                            RommPendingReviewSummary.Status.SERVER_MISMATCH ->
                                R.string.opennes_romm_pending_other_server
                            RommPendingReviewSummary.Status.JOURNAL_UNAVAILABLE ->
                                R.string.opennes_romm_pending_corrupt
                            RommPendingReviewSummary.Status.REQUEST_CHANGED ->
                                R.string.opennes_romm_pending_request_stale
                            RommPendingReviewSummary.Status.LOCAL_CHANGED ->
                                R.string.opennes_romm_pending_local_changed
                            RommPendingReviewSummary.Status.RECORDED_MATCH ->
                                R.string.opennes_romm_pending_recorded_equal
                            RommPendingReviewSummary.Status.RECORDED_DIFFERENCE ->
                                R.string.opennes_romm_pending_recorded_different
                        }
                    Text(stringResource(message))
                    Text(
                        stringResource(
                            R.string.opennes_romm_pending_local_snapshot,
                            summary.localBytes,
                            summary.localHash,
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.opennes_romm_pending_remote_snapshot,
                            summary.archivedBytes,
                            summary.archivedHash,
                        ),
                    )
                    Text(stringResource(R.string.opennes_romm_pending_account_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingIntentDetails = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
    pendingIntentDeletion?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingIntentDeletion = null },
            title = { Text(stringResource(R.string.opennes_romm_pending_discard)) },
            text = {
                Text(stringResource(R.string.opennes_romm_pending_discard_confirm, selected.romId))
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingIntentDeletion = null
                        perform("Discarding pending request") {
                            val discarded =
                                withContext(Dispatchers.IO) {
                                    syncJournal.discard(selected.id)
                                }
                            syncQueue = withContext(Dispatchers.IO) { syncJournal.list() }
                            localReviews = localReviews - selected.id
                            status =
                                context.getString(
                                    if (discarded) {
                                        R.string.opennes_romm_pending_discarded
                                    } else {
                                        R.string.opennes_romm_pending_discard_failed
                                    },
                                )
                        }
                    },
                ) { Text(stringResource(R.string.opennes_romm_pending_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingIntentDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
    Text(game.name, fontWeight = FontWeight.SemiBold)
    game.description?.takeIf { it.isNotBlank() }?.let { summary ->
        Text(
            summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        game.fileName,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
            Text(stringResource(R.string.opennes_romm_download))
        }
    }
}
