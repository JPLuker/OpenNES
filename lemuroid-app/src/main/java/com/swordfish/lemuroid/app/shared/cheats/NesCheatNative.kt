package com.swordfish.lemuroid.app.shared.cheats

/**
 * Atomic FCEUmm cheat replacement using the same coreLock as retro_run().
 * A generation token prevents a late pause-menu action touching another ROM.
 */
internal object NesCheatNative {
    init {
        System.loadLibrary("libretrodroid")
    }

    external fun currentGeneration(): Long

    external fun replaceCheats(
        generation: Long,
        codes: Array<String>,
    ): Boolean
}
