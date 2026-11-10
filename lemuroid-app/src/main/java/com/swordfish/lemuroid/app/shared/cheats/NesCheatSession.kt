package com.swordfish.lemuroid.app.shared.cheats

import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import com.swordfish.lemuroid.app.shared.achievements.HardcoreSessionPolicy

/**
 * Live, in-memory cheat enablement. The current ROM must have been explicitly
 * registered by the emulator ViewModel; saved codes alone never activate.
 * FCEUmm ignores individual enabled flags, so replacement must clear and
 * reapply the enabled codes atomically under the native emulator core lock.
 */
internal object NesCheatSession {
    private var romUri: String? = null
    private var enabledIds: Set<String> = emptySet()

    @Synchronized
    fun begin(romUri: String) {
        this.romUri = romUri
        enabledIds = emptySet()
    }

    /** The first rendered frame proves that the new ROM has loaded. */
    @Synchronized
    fun clearAtGameStart(romUri: String): Boolean {
        if (this.romUri != romUri) return false
        return runCatching {
            val generation = NesCheatNative.currentGeneration()
            val cleared = NesCheatNative.replaceCheats(generation, emptyArray())
            if (cleared) enabledIds = emptySet()
            cleared
        }.getOrDefault(false)
    }

    @Synchronized
    fun end(romUri: String) {
        if (this.romUri == romUri) {
            // If the core is still alive, leave it cheat-free. A new game
            // cannot accidentally receive old codes because the JNI method
            // also validates the loaded core generation under coreLock.
            runCatching {
                val generation = NesCheatNative.currentGeneration()
                NesCheatNative.replaceCheats(generation, emptyArray())
            }
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
        return runCatching {
            val generation = NesCheatNative.currentGeneration()
            val codes = definitions.filter { it.id in requested }.map { it.code }.toTypedArray()
            val applied = NesCheatNative.replaceCheats(generation, codes)
            // If a new core replaced the current game between the two JNI
            // calls, native returns false *without modifying that core*.
            if (applied) enabledIds = requested.toSet()
            applied
        }.getOrDefault(false)
    }
}
