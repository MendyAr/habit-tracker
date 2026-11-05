package io.github.mendyar.habittracker.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.ui.colorOf

/**
 * A chart legend: a sample of each mark (a bar, a dot, a solid or a dashed line)
 * followed by its name, wrapping onto more lines when the width runs out.
 */
class LegendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mark { BAR, DOT, LINE, DASHED }

    class Item(val mark: Mark, val color: Int, val label: String)

    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
    private val rect = RectF()
    private val path = android.graphics.Path()
    private var items: List<Item> = emptyList()

    fun setItems(items: List<Item>) {
        this.items = items
        contentDescription = items.joinToString(", ") { it.label }
        requestLayout()
        invalidate()
    }

    private val swatch get() = 16 * density
    private val gap get() = 6 * density
    private val spacing get() = 14 * density
    private val lineHeight get() = textPaint.textSize + 8 * density

    /** Where each item goes, row by row, for a [width]. */
    private fun layoutItems(width: Int): List<Triple<Item, Float, Int>> {
        var x = paddingLeft.toFloat()
        var row = 0
        return items.map { item ->
            val w = swatch + gap + textPaint.measureText(item.label)
            if (x > paddingLeft && x + w > width - paddingRight) {
                x = paddingLeft.toFloat()
                row++
            }
            Triple(item, x, row).also { x += w + spacing }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = (layoutItems(width).maxOfOrNull { it.third } ?: -1) + 1
        setMeasuredDimension(width, (rows * lineHeight + paddingTop + paddingBottom).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        for ((item, x, row) in layoutItems(width)) {
            val cy = paddingTop + row * lineHeight + lineHeight / 2f
            markPaint.color = item.color
            markPaint.pathEffect = null
            when (item.mark) {
                Mark.BAR -> {
                    rect.set(x + 3 * density, cy - 5 * density, x + swatch - 3 * density, cy + 5 * density)
                    canvas.drawRoundRect(rect, 2 * density, 2 * density, markPaint)
                }
                Mark.DOT -> canvas.drawCircle(x + swatch / 2f, cy, 4 * density, markPaint)
                Mark.LINE, Mark.DASHED -> {
                    markPaint.style = Paint.Style.STROKE
                    markPaint.strokeWidth = 2 * density
                    if (item.mark == Mark.DASHED) markPaint.pathEffect = dash
                    path.reset()
                    path.moveTo(x, cy)
                    path.lineTo(x + swatch, cy)
                    canvas.drawPath(path, markPaint)
                    markPaint.style = Paint.Style.FILL
                }
            }
            canvas.drawText(item.label, x + swatch + gap, cy + textPaint.textSize / 3f, textPaint)
        }
    }
}
