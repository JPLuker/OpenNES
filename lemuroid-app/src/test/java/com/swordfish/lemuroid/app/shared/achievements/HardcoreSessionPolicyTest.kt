package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcoreSessionPolicyTest {
    @Test
    fun shippingBuildCannotEnableHardcoreWithoutApprovalAndNativeReadiness() {
        val unapproved = HardcoreSessionPolicy(emulatorApproved = false, nativeHardcoreAvailable = true)
        val nativeMissing = HardcoreSessionPolicy(emulatorApproved = true, nativeHardcoreAvailable = false)
        assertEquals(
            HardcoreSessionPolicy.Activation.UNAVAILABLE,
            unapproved.requestHardcore(freshReset = true, cheatsActive = false),
        )
        assertEquals(
            HardcoreSessionPolicy.Activation.UNAVAILABLE,
            nativeMissing.requestHardcore(freshReset = true, cheatsActive = false),
        )
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, unapproved.mode)
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, nativeMissing.mode)
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, HardcoreRuntime.mode())
    }

    @Test
    fun hardcoreRequiresCleanResetAndNoActiveCheats() {
        val policy = HardcoreSessionPolicy(emulatorApproved = true, nativeHardcoreAvailable = true)
        assertEquals(
            HardcoreSessionPolicy.Activation.REQUIRES_FULL_RESET,
            policy.requestHardcore(freshReset = false, cheatsActive = false),
        )
        assertEquals(
            HardcoreSessionPolicy.Activation.CHEATS_ACTIVE,
            policy.requestHardcore(freshReset = true, cheatsActive = true),
        )
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, policy.mode)
        assertEquals(
            HardcoreSessionPolicy.Activation.ACTIVE,
            policy.requestHardcore(freshReset = true, cheatsActive = false),
        )
        assertEquals(HardcoreSessionPolicy.Mode.HARDCORE, policy.mode)
    }

    @Test
    fun hardcoreBlocksAllKnownAdvantagesButNotFastForwardOrSavingState() {
        val policy = HardcoreSessionPolicy(emulatorApproved = true, nativeHardcoreAvailable = true)
        policy.requestHardcore(freshReset = true, cheatsActive = false)
        val blocked =
            listOf(
                HardcoreSessionPolicy.Operation.LOAD_STATE,
                HardcoreSessionPolicy.Operation.CHEATS,
                HardcoreSessionPolicy.Operation.GAME_GENIE_DEVICE,
                HardcoreSessionPolicy.Operation.REWIND,
                HardcoreSessionPolicy.Operation.SLOW_MOTION,
                HardcoreSessionPolicy.Operation.FRAME_ADVANCE,
            )
        blocked.forEach { assertFalse(policy.allow(it)) }
        assertTrue(policy.allow(HardcoreSessionPolicy.Operation.SAVE_STATE))
        assertTrue(policy.allow(HardcoreSessionPolicy.Operation.BATTERY_SAVE))
        assertTrue(policy.allow(HardcoreSessionPolicy.Operation.FAST_FORWARD))
    }

    @Test
    fun suspendAndVoluntaryDowngradeRequireNewCleanSessionForHardcore() {
        val policy = HardcoreSessionPolicy(emulatorApproved = true, nativeHardcoreAvailable = true)
        policy.requestHardcore(freshReset = true, cheatsActive = false)
        policy.onSuspended()
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, policy.mode)
        assertTrue(policy.allow(HardcoreSessionPolicy.Operation.LOAD_STATE))
        assertEquals(
            HardcoreSessionPolicy.Activation.REQUIRES_FULL_RESET,
            policy.requestHardcore(freshReset = false, cheatsActive = false),
        )
        policy.requestHardcore(freshReset = true, cheatsActive = false)
        policy.switchToCasual()
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, policy.mode)
        policy.onGameEnded()
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, policy.mode)
    }

    @Test
    fun staleGameCannotEndCurrentSession() {
        HardcoreRuntime.begin("rom-a")
        HardcoreRuntime.begin("rom-b")
        HardcoreRuntime.end("rom-a")
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, HardcoreRuntime.mode())
        HardcoreRuntime.end("rom-b")
    }

    @Test
    fun localHardcoreNeedsFreshBootAndIsCasualForRetroAchievementsUntilApproved() {
        assertFalse(HardcoreGate.EMULATOR_APPROVED)
        HardcoreRuntime.begin("rom", requestHardcore = true, freshBoot = false)
        assertEquals(HardcoreSessionPolicy.Mode.CASUAL, HardcoreRuntime.mode())
        HardcoreRuntime.begin("rom", requestHardcore = true, freshBoot = true)
        assertEquals(HardcoreSessionPolicy.Mode.HARDCORE, HardcoreRuntime.mode())
        assertFalse(HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE))
        assertFalse(HardcoreRuntime.retroAchievementsHardcore())
        HardcoreRuntime.begin("rom", requestHardcore = true, freshBoot = true, testUnapproved = true)
        assertTrue(HardcoreRuntime.retroAchievementsHardcore())
        HardcoreRuntime.end("rom")
        assertFalse(HardcoreRuntime.retroAchievementsHardcore())
    }

    @Test
    fun leavingHardcoreSwitchesNativeFirstAndNeverReturns() {
        val order = mutableListOf<String>()
        HardcoreRuntime.nativeDowngrade = {
            // Restrictions must still be in force while native switches.
            order += if (HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE)) "open" else "native"
        }
        HardcoreRuntime.onLeftHardcore = { order += "told" }
        try {
            HardcoreRuntime.begin("rom", requestHardcore = true, freshBoot = true, testUnapproved = true)
            assertEquals(HardcoreSessionPolicy.Mode.HARDCORE, HardcoreRuntime.mode())
            HardcoreRuntime.onSuspended("other-rom")
            assertEquals(HardcoreSessionPolicy.Mode.HARDCORE, HardcoreRuntime.mode())
            HardcoreRuntime.onSuspended("rom")
            assertEquals(HardcoreSessionPolicy.Mode.CASUAL, HardcoreRuntime.mode())
            HardcoreRuntime.switchToCasual()
            assertEquals(listOf("native", "told"), order)
        } finally {
            HardcoreRuntime.nativeDowngrade = null
            HardcoreRuntime.onLeftHardcore = null
            HardcoreRuntime.end("rom")
        }
    }

    @Test
    fun failedNativeDowngradeKeepsRestrictions() {
        HardcoreRuntime.nativeDowngrade = { error("native unavailable") }
        try {
            HardcoreRuntime.begin("rom", requestHardcore = true, freshBoot = true, testUnapproved = true)
            HardcoreRuntime.switchToCasual()
            assertEquals(HardcoreSessionPolicy.Mode.HARDCORE, HardcoreRuntime.mode())
            assertFalse(HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE))
        } finally {
            HardcoreRuntime.nativeDowngrade = null
            HardcoreRuntime.end("rom")
        }
    }
}
