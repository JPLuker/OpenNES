package com.swordfish.lemuroid.app.mobile.feature.input

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.input.InputBindingUpdater
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class GamePadBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater
    private var saving by mutableStateOf(false)
    private var saveFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                OpenNesBindingCapture(
                    title = inputBindingUpdater.getTitle(applicationContext),
                    instructions = inputBindingUpdater.getMessage(applicationContext),
                    progress =
                        when {
                            saveFailed -> stringResource(R.string.opennes_binding_save_failed)
                            saving -> stringResource(R.string.opennes_binding_saving)
                            else -> null
                        },
                    isError = saveFailed,
                    onCancel = ::finish,
                    onInput = ::handleKeyEvent,
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!inputBindingUpdater.accepts(event)) return false
        if (saving) return true

        if (event.action == KeyEvent.ACTION_UP) {
            saving = true
            saveFailed = false
            lifecycleScope.launch {
                try {
                    if (inputBindingUpdater.saveReleasedKey(event)) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        saving = false
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Unable to persist controller button binding")
                    saveFailed = true
                    saving = false
                }
            }
        }
        return true
    }

    @dagger.Module
    abstract class Module
}
