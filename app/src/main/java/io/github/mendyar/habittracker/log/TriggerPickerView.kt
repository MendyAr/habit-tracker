package io.github.mendyar.habittracker.log

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp

/**
 * The suggestion shown after logging a count habit: "What set it off?", with one
 * chip per trigger, *+ Add* for a new one and *Skip*. It is a card in the middle
 * of the dimmed home screen. Chips wrap onto as many rows as needed, and a long
 * name wraps inside its chip, so no name is ever cut off; with many triggers the
 * chips scroll. A tap outside the card is the same as *Skip*.
 *
 * The entry is already logged: picking only tags it.
 */
class TriggerPickerView(
    context: Context,
    color: Int,
    subtitle: String,
    triggers: List<String>,
    private val onPick: (String) -> Unit,
    private val onAdd: () -> Unit,
    private val onSkip: () -> Unit,
) : FrameLayout(context) {

    private val card = LinearLayout(context)
    private val chips = mutableListOf<TextView>()
    private var done = false

    init {
        setBackgroundColor(Color.argb(SCRIM_ALPHA, 0, 0, 0))
        isClickable = true
        setOnClickListener { finish(onSkip) }

        card.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = context.dp(24).toFloat()
                setColor(context.colorOf(R.color.surface))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = context.dp(8).toFloat()
            setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(8))
            isClickable = true // taps on the card don't dismiss it
            contentDescription = context.getString(R.string.picker_title)
        }
        card.addView(
            TextView(context).apply {
                setText(R.string.picker_title)
                setTextColor(context.colorOf(R.color.text_primary))
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            },
        )
        card.addView(
            TextView(context).apply {
                text = subtitle
                setTextColor(context.colorOf(R.color.text_secondary))
                textSize = 14f
                setPadding(0, context.dp(2), 0, context.dp(12))
            },
        )

        val flow = FlowLayout(context, context.dp(8))
        triggers.forEach { name -> flow.addView(chip(name, color, filled = false) { pick(name, it) }) }
        flow.addView(chip(context.getString(R.string.picker_add), color, filled = false, dashed = true) { finish(onAdd) })
        card.addView(
            MaxHeightScrollView(context).apply {
                maxHeightFraction = 0.5f
                isVerticalScrollBarEnabled = true
                addView(flow)
            },
        )

        card.addView(
            TextView(context).apply {
                setText(R.string.picker_skip)
                setTextColor(context.colorOf(R.color.brand))
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minHeight = context.dp(48)
                minWidth = context.dp(64)
                setPadding(context.dp(12), 0, context.dp(12), 0)
                background = selectable(context)
                setOnClickListener { finish(onSkip) }
            },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = context.dp(8)
            },
        )

        val margin = context.dp(16)
        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(margin, margin, margin, margin)
            },
        )
    }

    /** The names of the chips offered, in order (for tests and accessibility). */
    val offered: List<String> get() = chips.map { it.text.toString() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A comfortable card on tablets and in landscape too.
        val maxCard = context.dp(MAX_CARD_DP)
        (card.layoutParams as LayoutParams).width =
            if (MeasureSpec.getSize(widthMeasureSpec) - context.dp(32) > maxCard) maxCard else LayoutParams.MATCH_PARENT
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Fades and scales the card in (or just shows it when animations are off). */
    fun appear(animate: Boolean) {
        if (!animate) return
        alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = APPEAR_MS
            addUpdateListener {
                val t = it.animatedValue as Float
                alpha = t
                card.scaleX = 0.92f + 0.08f * t
                card.scaleY = 0.92f + 0.08f * t
            }
            start()
        }
    }

    private fun pick(name: String, chip: TextView) {
        if (done) return
        // Shows the choice for a moment, filled, before closing.
        chip.isSelected = true
        chip.setTextColor(Color.WHITE)
        (chip.background as GradientDrawable).setColor(chip.tag as Int)
        finish { onPick(name) }
    }

    private fun finish(action: () -> Unit) {
        if (done) return
        done = true
        action()
    }

    private fun chip(text: String, color: Int, filled: Boolean, dashed: Boolean = false, onClick: (TextView) -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            tag = color
            textSize = 15f
            setTextColor(context.colorOf(R.color.text_primary))
            gravity = Gravity.CENTER
            minHeight = context.dp(44)
            minWidth = context.dp(64)
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
            background = GradientDrawable().apply {
                cornerRadius = context.dp(22).toFloat()
                setColor(if (filled) color else Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)))
                if (dashed) {
                    setStroke(context.dp(1), color, context.dp(4).toFloat(), context.dp(3).toFloat())
                } else {
                    setStroke(context.dp(1), Color.argb(110, Color.red(color), Color.green(color), Color.blue(color)))
                }
            }
            isClickable = true
            setOnClickListener { onClick(this) }
            if (!dashed) chips += this
        }

    private fun selectable(context: Context): android.graphics.drawable.Drawable? {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return context.getDrawable(value.resourceId)
    }

    /** Lays its children out left to right, wrapping onto new rows; each row is centred. */
    private class FlowLayout(context: Context, private val gap: Int) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            var x = 0
            var y = 0
            var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                // A chip is never wider than the row: a long name wraps inside it instead.
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
                if (x > 0 && x + child.measuredWidth > width) {
                    x = 0
                    y += rowHeight + gap
                    rowHeight = 0
                }
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            setMeasuredDimension(width, y + rowHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            val rows = mutableListOf<MutableList<View>>(mutableListOf())
            var x = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (x > 0 && x + child.measuredWidth > width) {
                    rows += mutableListOf<View>()
                    x = 0
                }
                rows.last() += child
                x += child.measuredWidth + gap
            }
            var y = 0
            for (row in rows) {
                val rowWidth = row.sumOf { it.measuredWidth } + gap * (row.size - 1)
                val rowHeight = row.maxOfOrNull { it.measuredHeight } ?: 0
                var left = maxOf(0, (width - rowWidth) / 2)
                for (child in row) {
                    val top = y + (rowHeight - child.measuredHeight) / 2
                    child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
                    left += child.measuredWidth + gap
                }
                y += rowHeight + gap
            }
        }
    }

    /** A ScrollView no taller than [maxHeightFraction] of the screen. */
    private class MaxHeightScrollView(context: Context) : ScrollView(context) {
        var maxHeightFraction = 0.5f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val max = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
        }
    }

    private companion object {
        const val SCRIM_ALPHA = 90
        const val MAX_CARD_DP = 420
        const val APPEAR_MS = 200L
    }
}
