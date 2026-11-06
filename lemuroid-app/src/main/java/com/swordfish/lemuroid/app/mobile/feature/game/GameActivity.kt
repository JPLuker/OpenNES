package com.swordfish.lemuroid.app.mobile.feature.game

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber
import kotlin.math.abs

class GameActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Request 60 Hz before LibretroDroid initializes its display-based frame pacing.
        requestNesDisplayMode()
        super.onCreate(savedInstanceState)
    }

    @Suppress("DEPRECATION")
    private fun requestNesDisplayMode() {
        val stablePacingEnabled =
            SharedPreferencesHelper.getSharedPreferences(this)
                .getBoolean(getString(R.string.pref_key_nes_stable_audio), true)
        if (!stablePacingEnabled) return

        val display = windowManager.defaultDisplay
        val currentMode = display.mode
        val targetMode =
            display.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .minByOrNull { abs(it.refreshRate - NES_TARGET_HZ) }
                ?: return

        if (abs(targetMode.refreshRate - NES_TARGET_HZ) > REFRESH_RATE_TOLERANCE) return
        if (targetMode.modeId == currentMode.modeId) return

        window.attributes =
            window.attributes.apply {
                preferredDisplayModeId = targetMode.modeId
            }

        Timber.i(
            "OpenNES requested NES refresh rate: %.2f -> %.2f Hz",
            currentMode.refreshRate,
            targetMode.refreshRate,
        )
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java

    companion object {
        private const val NES_TARGET_HZ = 60f
        private const val REFRESH_RATE_TOLERANCE = 1.5f
    }
}
