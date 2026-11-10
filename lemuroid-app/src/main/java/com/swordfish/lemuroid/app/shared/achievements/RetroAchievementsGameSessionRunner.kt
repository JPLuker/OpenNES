package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.widget.Toast
import com.swordfish.lemuroid.R
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
        val credentials =
            try {
                withContext(Dispatchers.IO) {
                    RetroAchievementsNativeAccountStore(context).load()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return

        val identity =
            try {
                NesRetroAchievementsHash.forGame(context, game)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        // FCEUmm's FDS memory map is not yet validated. Never evaluate FDS
        // achievements through the NES (console 7) RAM mapping.
        if (!supportsNativeGame(identity)) return
        val md5 = identity.md5
        val ticket = RetroAchievementsGameStateStore.begin(md5)
        var session = 0L
        try {
            withContext(Dispatchers.IO) {
                session =
                    RetroAchievementsNative.startGameSession(
                        credentials.username,
                        credentials.token,
                        md5,
                    )
                if (session <= 0L) return@withContext
                pollGame(session, ticket, md5, credentials.username)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Don't expose tokens, raw POST forms, or potentially sensitive
            // server error messages through UI, logs or crash reporting.
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (session > 0) RetroAchievementsNative.stopGameSession(session)
                RetroAchievementsGameStateStore.clear(ticket)
            }
        }
    }

    private suspend fun pollGame(
        session: Long,
        ticket: Long,
        md5: String,
        username: String,
    ) {
        var active = false
        val connection = NativeConnectionGate()
        var lastRefresh = 0L
        var lastIdle = 0L
        val started = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            if (lastIdle == 0L || elapsedMillis(lastIdle, now) >= 250L) {
                // Process rc_client timers during pause and while a server
                // response is pending. Native never reads RAM outside frames.
                RetroAchievementsNative.idleGameSession(session)
                lastIdle = now
            }
            val status = RetroAchievementsNative.gameSessionStatus(session)
            when (status) {
                -2, -1 -> return
                2 -> {
                    if (RetroAchievementsNative.loadedGameId(session) <= 0) return
                    active = true
                }
            }
            if (!active && elapsedSeconds(started) > 120) return

            // Native callbacks are owned by this session. A cancelled or
            // superseded game cannot deliver a response to another client.
            val request = RetroAchievementsNative.takeGameRequest(session)
            if (request != null) {
                val id = request.getOrNull(0)?.toLongOrNull()
                if (request.size != 3 || id == null || id <= 0) return
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
                    return
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
                        }
                        is NativeGameEvent.Unlock -> {
                            if (connection.observe(event)) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.opennes_ra_live_unlock_toast, event.title),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            // Refresh actual native state after a frame event;
                            // never synthesize an unlock from an event string.
                            lastRefresh = 0L
                        }
                        null -> Unit
                    }
                }
                if (connection.isConnected) {
                    val now = System.nanoTime()
                    if (lastRefresh == 0L || elapsedMillis(lastRefresh, now) >= 1_000L) {
                        val achievements =
                            RetroAchievementsNative.gameAchievementRows(session)
                                ?.mapNotNull(::decodeAchievementRow)
                                ?: emptyList()
                        RetroAchievementsGameStateStore.publish(
                            ticket,
                            md5,
                            RetroAchievementsDisplayState.Active(md5, username, achievements),
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

        internal fun decodeAchievementRow(raw: String): VerifiedAchievement? {
            val fields = raw.split('\t', limit = 5)
            if (fields.size != 5) return null
            val id = fields[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
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
