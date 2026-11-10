package com.swordfish.lemuroid.app.shared.cheats

import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import com.swordfish.lemuroid.app.shared.achievements.HardcoreSessionPolicy
import com.swordfish.libretrodroid.LibretroDroid

/**
 * Live, in-memory cheat enablement. The current ROM must have been explicitly
 * registered by the emulator ViewModel; saved codes alone never activate.
 * Libretro FCEUmm ignores per-code enabled/index parameters, so a change
 * must clear the native list and reapply precisely the enabled codes.
 */
internal object NesCheatSession {
    private var romUri: String? = null
    private var enabledIds: Set<String> = emptySet()

    @Synchronized
    fun begin(romUri: String) {
        this.romUri = romUri
        enabledIds = emptySet()
    }

    @Synchronized
    fun end(romUri: String) {
        if (this.romUri == romUri) {
            this.romUri = null
            enabledIds = emptySet()
        }
    }

    @Synchronized
    fun enabledFor(romUri: String): Set<String> = if (this.romUri == romUri) enabledIds else emptySet()

    @Synchronized
    fun apply(
        romUri: String,
        definitions: List<NesCheatDefinition>,
        requested: Set<String>,
    ): Boolean {
        if (requested.isNotEmpty() &&
            !HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.CHEATS)
        ) {
            return false
        }
        if (this.romUri != romUri || definitions.size > NesCheatRepository.MAX_CHEATS ||
            definitions.map { it.id }.distinct().size != definitions.size ||
            !definitions.map { it.id }.containsAll(requested) ||
            definitions.any { NesCheatCodes.normalize(it.code) != it.code }
        ) {
            return false
        }
        return try {
            LibretroDroid.resetCheat()
            definitions.filter { it.id in requested }.forEachIndexed { index, item ->
                LibretroDroid.setCheat(index, true, item.code)
            }
            enabledIds = requested.toSet()
            true
        } catch (_: Exception) {
            // On any partially applied batch, disable all cheats rather than
            // reporting an enabled state that differs from the emulator.
            runCatching { LibretroDroid.resetCheat() }
            enabledIds = emptySet()
            false
        }
    }
}
