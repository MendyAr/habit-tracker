# Contributing

Thanks for helping! The app aims to stay small, fast and minimalist, so please
open an issue to discuss larger features before writing code.

## Development

- JDK 17, Android SDK 35. Open the project in Android Studio or use `./gradlew`.
- Keep the app free of runtime libraries (framework APIs only) and `minSdk` at 21;
  guard newer APIs with `Build.VERSION.SDK_INT` checks. Lint fails on unguarded calls.
- Put logic that doesn't need Android into `core` and unit-test it there.
- Database access goes through `HabitRepository` and runs off the main thread
  (`ui/Async`).
- New user-facing text goes into `res/values/strings.xml`.

## Before opening a pull request

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

CI additionally runs the instrumented tests on Android 5.0, 10 and 15 emulators.
Add a line under an *Unreleased* heading in [CHANGELOG.md](CHANGELOG.md) for
user-visible changes, and keep the in-app tutorial (*How to Use*, `tour_*` strings
in `OnboardingActivity`) describing the app as it is.

## Releasing

1. Bump `VERSION_NAME` and `VERSION_CODE` in `gradle.properties`, and turn
   *Unreleased* in CHANGELOG.md into the version's section.
2. Review the tutorial pages for the release's changes.
3. With user-visible changes, replace `whats_new_items` with them and add the version
   code to `Onboarding.WHATS_NEW_VERSIONS`.
4. Add the version code to `Onboarding.REVIEWED_VERSIONS` (`OnboardingTest` fails
   until you do).
5. Merge to `main`; when CI is green there, `release.yml` publishes the release.

## Icons

Built-in habit icons are Material Symbols imported by `tools/import_icons.py`.
To add one, append it to `ICONS` there, run the script, and add a matching entry
(with a label string) to `HabitIcons.kt`. Never rename an existing key: keys are
stored in users' databases.
