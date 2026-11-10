package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsNativeAccountStoreTest {
    @Test
    fun refusesPasswordsOrWhitespaceAsRememberedTokens() {
        assertFalse(RetroAchievementsNativeAccountStore.validToken("too-short"))
        assertFalse(RetroAchievementsNativeAccountStore.validToken("bad token containing whitespace"))
        assertTrue(RetroAchievementsNativeAccountStore.validToken("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun usernameHasStrictSafeLengthAndCharacters() {
        assertTrue(RetroAchievementsNativeAccountStore.validUsername("Player_123"))
        assertFalse(RetroAchievementsNativeAccountStore.validUsername("Player Name"))
        assertFalse(RetroAchievementsNativeAccountStore.validUsername("a"))
    }

    @Test
    fun credentialLoggingAlwaysRedactsToken() {
        val credentials = NativeCredentials("player", "verySecretToken12345")
        assertFalse(credentials.toString().contains(credentials.token))
    }
}
