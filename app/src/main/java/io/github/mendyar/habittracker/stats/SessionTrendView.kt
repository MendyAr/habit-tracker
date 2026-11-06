package io.github.mendyar.habittracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.RobustStats
import io.github.mendyar.habittracker.ui.colorOf
import kotlin.math.ceil

/**
 * A duration habit's sessions in the order they happened: a dot per session at
 * its length, a line through the median of the last 7 sessions (the trend, which
 * one slow session can't bend), and a dashed line at the target. Touching it
 * selects a session and reports it through [onSelect].
 */
class SessionTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called with the index of the session the user touched. */
    var onSelect: ((Int) -> Unit)? = null

    /** Formats a session start for the axis, e.g. "Nov 3". */
    var dateLabel: (Long) -> String = { "" }

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.colorOf(R.color.text_secondary)
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(5 * density, 4 * density), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.divider)
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val path = Path()

    private var starts = LongArray(0)
    private var minutes = DoubleArray(0)
    private var trend = DoubleArray(0)
    private var target = Double.NaN
    private var axisMax = 1
    private var selected = -1

    /** The median of the last 7 sessions at each session. */
    val rollingMedian: DoubleArray get() = trend

    /** Sessions starting at [starts] (oldest first) that lasted [minutes]; [target] in minutes, NaN for none. */
    fun setData(starts: LongArray, minutes: DoubleArray, target: Double, color: Int) {
        this.starts = starts
        this.minutes = minutes
        this.target = target
        trend = RobustStats.trailingMedian(minutes, 7)
        selected = -1
        dotPaint.color = color
        dotPaint.alpha = 120
        selectedPaint.color = color
        trendPaint.color = color
        val top = maxOf(minutes.maxOrNull() ?: 0.0, if (target.isNaN()) 0.0 else target)
        axisMax = niceMinutes(top)
        invalidate()
    }

    private fun axisWidth() = labelPaint.measureText(axisMax.toString()) + 8 * density

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft + axisWidth()
        val right = width - paddingRight - 6 * density
        val top = paddingTop + 6 * density
        val bottom = height - paddingBottom - labelPaint.textSize - 8 * density
        val plot = bottom - top

        for (step in 0..2) {
            val y = bottom - plot * step / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText((axisMax * step / 2).toString(), paddingLeft.toFloat(), y + labelPaint.textSize / 3f, labelPaint)
        }
        if (minutes.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), (left + right) / 2f, top + plot / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }

        fun x(i: Int) = if (minutes.size == 1) (left + right) / 2f else left + (right - left) * i / (minutes.size - 1)
        fun y(value: Double) = bottom - (plot * value / axisMax).toFloat()

        if (!target.isNaN()) {
            path.reset()
            path.moveTo(left, y(target))
            path.lineTo(right, y(target))
            canvas.drawPath(path, targetPaint)
        }
        path.reset()
        trend.forEachIndexed { i, value -> if (i == 0) path.moveTo(x(i), y(value)) else path.lineTo(x(i), y(value)) }
        canvas.drawPath(path, trendPaint)
        val radius = if (minutes.size > 60) 2.5f * density else 4 * density
        minutes.forEachIndexed { i, value -> canvas.drawCircle(x(i), y(value), radius, if (i == selected) selectedPaint else dotPaint) }
        if (selected in minutes.indices) canvas.drawCircle(x(selected), y(minutes[selected]), radius * 1.6f, selectedPaint)

        val baseline = height - paddingBottom - 2 * density
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateLabel(starts.first()), left, baseline, labelPaint)
        if (starts.size > 1) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(dateLabel(starts.last()), right, baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (minutes.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val left = paddingLeft + axisWidth()
                val right = width - paddingRight - 6 * density
                val fraction = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
                val index = Math.round(fraction * (minutes.size - 1)).coerceIn(0, minutes.size - 1)
                if (index != selected) {
                    selected = index
                    invalidate()
                    onSelect?.invoke(index)
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

    private companion object {
        /** A round axis top in minutes: 2, 4, 6, 10, 15, 20, 30, 45, 60, then whole hours. */
        fun niceMinutes(value: Double): Int {
            val steps = intArrayOf(2, 4, 6, 10, 15, 20, 30, 45, 60)
            return steps.firstOrNull { it >= value } ?: (ceil(value / 60.0).toInt() * 60)
        }
    }
}
