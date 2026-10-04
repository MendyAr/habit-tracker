# Architecture

Habit Tracker is a small app with one hard requirement: **tapping an icon on the
home screen must record a timestamp without taking the user anywhere.** Most of
the design follows from that and from supporting Android 5.0 (2014) onwards.

## Modules

| Module | What it is | Depends on |
| --- | --- | --- |
| `core` | Pure Kotlin/JVM: period arithmetic (`PeriodMath`), bucketing and statistics (`StatsEngine`), the time-of-day profile (`DayProfile`). No Android types, so it is unit-tested on the JVM in milliseconds. | Kotlin stdlib |
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
├── stats/      StatsActivity, BarChartView and DayProfileView: statistics screen
├── habits/     HabitsActivity (list) and HabitEditActivity (create/edit)
├── widget/     HabitWidgetProvider (resizable, one per habit, in four default sizes) and WidgetConfigActivity
├── launcher/   Shortcuts (pinned + long-press shortcuts), PinResult, LauncherSync, SyncReceiver
├── data/       HabitDatabase (SQLite), HabitRepository, Settings (SharedPreferences)
├── icons/      Built-in icon catalogue, colours, icon drawable/bitmap rendering
└── ui/         Async (serial I/O thread), Formats (locale-aware text), small helpers
```

## Key flows

### Tap: log a timestamp

```
launcher icon / pinned habit icon
        │  MAIN (app icon) or io.github.mendyar.habittracker.action.LOG (+ habit id)
        ▼
LogActivity  (Theme.HabitTracker.Log: translucent, no window animation, no preview,
             own task affinity, excludeFromRecents, noHistory)
        │ 1. insert entry on the I/O thread (app icon: the habit assigned to it; if there
        │    is none, open the habit list instead)
        │ 2. once the window is on screen (onEnterAnimationComplete), LogAnimationView draws
        │    a disc + check in the middle of the screen and a pill with the current count;
        │    ~1.25 s in total
        │ 3. finish() with transitions overridden to none
        ▼
home screen (it was visible underneath the whole time)
```

Android has no API that lets an app run code when its launcher icon is tapped
without starting an activity, so this is the closest possible to "nothing
opens". The activity's window is fully transparent and has no enter or exit
animation, so what the user sees is the home screen with the confirmation drawn
on top. It is always centred: launchers report icon bounds inconsistently (the
icon may be mid-press-animation), so anchoring on the icon made the position jump
between taps, and centring keeps app icon and habit icons alike.

The **widget** (`HabitWidgetProvider`) avoids even that: a tap sends a
broadcast, the entry is written in the background and the tapped widget's
`ViewFlipper` animates to a check with the count and back (a partial update of
that widget only; others are not redrawn). No window is involved at all.

Each habit has at most one widget. A widget is bound to its habit explicitly
(picker, or the pin request from the editor) and never falls back to another
habit: an unbound widget shows *Choose Habit*, one whose habit was deleted shows
*Habit Deleted*, and tapping either opens the picker. Widgets are resizable and
fill their space with the habit's colour.

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
they are disabled with a message when the habit is deleted (Android has no API
to remove a pinned shortcut or a widget, so launchers grey them out). The main
app icon logs the habit assigned to it in the editor, or opens the habit list
when none is. Only the very first run creates a habit ("Habit"), so the app
works right after installation; afterwards nothing is created implicitly.

Launchers silently ignore a request to pin a shortcut that is already pinned, so
`Shortcuts.requestPin` first checks `ShortcutManager.getPinnedShortcuts()`
(launchers unpin a shortcut when the user removes its icon) and reports
`PinResult.ALREADY_EXISTS`. Before Android 8 the legacy broadcast gives no way
to know.

### Adding a widget from the app

`AppWidgetManager.requestPinAppWidget` (Android 8+) cannot set a size and apps
cannot open a launcher's resize handles. Launchers do use the provider's
default size, though, so the widget exists as four providers:
`HabitWidgetProvider` (1 × 1, the one in the launcher's widget list) and the
nested `Medium`, `Large` and `ExtraLarge` (2 × 2 to 4 × 4, `hide_from_picker`).
They share all code and are freely resizable; the extra ones are enabled only
on Android 9+ (`@bool/sized_widgets`), where `hide_from_picker` exists.

The editor asks for a size (`WidgetSize`), remembers the habit as the pending
widget, and asks the launcher. The new widget is bound to the habit either by
`onUpdate` (the pending request) or by the pin callback, whichever comes first.
The callback also notifies the editor (`HabitWidgetProvider.onPinned`), which
then opens the home screen and explains how to resize. If the user placed the
widget by hand (and is on the home screen already), the editor only shows that
hint.

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

The **time-of-day profile** (`DayProfile`) uses the same window, split into
local days (the day in progress is left out unless it is the only one). For
each day, every entry contributes a Gaussian bump of height 1 and σ = 20 min
around its local wall-clock time; the bumps of one day combine as
`1 − Π(1 − bump)`, the chance of "at least one entry around this time" if each
bump were an independent chance. The profile is the mean of these daily curves
over all days, sampled every 5 minutes on a circular 24-hour axis. So 1.0 means
"around this time every day", it cannot exceed 1 however many entries a day
has, and days without entries pull it down as they should. A plain smoothed
histogram divided by the number of days would instead lower the peak of an
every-day-at-8:00 habit below 1 (a density kernel of area 1) and exceed 1 for
bursts.

## Threading

All database work runs on one serial background thread (`ui/Async`), so writes
happen in order and the UI thread never touches disk. Results are posted back to
the main thread. Broadcast receivers use `goAsync()` for the same.

## Tests

| Where | What | Runs |
| --- | --- | --- |
| `core/src/test` | Period arithmetic incl. DST and week starts, bucketing, statistics, ranges, time-of-day profile | JVM |
| `app/src/test` | Repository and settings, `LogActivity` (logs, never twice, closes itself, publishes shortcuts), `StatsActivity`, the editor (app-icon switch, clearing, "already on the home screen", widget size and return home), widgets of every size, formatting | JVM via Robolectric |
| `app/src/androidTest` | Tap flow end to end (including a real tap on the launcher icon where the launcher shows it), long-press shortcuts published on the device, widgets hosted by a real `AppWidgetHost`, adding a widget through the real launcher dialog, every screen with screenshots | Emulators API 21, 29, 35 in CI |
