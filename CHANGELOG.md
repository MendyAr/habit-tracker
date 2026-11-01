# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-01

### Added
- Logging a habit again within a minute of its last entry asks first ("Log Again?"),
  since it is usually a double tap. A widget asks for a second tap instead. Adding an
  entry by hand in a minute that already has one asks too. *Don't Ask Again* turns
  this off for that habit (#19).
- The Week, Month and Year tabs now change the Time of Day card too: it becomes
  *Day of Week*, *Day of Month* or *Month of Year*, with bars for the share of each
  weekday, day of the month or month on which the habit was logged (#20).

### Changed
- Home-screen screenshots in the README use a plain background (#15).
- A clearer, livelier confirmation when logging: a larger disc, two ripples and a
  burst of dots in the habit's colour, a light dim behind it so it stands out on any
  wallpaper, the habit's name with the count ("Water · 3 today"), and it stays a
  little longer. With system animations turned off it now shows a still
  confirmation instead of none, and TalkBack reads it (#22).
- *Use Your Own Image* is now a clearly labelled option above the built-in icons
  (it used to be an unlabelled first cell of the icon grid). With an image chosen,
  it offers *Change Image* and *Remove* (#14).

## [1.2.0] - 2026-10-04

### Added
- **Time of Day** chart in the statistics: how likely you are to log the habit at
  each moment of the day, from 0% (never around then) to 100% (every day around
  then), as a smooth curve over the History window. Touch or drag across it to
  read any time; it starts on the most likely one.
- *Add One-Tap Widget* first asks for the widget's size (1 × 1 up to 4 × 4,
  Android 9+), and once the launcher has placed it, takes you to the home screen
  and tells you how to resize it.

### Changed
- *Add Icon to Home Screen* says when the habit's icon is already on the home
  screen, instead of silently doing nothing (Android 8+).
- *Add One-Tap Widget* says so before asking for a size when the habit already
  has a widget, and names the habit.

## [1.1.0] - 2026-10-03

### Added
- *Clear All Entries* for each habit, after a confirmation.
- The habit the app icon logs can now be switched off; the app icon then opens the
  habit list. With no habits at all it opens the (empty) list too.
- Widgets can be resized to any size and fill it with the habit's colour.

### Changed
- The tap confirmation is always shown in the middle of the screen, for the app
  icon and habit icons alike, so it no longer jumps around or covers the icon.
- Each habit can have at most one widget. A widget that has no habit yet asks for
  one, and a widget whose habit was deleted says so; neither shows another habit.
- Headings, buttons and shortcut labels use title case ("All Habits", "Edit Habit").
- The habit list tip now reads "Tap a habit for its statistics, or + to log it."
- Deleting a habit explains that Android lets only the user remove its greyed-out
  home-screen icon and widget.

### Fixed
- Logging no longer makes every widget on the home screen flicker.
- *Add One-Tap Widget* created a widget for the app-icon habit instead of the
  chosen one, which then followed the app-icon setting.
- The *App Icon Logs This Habit* switch showed as off and could not be changed.
- Deleting the app-icon habit, or every habit, no longer assigns or creates a habit.
- The long-press *Statistics* shortcut opened the editor when it was open in the
  background.

## [1.0.0] - 2026-10-03

### Added
- One-tap logging: tapping the app icon records a timestamp and plays a short
  confirmation animation over the home screen, then returns you to it. No screen
  is shown and nothing stays in recents.
- Statistics, reached by long-pressing the icon: the count for today or any
  picked day, plus average, minimum, maximum, standard deviation and variance
  per day, week, month or year.
- History bar chart with an adjustable date window (default: all time), tap or
  drag to inspect a bar.
- Entry list per period, with manual add (for a forgotten log) and delete (for an
  accidental tap).
- Multiple habits, each with its own home-screen icon (pinned shortcut), name,
  colour and icon: 65 built-in icons or your own image.
- Optional 1×1 home-screen widget per habit that logs completely in place.
- The chosen period and chart window are remembered per habit.
- Light and dark theme, themed (monochrome) launcher icon on Android 13+.
- Runs on Android 5.0 (API 21) and newer; no internet permission; data is
  included in Android backup and device-to-device transfer.

[1.3.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.3.0
[1.2.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.2.0
[1.1.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.1.0
[1.0.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.0.0
