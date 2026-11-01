# Release Guide

This document describes the release procedure for Interval, including version bumping, R8 minification smoke
tests, keystore setup, CI automation, and the pending follow-ups for 0.2.0. What landed in each version is
tracked in [CHANGELOG.md](../CHANGELOG.md).

---

## Release Pre-Flight Checklist

| Step | Action | Command / Check |
|---|---|---|
| 1 | **Static checks & tests** | `./kotlin check lint detekt` and `./kotlin test -p jvm -p android` (a bare `./kotlin check` also links every iOS test in Release: 15+ min) |
| 2 | **Version parity** | `python3 tools/check_versions.py` (Android and iOS match) |
| 3 | **Full build & opt-in check** | `./kotlin build` (compiles all targets including iOS framework) |
| 4 | **R8 Minification Smoke Test** | Build release APK, install on emulator, run key journeys |
| 5 | **On-device manual checks** | Share sheet, rest alerts, Android backup/restore, TalkBack |
| 6 | **Tag & CI release bundle** | `git tag v<version> && git push origin v<version>` |

---

## Version Bumping Procedure

Both Android and iOS versions must be bumped in unison. CI will fail via `tools/check_versions.py` if either
`versionName`/`MARKETING_VERSION` or `versionCode`/`CURRENT_PROJECT_VERSION` diverge.

1. **Update Android**:
   In `apps/androidApp/module.yaml`:
   ```yaml
   android:
     versionName: "0.2.0"
     versionCode: 2
   ```
2. **Update iOS**:
   In `apps/iosApp/module.xcodeproj/project.pbxproj`:
   Update all occurrences of:
   ```pbxproj
   MARKETING_VERSION = 0.2.0;
   CURRENT_PROJECT_VERSION = 2;
   ```
3. **Verify parity**:
   ```sh
   python3 tools/check_versions.py
   ```
4. **Update Changelog**:
   In `CHANGELOG.md`, change `[Unreleased]` to `[<version>] — <YYYY-MM-DD>`.

---

## Before the first upload (0.2.0)

1. **Repository secrets for the `release` job** (`.github/workflows/ci.yml`). The job runs on a `v*` tag and
   writes the keystore and `apps/androidApp/keystore.properties` from four secrets:
   `ANDROID_KEYSTORE_BASE64` (`base64 < ~/.keystores/interval-release.keystore`), `ANDROID_STORE_PASSWORD`,
   `ANDROID_KEY_ALIAS` (`interval`) and `ANDROID_KEY_PASSWORD`. The values are in
   `~/.keystores/interval.properties` on the development Mac. Never commit either file: the keystore cannot be
   regenerated once a build signed with it is on Play.
2. **The R8 smoke test.** A release build is minified, which is where Room, Koin and kotlinx.serialization
   break silently. Build it, replace the debug build on the emulator (a different signature cannot update it),
   and run the two journeys:
   ```sh
   ./kotlin build -m androidApp -v release
   android emulator start amper-37
   adb -s emulator-5554 uninstall dev.justinsmith.interval
   adb -s emulator-5554 install build/tasks/_androidApp_buildAndroidRelease/gradle-project-release.apk
   ```
   then `journeys/start-workout.xml` and `journeys/lift-equipment.xml`. A crash here wants a rule in
   `apps/androidApp/proguard-rules.pro`.
   **Done 2026-10-29, no rules needed.** On amper-37 (phone) the release APK passed start-workout,
   lift-equipment, settings (the exported JSON read back whole), system-back, history and presets, and a
   process killed while backgrounded (`kill -9` as root, after the task's state was saved) brought back a
   running workout and the Presets page.
3. **On-device checks CI cannot run.**
   - `journeys/settings.xml`: Export opens the share sheet with `interval-<date>.json`.
   - `journeys/lift-rest-alert.xml`: a rest keeps counting on the lock screen and beeps as it ends.
   - Backup: `adb shell bmgr backupnow dev.justinsmith.interval`, then `pm clear` and a restore, brings history
     and presets back.
   - TalkBack's headings navigation reaches every section of Settings, History and the Lift tab.
4. **Health Connect on Play.** The listing needs a privacy policy URL whose words match the app's Privacy page
   (`apps/app/src/about/PrivacyScreen.kt`): `PRIVACY.md`, at the repository's root, is that text, and its GitHub
   page (https://github.com/smithjustinn84-netizen/interval/blob/main/PRIVACY.md) is the URL to give Play. And
   the Play Console's Health apps declaration must name the data types in the manifest: exercise and total
   calories burned (write) and, from 0.3, body weight (read). Without the declaration Play blocks the release's
   review.
5. **Tag and upload.** `git tag v0.2.0 && git push origin v0.2.0` (jj has no tags). The tag must be `v` +
   `versionName` (`tools/check_versions.py --tag` fails the job before anything is signed). CI attaches
   `interval-v0.2.0.aab` to the workflow run; upload it to the Play Console's internal testing track. Play
   vitals reports crashes and ANRs for a signed upload, so no crash-reporting SDK is needed.

## Follow-ups found during the sprint

The next sprint's tickets, which take most of these up, are in [sprint-0.3.md](sprint-0.3.md).

- **iOS: the launcher shortcuts.** Android's two (0.3) open the app through `App(shortcut = …)`; iOS would need
  `UIApplicationShortcutItem`s in `Info.plist` and a scene delegate's `performActionFor` handing the item's type
  (a `Shortcut.id`) to `ViewController()` the same way.
- **Other workspaces' stacks.** Commits made in another jj workspace above the old `main` rebase onto the
  new one with `jj rebase -d main`.

## Left out on purpose

- **Import** of the export: the file carries `"schema": 1` so a reader can be added without a format change;
  merge rules and validation are a sprint of their own.
- **HealthKit, widgets, Wear OS**: each a platform project. Health Connect's write side landed (the `HealthStore`
  in core-platform), and in 0.3 the body weight's read; HealthKit would be its iOS actual, and a watch's heart
  rate a layer on top.
- **Localisation**: the built-in presets' names are stored data by design, and both text parsers are
  English-keyword; a second locale is a sprint, not a ticket.
- **Splitting the `TooManyFunctions` classes** (`WorkoutViewModel`, `IntervalRepository`, `LiftRepository`,
  `LiftDatabase`): real debt, blocking nothing.
- **iOS signing and an Apple team**, screenshot tests, coverage: out of the Android-only goal or blocked by
  the toolchain (see [testing.md](testing.md)).
