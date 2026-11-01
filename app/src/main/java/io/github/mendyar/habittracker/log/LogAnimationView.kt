package io.github.mendyar.habittracker.log

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/**
 * The confirmation shown when a timestamp is logged: the screen dims slightly, a
 * large disc in the habit's colour pops up in the middle with a check that draws
 * itself, two rings ripple outwards with a burst of small dots, and a pill with the
 * habit's name and count floats below it. Everything then fades away and
 * [onFinished] is invoked. With system animations turned off it shows the same
 * confirmation without motion, for the same time.
 *
 * It is always drawn in the same place, whichever icon or widget was tapped. The
 * view is transparent everywhere else, so the home screen stays visible.
 */
class LogAnimationView(context: Context) : View(context) {

    var onFinished: (() -> Unit)? = null

    /** Whether [start] has been called. */
    val isStarted: Boolean get() = started

    private val density = resources.displayMetrics.density

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val scrimPaint = Paint().apply { color = Color.BLACK }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE
        textSize = sp(16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE
        textSize = sp(13f)
    }

    private val checkPath = Path()
    private val partialCheck = Path()
    private val measure = PathMeasure()
    private val pillRect = RectF()
    private val pop = OvershootInterpolator(2.2f)
    private val ease = DecelerateInterpolator(1.6f)

    private var label = ""
    private var hint: String? = null
    private var holdMs = HOLD_MS
    private var elapsed = 0f
    private var animator: ValueAnimator? = null
    private var started = false
    private val finishStill = Runnable { onFinished?.invoke() }
    private val handler = Handler(Looper.getMainLooper())

    /** Whether system animations are on; replaceable in tests. */
    internal var motionEnabled: () -> Boolean = ::animationsEnabled

