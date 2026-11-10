package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Drives the pinned rc_client's actual token-login callbacks over HTTPS.
 *
 * A separate, short-lived native client verifies the saved login; it never
 * loads a game or touches emulator RAM. The native request queue is bounded,
 * and closing/cancelling the coroutine invalidates all outstanding callbacks.
 */
internal class RetroAchievementsNativeTokenVerifier(
    private val backend: NativeBridge = JniBridge,
    private val network: suspend (String, String) -> RetroAchievementsNativeHttpTransport.Reply = { url, form ->
        RetroAchievementsNativeHttpTransport().execute(url, form)
    },
) {
    internal interface NativeBridge {
        fun start(username: String, token: String): Long

        fun take(session: Long): Array<String>?

        fun complete(
            session: Long,
            id: Long,
            statusCode: Int,
            response: ByteArray,
        ): Boolean

        fun state(session: Long): Int

        fun username(session: Long): String?

        fun stop(session: Long)
    }

    private object JniBridge : NativeBridge {
        override fun start(username: String, token: String) =
            RetroAchievementsNative.beginTokenValidation(username, token)

        override fun take(session: Long) = RetroAchievementsNative.takeValidationRequest(session)

        override fun complete(
            session: Long,
            id: Long,
            statusCode: Int,
            response: ByteArray,
        ) = RetroAchievementsNative.completeValidationRequest(session, id, statusCode, response)

        override fun state(session: Long) = RetroAchievementsNative.tokenValidationState(session)

        override fun username(session: Long) = RetroAchievementsNative.validatedTokenUsername(session)

        override fun stop(session: Long) = RetroAchievementsNative.stopTokenValidation(session)
    }

    sealed interface Result {
        data class Verified(val username: String) : Result

        data object Unavailable : Result
    }

    suspend fun verify(credentials: NativeCredentials): Result =
        validationMutex.withLock {
            withContext(Dispatchers.IO) {
                val session = backend.start(credentials.username, credentials.token)
                if (session <= 0L) return@withContext Result.Unavailable
                try {
                    withTimeout(60_000L) {
                        processSession(session)
                    }
                } catch (_: TimeoutCancellationException) {
                    Result.Unavailable
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Result.Unavailable
                } finally {
                    backend.stop(session)
                }
            }
        }

    private suspend fun processSession(session: Long): Result {
        while (true) {
            when (backend.state(session)) {
                1 -> {
                    val nativeName = backend.username(session)
                    return if (nativeName != null &&
                        RetroAchievementsNativeAccountStore.validUsername(nativeName)
                    ) {
                        Result.Verified(nativeName)
                    } else {
                        Result.Unavailable
                    }
                }
                -1, -2 -> return Result.Unavailable
            }
            val request = backend.take(session)
            if (request == null) {
                delay(35)
                continue
            }
            val id = request.getOrNull(0)?.toLongOrNull()
            if (request.size != 3 || id == null || id <= 0L) {
                return Result.Unavailable
            }
            val url = request[1]
            val form = request[2]
            val permitted = isOfficialLoginRequest(url, form)
            val response =
                if (permitted) {
                    try {
                        network(url, form)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RetroAchievementsNativeHttpTransport.Reply(-1, ByteArray(0))
                    }
                } else {
                    RetroAchievementsNativeHttpTransport.Reply(-1, ByteArray(0))
                }
            if (!backend.complete(session, id, response.statusCode, response.body)) {
                return Result.Unavailable
            }
        }
    }

    companion object {
        // JNI validation is a process-global singleton until game-session
        // ownership is implemented. Never allow concurrent checks to replace
        // another caller's active rc_client pointer.
        private val validationMutex = Mutex()

        internal fun isOfficialLoginRequest(url: String, form: String): Boolean =
            try {
                val endpoint = RetroAchievementsNativeHttpTransport.validatedEndpoint(url)
                endpoint.encodedPath == "/dorequest.php" &&
                    endpoint.query == null &&
                    form.length <= 64 * 1024 &&
                    form.startsWith("r=login2&u=") &&
                    form.contains("&t=") &&
                    !form.contains("&p=")
            } catch (_: IllegalArgumentException) {
                false
            }
    }
}
