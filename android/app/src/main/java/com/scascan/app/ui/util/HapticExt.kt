package com.scascan.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Subtle tick — use for toggles, date-nav arrows, tab switches. */
fun View.hapticTick() =
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

/** Standard press — use for buttons, card taps. */
fun View.hapticClick() =
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

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
