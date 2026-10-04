package io.github.mendyar.habittracker.widget

/**
 * The sizes, in home-screen cells, a widget can be added in from the app.
 *
 * Android has no way to tell the launcher what size to give a pinned widget, or
 * to open its resize handles, but launchers use the provider's default size. So
 * each size has its own provider (hidden from the launcher's widget list); all of
 * them draw the same widget and can be resized freely afterwards.
 */
enum class WidgetSize(val cells: Int, val provider: Class<out HabitWidgetProvider>) {
    SMALL(1, HabitWidgetProvider::class.java),
    MEDIUM(2, HabitWidgetProvider.Medium::class.java),
    LARGE(3, HabitWidgetProvider.Large::class.java),
    EXTRA_LARGE(4, HabitWidgetProvider.ExtraLarge::class.java),
}
