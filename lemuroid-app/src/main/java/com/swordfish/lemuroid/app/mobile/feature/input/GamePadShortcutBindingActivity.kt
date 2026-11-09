package com.swordfish.lemuroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import javax.inject.Inject

class GamePadShortcutBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var shortcutBindingUpdater: ShortcutBindingUpdater
    private var firstButton by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutBindingUpdater = ShortcutBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                OpenNesBindingCapture(
                    title = shortcutBindingUpdater.getTitle(applicationContext),
                    instructions = shortcutBindingUpdater.getMessage(applicationContext),
                    progress =
                        firstButton?.let {
                            stringResource(R.string.opennes_binding_first_captured, it)
                        },
                    onCancel = ::finish,
                    onInput = ::handleKeyEvent,
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val handled = shortcutBindingUpdater.handleKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) {
            firstButton = shortcutBindingUpdater.firstButtonLabel()
            if (handled) {
                finish()
            }
        }

        // The first half of a combination is intentionally not a completed
        // binding. Consume its key-up so it cannot trigger dialog navigation.
        return handled ||
            (
                event.device?.name == shortcutBindingUpdater.extras.device.name &&
                    event.action == KeyEvent.ACTION_UP
            )
    }

    @dagger.Module
    abstract class Module
}