    /** Starts the animation in the colour of the logged habit. */
    fun start(color: Int, label: String, hint: String?) {
        if (started) return
        started = true
        discPaint.color = color
        ringPaint.color = color
        dotPaint.color = color
        this.label = label
        this.hint = hint
        holdMs = if (hint != null) HOLD_WITH_HINT_MS else HOLD_MS
        announceForAccessibility(if (hint != null) "$label. $hint" else label)
        val total = (holdMs + EXIT_MS).toFloat()
        if (!motionEnabled()) {
            // Reduced motion: the fully shown confirmation, still, for as long as usual.
            elapsed = SHOWN_AT_MS
            invalidate()
            handler.postDelayed(finishStill, total.toLong())
            return
        }
        animator = ValueAnimator.ofFloat(0f, total).apply {
            duration = total.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsed = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onFinished?.invoke()
                }
            })
            start()
        }
    }

    /** Ends the animation immediately, e.g. when the user taps the screen. */
    fun skip() {
        val running = animator
        if (running != null) {
            running.end()
        } else {
            handler.removeCallbacks(finishStill)
            onFinished?.invoke()
        }
    }

    private fun animationsEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(finishStill)
        animator?.removeAllListeners()
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!started) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = DISC_RADIUS_DP * density

        val exit = progress(holdMs.toFloat(), EXIT_MS.toFloat())
        val fade = 1f - ease.getInterpolation(exit)

        // A light dim, so the confirmation stands out on any wallpaper.
        scrimPaint.alpha = (SCRIM_ALPHA * ease.getInterpolation(progress(0f, 200f)) * fade).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // Two ripple rings, the second a little later.
        drawRing(canvas, cx, cy, radius, progress(0f, 750f))
        drawRing(canvas, cx, cy, radius, progress(180f, 750f))

        // A burst of small dots in the habit's colour.
        val burst = progress(60f, 650f)
        if (burst in 0.001f..0.999f) {
            val travel = radius * (1.05f + 0.75f * ease.getInterpolation(burst))
            dotPaint.alpha = (255 * (1f - burst)).toInt()
            for (i in 0 until DOTS) {
                val angle = Math.toRadians(i * 360.0 / DOTS - 90.0)
                val dotRadius = (if (i % 2 == 0) 4.5f else 3f) * density * (1f - 0.5f * burst)
                canvas.drawCircle(cx + travel * Math.cos(angle).toFloat(), cy + travel * Math.sin(angle).toFloat(), dotRadius, dotPaint)
            }
        }

        // Disc with a check that draws itself.
        val grow = progress(0f, 260f)
        val discRadius = if (grow > 0f) radius * pop.getInterpolation(grow) * (1f - 0.35f * exit) else 0f
        if (discRadius > 0f) {
            discPaint.alpha = (245 * fade).toInt()
            canvas.drawCircle(cx, cy, discRadius, discPaint)
            drawCheck(canvas, cx, cy, discRadius, progress(120f, 280f), fade)
        }

        drawPill(canvas, cx, cy, radius, progress(160f, 300f), fade)
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, ring: Float) {
        if (ring <= 0f || ring >= 1f) return
        ringPaint.strokeWidth = (5f - 4f * ring) * density
        ringPaint.alpha = (200 * (1f - ring)).toInt()
        canvas.drawCircle(cx, cy, radius * (1f + 1.1f * ease.getInterpolation(ring)), ringPaint)
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float, r: Float, amount: Float, fade: Float) {
        if (amount <= 0f) return
        checkPath.reset()
        checkPath.moveTo(cx - 0.42f * r, cy + 0.02f * r)
        checkPath.lineTo(cx - 0.12f * r, cy + 0.32f * r)
        checkPath.lineTo(cx + 0.45f * r, cy - 0.28f * r)
        measure.setPath(checkPath, false)
        partialCheck.reset()
        measure.getSegment(0f, measure.length * ease.getInterpolation(amount), partialCheck, true)
        checkPaint.strokeWidth = maxOf(2f * density, r * 0.16f)
        checkPaint.alpha = (255 * fade).toInt()
        canvas.drawPath(partialCheck, checkPaint)
    }

    private fun drawPill(canvas: Canvas, cx: Float, cy: Float, radius: Float, amount: Float, fade: Float) {
        if (amount <= 0f) return
        val hintText = hint
        val padH = 14 * density
        val padV = 8 * density
        val gap = 14 * density
        val lineGap = 2 * density
        val textWidth = maxOf(textPaint.measureText(label), hintText?.let { hintPaint.measureText(it) } ?: 0f)
        val w = minOf(textWidth + 2 * padH, width - 16 * density)
        val h = textPaint.textSize + (if (hintText != null) hintPaint.textSize + lineGap else 0f) + 2 * padV
        val lift = (1f - ease.getInterpolation(amount)) * 10 * density
        val top = cy + radius + gap - lift
        val left = (cx - w / 2f).coerceIn(8 * density, maxOf(8 * density, width - 8 * density - w))
        pillRect.set(left, top, left + w, top + h)

        val alpha = ease.getInterpolation(amount) * fade
        pillPaint.alpha = (Color.alpha(PILL) * alpha).toInt()
        canvas.drawRoundRect(pillRect, h / 2f, h / 2f, pillPaint)

        textPaint.alpha = (255 * alpha).toInt()
        val baseline = top + padV - textPaint.ascent()
        canvas.drawText(label, pillRect.centerX() - textPaint.measureText(label) / 2f, baseline, textPaint)
        if (hintText != null) {
            hintPaint.alpha = (215 * alpha).toInt()
            val hintBaseline = baseline + textPaint.descent() + lineGap - hintPaint.ascent()
            canvas.drawText(hintText, pillRect.centerX() - hintPaint.measureText(hintText) / 2f, hintBaseline, hintPaint)
        }
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    /** Linear progress of a sub-animation that starts at [start] ms and lasts [duration] ms. */
    private fun progress(start: Float, duration: Float): Float = ((elapsed - start) / duration).coerceIn(0f, 1f)

    private companion object {
        const val DISC_RADIUS_DP = 60
        const val HOLD_MS = 1700L
        const val HOLD_WITH_HINT_MS = 2800L
        const val EXIT_MS = 350L

        /** Elapsed time at which everything is fully shown (used without motion). */
        const val SHOWN_AT_MS = 900f
        const val SCRIM_ALPHA = 70
        const val DOTS = 10
        const val WHITE = 0xFFFFFFFF.toInt()
        const val PILL = 0xE6202423.toInt()
    }
}
