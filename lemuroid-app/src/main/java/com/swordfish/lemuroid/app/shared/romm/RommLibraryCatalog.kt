package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    )

    private const val PAGE_SIZE = 100
    private const val MAX_GAMES = 10_000
    private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

    private val refreshMutex = Mutex()
    private val downloadMutex = Mutex()
    private var refreshedAt = 0L
    private var credentialRevision: String? = null
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
        )

    fun remoteFor(game: Game, current: State): RommApiClient.Rom? {
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

    suspend fun download(
        context: Context,
        rom: RommApiClient.Rom,
    ) {
        downloadMutex.withLock {
            val current = snapshot.value
            if (rom.id in current.downloadingIds || rom.id in current.awaitingIndexIds) return@withLock
            val store = RommConnectionStore(context.applicationContext)
            val source = RommServerIdentity.fingerprint(store.configuredServer())
            if (source == null || current.source != source ||
                credentialRevision != store.credentialRevision() || current.games.none {
                    it.id == rom.id && it.fileName == rom.fileName
                }
            ) {
                snapshot.value = current.copy(downloadError = "RomM library changed. Refresh and try again.")
                return@withLock
            }
            snapshot.value =
                current.copy(
                    downloadingIds = current.downloadingIds + rom.id,
                    downloadError = null,
                )
            try {
                val connection =
                    withContext(Dispatchers.IO) {
                        store.load() ?: error("RomM credentials unavailable")
                    }
                require(RommServerIdentity.fingerprint(connection.serverUrl) == source)
                RommApiClient().importIntoSelectedNesFolder(context.applicationContext, connection, rom)
                snapshot.value =
                    snapshot.value.copy(
                        downloadingIds = snapshot.value.downloadingIds - rom.id,
                        awaitingIndexIds = snapshot.value.awaitingIndexIds + rom.id,
                    )
            } catch (cancelled: CancellationException) {
                snapshot.value =
                    snapshot.value.copy(downloadingIds = snapshot.value.downloadingIds - rom.id)
                throw cancelled
            } catch (_: Exception) {
                snapshot.value =
                    snapshot.value.copy(
                        downloadingIds = snapshot.value.downloadingIds - rom.id,
                        downloadError =
                            "Download failed. Check the RomM connection, available space, " +
                                "and your selected writable NES ROM folder.",
                    )
            }
        }
    }
}
