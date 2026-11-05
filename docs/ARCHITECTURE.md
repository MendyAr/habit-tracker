# Architecture

Habit Tracker is a small app with one hard requirement: **tapping an icon on the
home screen must record a timestamp without taking the user anywhere.** Most of
the design follows from that and from supporting Android 5.0 (2014) onwards.

## Modules

| Module | What it is | Depends on |
| --- | --- | --- |
| `core` | Pure Kotlin/JVM: period arithmetic (`PeriodMath`), bucketing and statistics (`StatsEngine`, robust `RobustStats`, `DailyTimes`), the time-of-day and position profiles (`DayProfile`, `PositionProfile`). No Android types, so it is unit-tested on the JVM in milliseconds. | Kotlin stdlib |
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
habit widget (or a habit icon pinned by an older version)
        │  io.github.mendyar.habittracker.action.LOG (+ habit id, + the widget's source bounds)
        ▼
LogActivity  (Theme.HabitTracker.Log: translucent, no window animation, no preview,
             own task affinity, excludeFromRecents, noHistory)
        │ 1. insert entry on the I/O thread (or ask "Log Again?" right after one;
        │    duration habits follow data/Sessions)
        │ 2. once the window is on screen (onEnterAnimationComplete), LogAnimationView draws
        │    a disc + check in the middle of the screen and a pill with the habit and count;
        │    about 2 s in total
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
between taps.

The **main app icon** also starts `LogActivity` (`MAIN`/`LAUNCHER`), but it never
logs: it opens the habit list, or the statistics of the only habit. `LogActivity`
stays the launcher component because launchers drop an app's home-screen icon when
its launcher component is renamed. Logging only from a habit's own icon or widget
means opening the app, including from the installer's *Open* button after an
update, never records anything.

A **widget** (`HabitWidgetProvider`) tap starts the same activity, through an
activity `PendingIntent` (`HabitWidgetProvider.tapIntent`), so a widget confirms
exactly like an icon. Up to 2.0 widgets logged in a broadcast receiver and
flipped to a check in place; the receiver held each broadcast open (`goAsync`)
until the widget flipped back, and since an app's broadcasts are delivered one at
a time, quick taps were queued and registered seconds late. Widgets drawn by
those versions still send `ACTION_LOG`; the provider just redraws them.

The confirmation window covers the home screen, so a second tap on the widget
lands on it. Launchers pass the widget's position as the intent's
`sourceBounds`; `LogActivity` treats a tap there (8 dp of slack) as another tap
of the widget (e.g. "Already Started"), and a tap anywhere else dismisses the
confirmation.

Each habit has at most one widget. A widget is bound to its habit explicitly
(picker, or the pin request from the editor) and never falls back to another
habit: an unbound widget shows *Choose Habit*, one whose habit was deleted shows
*Habit Deleted*, and tapping either opens the picker. Widgets are resizable and
fill their space with the habit's colour.

### Long-press: statistics

Long-pressing the app icon shows the app's shortcuts (Android 7.1+). The app
publishes one dynamic shortcut per habit (most recently logged first) that opens
`StatsActivity` for that habit, plus a static "New Habit" shortcut.

### Habit icons (legacy)

Up to 2.0 the editor could pin a **home-screen icon** per habit (a pinned
shortcut). Since 2.1 habits go on the home screen as widgets only, but icons
users already have keep working: they still launch `LogActivity` with the habit
id (`Shortcuts.logIntent`), their label and icon follow renames (`LauncherSync`),
and they are disabled with a message when the habit is deleted (Android has no
API to remove a pinned shortcut or a widget, so launchers grey them out).

The widget providers don't declare `reconfigurable`, so launchers don't offer to
re-pick a widget's habit from its long-press menu. Nothing is ever created
implicitly: a fresh install starts with an empty habit list.

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

### First open and updates

`onboarding/Onboarding` compares the app's version code with the one stored when
the app was last opened (`Settings.lastSeenVersion`). The main app icon then opens
`OnboardingActivity` on top of the app once: the tutorial on a fresh install, or
"What's new" after an update from 1.3 or older (habits but no stored version) or to
a version listed in `WHATS_NEW_VERSIONS` (its items are `R.array.whats_new_items`).

## Data

SQLite, two tables (schema version 3):

```
habits(_id, name, icon, custom_icon, color, created_at, type)   type: 0 count, 1 duration
entries(_id, habit_id → habits(_id) ON DELETE CASCADE, ts, duration, trigger_tag)   index (habit_id, ts)
```

For a **duration** habit an entry is a session: `ts` is its start and `duration`
(ms) is null while it runs. Taps on its widget or icon follow `data/Sessions`:
the first starts a session (*Starting*), a tap within 3 s of the start changes
nothing (*Already Started*), a later one finishes it (*Ended*, with its length
large below), a tap within 3 s of the end changes nothing (*Already Ended*, the
same length, from `HabitRepository.lastFinished`), and after that a tap starts a
new session. A session older than 3 hours asks before it is finished. Version 1 databases are upgraded in place by adding
the two columns; existing data stays count data.

**Trigger tags** (version 3): `trigger_tag` is what set a count entry off, as the
name the user picked (null when none). After `LogActivity` logs a count habit, a
short confirmation is followed by `TriggerPickerView`, a card of wrapping chips in
the middle of the screen (a chip never cuts a name: it wraps). Picking tags the
entry; *Skip*, a tap outside or 12 s without a choice leave it untagged. *+ Add*
types a new trigger, which tags the entry and is appended to the habit's list.
Each habit's list (presets in `data/Triggers` until changed) and its on/off switch
are in `Settings` (`triggers_<id>`, `suggest_triggers_<id>`), edited in the habit
editor (`TriggersDialog`). Renaming isn't offered, so stored names never go stale;
a removed trigger stays on the entries it tagged.

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

On the Week, Month and Year tabs the same card shows a **position profile**
(`PositionProfile`): for every weekday, day of the month (1–31) or month, the
share of those days (months, on the Year tab) in the window that had at least
one entry. Each one counts once however many entries it had, days without
entries count, and the day (month) still running is left out unless it is the
only one. Dividing by how many times each position occurs makes uneven months
fair: the 31st is compared with the months that have one.

### Statistics catalogue

`stats/StatKind` lists every statistic with its label and explanation; the default
set is the robust one described in [STATISTICS.md](STATISTICS.md). `StatValues`
computes them from the bucketed counts. Each habit's choice (`StatsConfig`: the
statistics, hidden cards, an optional target and day-start hour) is stored in
`Settings` as a short text, absent while it is the default, and edited in
`StatsCustomizeDialog` from the statistics screen or the editor.

## Threading

All database work runs on one serial background thread (`ui/Async`), so writes
happen in order and the UI thread never touches disk. Results are posted back to
the main thread. Broadcast receivers use `goAsync()` for the same.

## Tests

| Where | What | Runs |
| --- | --- | --- |
| `core/src/test` | Period arithmetic incl. DST and week starts, bucketing, statistics, ranges, time-of-day profile | JVM |
| `app/src/test` | Repository and settings, `LogActivity` (logs, never twice, closes itself, publishes shortcuts), `StatsActivity`, the editor (app-icon switch, clearing, "already on the home screen", widget size and return home), widgets of every size, formatting | JVM via Robolectric |
| `app/src/androidTest` | Tap flow end to end (a real tap on the launcher icon opens the app; a habit icon logs), long-press shortcuts published on the device, widgets hosted by a real `AppWidgetHost`, adding a widget through the real launcher dialog, every screen with screenshots | Emulators API 21, 29, 35 in CI |
