package com.swordfish.lemuroid.app.shared.game

import com.swordfish.libretrodroid.Controller

/** Zapper is a port-2 device on ordinary NES games; never remap player 1. */
internal object NesZapperController {
    fun portTwoZapperId(supportedControllers: Array<Array<Controller>>): Int? =
        supportedControllers.getOrNull(1)
            ?.firstOrNull {
                it.id >= 0 && it.description?.contains("Zapper", ignoreCase = true) == true
            }?.id
}
