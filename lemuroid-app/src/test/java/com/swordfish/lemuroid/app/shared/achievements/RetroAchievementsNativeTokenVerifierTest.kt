package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsNativeTokenVerifierTest {
    private val credentials = NativeCredentials("TestUser", "1234567890abcdef1234567890abcdef")

    private class FakeBridge(
        private val endpoint: String = "https://retroachievements.org/dorequest.php",
        private val form: String = "r=login2&u=TestUser&t=1234567890abcdef1234567890abcdef",
    ) : RetroAchievementsNativeTokenVerifier.NativeBridge {
        var closed = false
        var status = 0
        var transmitted: Int? = null
        var requestAvailable = true

        override fun start(username: String, token: String): Long = 37L

        override fun take(session: Long): Array<String>? {
            if (!requestAvailable) return null
            requestAvailable = false
            return arrayOf("42", endpoint, form)
        }

        override fun complete(
            session: Long,
            id: Long,
            statusCode: Int,
            response: ByteArray,
        ): Boolean {
            if (session != 37L || id != 42L) return false
            transmitted = statusCode
            status = if (statusCode == 200) 1 else -1
            return true
        }

        override fun state(session: Long) = status

        override fun username(session: Long): String? =
            if (status == 1) "TestUser" else null

        override fun stop(session: Long) {
            closed = true
        }
    }

    @Test
    fun approvedNativeRequestIsPassedToHttpThenSessionCloses() {
        runBlocking {
            val fake = FakeBridge()
            var called = false
            val verifier =
                RetroAchievementsNativeTokenVerifier(fake) { url, form ->
                    called = true
                    assertEquals("https://retroachievements.org/dorequest.php", url)
                    assertTrue(form.startsWith("r=login2&u=TestUser&t="))
                    RetroAchievementsNativeHttpTransport.Reply(200, "{}".toByteArray())
                }
            assertEquals(
                RetroAchievementsNativeTokenVerifier.Result.Verified("TestUser"),
                verifier.verify(credentials),
            )
            assertTrue(called)
            assertEquals(200, fake.transmitted)
            assertTrue(fake.closed)
        }
    }

    @Test
    fun foreignEndpointFailsClosedWithoutNetworkRequest() {
        runBlocking {
            val fake = FakeBridge(endpoint = "https://unknown.example/dorequest.php")
            var called = false
            val verifier =
                RetroAchievementsNativeTokenVerifier(fake) { _, _ ->
                    called = true
                    RetroAchievementsNativeHttpTransport.Reply(200, ByteArray(1))
                }
            assertEquals(
                RetroAchievementsNativeTokenVerifier.Result.Unavailable,
                verifier.verify(credentials),
            )
            assertFalse(called)
            assertEquals(-1, fake.transmitted)
            assertTrue(fake.closed)
        }
    }

    @Test
    fun loginPasswordFormIsRejectedForTokenOnlyValidation() {
        assertFalse(
            RetroAchievementsNativeTokenVerifier.isOfficialLoginRequest(
                "https://retroachievements.org/dorequest.php",
                "r=login2&u=TestUser&p=plaintext",
            ),
        )
        assertTrue(
            RetroAchievementsNativeTokenVerifier.isOfficialLoginRequest(
                "https://retroachievements.org/dorequest.php",
                "r=login2&u=TestUser&t=token",
            ),
        )
    }

    @Test
    fun cancellingActiveNetworkRequestClosesNativeSession() {
        runBlocking {
            val fake = FakeBridge()
            val verifier =
                RetroAchievementsNativeTokenVerifier(fake) { _, _ ->
                    delay(10_000)
                    RetroAchievementsNativeHttpTransport.Reply(200, ByteArray(1))
                }
            val work = launch(Dispatchers.Default) { verifier.verify(credentials) }
            delay(100)
            work.cancelAndJoin()
            assertTrue(fake.closed)
        }
    }
}
