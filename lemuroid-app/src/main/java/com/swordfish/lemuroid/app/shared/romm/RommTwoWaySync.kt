package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import com.swordfish.lemuroid.app.shared.game.ActiveGameSession
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Two-way battery-save sync with the player's RomM server (RomM 5.4 device
 * sync). Off until the player turns it on.
 *
 * Every run: negotiate with RomM, let [RommTwoWaySyncPlan] decide each game,
 * then upload, download or record a conflict. A download always backs up the
 * phone's save first and sets the autosave state aside. The running game is
 * never touched, and nothing on the phone is ever deleted.
 *
 * One run at a time per process; all callers share [mutex].
 */
internal object RommTwoWaySync {
    sealed interface Outcome {
        data object Disabled : Outcome

        data class Done(val run: RommTwoWaySyncState.LastRun) : Outcome

        data class Failed(val problem: String) : Outcome
    }

    data class Status(
        val running: Boolean = false,
        val state: RommTwoWaySyncState = RommTwoWaySyncState(),
    )

    private const val STATE_FILE = "romm-two-way-sync-v1.json"
    private const val BACKUP_DIR = "romm-save-archive-v1"
    private const val ASIDE_DIR = "romm-replaced-autosaves"
    private const val MAX_ROMS_PER_NEGOTIATION = 100
    const val BEFORE_LAUNCH_TIMEOUT_MS = 6_000L

    private val mutex = Mutex()

    // The game being launched, before its own process marks it as running.
    @Volatile private var launching: Pair<Int, Long>? = null
    private const val LAUNCH_GUARD_MS = 2 * 60 * 1000L

    /** Called once a game's pre-launch sync is done; its save is then off limits. */
    fun protectLaunchingGame(gameId: Int) {
        launching = gameId to System.currentTimeMillis()
    }

    /** Called when the game has closed or failed to start. */
    fun clearLaunchingGame() {
        launching = null
    }

    private fun launchingGameId(): Int? =
        launching?.takeIf { System.currentTimeMillis() - it.second in 0 until LAUNCH_GUARD_MS }?.first

    private val status = MutableStateFlow(Status())
    val statusFlow: StateFlow<Status> = status.asStateFlow()

    private class ProblemException(message: String) : Exception(message)

    private class Session(
        val connection: RommConnection,
        val deviceId: String,
        val serverFingerprint: String,
        val accountId: Long,
    ) {
        val owner = RommTwoWaySyncState.owner(serverFingerprint, accountId)
    }

    private fun stateFile(context: Context) =
        RommTwoWaySyncStateFile(File(context.applicationContext.noBackupFilesDir, STATE_FILE))

    /** Loads the saved state into [statusFlow] for the settings screen. */
    suspend fun refreshStatus(context: Context) {
        val state = withContext(Dispatchers.IO) { stateFile(context).read() }
        status.value = status.value.copy(state = state)
    }

    /** When the last run finished, or null if sync never ran. */
    suspend fun lastRunAtMillis(context: Context): Long? =
        withContext(Dispatchers.IO) { stateFile(context).read().lastRun?.atMillis }

    suspend fun isEnabled(context: Context): Boolean = withContext(Dispatchers.IO) { stateFile(context).read().enabled }

