package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.ControllerAppearancePreview
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.NesColorWheelDialog
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsGameStateStore
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.LocalNesControllerPalette
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
import com.swordfish.touchinput.radial.NesControllerPresets
import com.swordfish.touchinput.radial.layouts.LocalNesControlBoundsReporter
import com.swordfish.touchinput.radial.layouts.LocalNesDpadEditMode
import com.swordfish.touchinput.radial.layouts.LocalNesLandscapeHandheld
import com.swordfish.touchinput.radial.layouts.LocalNesLandscapeMinimal
import com.swordfish.touchinput.radial.layouts.LocalNesPortraitLayout
import com.swordfish.touchinput.radial.layouts.NESLandscapeLeft
import com.swordfish.touchinput.radial.layouts.NESLandscapeRight
import com.swordfish.touchinput.radial.layouts.NesEditableControl
import com.swordfish.touchinput.radial.rememberNesControllerAppearance
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        // Appearance is global; geometry is still stored per orientation.
        val controllerAppearance = rememberNesControllerAppearance()
        val appContext = LocalContext.current.applicationContext
        val onboardingStore = remember(appContext) { NesControllerOnboardingStore(appContext) }
        val appearanceStore = remember(appContext) { NesControllerAppearanceStore(appContext) }
        val onboardingVisible = remember { mutableStateOf(false) }
        val pendingLayoutEditor = remember { mutableStateOf(false) }
        val nativeUnlock = RetroAchievementsGameStateStore.snapshots.collectAsState().value.notice

        LaunchedEffect(nativeUnlock?.sequence) {
            nativeUnlock?.let { notice ->
                delay(4_500L)
                RetroAchievementsGameStateStore.dismissUnlock(notice.sequence)
            }
        }

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
        // A compact screen changes only the generated default layout.
        // Existing saved per-orientation settings remain untouched.
        val density = LocalDensity.current
        val cutoutInsets = WindowInsets.displayCutout
        val portraitWidthDp = if (isLandscape) null else maxWidth.value
        val landscapeHeightDp = if (isLandscape) maxHeight.value else null
        val touchSettingsFlow =
            remember(viewModel, density, cutoutInsets, portraitWidthDp, landscapeHeightDp) {
                viewModel.getTouchControlsSettings(
                    density,
                    cutoutInsets,
                    portraitWidthDp,
                    landscapeHeightDp,
                )
            }
        val touchControllerSettingsState = touchSettingsFlow.collectAsState(null)

        val landscapeSetupSettings =
            remember(viewModel, density, cutoutInsets) {
                viewModel.getLandscapeTouchControlsSettings(density, cutoutInsets)
            }.collectAsState(null).value
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
        val isLoading = viewModel.loadingState.collectAsState(true).value
        val rewindHoldAvailable = viewModel.canHoldRewind().collectAsState(false).value

        // First-game onboarding waits until real NES controls are visible.
        // A paired physical gamepad does not cause an unusable setup popup.
        LaunchedEffect(showTouchPads, isLoading, dragEditMode) {
            if (showTouchPads && !isLoading && !dragEditMode && onboardingStore.shouldShow()) {
                onboardingVisible.value = true
            }
            if (!showTouchPads) onboardingVisible.value = false
        }

        // The onboarding sheet is a separate modal window. Open the editor
        // only AFTER that window has dismissed, so the next editor is not
        // obscured underneath its outgoing scrim.
        LaunchedEffect(onboardingVisible.value, pendingLayoutEditor.value, showTouchPads) {
            if (!showTouchPads) {
                pendingLayoutEditor.value = false
            } else if (!onboardingVisible.value && pendingLayoutEditor.value) {
                delay(350)
                pendingLayoutEditor.value = false
                viewModel.showEditControls(true)
            }
        }

        val landscapeOverlaySelected =
            touchControllerSettings?.landscapeOverlay ?: currentControllerConfig?.allowTouchOverlay ?: true
        val requestedHandheld = touchControllerSettings?.landscapeHandheld == true
        val requestedSidePanels = requestedHandheld || !landscapeOverlaySelected
        // The previous implementation used PadKit's radial layout for
        // framed panels and gave the left/right grips different intrinsic
        // widths. That shrank the controls AND shifted gameplay right.
        // Both side-panel styles now use the same centered 4:3 viewport.
        val compactLandscape =
            isLandscape &&
                requestedSidePanels &&
                !NesLandscapeFit.canShowSideControls(maxWidth.value, maxHeight.value)
        val handheldActive = isLandscape && showTouchPads && requestedHandheld && !compactLandscape
        val allowTouchOverlay =
            when {
                compactLandscape -> true
                handheldActive -> false
                else -> landscapeOverlaySelected
            }
        val sidePanelWidthDp =
            if (isLandscape && showTouchPads && !allowTouchOverlay) {
                NesLandscapeFit.sideWidthDp(maxWidth.value, maxHeight.value)
            } else {
                null
            }

        // The editing overlay must never outlive the virtual controls.
        // A physical gamepad can become active mid-drag and hide PadKit.
        LaunchedEffect(dragEditMode, showTouchPads) {
            if (dragEditMode && !showTouchPads) {
                viewModel.setDragEditMode(false)
            }
        }

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
            val controlBounds = remember { mutableStateMapOf<NesEditableControl, Rect>() }
            LaunchedEffect(showTouchPads, isLandscape) { controlBounds.clear() }

            // Clear bounds only when PadKit controls disappear. On rotation,
            // onGloballyPositioned supplies the new sizes; clearing them after
            // that layout pass could erase the newly measured drag targets.
            LaunchedEffect(showTouchPads) {
                if (!showTouchPads) {
                    leftPadBounds.value = null
                    rightPadBounds.value = null
                }
            }

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
                if (
                    fullPos == null || viewPos == null ||
                    fullPos.width <= 0f || fullPos.height <= 0f ||
                    viewPos.width <= 0f || viewPos.height <= 0f
                ) {
                    return@LaunchedEffect
                }
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
                        allowTouchOverlay,
                        showTouchPads,
                        sidePanelWidthDp,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(
                                    if (isLandscape && allowTouchOverlay) {
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                    } else {
                                        // Side-panel controls separate gameplay
                                        // from cutouts. Do not shift the centered
                                        // NES picture for a left-side camera hole.
                                        WindowInsetsSides.Top
                                    },
                                ),
                            )
                            .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                )

                if (showTouchPads && touchControllerSettings != null && currentControllerConfig != null) {
                    CompositionLocalProvider(
                        LocalLemuroidPadTheme provides LemuroidPadTheme(touchControllerSettings.opacity),
                        LocalNesControllerPalette provides controllerAppearance.palette,
                        LocalNesControlBoundsReporter provides { id, bounds ->
                            if (controlBounds[id] != bounds) controlBounds[id] = bounds
                        },
                        LocalNesDpadEditMode provides dragEditMode,
                        LocalNesPortraitLayout provides !isLandscape,
                        LocalNesLandscapeHandheld provides handheldActive,
                        LocalNesLandscapeMinimal provides (
                            isLandscape && allowTouchOverlay && touchControllerSettings.landscapeMinimal
                        ),
                    ) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                                portrait = true,
                            )
                        } else if (handheldActive) {
                            val full = fullScreenPosition.value
                            val view = viewportPosition.value
                            val viewport =
                                if (full != null && view != null) {
                                    Rect(
                                        view.left - full.left,
                                        view.top - full.top,
                                        view.right - full.left,
                                        view.bottom - full.top,
                                    )
                                } else {
                                    null
                                }
                            NesHandheldShell(modifier = Modifier.fillMaxSize(), viewport = viewport)
                        } else if (!allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                                innerEdge = LandscapePanelInnerEdge.RIGHT,
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                                innerEdge = LandscapePanelInnerEdge.LEFT,
                            )
                        }

                        if (isLandscape && !allowTouchOverlay) {
                            // Independent PadKit control surfaces use the
                            // ENTIRE equal-width grips. Secondary buttons
                            // no longer shrink the D-pad and A/B dial.
                            NESLandscapeLeft(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                                settings = touchControllerSettings,
                                gripWidthDp = sidePanelWidthDp ?: 0f,
                                onPrimaryBounds = { leftPadBounds.value = it },
                            )
                            NESLandscapeRight(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                                settings = touchControllerSettings,
                                gripWidthDp = sidePanelWidthDp ?: 0f,
                                onPrimaryBounds = { rightPadBounds.value = it },
                            )
                        } else {
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
                        }

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                            isLandscape = isLandscape,
                            compactLandscape = compactLandscape,
                        )
                    }
                }
            }

            if (showTouchPads && touchControllerSettings != null && !isLoading && !onboardingVisible.value) {
                NesTransportControls(
                    menuBounds = controlBounds[NesEditableControl.MENU],
                    fullBounds = fullScreenPosition.value,
                    settings = touchControllerSettings,
                    editing = dragEditMode,
                    rewindAvailable = rewindHoldAvailable,
                    onRewindPress = viewModel::startHoldRewind,
                    onRewindRelease = viewModel::stopHoldRewind,
                    onRewindStep = viewModel::rewindAccessibleStep,
                    onFastForward = viewModel::toggleFastForward,
                    reportBounds = { id, bounds ->
                        if (controlBounds[id] != bounds) controlBounds[id] = bounds
                    },
                )
            }

            if (dragEditMode && showTouchPads && touchControllerSettings != null) {
                NesControllerDragOverlay(
                    leftBounds = leftPadBounds.value,
                    rightBounds = rightPadBounds.value,
                    controlBounds = controlBounds,
                    settings = touchControllerSettings,
                    landscapeSidePanels = isLandscape && !allowTouchOverlay,
                    onPreview = viewModel::previewTouchControllerSettings,
                    onCommit = viewModel::updateTouchControllerSettings,
                    onDone = { viewModel.setDragEditMode(false) },
                    onFineTune = {
                        viewModel.setDragEditMode(false)
                        viewModel.showEditControls(true)
                    },
                    onRestoreDefaults = viewModel::resetTouchControls,
                )
            }
        }

        if (onboardingVisible.value && showTouchPads && !isLoading) {
            NesControllerSetupSheet(
                currentPresetId = controllerAppearance.preset.id,
                currentPalette = controllerAppearance.palette,
                selectedLandscapeStyle = NesLandscapeStyle.fromSettings(landscapeSetupSettings),
                onSelectLandscapeStyle = { style ->
                    val current = landscapeSetupSettings ?: TouchControllerSettingsManager.Settings()
                    viewModel.updateLandscapeTouchControllerSettings(style.apply(current))
                },
                onChoosePreset = { appearanceStore.selectPreset(it) },
                onChooseColor = { part, hex -> appearanceStore.setColor(part, hex) },
                onCustomizeLayout = {
                    onboardingStore.complete()
                    pendingLayoutEditor.value = true
                    onboardingVisible.value = false
                },
                onUseDefaults = {
                    onboardingStore.complete()
                    onboardingVisible.value = false
                },
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        if (nativeUnlock != null && !isLoading && !onboardingVisible.value) {
            RetroAchievementUnlockBanner(
                title = nativeUnlock.title,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private enum class LandscapePanelInnerEdge {
    LEFT,
    RIGHT,
}

@Composable
private fun PadContainer(
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
    innerEdge: LandscapePanelInnerEdge? = null,
) {
    val theme = LocalLemuroidPadTheme.current
    val palette = LocalNesControllerPalette.current
    // One housing surface replaces overlapping inherited glass rectangles.
    // Decorative only: all NES input events remain handled by PadKit.
    Canvas(modifier = modifier.fillMaxSize()) {
        val inset = if (portrait) 6.dp.toPx() else 3.dp.toPx()
        val panelSize =
            Size(
                (size.width - 2f * inset).coerceAtLeast(0f),
                (size.height - 2f * inset).coerceAtLeast(0f),
            )
        if (panelSize.width <= 0f || panelSize.height <= 0f) return@Canvas
        val corner = 18.dp.toPx()
        val origin = Offset(inset, inset)
        drawRoundRect(
            color = palette.panel.copy(alpha = 0.90f * theme.opacity),
            topLeft = origin,
            size = panelSize,
            cornerRadius = CornerRadius(corner, corner),
        )
        drawRoundRect(
            color = palette.outline.copy(alpha = 0.48f * theme.opacity),
            topLeft = origin,
            size = panelSize,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        if (portrait && panelSize.width > 56.dp.toPx()) {
            val y = origin.y + 10.dp.toPx()
            drawLine(
                color = palette.accent.copy(alpha = 0.88f * theme.opacity),
                start = Offset(origin.x + 28.dp.toPx(), y),
                end = Offset(origin.x + panelSize.width - 28.dp.toPx(), y),
                strokeWidth = 3.dp.toPx(),
            )
        } else if (innerEdge != null && panelSize.height > 56.dp.toPx()) {
            val isLeftEdge = innerEdge == LandscapePanelInnerEdge.LEFT
            val x =
                if (isLeftEdge) {
                    origin.x + 9.dp.toPx()
                } else {
                    origin.x + panelSize.width - 9.dp.toPx()
                }
            drawLine(
                color = palette.accent.copy(alpha = 0.82f * theme.opacity),
                start = Offset(x, origin.y + 24.dp.toPx()),
                end = Offset(x, origin.y + panelSize.height - 24.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
    isLandscape: Boolean,
    compactLandscape: Boolean,
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
        MenuEditTouchControls(
            viewModel,
            controllerConfig,
            touchControllerSettings,
            isLandscape,
            compactLandscape,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    isLandscape: Boolean,
    compactLandscape: Boolean,
) {
    val visible = viewModel.isEditControlShown().collectAsState(false)
    val confirmReset = remember { mutableStateOf(false) }
    val selectedTab = remember { mutableStateOf(0) }
    val secondaryTab = remember { mutableStateOf(0) }
    val faceButtonTab = remember { mutableStateOf(0) }
    val selectedColorPart = remember { mutableStateOf<NesColorPart?>(null) }
    val context = LocalContext.current.applicationContext
    val appearanceStore = remember(context) { NesControllerAppearanceStore(context) }
    val appearance = rememberNesControllerAppearance()
    if (!visible.value) return

    ModalBottomSheet(
        onDismissRequest = { viewModel.showEditControls(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        stringResource(
                            if (isLandscape) {
                                com.swordfish.lemuroid.R.string.opennes_editor_landscape_title
                            } else {
                                com.swordfish.lemuroid.R.string.opennes_editor_portrait_title
                            },
                        ),
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { viewModel.showEditControls(false) }) {
                    Text(stringResource(R.string.touch_customize_button_done))
                }
            }
            if (isLandscape) {
                Text(
                    stringResource(com.swordfish.lemuroid.R.string.opennes_landscape_style_title),
                    fontWeight = FontWeight.Bold,
                )
                NesLandscapeStylePicker(
                    selected =
                        NesLandscapeStyle.fromSettings(
                            touchControllerSettings,
                            controllerConfig.allowTouchOverlay,
                        ),
                    palette = appearance.palette,
                    onSelect = { style ->
                        viewModel.updateTouchControllerSettings(style.apply(touchControllerSettings))
                    },
                    compact = true,
                )
                if (compactLandscape) {
                    Text(stringResource(com.swordfish.lemuroid.R.string.opennes_landscape_compact_fallback))
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setDragEditMode(true) },
            ) {
                Text(stringResource(com.swordfish.lemuroid.R.string.opennes_editor_move))
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tabs =
                    listOf(
                        com.swordfish.lemuroid.R.string.opennes_editor_dpad,
                        com.swordfish.lemuroid.R.string.opennes_editor_buttons,
                        com.swordfish.lemuroid.R.string.opennes_editor_more,
                        com.swordfish.lemuroid.R.string.opennes_editor_themes,
                    )
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            when (selectedTab.value) {
                0 -> {
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
                    MenuEditIndividualDpadHitboxes(
                        settings = touchControllerSettings,
                        onPreview = viewModel::previewTouchControllerSettings,
                        onCommit = viewModel::updateTouchControllerSettings,
                    )
                }
                1 -> {
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
                        text = stringResource(com.swordfish.lemuroid.R.string.opennes_editor_individual_faces),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A", "B").forEachIndexed { index, name ->
                            FilterChip(
                                selected = faceButtonTab.value == index,
                                onClick = { faceButtonTab.value = index },
                                label = { Text(name) },
                            )
                        }
                    }
                    val selectedFaceGeometry =
                        if (faceButtonTab.value == 0) {
                            touchControllerSettings.aButton
                        } else {
                            touchControllerSettings.bButton
                        }
                    val updateFace: (TouchControllerSettingsManager.ButtonGeometry) ->
                    TouchControllerSettingsManager.Settings = { geometry ->
                        if (faceButtonTab.value == 0) {
                            touchControllerSettings.copy(aButton = geometry)
                        } else {
                            touchControllerSettings.copy(bButton = geometry)
                        }
                    }
                    MenuEditSecondaryButton(
                        geometry = selectedFaceGeometry,
                        onPreview = { viewModel.previewTouchControllerSettings(updateFace(it)) },
                        onCommit = { viewModel.updateTouchControllerSettings(updateFace(it)) },
                        onReset = {
                            viewModel.updateTouchControllerSettings(
                                updateFace(TouchControllerSettingsManager.ButtonGeometry()),
                            )
                        },
                    )
                }
                2 -> {
                    Text(
                        text = stringResource(com.swordfish.lemuroid.R.string.opennes_editor_secondary_controls),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("START", "SELECT", "MENU").forEachIndexed { index, label ->
                            FilterChip(
                                selected = secondaryTab.value == index,
                                onClick = { secondaryTab.value = index },
                                label = { Text(label) },
                            )
                        }
                    }
                    val buttonGeometry =
                        when (secondaryTab.value) {
                            0 -> touchControllerSettings.startButton
                            1 -> touchControllerSettings.selectButton
                            else -> touchControllerSettings.menuButton
                        }
                    val updateButton: (TouchControllerSettingsManager.ButtonGeometry) ->
                    TouchControllerSettingsManager.Settings = { geometry ->
                        when (secondaryTab.value) {
                            0 -> touchControllerSettings.copy(startButton = geometry)
                            1 -> touchControllerSettings.copy(selectButton = geometry)
                            else -> touchControllerSettings.copy(menuButton = geometry)
                        }
                    }
                    MenuEditSecondaryButton(
                        geometry = buttonGeometry,
                        onPreview = { viewModel.previewTouchControllerSettings(updateButton(it)) },
                        onCommit = { viewModel.updateTouchControllerSettings(updateButton(it)) },
                        onReset = {
                            viewModel.updateTouchControllerSettings(
                                updateButton(TouchControllerSettingsManager.ButtonGeometry()),
                            )
                        },
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
                                    viewModel.previewTouchControllerSettings(
                                        touchControllerSettings.copy(rotation = it),
                                    )
                                },
                                onCommit = {
                                    viewModel.updateTouchControllerSettings(touchControllerSettings.copy(rotation = it))
                                },
                            )
                        }
                    }
                    if (isLandscape) {
                        val mode =
                            NesLandscapeStyle.fromSettings(touchControllerSettings, controllerConfig.allowTouchOverlay)
                        if (mode == NesLandscapeStyle.OVERLAY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(com.swordfish.lemuroid.R.string.opennes_landscape_minimal),
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = touchControllerSettings.landscapeMinimal,
                                    onCheckedChange = {
                                        viewModel.updateTouchControllerSettings(
                                            touchControllerSettings.copy(landscapeMinimal = it),
                                        )
                                    },
                                )
                            }
                        }
                        if (compactLandscape && mode != NesLandscapeStyle.OVERLAY) {
                            Text(
                                stringResource(com.swordfish.lemuroid.R.string.opennes_editor_compact_hint),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    TextButton(onClick = { confirmReset.value = true }) {
                        Text(stringResource(R.string.touch_customize_button_reset))
                    }
                }
                else -> {
                    // The same real-time rendering as Settings and onboarding.
                    // Theme and color changes update the running game immediately.
                    ControllerAppearancePreview(appearance.palette)
                    NesControllerPresets.all.chunked(2).forEach { presets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = appearance.preset.id == preset.id,
                                    onClick = { appearanceStore.selectPreset(preset.id) },
                                    label = { Text(preset.title, maxLines = 2) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(com.swordfish.lemuroid.R.string.opennes_editor_color_picker_title),
                        fontWeight = FontWeight.Bold,
                    )
                    NesColorPart.values().forEach { part ->
                        TextButton(
                            onClick = { selectedColorPart.value = part },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(28.dp)
                                            .heightIn(min = 28.dp)
                                            .background(appearance.palette.color(part), CircleShape),
                                )
                                Text(part.label, modifier = Modifier.weight(1f), maxLines = 2)
                                Text(
                                    NesControllerAppearanceStore.toHex(appearance.palette.color(part)),
                                    fontSize = 11.sp,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(com.swordfish.lemuroid.R.string.opennes_editor_orientation_hint),
                fontSize = 11.sp,
            )
        }
    }

    selectedColorPart.value?.let { part ->
        NesColorWheelDialog(
            part = part,
            initialColor = appearance.palette.color(part),
            onDismiss = { selectedColorPart.value = null },
            onApply = { hex ->
                appearanceStore.setColor(part, hex)
                selectedColorPart.value = null
            },
        )
    }

    if (confirmReset.value) {
        AlertDialog(
            onDismissRequest = { confirmReset.value = false },
            title = {
                Text(stringResource(com.swordfish.lemuroid.R.string.opennes_controls_reset_confirm_title))
            },
            text = {
                Text(stringResource(com.swordfish.lemuroid.R.string.opennes_controls_reset_confirm_description))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset.value = false
                        viewModel.resetTouchControls()
                    },
                ) {
                    Text(stringResource(R.string.touch_customize_button_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset.value = false }) {
                    Text(stringResource(com.swordfish.lemuroid.R.string.cancel))
                }
            },
        )
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
 * Fine-tune each real directional PadKit hitbox without splitting the visible
 * molded D-pad into four disconnected buttons. Settings remain orientation-
 * specific because the existing controller SettingsManager stores each view.
 */
@Composable
private fun MenuEditIndividualDpadHitboxes(
    settings: TouchControllerSettingsManager.Settings,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onCommit: (TouchControllerSettingsManager.Settings) -> Unit,
) {
    val direction = remember { mutableStateOf(0) }
    Text(
        text = stringResource(com.swordfish.lemuroid.R.string.opennes_editor_directional_hitboxes),
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(com.swordfish.lemuroid.R.string.opennes_editor_directional_hitboxes_hint),
        fontSize = 12.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("UP", "DOWN", "LEFT", "RIGHT").forEachIndexed { index, label ->
            FilterChip(
                selected = direction.value == index,
                onClick = { direction.value = index },
                label = { Text(label) },
            )
        }
    }
    val geometry =
        when (direction.value) {
            0 -> settings.dpadUp
            1 -> settings.dpadDown
            2 -> settings.dpadLeft
            else -> settings.dpadRight
        }
    val update: (TouchControllerSettingsManager.ButtonGeometry) ->
    TouchControllerSettingsManager.Settings = { value ->
        when (direction.value) {
            0 -> settings.copy(individualDpadHitboxes = true, dpadUp = value)
            1 -> settings.copy(individualDpadHitboxes = true, dpadDown = value)
            2 -> settings.copy(individualDpadHitboxes = true, dpadLeft = value)
            else -> settings.copy(individualDpadHitboxes = true, dpadRight = value)
        }
    }
    MenuEditSecondaryButton(
        geometry = geometry,
        onPreview = { onPreview(update(it)) },
        onCommit = { onCommit(update(it)) },
        onReset = {
            val reset = update(TouchControllerSettingsManager.ButtonGeometry())
            val defaults = TouchControllerSettingsManager.ButtonGeometry()
            onCommit(
                if (
                    reset.dpadUp == defaults && reset.dpadDown == defaults &&
                    reset.dpadLeft == defaults && reset.dpadRight == defaults
                ) {
                    reset.copy(individualDpadHitboxes = false)
                } else {
                    reset
                },
            )
        },
    )
}

@Composable
private fun MenuEditSecondaryButton(
    geometry: TouchControllerSettingsManager.ButtonGeometry,
    onPreview: (TouchControllerSettingsManager.ButtonGeometry) -> Unit,
    onCommit: (TouchControllerSettingsManager.ButtonGeometry) -> Unit,
    onReset: () -> Unit,
) {
    MenuEditTouchControlRow(label = stringResource(com.swordfish.lemuroid.R.string.opennes_controls_size)) {
        TouchControlEditorSlider(
            value = geometry.size,
            valueRange = TouchControllerSettingsManager.MIN_BUTTON_SIZE..TouchControllerSettingsManager.MAX_BUTTON_SIZE,
            onPreview = { onPreview(geometry.copy(size = it)) },
            onCommit = { onCommit(geometry.copy(size = it)) },
        )
    }
    MenuEditTouchControlRow(
        label =
            stringResource(com.swordfish.lemuroid.R.string.opennes_controls_horizontal) +
                " (${(geometry.offsetX * TouchControllerSettingsManager.MAX_BUTTON_OFFSET_X).roundToInt()} dp)",
    ) {
        TouchControlEditorSlider(
            value = geometry.offsetX,
            valueRange = -1f..1f,
            onPreview = { onPreview(geometry.copy(offsetX = it)) },
            onCommit = { onCommit(geometry.copy(offsetX = it)) },
        )
    }
    MenuEditTouchControlRow(
        label =
            stringResource(com.swordfish.lemuroid.R.string.opennes_controls_vertical) +
                " (${(geometry.offsetY * TouchControllerSettingsManager.MAX_BUTTON_OFFSET_Y).roundToInt()} dp)",
    ) {
        TouchControlEditorSlider(
            value = geometry.offsetY,
            valueRange = -1f..1f,
            onPreview = { onPreview(geometry.copy(offsetY = it)) },
            onCommit = { onCommit(geometry.copy(offsetY = it)) },
        )
    }
    TextButton(onClick = onReset) {
        Text(stringResource(com.swordfish.lemuroid.R.string.opennes_editor_reset_secondary))
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
    val hasEdited = remember { mutableStateOf(false) }
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        valueRange = valueRange,
        onValueChange = {
            hasEdited.value = true
            lastPreview.floatValue = it
            onPreview(it)
        },
        onValueChangeFinished = {
            // Android can finish a gesture after an editor reset or layout
            // change. Do not persist the initial/stale value on a no-op.
            if (hasEdited.value) {
                hasEdited.value = false
                onCommit(lastPreview.floatValue)
            }
        },
    )
}

@Composable
private fun MenuEditTouchControlRow(
    label: String,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.width(112.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            slider()
        }
    }
}

/**
 * Transport controls are independent Compose surfaces anchored beside the real
 * Menu pill. Their saved per-orientation drag coordinates move the visible
 * glyph and touch target together; the existing drag editor uses their bounds.
 */
@Composable
private fun NesTransportControls(
    menuBounds: Rect?,
    fullBounds: Rect?,
    settings: TouchControllerSettingsManager.Settings,
    editing: Boolean,
    rewindAvailable: Boolean,
    onRewindPress: () -> Unit,
    onRewindRelease: () -> Unit,
    onRewindStep: () -> Unit,
    onFastForward: () -> Unit,
    reportBounds: (NesEditableControl, Rect) -> Unit,
) {
    if (menuBounds == null || fullBounds == null) return
    val density = LocalDensity.current
    val buttonPx = with(density) { 36.dp.toPx() }
    val gapPx = with(density) { 12.dp.toPx() }
    val safe = WindowInsets.safeDrawing
    val safeLeft = safe.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat()
    val safeRight = safe.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat()
    val safeTop = safe.getTop(density).toFloat()
    val safeBottom = safe.getBottom(density).toFloat()

    @Composable
    fun TransportPosition(
        geometry: TouchControllerSettingsManager.ButtonGeometry,
        control: NesEditableControl,
        defaultX: Float,
        content: @Composable (Modifier) -> Unit,
    ) {
        val x =
            (defaultX + geometry.dragX * density.density)
                .coerceIn(safeLeft, (fullBounds.width - safeRight - buttonPx).coerceAtLeast(safeLeft))
        val y =
            (menuBounds.center.y - fullBounds.top - buttonPx / 2f + geometry.dragY * density.density)
                .coerceIn(safeTop, (fullBounds.height - safeBottom - buttonPx).coerceAtLeast(safeTop))
        content(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(36.dp)
                .onGloballyPositioned { reportBounds(control, it.boundsInRoot()) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TransportPosition(
            geometry = settings.rewindButton,
            control = NesEditableControl.REWIND,
            defaultX = menuBounds.left - fullBounds.left - 2f * (buttonPx + gapPx),
        ) { modifier ->
            if (rewindAvailable || editing) {
                NesRewindHoldButton(
                    modifier = modifier,
                    onPress = if (editing) ({}) else onRewindPress,
                    onRelease = if (editing) ({}) else onRewindRelease,
                    onAccessibleStep = if (editing) ({}) else onRewindStep,
                )
            }
        }
        TransportPosition(
            geometry = settings.fastForwardButton,
            control = NesEditableControl.FAST_FORWARD,
            defaultX = menuBounds.left - fullBounds.left - buttonPx - gapPx,
        ) { modifier ->
            Box(
                modifier =
                    modifier
                        .background(
                            androidx.compose.ui.graphics.Color(0xFF45454B),
                            androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        )
                        .clickable(enabled = !editing, onClick = onFastForward),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "⏩",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
