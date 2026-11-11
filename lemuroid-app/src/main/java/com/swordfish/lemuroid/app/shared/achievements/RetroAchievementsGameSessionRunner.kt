package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Owns an actual rc_client session for exactly one loaded NES ROM.
 *
 * Only native rc_client may activate the game, evaluate core memory, and
 * generate unlocks. HTTP, ROM hashing and UI polling stay off the GL thread.
 * The native frame observer is detached before its client is destroyed.
 */
internal class RetroAchievementsGameSessionRunner(
    private val context: Context,
    private val transport: RetroAchievementsNativeHttpTransport = RetroAchievementsNativeHttpTransport(),
) {
    suspend fun run(game: Game) {
        val identity =
            try {
                NesRetroAchievementsHash.forGame(context, game)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        // FDS memory has not been validated against the live core.
        if (!supportsNativeGame(identity)) return

        val md5 = identity.md5
        val accountStore = RetroAchievementsNativeAccountStore(context)
        var previousCredentials: NativeCredentials? = null
        var previousRetryVersion = 0L

        // Bound to the game ViewModel: signing in, signing out, switching
        // accounts and manually retrying can all happen without restarting
        // an already running ROM. Failed native logins never auto-hammer the
        // server: wait for changed credentials or an explicit Retry tap.
        while (true) {
            val credentials = awaitCredentials(accountStore, previousCredentials, md5, previousRetryVersion)
            val ticket = RetroAchievementsGameStateStore.begin(md5)
            var session = 0L
            var outcome = SessionEnd.Failed
            try {
                withContext(Dispatchers.IO) {
                    session =
                        RetroAchievementsNative.startGameSession(
                            credentials.username,
                            credentials.token,
                            md5,
                        )
                    if (session > 0L) {
                        outcome = pollGame(session, ticket, md5, credentials, accountStore)
                    }
                }
            } catch (e: CancellationException) {
                outcome = SessionEnd.GameStopped
                throw e
            } catch (_: Exception) {
                // Do not expose raw HTTP forms, tokens or server messages.
                outcome = SessionEnd.Failed
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (session > 0L) RetroAchievementsNative.stopGameSession(session)
                    if (outcome == SessionEnd.Failed) {
                        RetroAchievementsGameStateStore.finishFailed(ticket, md5)
                    } else {
                        RetroAchievementsGameStateStore.clear(ticket)
                    }
                }
            }
            if (outcome == SessionEnd.GameStopped) return
            previousCredentials = credentials
            previousRetryVersion = RetroAchievementsGameStateStore.retryVersion(md5)
        }
    }

    internal enum class SessionEnd {
        Failed,
        AccountChanged,
        GameStopped,
    }

    /**
     * Recheck saved credentials on a modest cadence. No JNI client, network
     * connection or token verification is created while signed out. After a
     * failure, identical credentials need an explicit Retry request.
     */
    private suspend fun awaitCredentials(
        accountStore: RetroAchievementsNativeAccountStore,
        previous: NativeCredentials?,
        md5: String,
        retryVersion: Long,
    ): NativeCredentials {
        var observedSignOut = false
        while (true) {
            val available = loadCredentials(accountStore)
            if (available == null) observedSignOut = true
            if (
                available != null &&
                (
                    previous == null ||
                        observedSignOut ||
                        !sameCredentials(available, previous) ||
                        RetroAchievementsGameStateStore.retryVersion(md5) != retryVersion
                )
            ) {
                return available
            }
            delay(2_000)
        }
    }

    private suspend fun loadCredentials(store: RetroAchievementsNativeAccountStore): NativeCredentials? =
        try {
            withContext(Dispatchers.IO) { store.load() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun pollGame(
        session: Long,
        ticket: Long,
        md5: String,
        credentials: NativeCredentials,
        accountStore: RetroAchievementsNativeAccountStore,
    ): SessionEnd {
        var active = false
        val connection = NativeConnectionGate()
        var lastRefresh = 0L
        var lastIdle = 0L
        var lastAccountCheck = 0L
        var lastLeaderboardRefresh = 0L
        var leaderboards = emptyList<VerifiedLeaderboard>()
        val started = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            if (lastIdle == 0L || elapsedMillis(lastIdle, now) >= 250L) {
                // Process rc_client timers during pause and while a server
                // response is pending. Native never reads RAM outside frames.
                RetroAchievementsNative.idleGameSession(session)
                lastIdle = now
            }
            if (lastAccountCheck == 0L || elapsedMillis(lastAccountCheck, now) >= 3_000L) {
                val currentCredentials = loadCredentials(accountStore)
                if (currentCredentials == null || !sameCredentials(currentCredentials, credentials)) {
                    return SessionEnd.AccountChanged
                }
                lastAccountCheck = now
            }
            val status = RetroAchievementsNative.gameSessionStatus(session)
            when (status) {
                -2 -> return SessionEnd.GameStopped // Session replaced or game exited.
                -1 -> return SessionEnd.Failed // Native login, game load or memory failure.
                2 -> {
                    if (RetroAchievementsNative.loadedGameId(session) <= 0) return SessionEnd.Failed
                    active = true
                }
            }
            if (!active && elapsedSeconds(started) > 120) return SessionEnd.Failed

            // Native callbacks are owned by this session. A cancelled or
            // superseded game cannot deliver a response to another client.
            val request = RetroAchievementsNative.takeGameRequest(session)
            if (request != null) {
                val id = request.getOrNull(0)?.toLongOrNull()
                if (request.size != 3 || id == null || id <= 0) return SessionEnd.Failed
                val reply =
                    try {
                        if (!isOfficialGameRequest(request[1], request[2])) {
                            RetroAchievementsNativeHttpTransport.Reply(-1, ByteArray(0))
                        } else {
                            transport.execute(request[1], request[2])
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RetroAchievementsNativeHttpTransport.Reply(-1, ByteArray(0))
                    }
                if (!RetroAchievementsNative.completeGameRequest(session, id, reply.statusCode, reply.body)) {
                    return SessionEnd.GameStopped // Stale response after new ROM or teardown.
                }
            }

            if (active) {
                // Process network events *before* publishing an Active
                // snapshot. Once the official client reports a disconnect,
                // hide active awards until it explicitly reports reconnection.
                while (true) {
                    val raw = RetroAchievementsNative.takeGameEvent(session) ?: break
                    when (val event = decodeNativeEvent(raw)) {
                        NativeGameEvent.Disconnected -> {
                            connection.observe(event)
                            RetroAchievementsGameStateStore.publish(
                                ticket,
                                md5,
                                RetroAchievementsDisplayState.NotConnected,
                            )
                        }
                        NativeGameEvent.Reconnected -> {
                            connection.observe(event)
                            lastRefresh = 0L
                            lastLeaderboardRefresh = 0L
                        }
                        is NativeGameEvent.Unlock -> {
                            if (connection.observe(event)) {
                                RetroAchievementsGameStateStore.notifyUnlock(
                                    ticket = ticket,
                                    romMd5 = md5,
                                    achievementId = event.id,
                                    title = event.title,
                                )
                            }
                            // Refresh native state rather than inferring award
                            // persistence from a UI event.
                            lastRefresh = 0L
                        }
                        null -> Unit
                    }
                }
                if (connection.isConnected) {
                    val now = System.nanoTime()
                    if (lastRefresh == 0L || elapsedMillis(lastRefresh, now) >= 1_000L) {
                        val rows = RetroAchievementsNative.gameAchievementRows(session)?.toList().orEmpty()
                        val achievements = rows.mapNotNull(::decodeAchievementRow)
                        val notices = rows.mapNotNull(::decodeCompatibilityNotice).distinct().take(3)
                        if (lastLeaderboardRefresh == 0L ||
                            elapsedMillis(lastLeaderboardRefresh, now) >= 5_000L
                        ) {
                            leaderboards =
                                RetroAchievementsNative.gameLeaderboardRows(session)
                                    ?.mapNotNull(::decodeLeaderboardRow)
                                    ?: emptyList()
                            lastLeaderboardRefresh = now
                        }
                        val presence =
                            RetroAchievementsNative.gameRichPresence(session)
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) }
                        RetroAchievementsGameStateStore.publish(
                            ticket,
                            md5,
                            RetroAchievementsDisplayState.Active(
                                md5,
                                credentials.username,
                                achievements,
                                compatibilityNotices = notices,
                                richPresence = presence,
                                leaderboards = leaderboards,
                            ),
                        )
                        lastRefresh = now
                    }
                }
            }
            delay(if (request != null) 10 else 50)
        }
    }

    internal sealed interface NativeGameEvent {
        data class Unlock(val id: Int, val title: String) : NativeGameEvent

        data object Disconnected : NativeGameEvent

        data object Reconnected : NativeGameEvent
    }

    /** The client must explicitly reconnect before its progress is displayed again. */
    internal class NativeConnectionGate {
        var isConnected = true
            private set

        fun observe(event: NativeGameEvent): Boolean {
            when (event) {
                NativeGameEvent.Disconnected -> isConnected = false
                NativeGameEvent.Reconnected -> isConnected = true
                is NativeGameEvent.Unlock -> Unit
            }
            return isConnected
        }
    }

    companion object {
        internal fun sameCredentials(
            first: NativeCredentials,
            second: NativeCredentials,
        ): Boolean {
            return first.username == second.username && first.token == second.token
        }

        internal fun supportsNativeGame(identity: NesRetroAchievementsHash.Identity): Boolean =
            identity.console == NesRetroAchievementsHash.Console.NES

        internal fun decodeNativeEvent(raw: String): NativeGameEvent? {
            if (raw == "disconnected") return NativeGameEvent.Disconnected
            if (raw == "reconnected") return NativeGameEvent.Reconnected
            val fields = raw.split('\t', limit = 4)
            if (fields.size != 4 || fields[0] != "unlock") return null
            val id = fields[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            if (fields[2].toIntOrNull()?.let { it in 0..500 } != true) return null
            val title = fields[3].trim().takeIf { it.isNotEmpty() } ?: return null
            if (title.any { it.isISOControl() }) return null
            return NativeGameEvent.Unlock(id, title.take(220))
        }

        private fun elapsedSeconds(started: Long): Long = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)

        private fun elapsedMillis(
            from: Long,
            to: Long,
        ): Long = TimeUnit.NANOSECONDS.toMillis(to - from)

        /**
         * Only the official rcheevos POST service can receive a native
         * request. Accept award, session, ping and login request variants
         * from the pinned library without building URLs in Kotlin.
         */
        internal fun isOfficialGameRequest(
            url: String,
            form: String,
        ): Boolean =
            try {
                val endpoint = RetroAchievementsNativeHttpTransport.validatedEndpoint(url)
                endpoint.encodedPath == "/dorequest.php" &&
                    endpoint.query == null &&
                    form.length in 3..(64 * 1024) &&
                    form.startsWith("r=") &&
                    !form.contains("&p=")
            } catch (_: IllegalArgumentException) {
                false
            }

        internal fun decodeLeaderboardRow(raw: String): VerifiedLeaderboard? {
            val fields = raw.split('\t', limit = 5)
            if (fields.size != 5) return null
            val id = fields[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val state = fields[1].toIntOrNull()?.takeIf { it in 0..3 } ?: return null
            val title = fields[2].trim().takeIf { it.isNotEmpty() } ?: return null
            val description = fields[3].trim()
            val tracker = fields[4].trim().takeIf { it.isNotEmpty() }
            if (title.any(Char::isISOControl) ||
                description.any(Char::isISOControl) ||
                tracker?.any(Char::isISOControl) == true
            ) {
                return null
            }
            return VerifiedLeaderboard(
                id = id,
                title = title.take(220),
                description = description.take(640),
                state = state,
                trackerValue = tracker?.take(64),
            )
        }

        // rcheevos reserves IDs >= 101000001 for client-generated notices,
        // such as "Warning: Unknown Emulator". They never count as awards.
        internal const val CLIENT_WARNING_ID = 101000001

        internal fun decodeCompatibilityNotice(raw: String): String? {
            val fields = raw.split('\t', limit = 3)
            if (fields.size != 3 || fields[0] != "notice") return null
            val heading = fields[1].trim().take(220)
            val detail = fields[2].trim().take(640)
            if (heading.isEmpty() || detail.isEmpty() ||
                heading.any { it.isISOControl() } || detail.any { it.isISOControl() }
            ) {
                return null
            }
            return "$heading: $detail"
        }

        internal fun decodeAchievementRow(raw: String): VerifiedAchievement? {
            val fields = raw.split('\t', limit = 5)
            if (fields.size != 5) return null
            val id = fields[0].toIntOrNull()?.takeIf { it in 1 until CLIENT_WARNING_ID } ?: return null
            val points = fields[1].toIntOrNull()?.takeIf { it in 0..500 } ?: return null
            if (fields[2] != "0" && fields[2] != "1") return null
            return VerifiedAchievement(
                id = id,
                points = points,
                title = fields[3].take(220),
                description = fields[4].take(640),
                unlocked = fields[2] == "1",
            )
        }
    }
}
