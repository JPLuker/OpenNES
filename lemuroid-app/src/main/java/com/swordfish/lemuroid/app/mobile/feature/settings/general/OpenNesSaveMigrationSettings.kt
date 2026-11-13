package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsLink
import com.swordfish.lemuroid.app.shared.migration.OpenNesSaveTransfer
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Explicit one-way file export/import between separate Android package IDs.
 * Nothing runs automatically, and existing destination progress is preserved.
 */
@Composable
internal fun OpenNesSaveMigrationSettings() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    var lastCount by remember { mutableStateOf(0) }
    var lastSkipped by remember { mutableStateOf(0) }
    var staged by remember { mutableStateOf<OpenNesSaveTransfer.PreparedImport?>(null) }

    val exportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            if (uri != null && !busy) {
                scope.launch {
                    busy = true
                    status = null
                    try {
                        lastCount =
                            withContext(Dispatchers.IO) {
                                val root =
                                    context.getExternalFilesDir(null)
                                        ?: throw IOException("NES save directory unavailable")
                                GameProcessLock.withNoGameRunning(context) {
                                    val temporary = File.createTempFile("opennes-export-", ".zip", context.cacheDir)
                                    try {
                                        val count =
                                            temporary.outputStream().use {
                                                OpenNesSaveTransfer.export(root, it)
                                            }
                                        val expected = hashFile(temporary)
                                        val output =
                                            context.contentResolver.openOutputStream(uri, "w")
                                                ?: throw IOException("Cannot write selected document")
                                        temporary.inputStream().use { input ->
                                            output.use { input.copyTo(it) }
                                        }
                                        val actual =
                                            context.contentResolver.openInputStream(uri)?.use(::hashStream)
                                                ?: throw IOException("Cannot verify selected document")
                                        if (!MessageDigest.isEqual(expected, actual)) {
                                            throw IOException("Exported save checksum mismatch")
                                        }
                                        count
                                    } finally {
                                        temporary.delete()
                                    }
                                }
                            }
                        status = R.string.opennes_save_transfer_exported
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        status = R.string.opennes_save_transfer_export_failed
                    } finally {
                        busy = false
                    }
                }
            }
        }

    val importPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null && !busy) {
                scope.launch {
                    busy = true
                    status = null
                    try {
                        staged?.discard()
                        staged = null
                        staged =
                            withContext(Dispatchers.IO) {
                                val source =
                                    context.contentResolver.openInputStream(uri)
                                        ?: throw IOException("Cannot read selected archive")
                                source.use { OpenNesSaveTransfer.prepare(it, context.cacheDir) }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        status = R.string.opennes_save_transfer_invalid
                    } finally {
                        busy = false
                    }
                }
            }
        }

    OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_save_transfer_title)) }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.opennes_save_transfer_description))
        }
        OpenNesSettingsLink(
            title = { Text(stringResource(R.string.opennes_save_transfer_export)) },
            onClick = { exportPicker.launch("OpenNES-saves.zip") },
            enabled = !busy && staged == null,
        )
        OpenNesSettingsLink(
            title = { Text(stringResource(R.string.opennes_save_transfer_import)) },
            onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = !busy && staged == null,
        )
        if (busy) {
            Text(stringResource(R.string.opennes_save_transfer_working))
        }
        status?.let { message ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    if (message == R.string.opennes_save_transfer_imported) {
                        stringResource(message, lastCount, lastSkipped)
                    } else if (message == R.string.opennes_save_transfer_exported) {
                        stringResource(message, lastCount)
                    } else {
                        stringResource(message)
                    },
                )
            }
        }
    }

    staged?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pending.discard()
                staged = null
            },
            title = { Text(stringResource(R.string.opennes_save_transfer_confirm_title)) },
            text = { Text(stringResource(R.string.opennes_save_transfer_confirm, pending.items.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        staged = null
                        scope.launch {
                            busy = true
                            status = null
                            try {
                                val outcome =
                                    withContext(Dispatchers.IO) {
                                        val root =
                                            context.getExternalFilesDir(null)
                                                ?: throw IOException("NES save directory unavailable")
                                        GameProcessLock.withNoGameRunning(context) {
                                            pending.apply(root)
                                        }
                                    }
                                lastCount = outcome.imported
                                lastSkipped = outcome.existingPreserved
                                status = R.string.opennes_save_transfer_imported
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                status = R.string.opennes_save_transfer_import_failed
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.opennes_save_transfer_confirm_action)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pending.discard()
                        staged = null
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun hashFile(file: File): ByteArray = file.inputStream().use(::hashStream)

private fun hashStream(stream: java.io.InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        if (count == 0) throw IOException("Stalled save document")
        digest.update(buffer, 0, count)
    }
    return digest.digest()
}
