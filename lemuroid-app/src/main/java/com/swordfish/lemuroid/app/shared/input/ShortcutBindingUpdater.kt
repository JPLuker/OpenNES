package com.swordfish.lemuroid.app.shared.input

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.settings.GameShortcutType
import kotlinx.coroutines.runBlocking

/** Records two different controller buttons and persists a completed shortcut. */
class ShortcutBindingUpdater(
    private val inputDeviceManager: InputDeviceManager,
    intent: Intent,
) {
    val extras = parseExtras(intent)
    private var firstKeyCodeInCombo: Int? = null

    fun getTitle(context: Context): String =
        context.getString(R.string.shortcut_binding_update_title, extras.shortcutType.displayName())

    fun getMessage(context: Context): String =
        context.getString(R.string.shortcut_binding_update_description, extras.device.name)

    fun firstButtonLabel(): String? = firstKeyCodeInCombo?.let { InputKey(it).displayName() }

    fun accepts(event: KeyEvent): Boolean =
        (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) &&
            event.keyCode != KeyEvent.KEYCODE_UNKNOWN &&
            event.device?.descriptor == extras.device.descriptor

    /**
     * Returns true only after the complete pair has been written. A repeated
     * first key does not finish the capture or overwrite another shortcut.
     */
    suspend fun saveReleasedKey(event: KeyEvent): Boolean {
        if (!accepts(event) || event.action != KeyEvent.ACTION_UP) return false
        val firstKey = firstKeyCodeInCombo
        if (firstKey == null) {
            firstKeyCodeInCombo = event.keyCode
            return false
        }
        if (firstKey == event.keyCode) return false

        inputDeviceManager.updateShortcutBinding(
            event.device ?: return false,
            extras.shortcutType,
            Pair(InputKey(firstKey), InputKey(event.keyCode)),
        )
        firstKeyCodeInCombo = null
        return true
    }

    fun restartCapture() {
        firstKeyCodeInCombo = null
    }

    /** Preserve behavior for the older Android TV capture activity. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!accepts(event)) return false
        if (event.action == KeyEvent.ACTION_UP) {
            return runBlocking { saveReleasedKey(event) }
        }
        return true
    }

    private fun parseExtras(intent: Intent): IntentExtras {
        val device =
            intent.extras?.getParcelable<InputDevice>(REQUEST_DEVICE)
                ?: throw IllegalArgumentException("REQUEST_DEVICE has not been passed")
        val shortcutType =
            intent.extras?.getString(REQUEST_SHORTCUT_TYPE)
                ?: throw IllegalArgumentException("REQUEST_SHORTCUT_TYPE has not been passed")

        return IntentExtras(device, GameShortcutType.valueOf(shortcutType))
    }

    data class IntentExtras(val device: InputDevice, val shortcutType: GameShortcutType)

    companion object {
        const val REQUEST_DEVICE = "REQUEST_DEVICE"
        const val REQUEST_SHORTCUT_TYPE = "REQUEST_SHORTCUT_TYPE"
    }
}
