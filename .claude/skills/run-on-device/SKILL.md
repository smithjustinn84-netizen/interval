---
name: run-on-device
description: Build, install and launch interval on the amper-37 Android emulator or the iOS simulator, and inspect its UI there.
---

### Android (android CLI)
```sh
./kotlin build -m androidApp
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
android layout -p                   # UI tree as JSON, faster than a screenshot
android screen capture -o shot.png
android docs search "<topic>"       # Official Android docs
```

### iOS
```sh
./kotlin build -m iosApp -p iosSimulatorArm64
xcrun simctl install booted build/tasks/_iosApp_buildIosAppIosSimulatorArm64Debug/bin/Debug-iphonesimulator/interval.app
xcrun simctl launch booted dev.justinsmith.interval
```
