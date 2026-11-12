package com.swordfish.lemuroid.app.shared.game

import android.content.Context

/**
 * Only the player-selected ROM may force a controller-port 2 Zapper.
 * Unselected games keep FCEUmm's regular controller autodetection.
 * Keys contain a local database game ID, never a ROM path or filename.
 */
internal class NesZapperPreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "opennes_nes_zapper_per_game_v1",
            Context.MODE_PRIVATE,
        )

    fun isEnabled(gameId: Int): Boolean =
        if (gameId > 0) preferences.getBoolean(keyFor(gameId), false) else false

    fun setEnabled(
        gameId: Int,
        enabled: Boolean,
    ) {
        if (gameId <= 0) return
        preferences.edit().putBoolean(keyFor(gameId), enabled).apply()
    }

    companion object {
        internal fun keyFor(gameId: Int): String {
            require(gameId > 0) { "Game ID must be positive" }
            return "zapper_game_$gameId"
        }
    }
}
