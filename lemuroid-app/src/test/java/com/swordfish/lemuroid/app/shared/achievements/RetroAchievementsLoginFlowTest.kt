package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetroAchievementsLoginFlowTest {
    private val saved = NativeCredentials("ActualUser", "1234567890abcdef1234567890abcdef")

    @Test
    fun reportsVerifiedOnlyAfterPasswordAndMatchingNativeAuthentication() {
        runBlocking {
            var passwordCalled = false
            var nativeCalled = false
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { username, password ->
                        assertEquals("ActualUser", username)
                        assertEquals("secret", password)
                        passwordCalled = true
                        "ActualUser"
                    },
                    readSavedCredentials = { saved },
                    nativeVerify = { credentials ->
                        assertTrue(credentials === saved)
                        nativeCalled = true
                        RetroAchievementsNativeTokenVerifier.Result.Verified("ActualUser")
                    },
                )
            assertEquals(
                RetroAchievementsLoginFlow.Result.Verified("ActualUser"),
                flow.signInAndVerify("ActualUser", "secret"),
            )
            assertTrue(passwordCalled)
            assertTrue(nativeCalled)
        }
    }

    @Test
    fun passwordRejectionNeverChecksNativeToken() {
        runBlocking {
            var nativeCalled = false
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { _, _ -> throw IOException("Wrong password") },
                    readSavedCredentials = { saved },
                    nativeVerify = {
                        nativeCalled = true
                        RetroAchievementsNativeTokenVerifier.Result.Verified("ActualUser")
                    },
                )
            assertThrows(IOException::class.java) {
                runBlocking { flow.signInAndVerify("ActualUser", "wrong") }
            }
            assertFalse(nativeCalled)
        }
    }

    @Test
    fun nativeNetworkFailureRetainsSavedAccountButNeverClaimsVerified() {
        runBlocking {
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { _, _ -> "ActualUser" },
                    readSavedCredentials = { saved },
                    nativeVerify = { throw IOException("No network") },
                )
            assertEquals(
                RetroAchievementsLoginFlow.Result.SavedButUnverified("ActualUser"),
                flow.signInAndVerify("ActualUser", "secret"),
            )
        }
    }

    @Test
    fun nativeUsernameMismatchCannotVerifyADifferentAccount() {
        runBlocking {
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { _, _ -> "ActualUser" },
                    readSavedCredentials = { saved },
                    nativeVerify = {
                        RetroAchievementsNativeTokenVerifier.Result.Verified("DifferentUser")
                    },
                )
            assertEquals(
                RetroAchievementsLoginFlow.Result.SavedButUnverified("ActualUser"),
                flow.signInAndVerify("ActualUser", "secret"),
            )
        }
    }

    @Test
    fun missingSavedCredentialCannotProduceVerifiedStatus() {
        runBlocking {
            var nativeCalled = false
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { _, _ -> "ActualUser" },
                    readSavedCredentials = { null },
                    nativeVerify = {
                        nativeCalled = true
                        RetroAchievementsNativeTokenVerifier.Result.Verified("ActualUser")
                    },
                )
            assertEquals(
                RetroAchievementsLoginFlow.Result.SavedButUnverified("ActualUser"),
                flow.signInAndVerify("ActualUser", "secret"),
            )
            assertFalse(nativeCalled)
        }
    }

    @Test
    fun cancellationPropagatesAndNeverClaimsLoginSuccess() {
        runBlocking {
            val flow =
                RetroAchievementsLoginFlow(
                    passwordSignIn = { _, _ -> "ActualUser" },
                    readSavedCredentials = { saved },
                    nativeVerify = { throw CancellationException("Settings closed") },
                )
            var cancelled = false
            try {
                flow.signInAndVerify("ActualUser", "secret")
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
    }
}
