package com.swordfish.lemuroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.input.InputBindingUpdater
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import javax.inject.Inject

class GamePadBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                OpenNesBindingCapture(
                    title = inputBindingUpdater.getTitle(applicationContext),
                    instructions = inputBindingUpdater.getMessage(applicationContext),
                    progress = null,
                    onCancel = ::finish,
                    onInput = ::handleKeyEvent,
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val handled = inputBindingUpdater.handleKeyEvent(event)
        if (handled && event.action == KeyEvent.ACTION_UP) {
            finish()
        }
        return handled
    }

    @dagger.Module
    abstract class Module
}
