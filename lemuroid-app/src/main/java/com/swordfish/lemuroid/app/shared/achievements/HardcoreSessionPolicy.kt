package com.swordfish.lemuroid.app.shared.achievements

/**
 * Centralized compliance rules for RetroAchievements Hardcore mode.
 *
 * Production is Casual-only until [HardcoreGate.EMULATOR_APPROVED] is set
 * after RetroAchievements approval. Only debug builds can test Hardcore
 * before then, through an explicit, clearly labeled switch.
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
 * The one switch that makes Hardcore available to players. It stays false
 * until RetroAchievements approves OpenNES as a Hardcore-compliant emulator;
 * unapproved clients must not offer Hardcore credit.
 */
internal object HardcoreGate {
    const val EMULATOR_APPROVED = false
}

/**
 * Game-process policy shared by save, cheat, and core-option entry points.
 *
 * Hardcore can only begin with a freshly booted game ([begin]) and can only
 * end ([leaveHardcore]); nothing re-enters it mid-session. Leaving switches
 * the native rc_client to Casual *before* this policy lifts its restrictions.
 */
internal object HardcoreRuntime {
    private var policy = HardcoreSessionPolicy(HardcoreGate.EMULATOR_APPROVED, nativeHardcoreAvailable = true)
    private var currentRomUri: String? = null

    /** Switches the native client to Casual; set by the game screen. Must not throw to succeed. */
    @Volatile
    var nativeDowngrade: (() -> Unit)? = null

    /** Told after Hardcore ended mid-session, to inform the player. */
    @Volatile
    var onLeftHardcore: (() -> Unit)? = null

    /**
     * Start a game. Hardcore needs the approval gate (or a debug build's
     * explicit test switch, [testUnapproved]), a fresh boot with no restored
     * state, and no cheats.
     */
    @Synchronized
    fun begin(
        romUri: String,
        requestHardcore: Boolean = false,
        freshBoot: Boolean = false,
        testUnapproved: Boolean = false,
    ): HardcoreSessionPolicy.Activation {
        currentRomUri = romUri
        policy =
            HardcoreSessionPolicy(
                emulatorApproved = HardcoreGate.EMULATOR_APPROVED || testUnapproved,
                nativeHardcoreAvailable = true,
            )
        if (!requestHardcore) return HardcoreSessionPolicy.Activation.UNAVAILABLE
        return policy.requestHardcore(freshReset = freshBoot, cheatsActive = false)
    }

    @Synchronized
    fun end(romUri: String) {
        if (currentRomUri != romUri) return
        currentRomUri = null
        policy = HardcoreSessionPolicy(HardcoreGate.EMULATOR_APPROVED, nativeHardcoreAvailable = true)
    }

    /** The app left the screen while the pause menu was open. */
    fun onBackgrounded() = leaveHardcore()

    /** The game went to the background: a resumed session is Casual. */
    fun onSuspended(romUri: String) {
        val current = synchronized(this) { currentRomUri == romUri }
        if (current) leaveHardcore()
    }

    /** The player's own choice from the pause menu; allowed at any time. */
    fun switchToCasual() = leaveHardcore()

    private fun leaveHardcore() {
        synchronized(this) {
            if (policy.mode != HardcoreSessionPolicy.Mode.HARDCORE) return
            // Native first. If it cannot be switched, stay restricted.
            val downgraded = runCatching { nativeDowngrade?.invoke() }.isSuccess
            if (!downgraded) return
            policy.switchToCasual()
        }
        runCatching { onLeftHardcore?.invoke() }
    }

    @Synchronized
    fun allow(operation: HardcoreSessionPolicy.Operation): Boolean = policy.allow(operation)

    @Synchronized
    fun mode(): HardcoreSessionPolicy.Mode = policy.mode

    @Synchronized
    fun hardcoreAvailable(): Boolean = policy.canRequestHardcore
}
