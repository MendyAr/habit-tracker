# Architecture

Habit Tracker is a small app with one hard requirement: **tapping an icon on the
home screen must record a timestamp without taking the user anywhere.** Most of
the design follows from that and from supporting Android 5.0 (2014) onwards.

## Modules

| Module | What it is | Depends on |
| --- | --- | --- |
| `core` | Pure Kotlin/JVM: period arithmetic (`PeriodMath`), bucketing and statistics (`StatsEngine`). No Android types, so it is unit-tested on the JVM in milliseconds. | Kotlin stdlib |
| `app` | The Android application. | `core`, Android framework only |

The app uses **no AndroidX or other runtime libraries**, only the Android
framework. That keeps the APK tiny (well under 1 MB), avoids the API 23 minimum
that current AndroidX releases require, and leaves nothing to slow down the
cold start that every tap triggers. AndroidX Test, Robolectric and JUnit are
test-only dependencies.

## Packages (`app`)

```
io.github.mendyar.habittracker
├── log/        LogActivity + LogAnimationView: the tap-to-log entry point
├── stats/      StatsActivity + BarChartView: statistics screen
├── habits/     HabitsActivity (list) and HabitEditActivity (create/edit)
├── widget/     HabitWidgetProvider (1×1 widget) and its WidgetConfigActivity
├── launcher/   Shortcuts (pinned + long-press shortcuts), LauncherSync, SyncReceiver
├── data/       HabitDatabase (SQLite), HabitRepository, Settings (SharedPreferences)
├── icons/      Built-in icon catalogue, colours, icon drawable/bitmap rendering
└── ui/         Async (serial I/O thread), Formats (locale-aware text), small helpers
```

## Key flows

### Tap: log a timestamp

```
launcher icon / pinned habit icon
        │  MAIN or io.github.mendyar.habittracker.action.LOG (+ habit id, + sourceBounds)
        ▼
LogActivity  (Theme.HabitTracker.Log: translucent, no window animation, no preview,
             own task affinity, excludeFromRecents, noHistory)
        │ 1. insert entry on the I/O thread
        │ 2. once the window is on screen (onEnterAnimationComplete), LogAnimationView draws
        │    a disc + check over the tapped icon (Intent.sourceBounds) and a pill with the
        │    current count; ~1.25 s in total
        │ 3. finish() with transitions overridden to none
        ▼
home screen (it was visible underneath the whole time)
```

Android has no API that lets an app run code when its launcher icon is tapped
without starting an activity, so this is the closest possible to "nothing
opens". The activity's window is fully transparent and has no enter or exit
animation, so what the user sees is the home screen with the confirmation drawn
on top of the icon they tapped. If the launcher does not report the icon's
bounds, the animation is centred on the screen.

The **widget** (`HabitWidgetProvider`) avoids even that: a tap sends a
broadcast, the entry is written in the background and the widget's `ViewFlipper`
animates to a check with the count and back. No window is involved at all.

### Long-press: statistics

Long-pressing an app icon shows the app's shortcuts (Android 7.1+). The app
publishes one dynamic shortcut per habit (most recently logged first) that opens
`StatsActivity` for that habit, plus a static "Habits" shortcut. On Android
7.0 and older, which have no shortcuts, a second launcher entry "Habit stats"
(an `activity-alias` enabled only there via `@bool/needs_stats_launcher`)
opens the statistics.

### One icon per habit

Android fixes an app's own launcher icon and label at install time, so habits
get their own **pinned shortcuts** (`ShortcutManager.requestPinShortcut` on
Android 8+, the `INSTALL_SHORTCUT` broadcast before that). Their label and icon
are updated whenever the habit is renamed or re-iconed (`LauncherSync`), and
they are disabled with a message when the habit is deleted. The main app icon
logs the "default" habit, which the user can reassign in the editor.

## Data

SQLite, two tables:

```
habits(_id, name, icon, custom_icon, color, created_at)
entries(_id, habit_id → habits(_id) ON DELETE CASCADE, ts)   index (habit_id, ts)
```

`ts` is epoch milliseconds (UTC). Bucketing into local days, weeks (locale's
first day of week), months and years happens at read time in `core`, so
statistics follow the device's current time zone and are correct across DST
changes. User-supplied icons are centre-cropped to 256 px PNGs under
`files/icons/`.

Per-habit preferences (period, chart window) and global ones (default habit,
widget→habit map, one-time hint) live in `SharedPreferences` (`Settings`).
Everything is included in Android backup and device transfer
(`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).

## Statistics

For the chosen period (day/week/month/year) and chart window:

- every period in the window becomes a bucket, **including empty ones**;
- average, min, max, population variance and standard deviation are computed
  over the buckets, **excluding the period still in progress** (otherwise a
  half-finished day would pull the average and minimum down), unless it is the
  only one;
- "total" counts every timestamp in the window.

(For a single series, covariance with itself equals the variance, which is
what the statistics card shows next to the standard deviation.)

## Threading

All database work runs on one serial background thread (`ui/Async`), so writes
happen in order and the UI thread never touches disk. Results are posted back to
the main thread. Broadcast receivers use `goAsync()` for the same.

## Tests

| Where | What | Runs |
| --- | --- | --- |
| `core/src/test` | Period arithmetic incl. DST and week starts, bucketing, statistics, ranges | JVM |
| `app/src/test` | Repository and settings, `LogActivity` (logs, never twice, closes itself, publishes shortcuts), `StatsActivity`, formatting | JVM via Robolectric |
| `app/src/androidTest` | Tap flow end to end (including a real tap on the launcher icon where the launcher shows it), long-press shortcuts published on the device, every screen with screenshots | Emulators API 21, 29, 35 in CI |
