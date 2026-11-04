package io.github.mendyar.habittracker.onboarding

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.mendyar.habittracker.R
import io.github.mendyar.habittracker.awaitCondition
import io.github.mendyar.habittracker.data.HabitRepository
import io.github.mendyar.habittracker.habits.HabitsActivity
import io.github.mendyar.habittracker.icons.HabitColors
import io.github.mendyar.habittracker.log.LogActivity
import io.github.mendyar.habittracker.ui.Async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OnboardingTest {

    private lateinit var context: Context
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repository = HabitRepository.get(context)
    }

    @After
    fun tearDown() {
        Async.awaitIdle()
        HabitRepository.resetForTests()
    }

    private fun openAppIcon(): List<Intent> {
        val activity = Robolectric.buildActivity(
            LogActivity::class.java,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        ).setup().get()
        awaitCondition { activity.isFinishing }
        return generateSequence { shadowOf(activity).nextStartedActivity }.toList()
    }

    @Test
    fun aFreshInstallShowsTheTutorialOnceOverTheHabitList() {
        val started = openAppIcon()
        assertEquals(HabitsActivity::class.java.name, started[0].component?.className)
        assertEquals(OnboardingActivity::class.java.name, started[1].component?.className)
        assertFalse(started[1].getBooleanExtra("whats_new", true))

        // Opening the app again shows the habit list only.
        assertEquals(1, openAppIcon().size)
        assertTrue(repository.habits().isEmpty()) // nothing created or logged
    }

    @Test
    fun anUpdateFromAnOlderVersionShowsWhatsNew() {
        repository.createHabit("Smoking", "smoking", null, HabitColors.DEFAULT)
        repository.createHabit("Water", "water", null, HabitColors.ALL[1])
        val started = openAppIcon()
        assertEquals(OnboardingActivity::class.java.name, started[1].component?.className)
        assertTrue(started[1].getBooleanExtra("whats_new", false))
        assertNull(Onboarding.pending(context))
    }

    @Test
    fun theTutorialPagesThroughAndFinishes() {
        val activity = Robolectric.buildActivity(
            OnboardingActivity::class.java,
            OnboardingActivity.intent(context, Onboarding.Mode.TUTORIAL),
        ).setup().get()
        val title = activity.findViewById<TextView>(R.id.tour_title)
        val next = activity.findViewById<TextView>(R.id.tour_next)
        assertEquals(context.getString(R.string.tour_welcome_title), title.text.toString())
        repeat(5) { next.performClick() }
        assertEquals(context.getString(R.string.tour_edit_title), title.text.toString())
        assertEquals(context.getString(R.string.tour_done), next.text.toString())
        next.performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun skipEndsTheTutorialAtOnce() {
        val activity = Robolectric.buildActivity(
            OnboardingActivity::class.java,
            OnboardingActivity.intent(context, Onboarding.Mode.TUTORIAL),
        ).setup().get()
        activity.findViewById<TextView>(R.id.tour_skip).performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun whatsNewIsOnePageListingTheChanges() {
        val activity = Robolectric.buildActivity(
            OnboardingActivity::class.java,
            OnboardingActivity.intent(context, Onboarding.Mode.WHATS_NEW),
        ).setup().get()
        assertEquals(context.getString(R.string.whats_new_title), activity.findViewById<TextView>(R.id.tour_title).text.toString())
        val text = activity.findViewById<TextView>(R.id.tour_text).text.toString()
        assertTrue(text.contains(context.resources.getStringArray(R.array.whats_new_items)[0]))
        assertEquals(context.getString(R.string.tour_done), activity.findViewById<TextView>(R.id.tour_next).text.toString())
    }
}
