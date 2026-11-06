package io.github.mendyar.habittracker.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.ui.colorOf
import io.github.mendyar.habittracker.ui.dp
import io.github.mendyar.habittracker.ui.visible

/**
 * A short, skippable tutorial (a few pages with an icon and two or three lines
 * each), or a single "What's new" page after an update. It only explains: it never
 * logs anything and asks for nothing.
 */
class OnboardingActivity : Activity() {

    private class Page(val icon: Int, val title: Int, val text: Int)

    private val tutorial = listOf(
        Page(R.drawable.ic_ui_check, R.string.tour_welcome_title, R.string.tour_welcome_text),
        Page(R.drawable.ic_ui_add, R.string.tour_add_title, R.string.tour_add_text),
        Page(R.drawable.ic_ui_widgets, R.string.tour_log_title, R.string.tour_log_text),
        Page(R.drawable.ic_habit_mind, R.string.tour_triggers_title, R.string.tour_triggers_text),
        Page(R.drawable.ic_habit_timer, R.string.tour_duration_title, R.string.tour_duration_text),
        Page(R.drawable.ic_ui_calendar_month, R.string.tour_stats_title, R.string.tour_stats_text),
        Page(R.drawable.ic_ui_edit, R.string.tour_edit_title, R.string.tour_edit_text),
    )

    private lateinit var pages: List<Page>
    private var index = 0

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var dots: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        icon = findViewById(R.id.tour_icon)
        title = findViewById(R.id.tour_title)
        text = findViewById(R.id.tour_text)
        dots = findViewById(R.id.tour_dots)
        back = findViewById(R.id.tour_back)
        next = findViewById(R.id.tour_next)

        val whatsNew = intent.getBooleanExtra(EXTRA_WHATS_NEW, false)
        pages = if (whatsNew) listOf(Page(R.drawable.ic_ui_check, R.string.whats_new_title, 0)) else tutorial
        if (whatsNew) {
            text.text = resources.getStringArray(R.array.whats_new_items).joinToString("\n\n") { "• $it" }
        }
        index = savedInstanceState?.getInt(STATE_INDEX) ?: 0

        findViewById<View>(R.id.tour_skip).apply {
            visible = !whatsNew
            setOnClickListener { finish() }
        }
        back.setOnClickListener { show(index - 1) }
        next.setOnClickListener { if (index == pages.lastIndex) finish() else show(index + 1) }
        show(index)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
    }

    private fun show(page: Int) {
        index = page.coerceIn(0, pages.lastIndex)
        val p = pages[index]
        icon.setImageDrawable(getDrawable(p.icon)?.mutate()?.apply { setTint(colorOf(R.color.brand)) })
        title.setText(p.title)
        if (p.text != 0) text.setText(p.text)
        back.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        next.setText(if (index == pages.lastIndex) R.string.tour_done else R.string.tour_next)
        dots.removeAllViews()
        dots.visible = pages.size > 1
        pages.indices.forEach { i ->
            dots.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colorOf(if (i == index) R.color.brand else R.color.divider))
                    }
                },
                LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(dp(4), 0, dp(4), 0) },
            )
        }
        findViewById<View>(R.id.tour_page).contentDescription = getString(R.string.tour_page_description, index + 1, pages.size)
    }

    companion object {
        private const val EXTRA_WHATS_NEW = "whats_new"
        private const val STATE_INDEX = "index"

        fun intent(context: Context, mode: Onboarding.Mode): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_WHATS_NEW, mode == Onboarding.Mode.WHATS_NEW)
    }
}
