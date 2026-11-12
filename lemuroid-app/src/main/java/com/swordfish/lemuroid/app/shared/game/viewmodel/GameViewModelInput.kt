package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.NesZapperController
import com.swordfish.lemuroid.app.shared.game.NesZapperPreferences
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.input.InputKey
import com.swordfish.lemuroid.app.shared.input.inputclass.getInputClass
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.shared.settings.GameShortcutType
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.kotlin.NTuple2
import com.swordfish.lemuroid.common.kotlin.NTuple4
import com.swordfish.lemuroid.common.kotlin.filterNotNullValues
import com.swordfish.lemuroid.common.kotlin.toIndexedMap
import com.swordfish.lemuroid.common.kotlin.zipOnKeys
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.libretrodroid.Controller
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_LEFT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_RIGHT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_DPAD
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

class GameViewModelInput(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val inputDeviceManager: InputDeviceManager,
    private val controllerConfigsManager: ControllerConfigsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val tilt: GameViewModelTilt,
    private val sideEffects: GameViewModelSideEffects,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private data class SingleAxisEvent(val axis: Int, val action: Int, val keyCode: Int, val port: Int)

    // Original Android input objects may be recycled once Activity dispatch
    // ends, before these async flows finish consuming them.
    private data class GamePadKeyEvent(
        val device: InputDevice,
        val action: Int,
        val keyCode: Int,
        val repeatCount: Int,
    )

    private data class GamePadMotionEvent(
        val device: InputDevice,
        val source: Int,
        val axisValues: Map<Int, Float>,
    ) {
        fun getAxisValue(axis: Int): Float = axisValues[axis] ?: 0f
    }

    private val controllerConfigsState = MutableStateFlow<Map<Int, ControllerConfig>>(mapOf())
    private val keyEventsFlow: MutableSharedFlow<GamePadKeyEvent> = MutableSharedFlow()
    private val motionEventsFlow: MutableSharedFlow<GamePadMotionEvent> = MutableSharedFlow()

    fun getAllTiltConfigurations(): List<TiltConfiguration> {
        return controllerConfigsState.value[0]
            ?.tiltConfigurations
            ?: emptyList()
    }

    fun getControllerConfigState(): Flow<Map<Int, ControllerConfig>> {
        return controllerConfigsState
    }

    private fun sendStickMotions(
        event: GamePadMotionEvent,
        port: Int,
    ) {
        if (port < 0) return
        when (event.source) {
            InputDevice.SOURCE_JOYSTICK -> {
                if (controllerConfigsState.value[port]?.mergeDPADAndLeftStickEvents == true) {
                    sendMergedMotionEvents(event, port)
                } else {
                    sendSeparateMotionEvents(event, port)
                }
            }
        }
    }

    private fun sendMergedMotionEvents(
        event: GamePadMotionEvent,
        port: Int,
    ) {
        val events =
            listOf(
                retrieveCoordinates(event, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y),
                retrieveCoordinates(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y),
            )

        val xVal = events.maxByOrNull { abs(it.x) }?.x ?: 0f
        val yVal = events.maxByOrNull { abs(it.y) }?.y ?: 0f

        retroGameView.retroGameView?.sendMotionEvent(MOTION_SOURCE_DPAD, xVal, yVal, port)
        retroGameView.retroGameView?.sendMotionEvent(MOTION_SOURCE_ANALOG_LEFT, xVal, yVal, port)

        sendStickMotion(
            event,
            MOTION_SOURCE_ANALOG_RIGHT,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            port,
        )
    }

    private fun sendStickMotion(
        event: GamePadMotionEvent,
        source: Int,
        xAxis: Int,
        yAxis: Int,
        port: Int,
    ) {
        val coords = retrieveCoordinates(event, xAxis, yAxis)
        retroGameView.retroGameView?.sendMotionEvent(source, coords.x, coords.y, port)
    }

    private fun sendDPADMotion(
        event: GamePadMotionEvent,
        source: Int,
        xAxis: Int,
        yAxis: Int,
        port: Int,
    ) {
        retroGameView.retroGameView?.sendMotionEvent(
            source,
            event.getAxisValue(xAxis),
            event.getAxisValue(yAxis),
            port,
        )
    }

    private fun sendSeparateMotionEvents(
        event: GamePadMotionEvent,
        port: Int,
    ) {
        sendDPADMotion(
            event,
            MOTION_SOURCE_DPAD,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            port,
        )
        sendStickMotion(
            event,
            MOTION_SOURCE_ANALOG_LEFT,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            port,
        )
        sendStickMotion(
            event,
            MOTION_SOURCE_ANALOG_RIGHT,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            port,
        )
    }

    private fun retrieveCoordinates(
        event: GamePadMotionEvent,
        xAxis: Int,
        yAxis: Int,
    ): PointF {
        return PointF(event.getAxisValue(xAxis), event.getAxisValue(yAxis))
    }

    fun sendKeyEvent(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val device = event.device ?: return false
        if (InputKey(keyCode) !in device.getInputClass().getInputKeys()) return false
        val snapshot = GamePadKeyEvent(device, event.action, keyCode, event.repeatCount)
        scope.launch {
            keyEventsFlow.emit(snapshot)
        }
        return true
    }

    fun sendMotionEvent(event: MotionEvent): Boolean {
        // Non-joystick generic motions should continue to Activity dispatch.
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        val device = event.device ?: return false
        // Never retain MotionEvent across a coroutine suspension: Android may
        // recycle it while either of our two flow collectors is still active.
        val axes =
            (
                device.getInputClass().getAxesMap().keys +
                    listOf(
                        MotionEvent.AXIS_HAT_X,
                        MotionEvent.AXIS_HAT_Y,
                        MotionEvent.AXIS_X,
                        MotionEvent.AXIS_Y,
                        MotionEvent.AXIS_Z,
                        MotionEvent.AXIS_RZ,
                    )
            ).distinct().associateWith { axis -> event.getAxisValue(axis) }
        val snapshot = GamePadMotionEvent(device, event.source, axes)
        scope.launch {
            motionEventsFlow.emit(snapshot)
        }
        return true
    }

    fun getEnabledInputDevices(): Flow<List<InputDevice>> {
        return inputDeviceManager.getEnabledInputsObservable()
    }

    private fun updateControllers(controllers: Map<Int, ControllerConfig>) {
        val gameView = retroGameView.retroGameView ?: return
        val supported = gameView.getControllers()
        supported.toIndexedMap()
            .zipOnKeys(controllers, this::findControllerId)
            .filterNotNullValues()
            .forEach { (port, controllerId) ->
                Timber.i("Controls setting $port to $controllerId")
                gameView.setControllerType(port, controllerId)
            }

        // FCEUmm autodetection remains the default. A player may explicitly
        // force Zapper on controller port 2 for a combination cartridge or
        // homebrew that the core's database does not recognize.
        if (NesZapperPreferences(appContext).isEnabled(retroGameView.selectedGameId())) {
            val zapperId = NesZapperController.portTwoZapperId(supported)
            if (zapperId != null) {
                gameView.setControllerType(1, zapperId)
            } else {
                Timber.w("This NES core does not expose a port 2 Zapper")
            }
        }
    }

    private fun findControllerId(
        supported: Array<Controller>,
        controllerConfig: ControllerConfig,
    ): Int? {
        return supported
            .firstOrNull { controller ->
                sequenceOf(
                    controller.id == controllerConfig.libretroId,
                    controller.description == controllerConfig.libretroDescriptor,
                ).any { it }
            }?.id
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.launchOnState(Lifecycle.State.CREATED) {
            initializeControllerConfigsFlow()
        }

        owner.launchOnState(Lifecycle.State.CREATED) {
            initializeGamePadShortcutsFlow()
        }

        owner.launchOnState(Lifecycle.State.CREATED) {
            initializeGamePadKeysFlow()
        }

        owner.launchOnState(Lifecycle.State.CREATED) {
            initializeVirtualGamePadMotionsFlow()
        }

        owner.launchOnState(Lifecycle.State.CREATED) {
            initializeGamePadMotionsFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeControllersConfigFlow()
        }
    }

    private suspend fun initializeControllerConfigsFlow() {
        retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
        controllerConfigsState.safeCollect {
            updateControllers(it)
        }
    }

    private suspend fun initializeGamePadShortcutsFlow() {
        inputDeviceManager.getGameShortcutsObservable()
            .distinctUntilChanged()
            .safeCollect { allShortcuts ->
                allShortcuts.values
                    .firstNotNullOfOrNull { shortcuts ->
                        shortcuts.firstOrNull { it.type == GameShortcutType.MENU }
                    }
                    ?.let {
                        val message =
                            appContext.resources.getString(
                                R.string.game_toast_settings_button_using_gamepad,
                                it.name,
                            )
                        sideEffects.showToast(message)
                    }
            }
    }

    private suspend fun initializeGamePadKeysFlow() {
        val pressedKeys = mutableSetOf<Int>()

        val filteredKeyEvents =
            keyEventsFlow
                .filter { it.repeatCount == 0 }
                .map { Triple(it.device, it.action, it.keyCode) }
                .distinctUntilChanged()

        val combinedObservable =
            combine(
                inputDeviceManager.getGameShortcutsObservable(),
                inputDeviceManager.getGamePadsPortMapperObservable(),
                inputDeviceManager.getInputBindingsObservable(),
                filteredKeyEvents,
                ::NTuple4,
            )

        combinedObservable
            .onStart { pressedKeys.clear() }
            .onCompletion { pressedKeys.clear() }
            .safeCollect { (shortcuts, ports, bindings, event) ->
                val (device, action, keyCode) = event
                val port = ports(device)
                val bindKeyCode = bindings(device)[InputKey(keyCode)]?.keyCode ?: keyCode

                if (port == 0) {
                    if (bindKeyCode == KeyEvent.KEYCODE_BUTTON_MODE && action == KeyEvent.ACTION_DOWN) {
                        sideEffects.showMenu(tilt, this)
                        return@safeCollect
                    }

                    if (action == KeyEvent.ACTION_DOWN) {
                        pressedKeys.add(keyCode)
                    } else if (action == KeyEvent.ACTION_UP) {
                        pressedKeys.remove(keyCode)
                    }

                    shortcuts[device]?.forEach { shortcut ->
                        if (shortcut.keys.isNotEmpty() && pressedKeys.containsAll(shortcut.keys)) {
                            when (shortcut.type) {
                                GameShortcutType.MENU -> sideEffects.showMenu(tilt, this)
                                GameShortcutType.QUICK_LOAD -> sideEffects.loadQuickSave()
                                GameShortcutType.QUICK_SAVE -> sideEffects.saveQuickSave()
                                GameShortcutType.TOGGLE_FAST_FORWARD -> sideEffects.toggleFastForward()
                            }
                            return@safeCollect
                        }
                    }
                }

                port?.let {
                    retroGameView.retroGameView?.sendKeyEvent(action, bindKeyCode, it)
                }
            }
    }

    private suspend fun initializeVirtualGamePadMotionsFlow() {
        val events =
            combine(
                inputDeviceManager.getGamePadsPortMapperObservable(),
                motionEventsFlow,
                ::NTuple2,
            )

        events
            .mapNotNull { (ports, event) ->
                ports(event.device)?.let { it to event }
            }
            .map { (port, event) ->
                val axes = event.device.getInputClass().getAxesMap().entries

                axes.map { (axis, button) ->
                    val action =
                        if (event.getAxisValue(axis) > 0.5) {
                            KeyEvent.ACTION_DOWN
                        } else {
                            KeyEvent.ACTION_UP
                        }
                    SingleAxisEvent(axis, action, button, port)
                }.toSet()
            }
            .scan(emptySet<SingleAxisEvent>()) { prev, next ->
                next.minus(prev).forEach {
                    retroGameView.retroGameView?.sendKeyEvent(it.action, it.keyCode, it.port)
                }
                next
            }
            .safeCollect { }
    }

    private suspend fun initializeGamePadMotionsFlow() {
        val events =
            combine(
                inputDeviceManager.getGamePadsPortMapperObservable(),
                motionEventsFlow,
                ::NTuple2,
            )

        events
            .safeCollect { (ports, event) ->
                ports(event.device)?.let {
                    sendStickMotions(event, it)
                }
            }
    }

    private suspend fun initializeControllersConfigFlow() {
        try {
            retroGameView.waitRetroGameViewInitialized()
            val controllers = controllerConfigsManager.getControllerConfigs(system.id, systemCoreConfig)
            controllerConfigsState.value = controllers
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
