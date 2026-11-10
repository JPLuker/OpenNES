package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException

/**
 * One explicit end-to-end login attempt for the Settings screen.
 *
 * The official password exchange must succeed and persist the server's
 * confirmed token before the pinned native rc_client can validate it.
 * A failed/temporary native check never pretends to be verified and never
 * discards the saved token; the user can retry without retyping a password.
 */
internal class RetroAchievementsLoginFlow(
    private val passwordSignIn: suspend (String, String) -> String,
    private val readSavedCredentials: () -> NativeCredentials?,
    private val nativeVerify: suspend (NativeCredentials) -> RetroAchievementsNativeTokenVerifier.Result,
) {
    sealed interface Result {
        data class Verified(val username: String) : Result

        data class SavedButUnverified(val username: String) : Result
    }

    suspend fun signInAndVerify(
        username: String,
        password: String,
    ): Result {
        val signedInName = passwordSignIn(username, password)
        val saved = readSavedCredentials()
        if (saved == null || !saved.username.equals(signedInName, ignoreCase = true)) {
            return Result.SavedButUnverified(signedInName)
        }
        val verified =
            try {
                nativeVerify(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RetroAchievementsNativeTokenVerifier.Result.Unavailable
            }
        return if (verified is RetroAchievementsNativeTokenVerifier.Result.Verified &&
            verified.username.equals(signedInName, ignoreCase = true)
        ) {
            Result.Verified(verified.username)
        } else {
            Result.SavedButUnverified(signedInName)
        }
    }
}
