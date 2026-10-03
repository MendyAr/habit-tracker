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
import io.github.mendyar.habittracker.core.Bucket
import io.github.mendyar.habittracker.core.StatsEngine
import io.github.mendyar.habittracker.ui.colorOf

/**
 * A minimal bar chart of per-period counts. Touching or dragging across the
 * chart selects a bar and reports it through [onSelect].
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called with the index of the bar the user touched. */
    var onSelect: ((Int) -> Unit)? = null

    /** Formats the axis label of a bucket start. */
    var axisLabel: (Long) -> String = { "" }

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.divider)
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = sp(11f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_primary)
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private var buckets: List<Bucket> = emptyList()
    private var selected = -1
    private var axisMax = 1

    fun setData(buckets: List<Bucket>, selected: Int, color: Int) {
        this.buckets = buckets
        this.selected = selected
        barPaint.color = color
        barPaint.alpha = 110
        selectedPaint.color = color
        axisMax = StatsEngine.niceCeiling(buckets.maxOfOrNull { it.count } ?: 0)
        contentDescription = if (buckets.isEmpty()) {
            context.getString(R.string.chart_empty)
        } else {
            context.getString(R.string.chart_description, buckets.size, buckets.maxOf { it.count })
        }
        invalidate()
    }

    fun setSelected(index: Int) {
        if (index == selected) return
        selected = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val axisWidth = labelPaint.measureText(axisMax.toString()) + 8 * density
        val left = paddingLeft + axisWidth
        val right = (width - paddingRight).toFloat()
        val top = paddingTop + valuePaint.textSize + 6 * density
        val labelHeight = labelPaint.textSize + 8 * density
        val bottom = height - paddingBottom - labelHeight
        val plotHeight = bottom - top

        // Grid at 0, half and max.
        for (step in 0..2) {
            val value = axisMax * step / 2
            if (step == 1 && axisMax < 2) continue
            val y = bottom - plotHeight * step / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(value.toString(), paddingLeft.toFloat(), y + labelPaint.textSize / 3f, labelPaint)
        }

        if (buckets.isEmpty()) {
            valuePaint.color = labelPaint.color
            canvas.drawText(context.getString(R.string.chart_empty), (left + right) / 2f, top + plotHeight / 2f, valuePaint)
            valuePaint.color = context.colorOf(R.color.text_primary)
            return
        }

        val slot = (right - left) / buckets.size
        val gap = if (slot > 6 * density) slot * 0.22f else if (slot > 2 * density) density * 0.5f else 0f
        val corner = minOf((slot - gap) / 2f, 4 * density)
        buckets.forEachIndexed { i, bucket ->
            if (bucket.count == 0 && i != selected) return@forEachIndexed
            val x = left + i * slot
            val h = maxOf(plotHeight * bucket.count / axisMax, if (bucket.count > 0) density * 2 else 0f)
            rect.set(x + gap / 2f, bottom - h, x + slot - gap / 2f, bottom)
            canvas.drawRoundRect(rect, corner, corner, if (i == selected) selectedPaint else barPaint)
        }

        if (selected in buckets.indices) {
            val bucket = buckets[selected]
            val cx = (left + (selected + 0.5f) * slot).coerceIn(left + 12 * density, right - 12 * density)
            val h = plotHeight * bucket.count / axisMax
            canvas.drawText(bucket.count.toString(), cx, bottom - h - 6 * density, valuePaint)
            if (bucket.count == 0) canvas.drawLine(cx - slot / 2f, bottom, cx + slot / 2f, bottom, selectedPaint.apply { strokeWidth = 2 * density })
        }

        // First and last bucket on the axis.
        val baseline = height - paddingBottom - 2 * density
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(axisLabel(buckets.first().start), left, baseline, labelPaint)
        if (buckets.size > 1) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(axisLabel(buckets.last().start), right, baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (buckets.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Keep the surrounding ScrollView from stealing horizontal scrubbing.
                parent?.requestDisallowInterceptTouchEvent(true)
                val axisWidth = labelPaint.measureText(axisMax.toString()) + 8 * density
                val left = paddingLeft + axisWidth
                val slot = (width - paddingRight - left) / buckets.size
                val index = ((event.x - left) / slot).toInt().coerceIn(0, buckets.size - 1)
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

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
