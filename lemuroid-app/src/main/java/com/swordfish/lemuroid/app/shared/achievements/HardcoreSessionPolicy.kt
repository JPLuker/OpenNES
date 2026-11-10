package com.swordfish.lemuroid.app.shared.achievements

/**
 * Centralized compliance rules for a future RetroAchievements Hardcore mode.
 *
 * Production is intentionally Casual-only: OpenNES is not an approved
 * Hardcore emulator and native rc_client currently runs in Casual mode.
 * No UI or stored preference can override the two explicit build gates.
 */
internal class HardcoreSessionPolicy(
    private val emulatorApproved: Boolean,
    private val nativeHardcoreAvailable: Boolean,
) {
    enum class Mode {
        CASUAL,
        HARDCORE,
    }

    enum class Operation {
        SAVE_STATE,
        LOAD_STATE,
        CHEATS,
        GAME_GENIE_DEVICE,
        REWIND,
        SLOW_MOTION,
        FRAME_ADVANCE,
        FAST_FORWARD,
        BATTERY_SAVE,
    }

    enum class Activation {
        UNAVAILABLE,
        REQUIRES_FULL_RESET,
        CHEATS_ACTIVE,
        ACTIVE,
    }

    var mode: Mode = Mode.CASUAL
        private set

    /**
     * Every entry into Hardcore must begin with a clean reset, before
     * any state-load or game-altering emulator feature is used.
     */
    fun requestHardcore(
        freshReset: Boolean,
        cheatsActive: Boolean,
    ): Activation {
        if (!emulatorApproved || !nativeHardcoreAvailable) return Activation.UNAVAILABLE
        if (!freshReset) return Activation.REQUIRES_FULL_RESET
        if (cheatsActive) return Activation.CHEATS_ACTIVE
        mode = Mode.HARDCORE
        return Activation.ACTIVE
    }

    fun allow(operation: Operation): Boolean {
        if (mode != Mode.HARDCORE) return true
        return when (operation) {
            Operation.SAVE_STATE,
            Operation.FAST_FORWARD,
            Operation.BATTERY_SAVE,
            -> true
            Operation.LOAD_STATE,
            Operation.CHEATS,
            Operation.GAME_GENIE_DEVICE,
            Operation.REWIND,
            Operation.SLOW_MOTION,
            Operation.FRAME_ADVANCE,
            -> false
        }
    }

    /** Backgrounded/quick-resumed games must not retain Hardcore credit. */
    fun onSuspended() {
        mode = Mode.CASUAL
    }

    fun switchToCasual() {
        mode = Mode.CASUAL
    }

    fun onGameEnded() {
        mode = Mode.CASUAL
    }

    val canRequestHardcore: Boolean
        get() = emulatorApproved && nativeHardcoreAvailable
}

/**
 * Game-process policy shared by save, cheat, and core-option entry points.
 * The build intentionally has no path that can activate Hardcore yet.
 */
internal object HardcoreRuntime {
    private val policy =
        HardcoreSessionPolicy(
            emulatorApproved = false,
            nativeHardcoreAvailable = false,
        )
    private var currentRomUri: String? = null

    @Synchronized
    fun begin(romUri: String) {
        currentRomUri = romUri
        policy.onGameEnded()
    }

    @Synchronized
    fun end(romUri: String) {
        if (currentRomUri != romUri) return
        currentRomUri = null
        policy.onGameEnded()
    }

    @Synchronized
    fun onSuspended(romUri: String) {
        if (currentRomUri == romUri) policy.onSuspended()
    }

    @Synchronized
    fun allow(operation: HardcoreSessionPolicy.Operation): Boolean = policy.allow(operation)

    @Synchronized
    fun mode(): HardcoreSessionPolicy.Mode = policy.mode

    @Synchronized
    fun hardcoreAvailable(): Boolean = policy.canRequestHardcore
}
