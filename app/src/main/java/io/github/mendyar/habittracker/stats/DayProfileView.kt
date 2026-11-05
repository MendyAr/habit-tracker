package io.github.mendyar.habittracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.DayProfile
import io.github.mendyar.habittracker.ui.colorOf

/**
 * A smooth area chart of a [DayProfile]: the time of day across, the share of
 * days with an entry around that time up. With [setBars] it shows separate bars
 * instead (weekdays, days of the month, months). Touching or dragging across it
 * selects a point and reports it through [onSelect].
 */
class DayProfileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called with the index of the time the user touched. */
    var onSelect: ((Int) -> Unit)? = null

    /** Formats an axis label for a whole hour (0 to 24). */
    var hourLabel: (Int) -> String = { "$it" }
        set(value) {
            field = value
            hourLabels = HOURS.map(value)
        }

    /** Formats an axis value between 0 and 1. */
    var percentLabel: (Double) -> String = { "${(it * 100).toInt()}%" }

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.colorOf(R.color.surface) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.divider)
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = sp(11f)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emphasisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var emphasized: Set<Int> = emptySet()
    private val rect = android.graphics.RectF()
    private val line = Path()
    private val area = Path()

    private var values = DoubleArray(0)
    private var selected = -1
    private var axisMax = 1.0
    private var hourLabels = HOURS.map(hourLabel)
    private var axisLabels = listOf("", "", "")

    /** Labels under each bar in bar mode (blank ones are skipped); null for the curve. */
    private var barLabels: List<String>? = null

    /** How many points the curve is rotated by, so the axis starts at the chosen hour. */
    private var shift = 0

    /**
     * Shows [profile] in [color] with [selected] highlighted (-1 for none), the axis
     * running from [startHour] to the same hour the next day.
     */
    fun setData(profile: DayProfile, selected: Int, color: Int, startHour: Int = 0) {
        barLabels = null
        emphasized = emptySet()
        val n = profile.values.size
        shift = startHour * 60 / profile.stepMinutes % n
        hourLabels = HOURS.map { hourLabel((startHour + it) % 24) }
        val rotated = DoubleArray(n) { profile.values[(it + shift) % n] }
        show(rotated, profile.peak, if (selected < 0) -1 else (selected - shift + n) % n, color)
    }

    /**
     * Shows one bar per value (shares 0 to 1) with [labels] under them; bars in
     * [emphasized] (e.g. the busiest hours) are darker than the others.
     */
    fun setBars(values: DoubleArray, labels: List<String>, selected: Int, color: Int, emphasized: Set<Int> = emptySet()) {
        barLabels = labels
        shift = 0
        this.emphasized = emphasized
        show(values, values.maxOrNull() ?: 0.0, selected, color)
        emphasisPaint.color = color
        emphasisPaint.alpha = 220
        if (emphasized.isNotEmpty()) barPaint.alpha = 80
    }

    private fun show(values: DoubleArray, peak: Double, selected: Int, color: Int) {
        this.values = values
        this.selected = selected
        axisMax = AXIS_STEPS.firstOrNull { it >= peak } ?: 1.0
        axisLabels = (0..2).map { percentLabel(axisMax * it / 2) }
        linePaint.color = color
        markerPaint.color = color
        fillPaint.color = color
        fillPaint.alpha = 56
        barPaint.color = color
        barPaint.alpha = 110
        invalidate()
    }

    fun setSelected(index: Int) {
        if (index == selected) return
        selected = index
        invalidate()
    }

    private val isEmpty get() = values.isEmpty() || values.all { it <= 0.0 }

    private fun axisWidth() = labelPaint.measureText(axisLabels.maxByOrNull { it.length }.orEmpty()) + 8 * density

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft + axisWidth()
        val right = width - paddingRight - 4 * density
        val top = paddingTop + 6 * density
        val labelHeight = labelPaint.textSize + 8 * density
        val bottom = height - paddingBottom - labelHeight
        val plotWidth = right - left
        val plotHeight = bottom - top

        for (step in 0..2) {
            val y = bottom - plotHeight * step / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(axisLabels[step], paddingLeft.toFloat(), y + labelPaint.textSize / 3f, labelPaint)
        }

        val baseline = height - paddingBottom - 2 * density
        val bars = barLabels
        if (bars != null) {
            drawBars(canvas, bars, left, plotWidth, top, bottom, baseline)
            return
        }

        // Hours along the bottom, every six hours.
        HOURS.forEachIndexed { i, hour ->
            val x = left + plotWidth * hour / 24f
            labelPaint.textAlign = when (hour) {
                0 -> Paint.Align.LEFT
                24 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(hourLabels[i], x, baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT

        if (isEmpty) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), (left + right) / 2f, top + plotHeight / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }

        // The curve wraps around midnight, so 24:00 repeats 00:00.
        fun x(i: Int) = left + plotWidth * i / values.size
        fun y(i: Int) = bottom - (plotHeight * values[i % values.size] / axisMax).toFloat()
        line.reset()
        line.moveTo(x(0), y(0))
        for (i in 1..values.size) line.lineTo(x(i), y(i))
        area.set(line)
        area.lineTo(x(values.size), bottom)
        area.lineTo(x(0), bottom)
        area.close()
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)

        if (selected in values.indices) {
            val sx = x(selected)
            val sy = y(selected)
            canvas.drawLine(sx, top, sx, bottom, markerPaint)
            canvas.drawCircle(sx, sy, 5 * density, dotRingPaint)
            canvas.drawCircle(sx, sy, 3.5f * density, markerPaint)
        }
    }

    private fun drawBars(canvas: Canvas, labels: List<String>, left: Float, plotWidth: Float, top: Float, bottom: Float, baseline: Float) {
        val slot = plotWidth / values.size
        labelPaint.textAlign = Paint.Align.CENTER
        labels.forEachIndexed { i, label ->
            if (label.isNotEmpty()) canvas.drawText(label, left + slot * (i + 0.5f), baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
        if (isEmpty) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), left + plotWidth / 2f, (top + bottom) / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }
        val gap = if (slot > 6 * density) slot * 0.25f else density * 0.5f
        val corner = minOf((slot - gap) / 2f, 4 * density)
        values.forEachIndexed { i, value ->
            val h = (bottom - top) * (value / axisMax).toFloat()
            if (h <= 0f && i != selected) return@forEachIndexed
            rect.set(left + slot * i + gap / 2f, bottom - maxOf(h, density * 2), left + slot * (i + 1) - gap / 2f, bottom)
            val paint = when {
                i == selected -> markerPaint
                i in emphasized -> emphasisPaint
                else -> barPaint
            }
            canvas.drawRoundRect(rect, corner, corner, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Keep the surrounding ScrollView from stealing horizontal scrubbing.
                parent?.requestDisallowInterceptTouchEvent(true)
                val left = paddingLeft + axisWidth()
                val right = width - paddingRight - 4 * density
                val fraction = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
                val index = (fraction * values.size).toInt().coerceIn(0, values.size - 1)
                if (index != selected) {
                    selected = index
                    invalidate()
                    onSelect?.invoke((index + shift) % values.size)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        /** Axis tops: rare habits get a taller curve instead of a flat line. */
        val AXIS_STEPS = doubleArrayOf(0.1, 0.2, 0.5, 1.0)

        /** Hours labelled along the bottom. */
        val HOURS = listOf(0, 6, 12, 18, 24)
    }
}
