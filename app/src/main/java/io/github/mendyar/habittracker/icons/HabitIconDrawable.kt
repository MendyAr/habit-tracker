package io.github.mendyar.habittracker.icons

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Draws a habit icon: either a white glyph on a coloured disc, or a user image
 * cropped to a circle.
 *
 * With [fullBleed] the background fills the whole bounds instead of a circle and
 * the content is kept inside the centre safe zone, as adaptive icons require.
 */
class HabitIconDrawable(
    private val glyph: Drawable?,
    private val image: Bitmap?,
    color: Int,
    private val fullBleed: Boolean = false,
) : Drawable() {

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()
    private val glyphBounds = Rect()
    private val imageRect = RectF()

    init {
        glyph?.mutate()?.setTint(0xFFFFFFFF.toInt())
        if (image != null) imagePaint.shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = minOf(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        if (fullBleed) canvas.drawRect(b, background) else if (image == null) canvas.drawCircle(cx, cy, size / 2f, background)

        if (image != null) {
            // Adaptive icons show roughly the centre two thirds; keep the photo inside it.
            val side = if (fullBleed) size * IMAGE_SAFE_ZONE else size
            imageRect.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
            val scale = side / minOf(image.width, image.height)
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate(
                imageRect.left - (image.width * scale - side) / 2f,
                imageRect.top - (image.height * scale - side) / 2f,
            )
            imagePaint.shader.setLocalMatrix(shaderMatrix)
            if (fullBleed) canvas.drawRect(imageRect, imagePaint) else canvas.drawOval(imageRect, imagePaint)
        } else if (glyph != null) {
            val g = (size * if (fullBleed) GLYPH_ADAPTIVE else GLYPH_CIRCLE).toInt()
            glyphBounds.set((cx - g / 2f).toInt(), (cy - g / 2f).toInt(), (cx + g / 2f).toInt(), (cy + g / 2f).toInt())
            glyph.bounds = glyphBounds
            glyph.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        background.alpha = alpha
        imagePaint.alpha = alpha
        glyph?.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        background.colorFilter = colorFilter
        imagePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val GLYPH_CIRCLE = 0.56f
        const val GLYPH_ADAPTIVE = 0.40f
        const val IMAGE_SAFE_ZONE = 0.70f
    }
}
