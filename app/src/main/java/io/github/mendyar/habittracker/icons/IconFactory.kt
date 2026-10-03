package io.github.mendyar.habittracker.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import io.github.mendyar.habittracker.data.Habit
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Builds drawables and bitmaps of habit icons and imports user images. */
object IconFactory {

    /** A scalable icon for in-app views. */
    fun drawable(context: Context, habit: Habit, fullBleed: Boolean = false): HabitIconDrawable =
        drawable(context, habit.icon, habit.customIcon, habit.color, fullBleed)

    fun drawable(
        context: Context,
        iconKey: String,
        customIcon: String?,
        color: Int,
        fullBleed: Boolean = false,
    ): HabitIconDrawable {
        val image = customIcon?.let { BitmapFactory.decodeFile(it) }
        val glyph = if (image == null) context.getDrawable(HabitIcons.find(iconKey).drawable) else null
        return HabitIconDrawable(glyph, image, color, fullBleed)
    }

    /** A circular icon bitmap of [sizePx] (legacy shortcuts and widgets). */
    fun circleBitmap(context: Context, habit: Habit, sizePx: Int): Bitmap =
        render(drawable(context, habit), sizePx)

    /** A 108dp full-bleed bitmap for adaptive shortcut icons (Android 8+). */
    fun adaptiveBitmap(context: Context, habit: Habit): Bitmap {
        val px = (108 * context.resources.displayMetrics.density).toInt()
        return render(drawable(context, habit, fullBleed = true), px)
    }

    private fun render(drawable: HabitIconDrawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * Copies the image at [uri] into private storage as a square PNG, centre-cropped
     * and scaled down to [CUSTOM_SIZE] px. Returns the file path, or null if the
     * image could not be read.
     */
    fun importImage(context: Context, uri: Uri): String? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CUSTOM_SIZE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            decoded?.let { saveSquare(context, rotate(context, uri, it)) }
        }
    } catch (e: Exception) {
        // Unreadable or unsupported image: the caller shows an error instead.
        null
    }

    private fun rotate(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return bitmap
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveSquare(context: Context, source: Bitmap): String {
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (side > CUSTOM_SIZE) Bitmap.createScaledBitmap(cropped, CUSTOM_SIZE, CUSTOM_SIZE, true) else cropped
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    private const val CUSTOM_SIZE = 256
}