    /**
     * The player's switch. Turning on checks the account, registers this
     * device with RomM if needed, then runs a first sync.
     */
    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
        games: List<Game>,
    ): Outcome {
        if (!enabled) {
            mutex.withLock {
                val file = stateFile(context)
                val state = withContext(Dispatchers.IO) { file.read() }.copy(enabled = false)
                withContext(Dispatchers.IO) { file.write(state) }
                status.value = status.value.copy(state = state)
            }
            return Outcome.Disabled
        }
        val registered =
            mutex.withLock {
                guard(context) {
                    val session = openSession(context, registerIfNeeded = true)
                    val file = stateFile(context)
                    val state =
                        withContext(Dispatchers.IO) { file.read() }
                            .forOwner(session.owner)
                            .copy(enabled = true)
                    withContext(Dispatchers.IO) { file.write(state) }
                    status.value = status.value.copy(state = state)
                    null
                }
            }
        if (registered is Outcome.Failed) return registered
        return syncAll(context, games)
    }

    /** Syncs every installed NES game that is also on the RomM server. */
    suspend fun syncAll(
        context: Context,
        games: List<Game>,
    ): Outcome =
        mutex.withLock {
            if (!isEnabled(context)) return@withLock Outcome.Disabled
            guard(context) {
                val session = openSession(context, registerIfNeeded = false)
                RommLibraryCatalog.refresh(context.applicationContext)
                run(context, session, pair(games, RommLibraryCatalog.state.value.games))
            } ?: Outcome.Failed("Sync stopped")
        }

    /**
     * Pulls a newer save for [game] just before it starts. Bounded by
     * [BEFORE_LAUNCH_TIMEOUT_MS]: an unreachable server never blocks play.
     */
    suspend fun syncBeforeLaunch(
        context: Context,
        game: Game,
    ) {
        if (!isEnabled(context)) return
        withTimeoutOrNull(BEFORE_LAUNCH_TIMEOUT_MS) {
            mutex.withLock {
                guard(context) {
                    val session = openSession(context, registerIfNeeded = false)
                    var remote = RommLibraryCatalog.state.value.games
                    if (remote.isEmpty()) {
                        RommLibraryCatalog.refresh(context.applicationContext)
                        remote = RommLibraryCatalog.state.value.games
                    }
                    run(context, session, pair(listOf(game), remote))
                }
            }
        }
    }

    /** The player's answer to one conflict. */
    suspend fun resolve(
        context: Context,
        conflict: RommTwoWaySyncState.Conflict,
        keepPhone: Boolean,
        games: List<Game>,
    ): Outcome =
        mutex.withLock {
            guard(context) {
                val session = openSession(context, registerIfNeeded = false)
                val game =
                    games.firstOrNull { it.id == conflict.gameId }
                        ?: throw ProblemException("This game is no longer in your library")
                refuseIfRunning(context, game)
                val dirs = DirectoriesManager(context.applicationContext)
                val target = RommLocalSaveFiles.saveFile(dirs.getSavesDirectory(), game.fileName)
                val client = RommApiClient()
                val md5 =
                    if (keepPhone) {
                        val local =
                            withContext(Dispatchers.IO) { RommLocalSaveFiles.snapshot(target) }
                                ?: throw ProblemException("There is no save on this phone for this game")
                        val bytes = withContext(Dispatchers.IO) { RommLocalSaveFiles.readForUpload(target, local.md5) }
                        // The server keeps its replaced copy as an older version.
                        client.uploadSyncedSave(
                            session.connection,
                            conflict.romId,
                            RommLocalSaveFiles.saveFileName(game.fileName),
                            bytes,
                            local.md5,
                            session.deviceId,
                            sessionId = null,
                            overwrite = true,
                        )
                        local.md5
                    } else {
                        val saveId = conflict.saveId ?: throw ProblemException("The server has no save to use")
                        download(context, session, client, game, conflict.romId, saveId, null, null)
                    }
                val file = stateFile(context)
                val state =
                    withContext(Dispatchers.IO) { file.read() }.forOwner(session.owner).let { current ->
                        current.copy(
                            baselines = current.baselines + (conflict.romId to md5),
                            conflicts = current.conflicts.filterNot { it.romId == conflict.romId },
                        )
                    }
                withContext(Dispatchers.IO) { file.write(state) }
                status.value = status.value.copy(state = state)
                null
            } ?: Outcome.Done(
                RommTwoWaySyncState.LastRun(
                    atMillis = System.currentTimeMillis(),
                    uploaded = if (keepPhone) 1 else 0,
                    downloaded = if (keepPhone) 0 else 1,
                    conflicts = 0,
                    failed = 0,
                ),
            )
        }

    /** Runs [block], turning every failure into a short, user-safe message. */
    private suspend fun guard(
        context: Context,
        block: suspend () -> Outcome?,
    ): Outcome? {
        status.value = status.value.copy(running = true)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = describe(e)
            Timber.w("RomM save sync stopped: %s", e.javaClass.simpleName)
            recordProblem(context, problem)
            Outcome.Failed(problem)
        } finally {
            status.value = status.value.copy(running = false)
        }
    }

    private suspend fun recordProblem(
        context: Context,
        problem: String,
    ) {
        runCatching {
            val file = stateFile(context)
            val state = withContext(Dispatchers.IO) { file.read() }
            val updated =
                state.copy(
                    lastRun = RommTwoWaySyncState.LastRun(System.currentTimeMillis(), 0, 0, 0, 0, problem),
                )
            withContext(Dispatchers.IO) { file.write(updated) }
            status.value = status.value.copy(state = updated)
        }
    }

    private fun describe(e: Exception): String =
        when (e) {
            is ProblemException -> e.message ?: "Sync stopped"
            is RommSyncHttpException ->
                when (e.code) {
                    401 -> "RomM rejected the token. Reconnect to RomM."
                    403 -> "The RomM token can't sync saves. Create one with save and device access."
                    404 -> "RomM didn't recognize this device or save. Turn sync off and on again."
                    else -> "RomM returned an error (HTTP ${e.code})."
                }
            is IOException -> "Couldn't reach RomM. Saves will sync next time."
            else -> "Sync stopped unexpectedly."
        }

    private suspend fun openSession(
        context: Context,
        registerIfNeeded: Boolean,
    ): Session {
        val app = context.applicationContext
        val store = RommConnectionStore(app)
        val connection =
            withContext(Dispatchers.IO) { store.load() }
                ?: throw ProblemException("Connect to RomM first.")
        val revision = store.credentialRevision() ?: throw ProblemException("Reconnect to RomM.")
        val fingerprint =
            RommServerIdentity.fingerprint(connection.serverUrl)
                ?: throw ProblemException("The RomM server address is not valid.")
        val client = RommApiClient()
        val accountId = client.checkAccountProfile(connection).userId
        if (accountId == null || store.lastVerifiedUserId() != accountId) {
            throw ProblemException("Reconnect to RomM to confirm your account.")
        }
        val identity = RommDeviceIdentityStore(app)
        val deviceId =
            identity.pinnedDevice(fingerprint, revision, accountId)
                ?: if (registerIfNeeded) {
                    client.registerDevice(app, connection).deviceId
                } else {
                    throw ProblemException("Turn save sync off and on again to register this phone.")
                }
        return Session(connection, deviceId, fingerprint, accountId)
    }

    /** Local games paired with server ROMs by exact file name, ignoring case. */
    private fun pair(
        games: List<Game>,
        remote: List<RommApiClient.Rom>,
    ): List<Pair<Game, RommApiClient.Rom>> {
        val byName = remote.groupBy { it.fileName.lowercase(Locale.ROOT) }
        return games
            .filter { it.id > 0 && it.systemId == "nes" }
            .mapNotNull { game ->
                // Two server ROMs with one file name are ambiguous: skip.
                byName[game.fileName.lowercase(Locale.ROOT)]?.singleOrNull()?.let { game to it }
            }
            .distinctBy { it.second.id }
    }

    private fun refuseIfRunning(
        context: Context,
        game: Game,
    ) {
        val app = context.applicationContext
        val running = ActiveGameSession.current(app)
        if (running?.gameId == game.id || launchingGameId() == game.id) {
            throw ProblemException("Close the game before changing its save.")
        }
        if (running == null && runCatching { GameProcessLock.isHeldByAnotherProcess(app) }.getOrDefault(true)) {
            throw ProblemException("Close the running game first.")
        }
    }

    private suspend fun run(
        context: Context,
        session: Session,
        allPairs: List<Pair<Game, RommApiClient.Rom>>,
    ): Outcome {
        val app = context.applicationContext
        // Never touch the save of a game that is open, or any save if the
        // running game can't be identified.
        val running = ActiveGameSession.current(app)
        val gameRunning = runCatching { GameProcessLock.isHeldByAnotherProcess(app) }.getOrDefault(true)
        if (gameRunning && running == null) throw ProblemException("Close the running game first.")
        val launchingId = launchingGameId()
        val pairs = allPairs.filter { (game, _) -> game.id != running?.gameId && game.id != launchingId }

        val file = stateFile(app)
        var state = withContext(Dispatchers.IO) { file.read() }.forOwner(session.owner)
        if (!state.enabled) return Outcome.Disabled
        val dirs = DirectoriesManager(app)
        val client = RommApiClient()
        var uploaded = 0
        var downloaded = 0
        var failed = 0
        val baselines = state.baselines.toMutableMap()
        val conflicts = state.conflicts.associateBy { it.romId }.toMutableMap()

        for (chunk in pairs.chunked(MAX_ROMS_PER_NEGOTIATION)) {
            val byRom = chunk.associate { (game, rom) -> rom.id to game }
            val snapshots =
                withContext(Dispatchers.IO) {
                    chunk.associate { (game, rom) ->
                        rom.id to
                            runCatching {
                                RommLocalSaveFiles.snapshot(
                                    RommLocalSaveFiles.saveFile(dirs.getSavesDirectory(), game.fileName),
                                )
                            }.getOrNull()
                    }
                }
            val request =
                RommDeviceSyncNegotiationRequest.build(
                    deviceId = session.deviceId,
                    requestedRomIds = byRom.keys,
                    localSaves =
                        chunk.mapNotNull { (game, rom) ->
                            snapshots[rom.id]?.let { snap ->
                                RommDeviceSyncNegotiationRequest.LocalSave(
                                    romId = rom.id,
                                    fileName = RommLocalSaveFiles.saveFileName(game.fileName),
                                    slot = RommApiClient.SYNC_SLOT,
                                    fingerprint = snap.fingerprint,
                                    updatedAt = snap.modifiedUtc,
                                    emulator = RommApiClient.SYNC_EMULATOR,
                                )
                            }
                        },
                    currentServerFingerprint = session.serverFingerprint,
                    currentCredentialRevision = RommConnectionStore(app).credentialRevision(),
                    currentAccountId = session.accountId,
                    pinnedServerFingerprint = session.serverFingerprint,
                    pinnedCredentialRevision = RommConnectionStore(app).credentialRevision(),
                    pinnedAccountId = session.accountId,
                )
            val negotiation = client.negotiateSaveSync(session.connection, request, byRom.keys)
            var completed = 0
            var notDone = 0
            for (operation in negotiation.operations) {
                val game = byRom[operation.romId] ?: continue
                val local = snapshots[operation.romId]
                val step = RommTwoWaySyncPlan.plan(operation, local?.md5, baselines[operation.romId])
                try {
                    when (step) {
                        RommTwoWaySyncPlan.Step.Nothing -> completed++
                        is RommTwoWaySyncPlan.Step.RecordBaseline -> {
                            baselines[operation.romId] = step.md5
                            conflicts.remove(operation.romId)
                            completed++
                        }
                        is RommTwoWaySyncPlan.Step.Upload -> {
                            val target = RommLocalSaveFiles.saveFile(dirs.getSavesDirectory(), game.fileName)
                            val bytes =
                                withContext(Dispatchers.IO) { RommLocalSaveFiles.readForUpload(target, step.md5) }
                            val accepted =
                                client.uploadSyncedSave(
                                    session.connection,
                                    operation.romId,
                                    RommLocalSaveFiles.saveFileName(game.fileName),
                                    bytes,
                                    step.md5,
                                    session.deviceId,
                                    negotiation.sessionId,
                                    overwrite = false,
                                )
                            if (accepted) {
                                baselines[operation.romId] = step.md5
                                conflicts.remove(operation.romId)
                                uploaded++
                                completed++
                            } else {
                                conflicts[operation.romId] =
                                    conflictOf(
                                        game,
                                        operation,
                                        RommTwoWaySyncPlan.Step.Conflict(
                                            operation.saveId,
                                            step.md5,
                                            operation.serverMd5,
                                            RommTwoWaySyncPlan.ConflictReason.SERVER_CHANGED_DURING_UPLOAD,
                                        ),
                                    )
                                notDone++
                            }
                        }
                        is RommTwoWaySyncPlan.Step.Download -> {
                            val md5 =
                                download(
                                    app,
                                    session,
                                    client,
                                    game,
                                    operation.romId,
                                    step.saveId,
                                    step.serverMd5,
                                    local?.md5,
                                    negotiation.sessionId,
                                )
                            baselines[operation.romId] = md5
                            conflicts.remove(operation.romId)
                            downloaded++
                            completed++
                        }
                        is RommTwoWaySyncPlan.Step.Conflict -> {
                            conflicts[operation.romId] = conflictOf(game, operation, step)
                            notDone++
                        }
                        is RommTwoWaySyncPlan.Step.Skip -> notDone++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One game's failure never stops the others.
                    Timber.w("RomM save sync skipped a game: %s", e.javaClass.simpleName)
                    failed++
                    notDone++
                }
            }
            runCatching {
                client.completeSaveSyncSession(
                    session.connection,
                    negotiation.sessionId,
                    completed,
                    notDone,
                )
            }
        }

        val run =
            RommTwoWaySyncState.LastRun(
                atMillis = System.currentTimeMillis(),
                uploaded = uploaded,
                downloaded = downloaded,
                conflicts = conflicts.size,
                failed = failed,
                problem = if (failed > 0) "Some saves couldn't sync and will be tried again." else null,
            )
        state =
            withContext(Dispatchers.IO) { file.read() }.forOwner(session.owner).copy(
                baselines = baselines.entries.take(RommTwoWaySyncState.MAX_BASELINES).associate { it.toPair() },
                conflicts = conflicts.values.take(RommTwoWaySyncState.MAX_CONFLICTS),
                lastRun = run,
            )
        withContext(Dispatchers.IO) { file.write(state) }
        status.value = status.value.copy(state = state)
        return Outcome.Done(run)
    }

    /** Download, verify and install one server save. Returns its MD5. */
    private suspend fun download(
        context: Context,
        session: Session,
        client: RommApiClient,
        game: Game,
        romId: Int,
        saveId: Long,
        expectedMd5: String?,
        currentLocalMd5: String?,
        sessionId: Long? = null,
    ): String {
        val app = context.applicationContext
        val dirs = DirectoriesManager(app)
        val target = RommLocalSaveFiles.saveFile(dirs.getSavesDirectory(), game.fileName)
        val bytes = client.downloadSyncedSave(session.connection, saveId, session.deviceId, sessionId)
        val md5 = RommLocalSaveFiles.md5(bytes)
        if (expectedMd5 != null && md5 != expectedMd5) throw IOException("Downloaded save failed its check")
        // A conflict answer re-reads the phone's current save; a negotiated
        // download insists it is still what negotiation saw.
        val current =
            if (expectedMd5 == null) {
                withContext(Dispatchers.IO) { RommLocalSaveFiles.snapshot(target)?.md5 }
            } else {
                currentLocalMd5
            }
        val backups = RommVersionedSaveBackupStore(File(app.noBackupFilesDir, BACKUP_DIR))
        withContext(Dispatchers.IO) {
            RommLocalSaveFiles.replaceWithDownload(
                target = target,
                downloaded = bytes,
                expectedMd5 = md5,
                currentMd5 = current,
                autosaveFiles = RommLocalSaveFiles.autosaveFiles(dirs.getStatesDirectory(), game.fileName),
                asideDirectory = File(app.noBackupFilesDir, ASIDE_DIR),
                nowMillis = System.currentTimeMillis(),
            ) { original ->
                backups.createVerifiedBackup(romId, original, session.serverFingerprint, session.accountId)
            }
        }
        // If this fails the phone already holds the save; the next run sees
        // identical content and records it, so nothing is lost or repeated.
        runCatching { client.confirmSyncedSaveDownloaded(session.connection, saveId, session.deviceId, md5) }
        return md5
    }

    private fun conflictOf(
        game: Game,
        operation: RommDeviceSyncProtocol.Operation,
        step: RommTwoWaySyncPlan.Step.Conflict,
    ) = RommTwoWaySyncState.Conflict(
        romId = operation.romId,
        gameId = game.id,
        title = game.displayName().take(120),
        saveId = step.saveId,
        localMd5 = step.localMd5,
        serverMd5 = step.serverMd5,
        reason = step.reason,
        detectedAtMillis = System.currentTimeMillis(),
    )
}
