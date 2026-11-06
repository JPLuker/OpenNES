package com.swordfish.lemuroid.app.mobile.feature.game

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber

class GameActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // LibretroDroid syncs NES frames against the display's refresh rate.
        // Ask Android for 60 Hz before the emulator creates its rendering surface.
        preferNesRefreshRate()
        super.onCreate(savedInstanceState)
    }

    private fun preferNesRefreshRate() {
        val stablePacingEnabled =
            SharedPreferencesHelper.getSharedPreferences(this)
                .getBoolean(getString(R.string.pref_key_nes_stable_audio), true)
        if (!stablePacingEnabled) return

        // Android may select another mode when 60 Hz cannot be used.
        // A refresh-rate hint avoids requesting a specific screen resolution.
        window.attributes =
            window.attributes.apply {
                preferredRefreshRate = NES_TARGET_HZ
            }
        Timber.i("OpenNES requested %.2f Hz NES gameplay refresh rate", NES_TARGET_HZ)
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java

    companion object {
        private const val NES_TARGET_HZ = 60f
    }
}
