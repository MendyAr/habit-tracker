# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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

[1.1.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.1.0
[1.0.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.0.0
