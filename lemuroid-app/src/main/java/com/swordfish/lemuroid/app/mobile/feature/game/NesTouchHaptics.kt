package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode

/**
 * Which vibration a touch control plays. Mirrors PadKit's own rules so the
 * on-screen controls PadKit does not own (rewind, fast-forward) feel exactly
 * like the D-pad and buttons, and follow the same Touch feedback setting.
 */
internal object NesHapticPolicy {
    enum class Effect {
        /** A firm click, as PadKit plays on press. */
        PRESS,

        /** A light tick, as PadKit plays on release. */
        RELEASE,
    }

    fun onPress(mode: HapticFeedbackMode): Effect? = if (mode == HapticFeedbackMode.NONE) null else Effect.PRESS

    fun onRelease(mode: HapticFeedbackMode): Effect? =
        if (mode == HapticFeedbackMode.PRESS_RELEASE) Effect.RELEASE else null
}

/** Plays [NesHapticPolicy] effects with the same vibration PadKit uses. */
internal class NesTouchHaptics(
    context: Context,
    private val mode: HapticFeedbackMode,
) {
    private val vibrator: Vibrator? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
        }.getOrNull()

    fun press() = play(NesHapticPolicy.onPress(mode))

    fun release() = play(NesHapticPolicy.onRelease(mode))

    private fun play(effect: NesHapticPolicy.Effect?) {
        if (effect == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        val vibration =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    when (effect) {
                        NesHapticPolicy.Effect.PRESS -> VibrationEffect.EFFECT_CLICK
                        NesHapticPolicy.Effect.RELEASE -> VibrationEffect.EFFECT_TICK
                    },
                )
            } else {
                VibrationEffect.createOneShot(
                    when (effect) {
                        NesHapticPolicy.Effect.PRESS -> 30L
                        NesHapticPolicy.Effect.RELEASE -> 15L
                    },
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
            }
        runCatching { target.vibrate(vibration) }
    }
}

@Composable
internal fun rememberNesTouchHaptics(mode: HapticFeedbackMode): NesTouchHaptics {
    val context = LocalContext.current.applicationContext
    return remember(context, mode) { NesTouchHaptics(context, mode) }
}
