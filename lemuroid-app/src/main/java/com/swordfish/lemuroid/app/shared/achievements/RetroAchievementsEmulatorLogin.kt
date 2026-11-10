package com.swordfish.lemuroid.app.shared.achievements

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Actual RetroAchievements emulator-account authentication using the official
 * rcheevos `login2` protocol. This is deliberately NOT the read-only website
 * API key flow. A password is sent once over the pinned HTTPS POST transport;
 * only the server-confirmed reusable token is passed to encrypted storage.
 *
 * Authentication alone does not enable earning achievements: native rc_client
 * still requires live game identification and frame/event integration.
 */
internal class RetroAchievementsEmulatorLogin(
    private val transport: RetroAchievementsNativeHttpTransport,
    private val saveConfirmedToken: (username: String, token: String) -> Unit,
) {
    suspend fun signIn(
        rawUsername: String,
        password: String,
    ): String {
        val username = rawUsername.trim()
        require(RetroAchievementsNativeAccountStore.validUsername(username)) {
            "Enter a valid RetroAchievements username"
        }
        require(password.length in 1..512 && password.none { it == '\u0000' }) {
            "Enter a valid RetroAchievements password"
        }

        // The pinned rcheevos 12.5.0 rc_api_init_login_request_hosted()
        // sends r=login2, u=<username>, p=<password> as URL-encoded POST data.
        // Never put the password in a URL, log entry or persistent preference.
        val form =
            listOf(
                "r" to "login2",
                "u" to username,
                "p" to password,
            ).joinToString("&") { (key, value) ->
                key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            }
        val reply = transport.execute(OFFICIAL_LOGIN_ENDPOINT, form)
        if (reply.statusCode !in 200..299) {
            throw IOException("RetroAchievements sign-in unavailable")
        }
        val document =
            try {
                JSONObject(String(reply.body, Charsets.UTF_8))
            } catch (_: Exception) {
                throw IOException("RetroAchievements returned an invalid login response")
            }
        if (!document.optBoolean("Success")) {
            throw IOException("RetroAchievements rejected this username or password")
        }
        val confirmedName = document.optString("User")
        val token = document.optString("Token")
        if (!RetroAchievementsNativeAccountStore.validUsername(confirmedName) ||
            !RetroAchievementsNativeAccountStore.validToken(token)
        ) {
            throw IOException("RetroAchievements returned invalid emulator credentials")
        }
        saveConfirmedToken(confirmedName, token)
        return confirmedName
    }

    companion object {
        private const val OFFICIAL_LOGIN_ENDPOINT = "https://retroachievements.org/dorequest.php"
    }
}
