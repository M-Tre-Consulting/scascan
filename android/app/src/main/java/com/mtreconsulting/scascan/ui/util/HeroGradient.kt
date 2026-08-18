package com.mtreconsulting.scascan.ui.util

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors

/**
 * Applies a diagonal two-color gradient background resolved from theme attrs at runtime
 * (never literal hex), so it stays correct under Material You dynamic color and dark mode.
 * Meant for a full-bleed content view inside a MaterialCardView — the card clips its content
 * to its own shape, so the card's rounded corners still mask the gradient correctly.
 */
fun View.applyHeroGradient(startAttr: Int, endAttr: Int, cornerRadiusPx: Float, angle: Int = 135) {
    val startColor = MaterialColors.getColor(this, startAttr)
    val endColor = MaterialColors.getColor(this, endAttr)
    val orientation = when (angle) {
        0 -> GradientDrawable.Orientation.LEFT_RIGHT
        45 -> GradientDrawable.Orientation.BL_TR
        90 -> GradientDrawable.Orientation.BOTTOM_TOP
        225 -> GradientDrawable.Orientation.TR_BL
        270 -> GradientDrawable.Orientation.TOP_BOTTOM
        315 -> GradientDrawable.Orientation.BR_TL
        else -> GradientDrawable.Orientation.TL_BR
    }
    background = GradientDrawable(orientation, intArrayOf(startColor, endColor)).apply {
        cornerRadius = cornerRadiusPx
    }
}

/**
 * Fades and slides each direct child in with a short per-child delay, for a staggered
 * entrance instead of everything popping in at once. Plain ViewPropertyAnimator, so it
 * already respects the system's reduced-motion / animator-duration-scale setting.
 */
fun ViewGroup.staggerChildrenIn(staggerMs: Long = 70L, startTranslationDp: Float = 28f) {
    val startTranslationPx = startTranslationDp * resources.displayMetrics.density
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        child.alpha = 0f
        child.translationY = startTranslationPx
        child.scaleX = 0.94f
        child.scaleY = 0.94f
        child.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(i * staggerMs)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }
}

/**
 * Tactile press feedback: scales the view down slightly on touch-down, back up on
 * release/cancel. Layered on top of any existing OnClickListener — doesn't consume the
 * touch event, just observes it, so click handling is unaffected.
 */
fun View.addPressScale(scale: Float = 0.97f) {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
        }
        false
    }
}
