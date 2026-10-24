---
name: run-on-device
description: Build, install and launch interval on the amper-37 Android emulator (resizable: phone, foldable, tablet, desktop) or the iOS simulator, and inspect its UI there.
---

### Android (android CLI)
```sh
./kotlin build -m androidApp
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
android layout -p                   # UI tree as JSON, faster than a screenshot
android docs search "<topic>"       # Official Android docs
```

`amper-37` is a **resizable** emulator on API 37 (the app's target SDK): one device for every form factor.
It boots as a phone; switch with `adb -s <serial> emu resize-display <mode>`:

| mode | form factor | pixels | dp |
|---|---|---|---|
| 0 | phone | 1080×2400 @ 420 | 411×914 |
| 1 | foldable, unfolded | 2208×1840 @ 420 | 841×701 |
| 2 | tablet | 1920×1200 @ 240 | 1280×800 |
| 3 | desktop | 1920×1080 @ 160 | 1920×1080 |

Check tablet and wide layouts (Settings beside the Timer, History beside a workout, two-column grids) in
mode 2, and put it back to 0 for phone checks and journeys. It has two displays, so a plain `screencap` or
`android screen capture` writes a "[Warning] Multiple displays…" text file instead of a PNG: capture
`adb -s <serial> exec-out screencap -p -d <id> > shot.png` with the id of `EMU_display_0` from
`adb shell dumpsys SurfaceFlinger --display-id`.

### iOS
```sh
./kotlin build -m iosApp -p iosSimulatorArm64
xcrun simctl install booted build/tasks/_iosApp_buildIosAppIosSimulatorArm64Debug/bin/Debug-iphonesimulator/interval.app
xcrun simctl launch booted dev.justinsmith.interval
```
