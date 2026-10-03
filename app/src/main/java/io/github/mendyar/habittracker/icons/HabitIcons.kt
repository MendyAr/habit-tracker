package io.github.mendyar.habittracker.icons

import io.github.mendyar.habittracker.R

/** The built-in icon catalogue. Keys are persisted, so never rename one. */
object HabitIcons {

    class Icon(val key: String, val drawable: Int, val label: Int)

    const val DEFAULT = "check"

    val ALL: List<Icon> = listOf(
        Icon("check", R.drawable.ic_habit_check, R.string.icon_check),
        Icon("smoking", R.drawable.ic_habit_smoking, R.string.icon_smoking),
        Icon("smoke_free", R.drawable.ic_habit_smoke_free, R.string.icon_smoke_free),
        Icon("vaping", R.drawable.ic_habit_vaping, R.string.icon_vaping),
        Icon("sweets", R.drawable.ic_habit_sweets, R.string.icon_sweets),
        Icon("icecream", R.drawable.ic_habit_icecream, R.string.icon_icecream),
        Icon("cake", R.drawable.ic_habit_cake, R.string.icon_cake),
        Icon("pizza", R.drawable.ic_habit_pizza, R.string.icon_pizza),
        Icon("food", R.drawable.ic_habit_food, R.string.icon_food),
        Icon("burger", R.drawable.ic_habit_burger, R.string.icon_burger),
        Icon("fruit", R.drawable.ic_habit_fruit, R.string.icon_fruit),
        Icon("greens", R.drawable.ic_habit_greens, R.string.icon_greens),
        Icon("coffee", R.drawable.ic_habit_coffee, R.string.icon_coffee),
        Icon("tea", R.drawable.ic_habit_tea, R.string.icon_tea),
        Icon("drinks", R.drawable.ic_habit_drinks, R.string.icon_drinks),
        Icon("beer", R.drawable.ic_habit_beer, R.string.icon_beer),
        Icon("wine", R.drawable.ic_habit_wine, R.string.icon_wine),
        Icon("water", R.drawable.ic_habit_water, R.string.icon_water),
        Icon("water_glass", R.drawable.ic_habit_water_glass, R.string.icon_water_glass),
        Icon("workout", R.drawable.ic_habit_workout, R.string.icon_workout),
        Icon("walk", R.drawable.ic_habit_walk, R.string.icon_walk),
        Icon("run", R.drawable.ic_habit_run, R.string.icon_run),
        Icon("bike", R.drawable.ic_habit_bike, R.string.icon_bike),
        Icon("swim", R.drawable.ic_habit_swim, R.string.icon_swim),
        Icon("hike", R.drawable.ic_habit_hike, R.string.icon_hike),
        Icon("football", R.drawable.ic_habit_football, R.string.icon_football),
        Icon("basketball", R.drawable.ic_habit_basketball, R.string.icon_basketball),
        Icon("meditation", R.drawable.ic_habit_meditation, R.string.icon_meditation),
        Icon("selfcare", R.drawable.ic_habit_selfcare, R.string.icon_selfcare),
        Icon("sleep", R.drawable.ic_habit_sleep, R.string.icon_sleep),
        Icon("wake", R.drawable.ic_habit_wake, R.string.icon_wake),
        Icon("alarm", R.drawable.ic_habit_alarm, R.string.icon_alarm),
        Icon("read", R.drawable.ic_habit_read, R.string.icon_read),
        Icon("journal", R.drawable.ic_habit_journal, R.string.icon_journal),
        Icon("medication", R.drawable.ic_habit_medication, R.string.icon_medication),
        Icon("love", R.drawable.ic_habit_love, R.string.icon_love),
        Icon("mood", R.drawable.ic_habit_mood, R.string.icon_mood),
        Icon("bad_mood", R.drawable.ic_habit_bad_mood, R.string.icon_bad_mood),
        Icon("phone", R.drawable.ic_habit_phone, R.string.icon_phone),
        Icon("call", R.drawable.ic_habit_call, R.string.icon_call),
        Icon("tv", R.drawable.ic_habit_tv, R.string.icon_tv),
        Icon("gaming", R.drawable.ic_habit_gaming, R.string.icon_gaming),
        Icon("music", R.drawable.ic_habit_music, R.string.icon_music),
        Icon("piano", R.drawable.ic_habit_piano, R.string.icon_piano),
        Icon("art", R.drawable.ic_habit_art, R.string.icon_art),
        Icon("photo", R.drawable.ic_habit_photo, R.string.icon_photo),
        Icon("cleaning", R.drawable.ic_habit_cleaning, R.string.icon_cleaning),
        Icon("shower", R.drawable.ic_habit_shower, R.string.icon_shower),
        Icon("teeth", R.drawable.ic_habit_teeth, R.string.icon_teeth),
        Icon("pets", R.drawable.ic_habit_pets, R.string.icon_pets),
        Icon("plants", R.drawable.ic_habit_plants, R.string.icon_plants),
        Icon("savings", R.drawable.ic_habit_savings, R.string.icon_savings),
        Icon("shopping", R.drawable.ic_habit_shopping, R.string.icon_shopping),
        Icon("code", R.drawable.ic_habit_code, R.string.icon_code),
        Icon("study", R.drawable.ic_habit_study, R.string.icon_study),
        Icon("language", R.drawable.ic_habit_language, R.string.icon_language),
        Icon("work", R.drawable.ic_habit_work, R.string.icon_work),
        Icon("weight", R.drawable.ic_habit_weight, R.string.icon_weight),
        Icon("timer", R.drawable.ic_habit_timer, R.string.icon_timer),
        Icon("star", R.drawable.ic_habit_star, R.string.icon_star),
        Icon("energy", R.drawable.ic_habit_energy, R.string.icon_energy),
        Icon("mind", R.drawable.ic_habit_mind, R.string.icon_mind),
        Icon("toilet", R.drawable.ic_habit_toilet, R.string.icon_toilet),
        Icon("kindness", R.drawable.ic_habit_kindness, R.string.icon_kindness),
        Icon("stop", R.drawable.ic_habit_stop, R.string.icon_stop),
    )

    private val byKey = ALL.associateBy { it.key }

    /** The icon for [key], falling back to [DEFAULT] for unknown keys. */
    fun find(key: String): Icon = byKey[key] ?: byKey.getValue(DEFAULT)
}
