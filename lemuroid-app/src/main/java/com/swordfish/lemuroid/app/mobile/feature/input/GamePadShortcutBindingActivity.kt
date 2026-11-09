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
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class GamePadShortcutBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var shortcutBindingUpdater: ShortcutBindingUpdater
    private var firstButton by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)
    private var saveFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutBindingUpdater = ShortcutBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                OpenNesBindingCapture(
                    title = shortcutBindingUpdater.getTitle(applicationContext),
                    instructions = shortcutBindingUpdater.getMessage(applicationContext),
                    progress =
                        when {
                            saveFailed -> stringResource(R.string.opennes_binding_save_failed)
                            saving -> stringResource(R.string.opennes_binding_saving)
                            firstButton != null ->
                                stringResource(R.string.opennes_binding_first_captured, firstButton!!)
                            else -> null
                        },
                    isError = saveFailed,
                    onRestart =
                        if (firstButton != null && !saving) {
                            {
                                shortcutBindingUpdater.restartCapture()
                                firstButton = null
                                saveFailed = false
                            }
                        } else {
                            null
                        },
                    onCancel = ::finish,
                    onInput = ::handleKeyEvent,
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!shortcutBindingUpdater.accepts(event)) return false
        if (saving) return true

        if (event.action == KeyEvent.ACTION_UP) {
            saving = true
            saveFailed = false
            lifecycleScope.launch {
                try {
                    val completed = shortcutBindingUpdater.saveReleasedKey(event)
                    firstButton = shortcutBindingUpdater.firstButtonLabel()
                    if (completed) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        saving = false
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Unable to persist controller shortcut binding")
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
