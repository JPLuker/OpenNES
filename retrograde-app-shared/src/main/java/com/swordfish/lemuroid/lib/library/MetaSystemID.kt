package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.common.graphics.ColorUtils
import com.swordfish.lemuroid.lib.R

fun GameSystem.metaSystemID() = MetaSystemID.fromSystemID(id)

/** A single NES/Famicom library, without categories for unrelated consoles. */
enum class MetaSystemID(val titleResId: Int, val imageResId: Int, val systemIDs: List<SystemID>) {
    NES(
        R.string.game_system_title_nes,
        R.drawable.game_system_nes,
        listOf(SystemID.NES),
    ),
    ;

    fun color(): Int {
        return ColorUtils.color(0f)
    }

    companion object {
        fun fromSystemID(systemID: SystemID): MetaSystemID {
            require(systemID == SystemID.NES) {
                "OpenNES does not support system: $systemID"
            }
            return NES
        }
    }
}
