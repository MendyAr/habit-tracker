# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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

[1.0.0]: https://github.com/MendyAr/habit-tracker/releases/tag/v1.0.0
