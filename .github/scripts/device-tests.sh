#!/usr/bin/env bash
# Runs the instrumented tests on the connected emulator and collects the
# screenshots they take. Instrumentation is started by hand (instead of
# connectedAndroidTest) so the app is not uninstalled before the pull.
set -euo pipefail

app=io.github.mendyar.habittracker
./gradlew --stacktrace :app:installDebug :app:installDebugAndroidTest

adb shell am instrument -w "${app}.test/androidx.test.runner.AndroidJUnitRunner" | tee instrument.log

mkdir -p screenshots
if ! adb pull "/sdcard/Android/data/${app}/files/screenshots" screenshots/; then
  # Without external storage (e.g. the API 21 image) the tests save to private storage.
  for f in $(adb shell run-as "$app" ls files/screenshots 2>/dev/null | tr -d '\r'); do
    adb exec-out run-as "$app" cat "files/screenshots/$f" > "screenshots/$f"
  done
fi

# `am instrument` exits 0 even when tests fail; its summary line tells.
grep -q "^OK (" instrument.log
