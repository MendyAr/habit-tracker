# Notes for AI coding agents (and humans picking this up)

Handoff file: everything needed to continue work on Habit Tracker without the
original session. Read this first, then `docs/ARCHITECTURE.md` and `CONTRIBUTING.md`.

## State (2026-11)

- Latest release: **v2.0.0** (versionCode 5), published from `main` (#13, #16, #17, #18, #23 done).
- `main` is the default branch. Work on a feature branch and open a PR to `main`.
- Open work: none planned beyond Dependabot (see *Roadmap* below); new ideas go in GitHub issues.
- The owner (MendyAr) writes in Hebrew or English; keep the code, commits, issues and docs in English.

## Build, test, release

```sh
./gradlew :core:test :app:testDebugUnitTest   # JVM + Robolectric tests (fast)
./gradlew :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest      # needs a device/emulator; see below
```

- JDK 17, Android SDK 35, AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1 wrapper.
- Instrumented tests in CI: `.github/scripts/device-tests.sh` installs both APKs, runs
  `adb shell appwidget grantbind --package io.github.mendyar.habittracker` (needed by
  `WidgetDeviceTest`), then `am instrument`, and pulls screenshots. To run locally, do the same.
- CI (`.github/workflows/ci.yml`): build/unit/lint plus emulators API 21, 29, 35. The API 35 job
  prints screenshots base64 between `=====SHOT name` / `=====END` markers in its log.
- **Release**: bump `VERSION_NAME`/`VERSION_CODE` in `gradle.properties`, add a CHANGELOG section,
  then merge to `main`. When CI is green on `main`, `release.yml` publishes `v<VERSION_NAME>` with the
  signed APK and its sha256, taking the notes from CHANGELOG. Nothing to do by hand.
- Signing: the public sideload key `keystore/sideload.jks` (password and alias `habittracker`) is
  committed on purpose so anyone can build update-compatible APKs; override it with `SIGNING_*`
  env vars or secrets. Never change it: users couldn't update over their install.

## Architecture in one breath

- `core/`: pure Kotlin (no Android). `PeriodMath`, `StatsEngine`, `DayProfile` (time-of-day curve).
- `app/`: Android **framework only, no AndroidX at runtime** (keep it that way), minSdk 21.
  - `log/LogActivity`: transparent, animation-free; **it is the launcher (MAIN/LAUNCHER) entry**, which
    opens the app (never logs); pinned habit icons (`Shortcuts.logIntent`) log or start/finish a session.
  - `widget/HabitWidgetProvider`: one widget per habit. Nested `Medium`/`Large`/`ExtraLarge` are the
    same widget at 2×2–4×4 default sizes (hidden from the picker, enabled on API 28+ via
    `@bool/sized_widgets`) used when adding a widget from the editor.
  - `data/`: SQLite (`habits`, `entries`, AUTOINCREMENT ids) + `Settings` (SharedPreferences).
  - `ui/Async`: one serial I/O thread. All DB work and **all widget bind/update calls** run on it.

## Gotchas learned the hard way

- **Never rename the launcher component** (`.log.LogActivity`) or widget provider classes: launchers
  drop the user's home-screen icon or widgets on update. For #16, keep the name (alias or router).
- Never rename built-in icon keys (stored in DBs). New icons: `tools/import_icons.py`, which
  normalises vector path data (Android 5 crashes on paths like `7.5.5`).
- Widget binding must go through the Async I/O thread (`bind`/`updateAll`), or a view computed
  before a binding can be published after it (flaky on Android 5).
- Robolectric: call `Async.awaitIdle()` in setUp/tearDown, and use `awaitCondition {}` (in
  `app/src/test/.../TestSupport.kt`) to wait for background work. Robolectric never calls
  `onEnterAnimationComplete`, so tests call it.
- `UiDevice.launcherPackageName` can return `com.android.settings` (FallbackHome). Resolve the home
  app with `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`.
- Android 5 emulator: no external storage (screenshots fall back to `filesDir` + `run-as`), and host
  views can't fire click PendingIntents, so tests send the broadcast directly.
- Android can't remove pinned shortcuts or widgets programmatically. Deleted habits show greyed
  icons and "Habit Deleted" widgets until the user removes them.
- Launchers silently ignore pinning an already pinned shortcut. `Shortcuts.isPinned` checks first.

## Roadmap (owner's backlog, open issues)

Proposed grouping; confirm with the owner before starting:

- **1.3 (done):** #14 own-image option · #15 plain wallpaper in screenshot tests · #19 duplicate-log
  question (dialog; widgets need a second tap) · #20 Week/Month/Year position profiles
  (`core/PositionProfile`) · #22 livelier confirmation (with a still mode when animations are off).
- **2.0 (done):** #16 app icon opens the app (LogActivity routes; logging only from habit icons and
  widgets) · #13 tutorial + What's new (`onboarding/`, add a version to `WHATS_NEW_VERSIONS` and
  items to `whats_new_items` when a release has user-visible changes) · #17 duration trackers (DB v2,
  `data/Sessions`, stopwatch badge, live widget timer) · #18/#23 `stats/StatKind` catalogue,
  `StatsConfig` per habit, `StatsCustomizeDialog`, `docs/STATISTICS.md`.
- Trigger tags when logging: considered and dropped by the owner (not planned).
- Dependabot PRs: #6–#9 are stale (built on v1.0) and #21 (AGP 9.5) is a major jump. Don't merge
  them blindly; re-test each on current `main` (e.g. one combined PR), and close #4-style no-ops.

## Working agreements with the owner

- When asked to "add an issue", only file or update the GitHub issue; don't change code.
- Every change ships with tests and docs (README, CHANGELOG, ARCHITECTURE as relevant).
- Ask before merging PRs; the owner merges (merging to `main` triggers a release).
- Keep the UI minimalist, Title Case headings, and the app working from Android 5 to 15.
