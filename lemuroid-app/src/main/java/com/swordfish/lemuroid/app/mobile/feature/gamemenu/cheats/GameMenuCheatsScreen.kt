package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuStyle
import com.swordfish.lemuroid.app.shared.cheats.NesCheatCodes
import com.swordfish.lemuroid.app.shared.cheats.NesCheatDefinition
import com.swordfish.lemuroid.app.shared.cheats.NesCheatExchange
import com.swordfish.lemuroid.app.shared.cheats.NesCheatRepository
import com.swordfish.lemuroid.app.shared.cheats.NesCheatSession
import com.swordfish.lemuroid.app.shared.cheats.NesLibretroCheatFinder
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline cheat manager. Saved definitions do not activate on their own:
 * enabled IDs live only for this loaded ROM in the current game process.
 */
@Composable
internal fun GameMenuCheatsScreen(game: Game) {
    val context = LocalContext.current
    val repository = remember(context) { NesCheatRepository(context) }
    val uri = game.fileUri
    var definitions by remember(uri) { mutableStateOf(repository.load(uri)) }
    var enabled by remember(uri) { mutableStateOf(NesCheatSession.enabledFor(uri)) }
    var error by remember(uri) { mutableStateOf<Int?>(null) }
    var status by remember(uri) { mutableStateOf<Int?>(null) }
    var editorOpen by remember(uri) { mutableStateOf(false) }
    var editing by remember(uri) { mutableStateOf<NesCheatDefinition?>(null) }
    var name by remember(uri) { mutableStateOf("") }
    var code by remember(uri) { mutableStateOf("") }
    var deleteTarget by remember(uri) { mutableStateOf<NesCheatDefinition?>(null) }
    var finderOpen by remember(uri) { mutableStateOf(false) }
    var finderTerm by remember(uri) { mutableStateOf(game.displayName()) }
    var finderLoading by remember(uri) { mutableStateOf(false) }
    var finderResults by remember(uri) { mutableStateOf<List<NesLibretroCheatFinder.Candidate>>(emptyList()) }
    var finderSelected by remember(uri) { mutableStateOf<Set<String>>(emptySet()) }
    var finderMessage by remember(uri) { mutableStateOf<String?>(null) }
    var finderRequestSerial by remember(uri) { mutableStateOf(0) }
    var finderJob by remember(uri) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val importDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { document ->
            if (document != null) {
                scope.launch {
                    val imported =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes =
                                    context.contentResolver.openInputStream(document)?.use {
                                        NesCheatExchange.readBounded(it)
                                    } ?: return@runCatching null
                                val entries = NesCheatExchange.decode(bytes) ?: return@runCatching null
                                val merged =
                                    NesCheatExchange.merge(definitions, entries) ?: return@runCatching null
                                if (repository.save(uri, merged)) merged else null
                            }.getOrNull()
                        }
                    if (imported == null) {
                        error = R.string.opennes_cheats_import_failed
                    } else {
                        definitions = imported
                        error = null
                        status = R.string.opennes_cheats_import_success
                    }
                }
            }
        }
    val exportDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { document ->
            if (document != null) {
                scope.launch {
                    val exported =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = NesCheatExchange.encode(definitions)
                                context.contentResolver.openOutputStream(document)?.use { output ->
                                    output.write(bytes)
                                    output.flush()
                                    true
                                } ?: false
                            }.getOrDefault(false)
                        }
                    if (exported) {
                        error = null
                        status = R.string.opennes_cheats_export_success
                    } else {
                        error = R.string.opennes_cheats_export_failed
                    }
                }
            }
        }

    fun openEditor(entry: NesCheatDefinition?) {
        editing = entry
        name = entry?.name.orEmpty()
        code = entry?.code.orEmpty()
        error = null
        status = null
        editorOpen = true
    }

    fun updateEnabled(requested: Set<String>): Boolean {
        val applied = NesCheatSession.apply(uri, definitions, requested)
        if (applied) {
            enabled = requested
            error = null
        } else {
            enabled = NesCheatSession.enabledFor(uri)
            error = R.string.opennes_cheats_apply_failed
        }
        return applied
    }

    fun cancelFinderLookup() {
        // Cancel the old request and invalidate any late results before the
        // user can change the title, close the dialog or begin another search.
        finderRequestSerial += 1
        finderJob?.cancel()
        finderJob = null
        finderLoading = false
    }

    fun dismissFinder() {
        cancelFinderLookup()
        finderOpen = false
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = game.displayName(),
            color = GameMenuStyle.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(
            text = stringResource(R.string.opennes_cheats_explanation),
            color = GameMenuStyle.muted,
            fontSize = 13.sp,
        )
        error?.let { message ->
            Text(text = stringResource(message), color = GameMenuStyle.red, fontSize = 13.sp)
        }
        status?.let { message ->
            Text(text = stringResource(message), color = GameMenuStyle.muted, fontSize = 13.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    status = null
                    importDocument.launch(arrayOf("application/json", "text/plain"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.opennes_cheats_import))
            }
            OutlinedButton(
                onClick = {
                    status = null
                    val title = game.displayName().replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
                    exportDocument.launch("OpenNES-$title-cheats.json")
                },
                modifier = Modifier.weight(1f),
                enabled = definitions.isNotEmpty(),
            ) {
                Text(stringResource(R.string.opennes_cheats_export))
            }
        }
        OutlinedButton(
            onClick = {
                cancelFinderLookup()
                finderResults = emptyList()
                finderSelected = emptySet()
                finderMessage = null
                finderOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.opennes_cheats_find))
        }
        Button(
            onClick = { openEditor(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GameMenuStyle.red),
        ) {
            Text(stringResource(R.string.opennes_cheats_add))
        }
        if (enabled.isNotEmpty()) {
            TextButton(
                onClick = { updateEnabled(emptySet()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.opennes_cheats_disable_all))
            }
        }
        if (definitions.isEmpty()) {
            Text(
                text = stringResource(R.string.opennes_cheats_empty),
                color = GameMenuStyle.muted,
                fontSize = 13.sp,
            )
        }
        definitions.forEach { entry ->
            Surface(
                color = GameMenuStyle.surface,
                border = BorderStroke(1.dp, GameMenuStyle.border),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = entry.name,
                            color = GameMenuStyle.foreground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = entry.code, color = GameMenuStyle.muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = entry.id in enabled,
                        onCheckedChange = { checked ->
                            val target = if (checked) enabled + entry.id else enabled - entry.id
                            updateEnabled(target)
                        },
                    )
                    IconButton(onClick = { openEditor(entry) }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.opennes_cheats_edit),
                            tint = GameMenuStyle.foreground,
                        )
                    }
                    IconButton(onClick = { deleteTarget = entry }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.opennes_cheats_delete),
                            tint = GameMenuStyle.foreground,
                        )
                    }
                }
            }
        }
    }

    if (finderOpen) {
        AlertDialog(
            onDismissRequest = { dismissFinder() },
            title = { Text(stringResource(R.string.opennes_cheats_find_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.opennes_cheats_find_notice),
                        color = GameMenuStyle.muted,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = finderTerm,
                        onValueChange = { entered ->
                            val next = entered.take(120)
                            if (next != finderTerm) {
                                cancelFinderLookup()
                                finderTerm = next
                                finderResults = emptyList()
                                finderSelected = emptySet()
                                finderMessage = null
                            }
                        },
                        label = { Text(stringResource(R.string.opennes_cheats_find_game_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedButton(
                        enabled = !finderLoading && finderTerm.trim().length >= 3,
                        onClick = {
                            cancelFinderLookup()
                            val searchedTitle = finderTerm.trim()
                            val requestSerial = finderRequestSerial
                            finderLoading = true
                            finderMessage = null
                            finderResults = emptyList()
                            finderSelected = emptySet()
                            finderJob =
                                scope.launch {
                                    val result =
                                        try {
                                            Result.success(NesLibretroCheatFinder.find(searchedTitle))
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            Result.failure(failure)
                                        }
                                    if (!finderOpen ||
                                        requestSerial != finderRequestSerial ||
                                        searchedTitle != finderTerm.trim()
                                    ) {
                                        return@launch
                                    }
                                    finderResults = result.getOrDefault(emptyList())
                                    finderLoading = false
                                    finderJob = null
                                    finderMessage =
                                        when {
                                            result.isFailure ->
                                                context.getString(R.string.opennes_cheats_find_unavailable)
                                            finderResults.isEmpty() ->
                                                context.getString(R.string.opennes_cheats_find_none)
                                            else ->
                                                context.resources.getQuantityString(
                                                    R.plurals.opennes_cheats_find_count,
                                                    finderResults.size,
                                                    finderResults.size,
                                                )
                                        }
                                }
                        },
                    ) { Text(stringResource(R.string.opennes_cheats_find_search)) }
                    if (finderLoading) CircularProgressIndicator()
                    finderMessage?.let { Text(it, color = GameMenuStyle.muted, fontSize = 12.sp) }
                    finderResults.forEach { candidate ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = candidate.code in finderSelected,
                                onCheckedChange = { checked ->
                                    finderSelected =
                                        if (checked) {
                                            finderSelected + candidate.code
                                        } else {
                                            finderSelected - candidate.code
                                        }
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.title, fontSize = 13.sp)
                                Text(candidate.code, color = GameMenuStyle.muted, fontSize = 11.sp)
                                Text(
                                    candidate.sourceTitle,
                                    color = GameMenuStyle.muted,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !finderLoading && finderSelected.isNotEmpty(),
                    onClick = {
                        val additions =
                            finderResults.filter { it.code in finderSelected }
                                .mapNotNull { repository.create(it.title, it.code) }
                        val merged = NesCheatExchange.merge(definitions, additions)
                        if (merged != null && repository.save(uri, merged)) {
                            definitions = merged
                            dismissFinder()
                            finderMessage = null
                        } else {
                            finderMessage = context.getString(R.string.opennes_cheats_find_import_failed)
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_cheats_find_import, finderSelected.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissFinder() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = GameMenuStyle.surface,
        )
    }

    if (editorOpen) {
        val normalized = NesCheatCodes.normalize(code)
        val nameValid = name.trim().length in 1..64
        val duplicate = definitions.any { it.id != editing?.id && it.code == normalized }
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            modifier = Modifier.imePadding(),
            title = {
                Text(
                    stringResource(
                        if (editing == null) R.string.opennes_cheats_add else R.string.opennes_cheats_edit,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(64) },
                        label = { Text(stringResource(R.string.opennes_cheats_name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(20) },
                        label = { Text(stringResource(R.string.opennes_cheats_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                    Text(
                        text = stringResource(R.string.opennes_cheats_code_hint),
                        color = GameMenuStyle.muted,
                        fontSize = 12.sp,
                    )
                    if (code.isNotBlank() && (normalized == null || duplicate)) {
                        Text(
                            text =
                                stringResource(
                                    if (duplicate) {
                                        R.string.opennes_cheats_duplicate
                                    } else {
                                        R.string.opennes_cheats_invalid_code
                                    },
                                ),
                            color = GameMenuStyle.red,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameValid && normalized != null && !duplicate,
                    onClick = {
                        val nextEntry =
                            if (editing == null) {
                                repository.create(name, normalized.orEmpty())
                            } else {
                                editing?.copy(name = name.trim(), code = normalized.orEmpty())
                            }
                        if (nextEntry != null) {
                            val next =
                                if (editing == null) {
                                    definitions + nextEntry
                                } else {
                                    definitions.map { if (it.id == nextEntry.id) nextEntry else it }
                                }
                            if (repository.save(uri, next)) {
                                val keepEnabled =
                                    if (editing?.code != null && editing?.code != nextEntry.code) {
                                        enabled - nextEntry.id
                                    } else {
                                        enabled
                                    }
                                definitions = next
                                val applied =
                                    if (keepEnabled != enabled) {
                                        updateEnabled(keepEnabled)
                                    } else {
                                        true
                                    }
                                if (applied) error = null
                                editorOpen = false
                            } else {
                                error = R.string.opennes_cheats_save_failed
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.opennes_cheats_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = GameMenuStyle.surface,
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.opennes_cheats_delete)) },
            text = { Text(stringResource(R.string.opennes_cheats_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = definitions.filterNot { it.id == target.id }
                        if (repository.save(uri, next)) {
                            val remaining = enabled - target.id
                            definitions = next
                            val applied =
                                if (remaining != enabled) {
                                    updateEnabled(remaining)
                                } else {
                                    true
                                }
                            if (applied) error = null
                        } else {
                            error = R.string.opennes_cheats_save_failed
                        }
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.opennes_cheats_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = GameMenuStyle.surface,
        )
    }
}
