package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState
import kotlin.math.roundToInt

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val dragEditMode = viewModel.isDragEditMode().collectAsState(false).value

        BackHandler(enabled = dragEditMode) {
            viewModel.setDragEditMode(false)
        }
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        val showTouchPads =
            touchControllerSettings != null &&
                currentControllerConfig != null &&
                touchControlsVisibleState.value

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }
            val leftPadBounds = remember { mutableStateOf<Rect?>(null) }
            val rightPadBounds = remember { mutableStateOf<Rect?>(null) }

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value

            LaunchedEffect(fullPos, viewPos) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                gameView.viewport = viewport
            }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                        showTouchPads,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                )

                if (showTouchPads && touchControllerSettings != null && currentControllerConfig != null) {
                    CompositionLocalProvider(
                        LocalLemuroidPadTheme provides LemuroidPadTheme(touchControllerSettings.opacity),
                    ) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

                        leftGamePad?.invoke(
                            this,
                            Modifier
                                .layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD)
                                .onGloballyPositioned { leftPadBounds.value = it.boundsInRoot() },
                            touchControllerSettings,
                        )
                        rightGamePad?.invoke(
                            this,
                            Modifier
                                .layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD)
                                .onGloballyPositioned { rightPadBounds.value = it.boundsInRoot() },
                            touchControllerSettings,
                        )

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                            isLandscape = isLandscape,
                        )
                    }
                }
            }

            if (dragEditMode && touchControllerSettings != null) {
                NesControllerDragOverlay(
                    leftBounds = leftPadBounds.value,
                    rightBounds = rightPadBounds.value,
                    settings = touchControllerSettings,
                    onPreview = viewModel::previewTouchControllerSettings,
                    onCommit = viewModel::updateTouchControllerSettings,
                    onDone = { viewModel.setDragEditMode(false) },
                    onFineTune = {
                        viewModel.setDragEditMode(false)
                        viewModel.showEditControls(true)
                    },
                )
            }
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
    isLandscape: Boolean,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        LemuroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
        MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings, isLandscape)
    }
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    isLandscape: Boolean,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isLandscape) {
                                com.swordfish.lemuroid.R.string.opennes_landscape_controls
                            } else {
                                com.swordfish.lemuroid.R.string.opennes_portrait_controls
                            },
                        ),
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_editor_hint),
                    fontSize = 12.sp,
                )
                TextButton(onClick = { viewModel.setDragEditMode(true) }) {
                    Text(stringResource(com.swordfish.lemuroid.R.string.opennes_controls_drag_start))
                }
                MenuEditControllerCluster(
                    label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_left_group),
                    resetLabel = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_reset_left),
                    size = touchControllerSettings.leftScale ?: touchControllerSettings.scale,
                    offsetX = touchControllerSettings.leftOffsetX,
                    offsetY = touchControllerSettings.leftOffsetY,
                    onSizeChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(leftScale = it))
                    },
                    onSizeCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(leftScale = it))
                    },
                    onXChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(leftOffsetX = it))
                    },
                    onXCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(leftOffsetX = it))
                    },
                    onYChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(leftOffsetY = it))
                    },
                    onYCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(leftOffsetY = it))
                    },
                    onReset = {
                        viewModel.updateTouchControllerSettings(
                            touchControllerSettings.copy(leftScale = null, leftOffsetX = 0f, leftOffsetY = 0f),
                        )
                    },
                )
                MenuEditControllerCluster(
                    label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_right_group),
                    resetLabel = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_reset_right),
                    size = touchControllerSettings.rightScale ?: touchControllerSettings.scale,
                    offsetX = touchControllerSettings.rightOffsetX,
                    offsetY = touchControllerSettings.rightOffsetY,
                    onSizeChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(rightScale = it))
                    },
                    onSizeCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(rightScale = it))
                    },
                    onXChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(rightOffsetX = it))
                    },
                    onXCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(rightOffsetX = it))
                    },
                    onYChange = {
                        viewModel.previewTouchControllerSettings(touchControllerSettings.copy(rightOffsetY = it))
                    },
                    onYCommit = {
                        viewModel.updateTouchControllerSettings(touchControllerSettings.copy(rightOffsetY = it))
                    },
                    onReset = {
                        viewModel.updateTouchControllerSettings(
                            touchControllerSettings.copy(rightScale = null, rightOffsetX = 0f, rightOffsetY = 0f),
                        )
                    },
                )
                Text(
                    text = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_shared_group),
                    fontWeight = FontWeight.Bold,
                )
                MenuEditTouchControlRow(
                    label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_opacity),
                ) {
                    TouchControlEditorSlider(
                        value = touchControllerSettings.opacity,
                        valueRange = 0.35f..1f,
                        onPreview = {
                            viewModel.previewTouchControllerSettings(touchControllerSettings.copy(opacity = it))
                        },
                        onCommit = {
                            viewModel.updateTouchControllerSettings(touchControllerSettings.copy(opacity = it))
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(
                        label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_rotation),
                    ) {
                        TouchControlEditorSlider(
                            value = touchControllerSettings.rotation,
                            onPreview = {
                                viewModel.previewTouchControllerSettings(touchControllerSettings.copy(rotation = it))
                            },
                            onCommit = {
                                viewModel.updateTouchControllerSettings(touchControllerSettings.copy(rotation = it))
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    TextButton(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditControllerCluster(
    label: String,
    resetLabel: String,
    size: Float,
    offsetX: Float,
    offsetY: Float,
    onSizeChange: (Float) -> Unit,
    onSizeCommit: (Float) -> Unit,
    onXChange: (Float) -> Unit,
    onXCommit: (Float) -> Unit,
    onYChange: (Float) -> Unit,
    onYCommit: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Text(text = label, fontWeight = FontWeight.Bold)
    MenuEditTouchControlRow(
        label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_size),
    ) {
        TouchControlEditorSlider(
            value = size,
            onPreview = onSizeChange,
            onCommit = onSizeCommit,
        )
    }
    MenuEditTouchControlRow(
        label =
            stringResource(com.swordfish.lemuroid.R.string.opennes_controls_horizontal) +
                " (${(offsetX * TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_X).roundToInt()} dp)",
    ) {
        TouchControlEditorSlider(
            value = offsetX,
            valueRange = -1f..1f,
            onPreview = onXChange,
            onCommit = onXCommit,
        )
    }
    MenuEditTouchControlRow(
        label =
            stringResource(com.swordfish.lemuroid.R.string.opennes_controls_vertical) +
                " (${(offsetY * TouchControllerSettingsManager.MAX_CLUSTER_OFFSET_Y).roundToInt()} dp)",
    ) {
        TouchControlEditorSlider(
            value = offsetY,
            valueRange = -1f..1f,
            onPreview = onYChange,
            onCommit = onYCommit,
        )
    }
    TextButton(onClick = onReset) {
        Text(text = resetLabel)
    }
}

/**
 * Preview slider changes immediately, then persist the final value only once
 * after release. Avoids launching a disk write for every pointer movement.
 */
@Composable
private fun TouchControlEditorSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val lastPreview = remember { mutableFloatStateOf(value) }
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        valueRange = valueRange,
        onValueChange = {
            lastPreview.floatValue = it
            onPreview(it)
        },
        onValueChangeFinished = { onCommit(lastPreview.floatValue) },
    )
}

@Composable
private fun MenuEditTouchControlRow(
    label: String,
    slider: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, fontSize = 12.sp)
        slider()
    }
}
