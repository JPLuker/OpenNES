package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

/**
 * Remote *catalogue*, not an emulator library database.
 *
 * Never store fake remote games in Room: the indexer purges missing files and
 * the emulator must never receive a remote-only fileUri. Only explicit Download
 * actions write ROM bytes, via RommApiClient's verified SAF import.
 */
internal object RommLibraryCatalog {
    data class State(
        val source: String? = null,
        val games: List<RommApiClient.Rom> = emptyList(),
        val refreshing: Boolean = false,
        val downloadingIds: Set<Int> = emptySet(),
        val awaitingIndexIds: Set<Int> = emptySet(),
        val error: String? = null,
        val downloadError: String? = null,
        val transferProgress: Map<Int, RommApiClient.ImportProgress> = emptyMap(),
        val downloadFailures: Map<Int, String> = emptyMap(),
        val indexingDelayedIds: Set<Int> = emptySet(),
    )

    private const val PAGE_SIZE = 100
    private const val MAX_GAMES = 10_000
    private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

    private val refreshMutex = Mutex()
    private val downloadMutex = Mutex()
    private var refreshedAt = 0L
    private var credentialRevision: String? = null
    private val awaitingIndexSince = mutableMapOf<Int, Long>()
    private const val INDEXING_NOTICE_DELAY_MS = 25_000L
    private val snapshot = MutableStateFlow(State())
    val state = snapshot.asStateFlow()

    /**
     * Filename is the only identity shared with the existing Room schema.
     * Never suppress a remote title merely because its display name matches.
     */
    fun available(
        remote: List<RommApiClient.Rom>,
        indexedFileNames: Set<String>,
    ): List<RommApiClient.Rom> {
        val indexed = indexedFileNames.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        return remote.filterNot { it.fileName.lowercase(Locale.ROOT) in indexed }
    }

    fun asPreview(rom: RommApiClient.Rom): Game =
        Game(
            id = -rom.id,
            fileName = rom.fileName,
            fileUri = "romm-preview://game/${rom.id}",
            title = rom.name,
            systemId = "nes",
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = 0,
            description = rom.description,
        )

    fun remoteFor(
        game: Game,
        current: State,
    ): RommApiClient.Rom? {
        if (game.id >= 0 || !game.fileUri.startsWith("romm-preview://game/")) return null
        return current.games.firstOrNull {
            it.id == -game.id && it.fileName == game.fileName
        }
    }

    suspend fun refresh(
        context: Context,
        force: Boolean = false,
    ) {
        refreshMutex.withLock {
            val store = RommConnectionStore(context.applicationContext)
            val source = RommServerIdentity.fingerprint(store.configuredServer())
            if (source == null || !store.hasSavedToken()) {
                snapshot.value = State()
                refreshedAt = 0L
                credentialRevision = null
                awaitingIndexSince.clear()
                return@withLock
            }
            val revision = store.credentialRevision()
            val previous =
                snapshot.value.takeIf {
                    it.source == source && credentialRevision == revision
                } ?: State(source = source)
            if (credentialRevision != revision || snapshot.value.source != source) {
                refreshedAt = 0L
                credentialRevision = revision
                awaitingIndexSince.clear()
            }
            snapshot.value = previous
            if (!force && !previous.refreshing && refreshedAt > 0 &&
                System.currentTimeMillis() - refreshedAt < REFRESH_INTERVAL_MS
            ) {
                return@withLock
            }
            snapshot.value = previous.copy(refreshing = true, error = null)
            try {
                val connection =
                    withContext(Dispatchers.IO) {
                        store.load() ?: error("RomM session is not configured")
                    }
                // An account/server switch during the request cannot publish
                // a catalogue belonging to the old credentials.
                require(RommServerIdentity.fingerprint(connection.serverUrl) == source)
                val api = RommApiClient()
                val found = linkedMapOf<Int, RommApiClient.Rom>()
                var offset = 0
                while (offset < MAX_GAMES) {
                    val page = api.listNesRoms(connection, offset, PAGE_SIZE)
                    require(page.offset == offset && page.total >= 0) {
                        "Unexpected RomM catalogue page"
                    }
                    require(page.total <= MAX_GAMES) { "RomM library exceeds supported catalogue size" }
                    page.items.forEach { found[it.id] = it }
                    offset += PAGE_SIZE
                    if (page.items.size < PAGE_SIZE || offset >= page.total) break
                }
                require(
                    RommServerIdentity.fingerprint(store.configuredServer()) == source &&
                        store.hasSavedToken() &&
                        store.credentialRevision() == revision,
                ) { "RomM connection changed during refresh" }
                val latest = snapshot.value
                snapshot.value =
                    latest.copy(
                        source = source,
                        games = found.values.sortedBy { it.name.lowercase(Locale.ROOT) },
                        refreshing = false,
                        error = null,
                    )
                refreshedAt = System.currentTimeMillis()
            } catch (cancelled: CancellationException) {
                snapshot.value = snapshot.value.copy(refreshing = false)
                throw cancelled
            } catch (_: Exception) {
                snapshot.value =
                    snapshot.value.copy(
                        refreshing = false,
                        error = "RomM library unavailable. Check the connection in Settings.",
                    )
            }
        }
    }

