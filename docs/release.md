# Releasing 0.2.0

What the sprint of 2026-10-27 landed is in [CHANGELOG.md](../CHANGELOG.md). This is what still stands between
`main` and the first Play upload, in order, and what was left out on purpose.

## Before the first upload

1. **Repository secrets for the `release` job** (`.github/workflows/ci.yml`). The job runs on a `v*` tag and
   writes the keystore and `androidApp/keystore.properties` from four secrets:
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
   `androidApp/proguard-rules.pro`.
3. **On-device checks CI cannot run.**
   - `journeys/settings.xml`: Export opens the share sheet with `interval-<date>.json`.
   - `journeys/lift-rest-alert.xml`: a rest keeps counting on the lock screen and beeps as it ends.
   - Backup: `adb shell bmgr backupnow dev.justinsmith.interval`, then `pm clear` and a restore, brings history
     and presets back.
   - TalkBack's headings navigation reaches every section of Settings, History and the Lift tab.
4. **Tag and upload.** `git tag v0.2.0 && git push origin v0.2.0` (jj has no tags). The tag must be `v` +
   `versionName` (`tools/check_versions.py --tag` fails the job before anything is signed). CI attaches
   `interval-v0.2.0.aab` to the workflow run; upload it to the Play Console's internal testing track. Play
   vitals reports crashes and ANRs for a signed upload, so no crash-reporting SDK is needed.

## Follow-ups found during the sprint

- **iOS: notification permission for Lift-only users.** The rest-over notification needs authorization, which
  only `AskToShowNotifications()` (interval-platform) asks for, on a workout's start and on Plan. A user who
  only lifts is never asked. Moving the hook to `core` would let the session ask too.
- **iOS: a plan reminder due while the app is open is not shown.** A `UNUserNotificationCenterDelegate` in
  `iosApp/src/iosApp.swift` whose `willPresent` presents only the `plan-reminder-` identifiers would show it.
  The workout's and the rest's notifications must stay silent in the foreground: the app beeps itself.
- **Android app shortcuts** (Start last workout, Lift session): a static `shortcuts.xml` plus a launch-intent
  hand-off in `shared/src/App.kt`.
- **Other workspaces' stacks.** Commits made in another jj workspace above the old `main` rebase onto the
  new one with `jj rebase -d main`.

## Left out on purpose

- **Import** of the export: the file carries `"schema": 1` so a reader can be added without a format change;
  merge rules and validation are a sprint of their own.
- **Health Connect / HealthKit, widgets, Wear OS**: each a platform project.
- **Localisation**: the built-in presets' names are stored data by design, and both text parsers are
  English-keyword; a second locale is a sprint, not a ticket.
- **Splitting the `TooManyFunctions` classes** (`WorkoutViewModel`, `IntervalRepository`, `BellsRepository`,
  `BellsDatabase`): real debt, blocking nothing.
- **iOS signing and an Apple team**, screenshot tests, coverage: out of the Android-only goal or blocked by
  the toolchain (see [testing.md](testing.md)).
