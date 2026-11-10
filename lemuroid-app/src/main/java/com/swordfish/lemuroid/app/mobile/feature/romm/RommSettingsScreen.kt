package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.swordfish.lemuroid.app.shared.romm.RommPendingSyncJournal
import com.swordfish.lemuroid.app.shared.romm.RommSaveSyncPlanner
import com.swordfish.lemuroid.app.shared.romm.RommServerIdentity
import com.swordfish.lemuroid.app.shared.romm.RommVersionedSaveBackupStore
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
