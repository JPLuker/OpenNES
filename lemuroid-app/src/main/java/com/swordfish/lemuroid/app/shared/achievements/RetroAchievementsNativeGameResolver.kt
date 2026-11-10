package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Public, non-authenticated game recognition via official rcheevos.
 *
 * A locally calculated NES/FDS hash is encoded by the pinned native C API,
 * sent over bounded HTTPS, and parsed by the same pinned C API. A successful
 * game ID means "known to RetroAchievements", NOT "achievements active".
 * This intentionally never uses the website API key or emulator login token.
 */
internal class RetroAchievementsNativeGameResolver(
    private val encode: (String) -> String? = RetroAchievementsNative::prepareGameResolveRequest,
    private val decode: (ByteArray, Int) -> Int = RetroAchievementsNative::parseGameResolveResponse,
    private val fetch: suspend (String) -> RetroAchievementsNativeHttpTransport.Reply = {
        RetroAchievementsNativeHttpTransport().execute(it, null)
    },
) {
    sealed interface Result {
        data class VerifiedGame(val id: Int) : Result

        data object NoMatch : Result

        data object Unavailable : Result
    }

    suspend fun resolve(identity: NesRetroAchievementsHash.Identity): Result {
        val md5 = identity.md5.trim()
        if (!md5.matches(HASH_PATTERN)) return Result.Unavailable
        try {
            val request = encode(md5) ?: return Result.Unavailable
            // Apply the transport's strict origin restriction even if an
            // unexpected native API request format is returned.
            RetroAchievementsNativeHttpTransport.validatedEndpoint(request)
            val reply = fetch(request)
            if (reply.statusCode !in 200..299) return Result.Unavailable
            val id = decode(reply.body, reply.statusCode)
            return if (id > 0) Result.VerifiedGame(id) else Result.NoMatch
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // May contain credentials in URL parameters. Never log raw error
            // strings or server bodies here.
            return Result.Unavailable
        }
    }

    companion object {
        private val HASH_PATTERN = Regex("[0-9a-f]{32}")
    }
}
