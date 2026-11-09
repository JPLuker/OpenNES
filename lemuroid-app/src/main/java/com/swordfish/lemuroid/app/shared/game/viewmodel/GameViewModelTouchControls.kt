package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.view.KeyEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_LEFT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_RIGHT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_DPAD
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTouchControls(
    private val settingsManager: SettingsManager,
    private val touchControllerSettingsManager: TouchControllerSettingsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val inputs: GameViewModelInput,
    private val tilt: GameViewModelTilt,
    private val sideEffects: GameViewModelSideEffects,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val touchControlId = MutableStateFlow(TouchControllerID.NES)
    private val screenOrientation = MutableStateFlow(TouchControllerSettingsManager.Orientation.PORTRAIT)
    private val menuPressed = MutableStateFlow(false)
    private val showEditControls = MutableStateFlow(false)
    private val dragEditMode = MutableStateFlow(false)
    private val hapticFeedbackMode = MutableStateFlow(HapticFeedbackMode.NONE)

    private var loadingMenuJob: Job? = null

    // Keep track of held virtual controls so an interrupted gesture cannot
    // leave a button or D-pad direction latched inside LibretroDroid.
    private val pressedVirtualKeys = mutableSetOf<Int>()
    private val activeVirtualDirections = mutableSetOf<Int>()

    override fun onCreate(owner: LifecycleOwner) {
        owner.launchOnState(Lifecycle.State.CREATED) {
            getTouchControllerConfig().safeCollect {
                touchControlId.value = it.touchControllerID
            }
        }
        owner.launchOnState(Lifecycle.State.CREATED) {
            withContext(Dispatchers.IO) {
                hapticFeedbackMode.value = HapticFeedbackMode.parse(settingsManager.hapticFeedbackMode())
            }
        }
        owner.launchOnState(Lifecycle.State.CREATED) {
            inputs.getEnabledInputDevices().safeCollect { devices ->
                // External-controller takeover hides the virtual pad. Release
                // touches that were already down before its disappearance.
                if (devices.isNotEmpty()) releaseVirtualInputs()
            }
        }
    }

    fun getTouchControlsSettings(
        density: Density,
        insets: WindowInsets,
    ): Flow<TouchControllerSettingsManager.Settings?> {
        return combine(
            touchControlId,
            screenOrientation,
        ) { touchControlId, orientation -> touchControlId to orientation }
            .flatMapLatest { (touchControlId, orientation) ->
                touchControllerSettingsManager.observeSettings(touchControlId, orientation, density, insets)
            }
    }

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> {
        return hapticFeedbackMode
    }

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        // Capture orientation before dispatch: a rotation must not save a
        // portrait gesture into the landscape slot (or vice versa).
        val controller = touchControlId.value
        val orientation = screenOrientation.value
        scope.launch {
            touchControllerSettingsManager.storeSettings(
                controller,
                orientation,
                touchControllerSettings,
            )
        }
    }

    fun previewTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        touchControllerSettingsManager.previewSettings(
            touchControlId.value,
            screenOrientation.value,
            touchControllerSettings,
        )
    }

    fun isDragEditMode(): Flow<Boolean> = dragEditMode

    fun setDragEditMode(enabled: Boolean) {
        if (enabled) {
            releaseVirtualInputs()
            showEditControls.value = false
        }
        dragEditMode.value = enabled
    }

    fun resetTouchControls() {
        val controller = touchControlId.value
        val orientation = screenOrientation.value
        scope.launch {
            touchControllerSettingsManager.resetSettings(controller, orientation)
        }
    }

    fun updateScreenOrientation(orientation: TouchControllerSettingsManager.Orientation) {
        if (screenOrientation.value != orientation) {
            releaseVirtualInputs()
            dragEditMode.value = false
        }
        screenOrientation.value = orientation
    }

    fun isTouchControllerVisible(): Flow<Boolean> {
        return inputs.getEnabledInputDevices()
            .map { it.isEmpty() }
    }

    fun getTouchControllerConfig(): Flow<ControllerConfig> {
        return inputs.getControllerConfigState()
            .map { it[0] }
            .filterNotNull()
            .distinctUntilChanged()
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        // PadKit may still see touches on a displayed control. Never forward
        // button/axis input while the player is repositioning the controls.
        if (dragEditMode.value) return
        val menuEvent = events.firstOrNull { it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE }
        if (menuEvent != null) {
            onMenuPressed((menuEvent as InputEvent.Button).pressed)
        }

        events.forEach { event ->
            when (event) {
                is InputEvent.Button -> {
                    handleVirtualInputButton(event)
                }

                is InputEvent.DiscreteDirection -> {
                    handleVirtualInputDirection(event.id, event.direction.x, -event.direction.y)
                }

                is InputEvent.ContinuousDirection -> {
                    handleVirtualInputDirection(event.id, event.direction.x, -event.direction.y)
                }
            }
        }
    }

    private fun onMenuPressed(pressed: Boolean) {
        menuPressed.value = pressed

        if (pressed) {
            loadingMenuJob?.cancel()
            loadingMenuJob =
                scope.launch {
                    delay(MENU_LOADING_ANIMATION_MILLIS.toLong())
                    sideEffects.showMenu(tilt, inputs)
                }
        } else {
            loadingMenuJob?.cancel()
            loadingMenuJob = null
        }
    }

    fun isMenuPressed(): Flow<Boolean> {
        return menuPressed
    }

    fun isEditControlsShown(): Flow<Boolean> {
        return showEditControls
    }

    fun showEditControls(show: Boolean) {
        if (show) releaseVirtualInputs()
        showEditControls.value = show
    }

    private fun handleVirtualInputButton(event: InputEvent.Button) {
        val changed =
            if (event.pressed) {
                pressedVirtualKeys.add(event.id)
            } else {
                pressedVirtualKeys.remove(event.id)
            }
        if (!changed) return

        val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        retroGameView.retroGameView?.sendKeyEvent(action, event.id)
    }

    private fun handleVirtualInputDirection(
        id: Int,
        xAxis: Float,
        yAxis: Float,
    ) {
        if (xAxis == 0f && yAxis == 0f) {
            activeVirtualDirections.remove(id)
        } else {
            activeVirtualDirections.add(id)
        }

        when (id) {
            ComposeTouchLayouts.MOTION_SOURCE_DPAD -> {
                retroGameView.retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, xAxis, yAxis)
            }

            ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
                retroGameView.retroGameView?.sendMotionEvent(MOTION_SOURCE_DPAD, xAxis, yAxis)
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_DPAD -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        releaseVirtualInputs()
        super.onPause(owner)
    }

    /**
     * Release every virtual input when a touch stream is interrupted. PadKit
     * can lose an UP event when the screen rotates or a menu takes focus.
     * Physical gamepads are handled independently and are not affected.
     */
    private fun releaseVirtualInputs() {
        val gameView = retroGameView.retroGameView
        pressedVirtualKeys.forEach { key ->
            gameView?.sendKeyEvent(KeyEvent.ACTION_UP, key)
        }
        pressedVirtualKeys.clear()

        activeVirtualDirections.forEach { direction ->
            when (direction) {
                ComposeTouchLayouts.MOTION_SOURCE_DPAD -> {
                    gameView?.sendMotionEvent(MOTION_SOURCE_DPAD, 0f, 0f)
                }
                ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK -> {
                    gameView?.sendMotionEvent(MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
                ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK,
                ComposeTouchLayouts.MOTION_SOURCE_RIGHT_DPAD -> {
                    gameView?.sendMotionEvent(MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f)
                }
                ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK -> {
                    gameView?.sendMotionEvent(MOTION_SOURCE_DPAD, 0f, 0f)
                    gameView?.sendMotionEvent(MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
            }
        }
        activeVirtualDirections.clear()

        loadingMenuJob?.cancel()
        loadingMenuJob = null
        menuPressed.value = false
    }

    companion object {
        const val MENU_LOADING_ANIMATION_MILLIS = 500
    }
}
