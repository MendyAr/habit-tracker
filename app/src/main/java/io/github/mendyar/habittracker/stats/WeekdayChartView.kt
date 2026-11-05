package io.github.mendyar.habittracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.core.WeekdayCounts
import io.github.mendyar.habittracker.ui.colorOf
import kotlin.math.ceil

/**
 * The median count of each weekday as a bar, with a line from its first to its
 * third quartile. The weekday with the highest median is darker. Touching a
 * weekday selects it and reports it through [onSelect].
 */
class WeekdayChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called with the index of the weekday the user touched. */
    var onSelect: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_primary)
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.divider)
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val selectedLabelPaint = Paint(labelPaint).apply { color = context.colorOf(R.color.text_primary) }
    private val rect = RectF()

    private var data: WeekdayCounts? = null
    private var labels: List<String> = emptyList()
    private var selected = -1
    private var axisMax = 1

    fun setData(data: WeekdayCounts, labels: List<String>, color: Int, selected: Int = -1) {
        this.data = data
        this.labels = labels
        this.selected = selected
        barPaint.color = color
        barPaint.alpha = 90
        peakPaint.color = color
        peakPaint.alpha = 220
        val top = data.q3.filter { !it.isNaN() }.maxOrNull() ?: 0.0
        axisMax = StatsEngine.niceCeiling(ceil(top).toInt())
        invalidate()
    }

    private val isEmpty get() = data?.medians?.all { it.isNaN() || it <= 0.0 } ?: true

    private fun axisWidth() = labelPaint.measureText(axisMax.toString()) + 8 * density

    override fun onDraw(canvas: Canvas) {
        val d = data ?: return
        val left = paddingLeft + axisWidth()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop + 6 * density
        val bottom = height - paddingBottom - labelPaint.textSize - 8 * density
        val plot = bottom - top

        for (step in 0..2) {
            if (step == 1 && axisMax < 2) continue
            val y = bottom - plot * step / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText((axisMax * step / 2).toString(), paddingLeft.toFloat(), y + labelPaint.textSize / 3f, labelPaint)
        }

        val slot = (right - left) / d.medians.size
        val baseline = height - paddingBottom - 2 * density
        labelPaint.textAlign = Paint.Align.CENTER
        selectedLabelPaint.textAlign = Paint.Align.CENTER
        labels.forEachIndexed { i, label ->
            canvas.drawText(label, left + slot * (i + 0.5f), baseline, if (i == selected) selectedLabelPaint else labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
        if (isEmpty) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), (left + right) / 2f, top + plot / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }

        fun y(value: Double) = bottom - (plot * value / axisMax).toFloat()
        val barWidth = minOf(slot * 0.55f, 32 * density)
        val corner = minOf(barWidth / 2f, 4 * density)
        val peak = d.peakIndex
        d.medians.forEachIndexed { i, median ->
            if (median.isNaN()) return@forEachIndexed
            val cx = left + slot * (i + 0.5f)
            rect.set(cx - barWidth / 2f, minOf(y(median), bottom - density * 2), cx + barWidth / 2f, bottom)
            canvas.drawRoundRect(rect, corner, corner, if (i == peak || i == selected) peakPaint else barPaint)
            if (!d.q1[i].isNaN() && d.q3[i] > d.q1[i]) canvas.drawLine(cx, y(d.q1[i]), cx, y(d.q3[i]), rangePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = data ?: return false
        if (isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val left = paddingLeft + axisWidth()
                val slot = (width - paddingRight - left) / d.medians.size
                val index = ((event.x - left) / slot).toInt().coerceIn(0, d.medians.size - 1)
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
}
