package com.swordfish.touchinput.radial.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.max
import androidx.core.content.edit
import com.swordfish.lemuroid.common.compose.pxToDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class TouchControllerSettingsManager(private val sharedPreferences: SharedPreferences) {
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }

    /**
     * Independent secondary-button placement. The neutral values preserve
     * older saved layouts and the existing PadKit touch mappings.
     */
    @Serializable
    data class ButtonGeometry(
        val size: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
    )

    @Serializable
    data class Settings(
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        val opacity: Float = DEFAULT_OPACITY,
        // Null retains the inherited controller's overlay decision for
        // existing installs; a selection in the landscape editor overrides it.
        val landscapeOverlay: Boolean? = null,
        // Visual-only minimal mode for overlaid landscape controls.
        val landscapeMinimal: Boolean = false,
        // Null per-side scale preserves existing saved shared scale on upgrade.
        val leftScale: Float? = null,
        val rightScale: Float? = null,
        // Relative signed offsets: positive X is right, positive Y is down.
        val leftOffsetX: Float = 0f,
        val leftOffsetY: Float = 0f,
        val rightOffsetX: Float = 0f,
        val rightOffsetY: Float = 0f,
        val startButton: ButtonGeometry = ButtonGeometry(),
        val selectButton: ButtonGeometry = ButtonGeometry(),
        val menuButton: ButtonGeometry = ButtonGeometry(),
        val aButton: ButtonGeometry = ButtonGeometry(),
        val bButton: ButtonGeometry = ButtonGeometry(),
    )

    private fun computeInsetsPaddings(
        density: Density,
        insets: WindowInsets,
    ): PaddingValues {
        val result =
            PaddingValues(
                insets.getLeft(density, layoutDirection = LayoutDirection.Ltr).pxToDp(density),
                insets.getTop(density).pxToDp(density),
                insets.getRight(density, layoutDirection = LayoutDirection.Ltr).pxToDp(density),
                insets.getBottom(density).pxToDp(density),
            )
        return result
    }

    private val cachedSettings = mutableMapOf<String, MutableStateFlow<Settings?>>()

    fun observeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        density: Density,
        insets: WindowInsets,
        portraitWidthDp: Float? = null,
        landscapeHeightDp: Float? = null,
    ): Flow<Settings> {
        val paddings = computeInsetsPaddings(density, insets)
        val horizontalPadding =
            max(
                paddings.calculateLeftPadding(LayoutDirection.Ltr),
                paddings.calculateRightPadding(LayoutDirection.Ltr),
            )
        val verticalPadding = paddings.calculateBottomPadding()
        // This width is used ONLY to construct defaults. The saved settings
        // flow remains authoritative, including legacy shared scale values.
        val safePortraitWidthDp =
            portraitWidthDp?.minus(horizontalPadding.value * 2f)
        // A narrow phone benefits from small side gutters. These affect only
        // generated defaults, never any saved controller configuration.
        val portraitSideGutterDp =
            if (
                orientation == Orientation.PORTRAIT &&
                safePortraitWidthDp != null &&
                safePortraitWidthDp <= PORTRAIT_COMPACT_WIDTH_DP
            ) {
                PORTRAIT_SIDE_GUTTER_DP
            } else {
                0f
            }
        val portraitScale =
            safePortraitWidthDp?.minus(portraitSideGutterDp * 2f)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { availableWidthDp ->
                    val dialWidthDp = (availableWidthDp - PORTRAIT_HORIZONTAL_RESERVE_DP) / 2f
                    val rawScale =
                        (dialWidthDp / BASE_DIAL_SIZE_DP - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)
                    rawScale.coerceIn(0f, DEFAULT_PORTRAIT_SCALE)
                } ?: DEFAULT_PORTRAIT_SCALE
        // A short landscape display cannot fit the original large
        // radial clusters plus secondary buttons. Scale only the generated
        // default; a stored layout is never silently reduced or replaced.
        val safeLandscapeHeightDp =
            landscapeHeightDp?.minus(paddings.calculateTopPadding().value + verticalPadding.value)
        val landscapeScale =
            safeLandscapeHeightDp
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { availableHeightDp ->
                    val dialHeightDp = availableHeightDp - LANDSCAPE_VERTICAL_RESERVE_DP
                    val rawScale =
                        (dialHeightDp / BASE_DIAL_SIZE_DP - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)
                    rawScale.coerceIn(0f, DEFAULT_LANDSCAPE_SCALE)
                } ?: DEFAULT_LANDSCAPE_SCALE
        val defaultSettings =
            Settings(
                scale =
                    if (orientation == Orientation.PORTRAIT) {
                        portraitScale
                    } else {
                        landscapeScale
                    },
                rotation = DEFAULT_ROTATION,
                marginX = (horizontalPadding.value + portraitSideGutterDp) / MAX_MARGINS,
                marginY = verticalPadding.value / MAX_MARGINS,
            )
        val settingsKey = getPreferenceString(touchControllerID, orientation)
        val cachedStateFlow =
            cachedSettings.getOrPut(settingsKey) {
                val currentSettings =
                    sharedPreferences.getString(settingsKey, null)?.let { raw ->
                        runCatching { Json.decodeFromString(Settings.serializer(), raw) }
                            .onFailure { error ->
                                Timber.w(error, "Ignoring invalid saved NES touch layout")
                            }
                            .getOrNull()
                    }

                MutableStateFlow(currentSettings)
            }
        return cachedStateFlow.map { it ?: defaultSettings }
    }

    /**
     * Applies changes to the visible controls immediately, without performing
     * a disk write for every pointer movement. Call storeSettings when a
     * gesture finishes to persist the final position.
     */
    fun previewSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings,
    ) {
        updateCachedSettings(touchControllerID, orientation, settings)
    }

    suspend fun storeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings,
    ) {
        Timber.d("Updating touch settings for $touchControllerID at $orientation to $settings")
        updateCachedSettings(touchControllerID, orientation, settings)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(
                    getPreferenceString(touchControllerID, orientation),
                    Json.encodeToString(Settings.serializer(), settings),
                )
            }
        }
    }

    private fun updateCachedSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings?,
    ) {
        val cacheKey = getPreferenceString(touchControllerID, orientation)
        val cacheFlow = cachedSettings.getOrPut(cacheKey) { MutableStateFlow(settings) }
        cacheFlow.value = settings
    }

    suspend fun resetSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
    ) {
        updateCachedSettings(touchControllerID, orientation, null)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                remove(getPreferenceString(touchControllerID, orientation))
            }
        }
    }

    companion object {
        const val DEFAULT_SCALE = 0.5f
        const val DEFAULT_ROTATION = 0.0f
        const val DEFAULT_MARGIN_X = 0.0f
        const val DEFAULT_MARGIN_Y = 0.0f
        const val DEFAULT_OPACITY = 1.0f
        const val DEFAULT_PORTRAIT_SCALE = 0.57f
        const val DEFAULT_LANDSCAPE_SCALE = 0.42f

        // A pair of 160dp PadKit primary dials needs breathing room on
        // compact screens. This controls only generated portrait defaults.
        const val BASE_DIAL_SIZE_DP = 160f
        const val PORTRAIT_HORIZONTAL_RESERVE_DP = 32f
        const val PORTRAIT_COMPACT_WIDTH_DP = 360f
        const val PORTRAIT_SIDE_GUTTER_DP = 8f
        const val LANDSCAPE_VERTICAL_RESERVE_DP = 64f
        const val LANDSCAPE_MIN_GAME_WIDTH_DP = 240f
        const val LANDSCAPE_HORIZONTAL_RESERVE_DP = 48f

        const val MAX_ROTATION = 45f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.5f

        const val MAX_MARGINS = 96f
        const val MAX_CLUSTER_OFFSET_X = 56f
        const val MAX_CLUSTER_OFFSET_Y = 72f
        const val MAX_BUTTON_OFFSET_X = 28f
        const val MAX_BUTTON_OFFSET_Y = 32f
        const val MIN_BUTTON_SIZE = 0.75f
        const val MAX_BUTTON_SIZE = 1.25f
    }

    private fun getPreferenceString(
        controllerID: TouchControllerID,
        orientation: Orientation,
    ): String {
        return "touch_controller_settings_${controllerID}_${orientation.ordinal}"
    }
}
