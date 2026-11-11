package com.swordfish.lemuroid.app.shared.saves

import com.swordfish.lemuroid.lib.saves.GameSaveProfile
import com.swordfish.lemuroid.lib.saves.SaveProfileDirectories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameSaveProfileTest {
    @Test
    fun casualAlwaysUsesLegacyFoldersWithoutMigration() {
        val sram = File("/app/saves")
        val states = File("/app/states/fceumm")
        assertEquals(sram, SaveProfileDirectories.sram(sram, GameSaveProfile.CASUAL))
        assertEquals(states, SaveProfileDirectories.states(states, GameSaveProfile.CASUAL))
    }

    @Test
    fun hardcoreCannotReadOrOverwriteCasualSramStateOrPreviews() {
        val sram = File("/app/saves")
        val states = File("/app/states/fceumm")
        val screenshots = File("/app/previews/fceumm")
        val hcSram = SaveProfileDirectories.sram(sram, GameSaveProfile.HARDCORE)
        val hcStates = SaveProfileDirectories.states(states, GameSaveProfile.HARDCORE)
        val hcPreviews = SaveProfileDirectories.states(screenshots, GameSaveProfile.HARDCORE)
        assertEquals(File(sram, "hardcore"), hcSram)
        assertEquals(File(states, "hardcore"), hcStates)
        assertEquals(File(screenshots, "hardcore"), hcPreviews)
        assertNotEquals(sram, hcSram)
        assertNotEquals(states, hcStates)
        assertTrue(hcSram.path.startsWith(sram.path))
    }
}