    /**
     * Keep pending imports visible until Room confirms the scanned file is
     * indexed. A slow scan offers a retry action rather than a silent disabled
     * button. A remote file is never redownloaded just to force indexing.
     */
    fun reconcileIndexed(
        indexedFileNames: Set<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        val names = indexedFileNames.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        snapshot.update { current ->
            val indexedIds =
                current.games.asSequence()
                    .filter { it.id in current.awaitingIndexIds && it.fileName.lowercase(Locale.ROOT) in names }
                    .map { it.id }
                    .toSet()
            indexedIds.forEach { awaitingIndexSince.remove(it) }
            val pending = current.awaitingIndexIds - indexedIds
            val delayed =
                pending.filterTo(mutableSetOf()) {
                    now - (awaitingIndexSince[it] ?: now) >= INDEXING_NOTICE_DELAY_MS
                }
            if (indexedIds.isEmpty() && delayed == current.indexingDelayedIds) {
                current
            } else {
                current.copy(
                    awaitingIndexIds = pending,
                    indexingDelayedIds = delayed,
                    transferProgress = current.transferProgress - indexedIds,
                )
            }
        }
    }

    fun retryLibraryIndex(
        context: Context,
        romId: Int,
    ) {
        if (romId !in snapshot.value.awaitingIndexIds) return
        try {
            com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler.scheduleLibrarySync(
                context.applicationContext,
            )
            awaitingIndexSince[romId] = System.currentTimeMillis()
            snapshot.update {
                it.copy(indexingDelayedIds = it.indexingDelayedIds - romId)
            }
        } catch (_: Exception) {
            snapshot.update {
                it.copy(downloadFailures = it.downloadFailures + (romId to "Could not restart library indexing. Check your ROM folder in Settings."))
            }
        }
    }

    private fun safeDownloadError(error: Exception): String {
        val detail = (error.message ?: "").lowercase(Locale.ROOT)
        return when {
            error is SocketTimeoutException -> "RomM connection timed out. Check your network and retry."
            "checksum mismatch" in detail -> "ROM verification failed: checksum mismatch. No game was imported."
            "unsupported" in detail || "invalid rom" in detail -> "RomM returned an invalid or unsupported NES ROM."
            "already exists" in detail || "appeared during download" in detail ->
                "The ROM file already exists. Rescan your NES library instead of downloading another copy."
            "not writable" in detail || "cannot write" in detail || "choose an opennes rom folder" in detail ->
                "OpenNES cannot write to the chosen ROM folder. Select a writable NES folder in Settings."
            "401" in detail || "403" in detail -> "RomM access was denied. Reconnect in Settings and check token permissions."
            "404" in detail -> "RomM could not find this ROM. Refresh the library and try again."
            "exceeds import size" in detail -> "This ROM exceeds OpenNES's supported file size."
            error is SecurityException -> "ROM folder access expired. Select the folder again in Settings."
            error is IOException -> "Download or file import failed. Check network access, free space, and folder permissions."
            else -> "Download failed. Check the RomM connection and your NES folder, then retry."
        }
    }

    suspend fun download(
        context: Context,
        rom: RommApiClient.Rom,
    ) {
        downloadMutex.withLock {
            val current = snapshot.value
            if (rom.id in current.downloadingIds || rom.id in current.awaitingIndexIds) return@withLock
            val store = RommConnectionStore(context.applicationContext)
            val source = RommServerIdentity.fingerprint(store.configuredServer())
            if (
                source == null ||
                current.source != source ||
                credentialRevision != store.credentialRevision() ||
                current.games.none { it.id == rom.id && it.fileName == rom.fileName }
            ) {
                snapshot.value =
                    current.copy(
                        downloadFailures =
                            current.downloadFailures + (rom.id to "RomM connection changed. Refresh the library and retry."),
                    )
                return@withLock
            }
            snapshot.value =
                current.copy(
                    downloadingIds = current.downloadingIds + rom.id,
                    downloadError = null,
                    downloadFailures = current.downloadFailures - rom.id,
                    transferProgress =
                        current.transferProgress +
                            (rom.id to RommApiClient.ImportProgress(RommApiClient.ImportStage.CONNECTING)),
                )
            try {
                val connection =
                    withContext(Dispatchers.IO) {
                        store.load() ?: error("RomM credentials unavailable")
                    }
                require(RommServerIdentity.fingerprint(connection.serverUrl) == source)
                RommApiClient().importIntoSelectedNesFolder(
                    context.applicationContext,
                    connection,
                    rom,
                ) { progress ->
                    snapshot.update { currentState ->
                        currentState.copy(
                            transferProgress = currentState.transferProgress + (rom.id to progress),
                        )
                    }
                }
                awaitingIndexSince[rom.id] = System.currentTimeMillis()
                snapshot.update { currentState ->
                    currentState.copy(
                        downloadingIds = currentState.downloadingIds - rom.id,
                        awaitingIndexIds = currentState.awaitingIndexIds + rom.id,
                        transferProgress =
                            currentState.transferProgress +
                                (rom.id to RommApiClient.ImportProgress(RommApiClient.ImportStage.INDEXING)),
                    )
                }
            } catch (cancelled: CancellationException) {
                snapshot.update { currentState ->
                    currentState.copy(
                        downloadingIds = currentState.downloadingIds - rom.id,
                        transferProgress = currentState.transferProgress - rom.id,
                    )
                }
                throw cancelled
            } catch (error: Exception) {
                snapshot.update { currentState ->
                    currentState.copy(
                        downloadingIds = currentState.downloadingIds - rom.id,
                        transferProgress = currentState.transferProgress - rom.id,
                        downloadFailures =
                            currentState.downloadFailures + (rom.id to safeDownloadError(error)),
                    )
                }
            }
        }
    }
}
