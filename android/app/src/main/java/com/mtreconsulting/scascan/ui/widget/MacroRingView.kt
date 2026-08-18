package com.mtreconsulting.scascan.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors

/**
 * A 3-segment ring chart showing protein/carbs/fat as a share of calories (not grams —
 * protein and carbs are 4 kcal/g, fat is 9 kcal/g, so a gram-based ring would misrepresent
 * fat's actual weight in the total). Colors match the app's existing macro color language
 * (protein=colorPrimary, carbs=colorSecondary, fat=colorTertiary — see the progress bars in
 * fragment_log.xml, this view doesn't invent a new mapping).
 */
class MacroRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokeWidthPx = 20f * resources.displayMetrics.density
    private val gapDegrees = 4f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private var proteinColor = 0
    private var carbsColor = 0
    private var fatColor = 0
    private var trackColor = 0

    private var proteinSweep = 0f
    private var carbsSweep = 0f
    private var fatSweep = 0f

    private val arcRect = RectF()
    private var sweepAnimator: ValueAnimator? = null

    override fun onDetachedFromWindow() {
        sweepAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        proteinColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        carbsColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary)
        fatColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary)
        trackColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
        trackPaint.color = trackColor
        invalidate()
    }

    /**
     * Grams in, converted to a share of total calories (protein/carbs 4 kcal/g, fat 9 kcal/g).
     * Sweeps animate in from zero together rather than snapping to their final angles, so the
     * ring reads as the screen's signature moment instead of a static chart.
     */
    fun setMacros(proteinG: Double, carbsG: Double, fatG: Double) {
        val proteinKcal = proteinG * 4.0
        val carbsKcal = carbsG * 4.0
        val fatKcal = fatG * 9.0
        val total = proteinKcal + carbsKcal + fatKcal

        val targetProtein: Float
        val targetCarbs: Float
        val targetFat: Float
        if (total <= 0.0) {
            targetProtein = 0f
            targetCarbs = 0f
            targetFat = 0f
        } else {
            val available = 360f - gapDegrees * 3f
            targetProtein = (proteinKcal / total * available).toFloat()
            targetCarbs = (carbsKcal / total * available).toFloat()
            targetFat = (fatKcal / total * available).toFloat()
        }

        sweepAnimator?.cancel()
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val f = it.animatedValue as Float
                proteinSweep = targetProtein * f
                carbsSweep = targetCarbs * f
                fatSweep = targetFat * f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx / 2f + paddingLeft
        arcRect.set(inset, inset, width - inset, height - inset)

        if (proteinSweep + carbsSweep + fatSweep <= 0f) {
            canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
            return
        }

        var start = -90f
        paint.color = proteinColor
        canvas.drawArc(arcRect, start, proteinSweep, false, paint)
        start += proteinSweep + gapDegrees

        paint.color = carbsColor
        canvas.drawArc(arcRect, start, carbsSweep, false, paint)
        start += carbsSweep + gapDegrees

        paint.color = fatColor
        canvas.drawArc(arcRect, start, fatSweep, false, paint)
    }
}
