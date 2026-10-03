package io.github.mendyar.habittracker.ui

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.Toast

/** Colour resource lookup that works on every supported API level. */
fun Context.colorOf(id: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id)
    } else {
        @Suppress("DEPRECATION")
        resources.getColor(id)
    }

/** Converts density-independent pixels to pixels. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.toast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** Shows or hides a view without leaving a gap. */
var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }
