package io.github.mendyar.habittracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.RobustStats
import io.github.mendyar.habittracker.ui.colorOf

/**
 * Every session of a duration habit as a bar, sorted from shortest to longest,
 * so the spread is visible at a glance. A line marks the median session.
 */
class DurationsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Formats a length in milliseconds for the axis, e.g. "4:30". */
    var lengthLabel: (Long) -> String = { "${it / 1000}s" }

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medianPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.divider)
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val rect = RectF()
    private var sorted = LongArray(0)
    private var median = 0.0
    private var axisMax = 1L
    private var axisLabels = listOf("", "")

    fun setData(durations: LongArray, color: Int) {
        sorted = durations.sortedArray()
        median = RobustStats.median(DoubleArray(sorted.size) { sorted[it].toDouble() })
        axisMax = maxOf(sorted.lastOrNull() ?: 0L, 1L)
        axisLabels = listOf(lengthLabel(0), lengthLabel(axisMax))
        barPaint.color = color
        barPaint.alpha = 140
        medianPaint.color = color
        contentDescription = if (sorted.isEmpty()) {
            context.getString(R.string.chart_empty)
        } else {
            context.getString(R.string.durations_description, sorted.size, lengthLabel(median.toLong()), lengthLabel(axisMax))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val axisWidth = labelPaint.measureText(axisLabels.maxByOrNull { it.length }.orEmpty()) + 8 * density
        val left = paddingLeft + axisWidth
        val right = width - paddingRight.toFloat()
        val top = paddingTop + 6 * density
        val bottom = height - paddingBottom - 6 * density
        val plotHeight = bottom - top
        canvas.drawLine(left, bottom, right, bottom, gridPaint)
        canvas.drawLine(left, top, right, top, gridPaint)
        canvas.drawText(axisLabels[0], paddingLeft.toFloat(), bottom + labelPaint.textSize / 3f, labelPaint)
        canvas.drawText(axisLabels[1], paddingLeft.toFloat(), top + labelPaint.textSize / 3f, labelPaint)
        if (sorted.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), (left + right) / 2f, (top + bottom) / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }
        val slot = (right - left) / sorted.size
        val gap = if (slot > 6 * density) slot * 0.2f else 0f
        sorted.forEachIndexed { i, value ->
            val h = maxOf(plotHeight * value / axisMax, 2 * density)
            rect.set(left + slot * i + gap / 2f, bottom - h, left + slot * (i + 1) - gap / 2f, bottom)
            canvas.drawRect(rect, barPaint)
        }
        val my = bottom - (plotHeight * median / axisMax).toFloat()
        canvas.drawLine(left, my, right, my, medianPaint)
    }
}
