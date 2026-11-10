package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException

/**
 * Public, non-authenticated game recognition via official rcheevos.
 *
 * A locally calculated NES/FDS hash is encoded by the pinned native C API,
 * sent over bounded HTTPS, and parsed by the same pinned C API. A successful
 * game ID means "known to RetroAchievements", NOT "achievements active".
 * This intentionally never uses the website API key or emulator login token.
 */
internal class RetroAchievementsNativeGameResolver(
    private val encode: (String) -> Array<String>? = RetroAchievementsNative::prepareGameResolveRequest,
    private val decode: (ByteArray, Int) -> Int = RetroAchievementsNative::parseGameResolveResponse,
    private val fetch: suspend (String, String) -> RetroAchievementsNativeHttpTransport.Reply = { url, form ->
        RetroAchievementsNativeHttpTransport().execute(url, form)
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
            if (request.size != 2) return Result.Unavailable
            val url = request[0]
            val form = request[1]
            // Fail closed on an unexpected C-library request. This is a
            // public hash lookup, never a credentials-bearing endpoint.
            val endpoint = RetroAchievementsNativeHttpTransport.validatedEndpoint(url)
            if (endpoint.encodedPath != "/dorequest.php" || endpoint.query != null) {
                return Result.Unavailable
            }
            if (!form.matches(Regex("r=gameid&m=[0-9a-fA-F]{32}")) ||
                !form.substringAfter("&m=").equals(md5, ignoreCase = true)
            ) {
                return Result.Unavailable
            }
            val reply = fetch(url, form)
            if (reply.statusCode !in 200..299) return Result.Unavailable
            val id = decode(reply.body, reply.statusCode)
            return when {
                id > 0 -> Result.VerifiedGame(id)
                id == 0 -> Result.NoMatch
                else -> Result.Unavailable
            }
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
