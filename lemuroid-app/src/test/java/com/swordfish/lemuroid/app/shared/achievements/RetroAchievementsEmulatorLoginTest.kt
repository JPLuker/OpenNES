package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetroAchievementsEmulatorLoginTest {
    private fun fakeTransport(
        body: String,
        observe: (String) -> Unit = {},
    ): RetroAchievementsNativeHttpTransport {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body?.writeTo(buffer)
                    observe(buffer.readUtf8())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        return RetroAchievementsNativeHttpTransport(client)
    }

    @Test
    fun authenticatesAgainstOfficialLogin2AndPersistsOnlyConfirmedToken() {
        runBlocking {
            var request = ""
            var savedUser = ""
            var savedToken = ""
            val login =
                RetroAchievementsEmulatorLogin(
                    fakeTransport(
                        """{"Success":true,"User":"ActualUser","Token":"1234567890abcdef1234567890abcdef"}""",
                    ) { request = it },
                ) { user, token ->
                    savedUser = user
                    savedToken = token
                }

            assertEquals("ActualUser", login.signIn(" ActualUser ", "pa&ss=word"))
            assertTrue(request.contains("r=login2"))
            assertTrue(request.contains("u=ActualUser"))
            assertTrue(request.contains("p=pa%26ss%3Dword"))
            assertFalse(request.contains("pa&ss=word"))
            assertEquals("ActualUser", savedUser)
            assertEquals("1234567890abcdef1234567890abcdef", savedToken)
        }
    }

    @Test
    fun doesNotPersistCredentialsOnRejectedLogin() {
        runBlocking {
            var saved = false
            val login =
                RetroAchievementsEmulatorLogin(
                    fakeTransport("""{"Success":false,"Error":"Invalid credentials"}"""),
                ) { _, _ -> saved = true }
            var rejected = false
            try {
                login.signIn("Tester", "wrong-password")
            } catch (_: IOException) {
                rejected = true
            }
            assertTrue(rejected)
            assertFalse(saved)
        }
    }

    @Test
    fun rejectsMalformedServerTokenInsteadOfCallingCredentialStore() {
        runBlocking {
            var saved = false
            val login =
                RetroAchievementsEmulatorLogin(
                    fakeTransport("""{"Success":true,"User":"Tester","Token":"short"}"""),
                ) { _, _ -> saved = true }
            var rejected = false
            try {
                login.signIn("Tester", "correct-password")
            } catch (_: IOException) {
                rejected = true
            }
            assertTrue(rejected)
            assertFalse(saved)
        }
    }

    @Test
    fun invalidUsernameIsRejectedWithoutNetworkOrPersistence() {
        runBlocking {
            var attempted = false
            val login =
                RetroAchievementsEmulatorLogin(
                    fakeTransport("""{"Success":true}""") { attempted = true },
                ) { _, _ -> attempted = true }
            var rejected = false
            try {
                login.signIn("Bad User", "password")
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertFalse(attempted)
        }
    }
}
