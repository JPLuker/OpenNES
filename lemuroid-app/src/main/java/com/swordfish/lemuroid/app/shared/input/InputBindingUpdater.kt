package com.swordfish.lemuroid.app.shared.input

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import com.swordfish.lemuroid.R
import kotlinx.coroutines.runBlocking

/**
 * Captures a physical input for one controller. The mobile activity persists
 * asynchronously and only closes when the binding write has completed.
 */
class InputBindingUpdater(
    private val inputDeviceManager: InputDeviceManager,
    intent: Intent,
) {
    val extras = parseExtras(intent)

    fun getTitle(context: Context): String {
        val keyName = InputKey(extras.retroKey).displayName()
        return context.getString(R.string.gamepad_binding_update_title, keyName)
    }

    fun getMessage(context: Context): String =
        context.getString(R.string.gamepad_binding_update_description, extras.device.name)

    fun accepts(event: KeyEvent): Boolean =
        (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) &&
            event.keyCode != KeyEvent.KEYCODE_UNKNOWN &&
            isTargetedDevice(event.device)

    /**
     * Must be called for a released key only. The underlying manager commits
     * the preference before this function completes; there is no detached job.
     */
    suspend fun saveReleasedKey(event: KeyEvent): Boolean {
        if (!accepts(event) || event.action != KeyEvent.ACTION_UP) return false
        inputDeviceManager.updateBinding(
            event.device ?: return false,
            RetroKey(extras.retroKey),
            InputKey(event.keyCode),
        )
        return true
    }

    /** Legacy Android TV handler (OpenNES ships the mobile NES experience). */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!accepts(event)) return false
        if (event.action == KeyEvent.ACTION_UP) {
            return runBlocking { saveReleasedKey(event) }
        }
        return true
    }

    private fun isTargetedDevice(device: InputDevice?): Boolean =
        device != null && extras.device.descriptor == device.descriptor

    private fun parseExtras(intent: Intent): IntentExtras {
        val device =
            intent.extras?.getParcelable<InputDevice>(REQUEST_DEVICE)
                ?: throw IllegalArgumentException("REQUEST_DEVICE has not been passed")
        val retroKey =
            intent.extras?.getInt(REQUEST_RETRO_KEY)
                ?: throw IllegalArgumentException("REQUEST_RETRO_KEY has not been passed")

        return IntentExtras(device, retroKey)
    }

    data class IntentExtras(val device: InputDevice, val retroKey: Int)

    companion object {
        const val REQUEST_DEVICE = "REQUEST_DEVICE"
        const val REQUEST_RETRO_KEY = "REQUEST_RETRO_KEY"
    }
}
