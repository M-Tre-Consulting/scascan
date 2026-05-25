package com.scascan.app.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Subtle tick — use for toggles, date-nav arrows, tab switches. */
fun View.hapticTick() {
    val success = performHapticFeedback(
        HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
    if (!success) {
        // Fallback to direct vibration if view-based haptics fail
        vibrateFallback(context, 15)
    }
}

/** Standard press — use for buttons, card taps. */
fun View.hapticClick() {
    val success = performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
    )
    if (!success) {
        vibrateFallback(context, 30)
    }
}

private fun vibrateFallback(context: Context, duration: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Last resort for older or specific hardware behaviors
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    } catch (e: Exception) {
        android.util.Log.e("HapticExt", "vibrateFallback failed: ${e.message}")
    }
}

/** Positive confirmation — use for "Added to log", "Saved". */
fun View.hapticConfirm() =
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM
        else
            HapticFeedbackConstants.VIRTUAL_KEY
    )

/** Negative feedback — use for delete / remove. */
fun View.hapticReject() =
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.REJECT
        else
            HapticFeedbackConstants.LONG_PRESS
    )
