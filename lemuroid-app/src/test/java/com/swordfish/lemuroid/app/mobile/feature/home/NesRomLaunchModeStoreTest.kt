package com.swordfish.lemuroid.app.mobile.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NesRomLaunchModeStoreTest {
    @Test
    fun eachRomHasAnIndependentPrivatePreferenceKey() {
        val zelda = NesRomLaunchModeStore.keyFor("content://games/zelda.nes")
        val mario = NesRomLaunchModeStore.keyFor("content://games/mario.nes")
        assertNotEquals(zelda, mario)
        assertEquals(zelda, NesRomLaunchModeStore.keyFor("content://games/zelda.nes"))
        assertFalse(zelda.contains("zelda"))
        assertEquals(68, zelda.length)
    }
}
