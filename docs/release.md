# Release Guide

This document describes the release procedure for Interval, including version bumping, R8 minification smoke
tests, keystore setup, Play Console configuration, and CI automation. What landed in each version is
tracked in [CHANGELOG.md](../CHANGELOG.md).

---

## Release Pre-Flight Checklist

| Step | Action | Command / Check |
|---|---|---|
| 1 | **Static checks, rules & tests** | `./kotlin check lint detekt`, `./kotlin test -p jvm -p android`, `python3 tools/check_module_rules.py`, and `python3 tools/check_ios_shards.py` (a bare `./kotlin check` also links every iOS test in Release: 15+ min) |
| 2 | **Version parity** | `python3 tools/check_versions.py` (Android and iOS match) |
| 3 | **Full build & opt-in check** | `./kotlin build` (compiles all targets including iOS framework) |
| 4 | **R8 Minification Smoke Test** | Build release APK, install on emulator, run key journeys (including `widget.xml`) |
| 5 | **On-device manual checks** | Share sheet, rest/EMOM screen-off alerts, Android backup/restore, TalkBack |
| 6 | **Tag & CI release bundle** | The [signing secrets](#adding-the-signing-secrets) are set, then `git tag v<version> && git push origin v<version>` |

---

## Version Bumping Procedure

Both Android and iOS versions must be bumped in unison. CI will fail via `tools/check_versions.py` if either
`versionName`/`MARKETING_VERSION` or `versionCode`/`CURRENT_PROJECT_VERSION` diverge.

1. **Update Android**:
   In `apps/androidApp/module.yaml`:
   ```yaml
   android:
     versionName: "0.5.0"
     versionCode: 5
   ```
2. **Update iOS**:
   In `apps/iosApp/module.xcodeproj/project.pbxproj`:
   Update all occurrences of:
   ```pbxproj
   MARKETING_VERSION = 0.5.0;
   CURRENT_PROJECT_VERSION = 5;
   ```
3. **Verify parity**:
   ```sh
   python3 tools/check_versions.py
   ```
4. **Update Changelog**:
   In `CHANGELOG.md`, change `[<version>] — Unreleased` to `[<version>] — <YYYY-MM-DD>`.

---

## Adding the signing secrets

The `release` job (`.github/workflows/ci.yml`) signs the bundle on a `v*` tag. It has no keystore of its own: it
rebuilds `apps/androidApp/release.keystore` and `keystore.properties` from four repository secrets, and fails at
"Install the signing key" while any is missing. Set them once; they stay until changed.

| Secret | Value | From |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64 | `~/.keystores/interval-release.keystore` |
| `ANDROID_STORE_PASSWORD` | the keystore's password | `storePassword` in `~/.keystores/interval.properties` |
| `ANDROID_KEY_ALIAS` | the key's name, `interval` | `keyAlias` in the same file |
| `ANDROID_KEY_PASSWORD` | the key's password | `keyPassword` in the same file |

Both files live only on the development Mac, outside the repository. Never commit them, paste them into an issue
or chat, or print them in a CI log: the keystore cannot be replaced once a build signed with it is on Play, and
anyone holding it and its passwords can publish as the app.

**From Terminal (recommended).** Each command reads its value from the file and hands it to `gh` on standard
input, so nothing is printed or kept in the shell's history. `gh` needs to be signed in to an account that
administers the repository (`gh auth status`).

```sh
REPO=smithjustinn84-netizen/interval
PROPS=~/.keystores/interval.properties
base64 < ~/.keystores/interval-release.keystore | gh secret set ANDROID_KEYSTORE_BASE64 --repo $REPO
printf %s "$(sed -n 's/^storePassword=//p' $PROPS)" | gh secret set ANDROID_STORE_PASSWORD --repo $REPO
printf %s "$(sed -n 's/^keyAlias=//p' $PROPS)" | gh secret set ANDROID_KEY_ALIAS --repo $REPO
printf %s "$(sed -n 's/^keyPassword=//p' $PROPS)" | gh secret set ANDROID_KEY_PASSWORD --repo $REPO
```

`printf %s` drops the line's newline, which would otherwise become part of the password.

**From the website instead.** The repository's Settings > Secrets and variables > Actions > New repository
secret, once per row of the table, the name exactly as written. For `ANDROID_KEYSTORE_BASE64`, copy the
encoded keystore with `base64 < ~/.keystores/interval-release.keystore | pbcopy` and paste it; open
`interval.properties` in an editor for the other three, and copy each value without the `name=` before it or a
trailing space.

**Check them.**
1. `gh secret list --repo smithjustinn84-netizen/interval` names all four, with today's date. GitHub never
   shows a secret's value again, so this is as far as the list goes.
2. The values are right only if they open the key: `keytool -list -keystore
   ~/.keystores/interval-release.keystore -alias interval` asks for the store password and prints the key's
   SHA-256 fingerprint. An error there means the file or the password differs from what was set.
3. The first tag is the real test. If its run fails at "Install the signing key" or "Package the signed release
   bundle", set the wrong secret again and rerun the failed job (`gh run rerun <run id> --failed`): there is no
   need to delete and push the tag again.

To change one later (a new password, say), run its command again: `gh secret set` replaces the value.

## Release Verification & Play Console Setup

1. **Repository secrets for the `release` job.** The four in [Adding the signing secrets](#adding-the-signing-secrets).
2. **The R8 smoke test.** A release build is minified, which is where Room, Koin and kotlinx.serialization
   break silently. Build it, replace the debug build on the emulator (a different signature cannot update it),
   and run the journeys (always including `widget.xml`):
   ```sh
   ./kotlin build -m androidApp -v release
   android emulator start amper-37
   adb -s emulator-5554 uninstall dev.justinsmith.interval
   adb -s emulator-5554 install build/tasks/_androidApp_buildAndroidRelease/gradle-project-release.apk
   ```
   then run `journeys/start-workout.xml`, `journeys/lift-equipment.xml`, `journeys/widget.xml`, and any journeys
   added or touched in the release. A crash here wants a rule in `apps/androidApp/proguard-rules.pro`. After
   editing that file, delete `build/tasks/_androidApp_buildAndroidRelease` before building again: the toolchain
   doesn't take the rules as an input, so R8 otherwise runs on the old copy and the APK comes out unchanged
   (check the rule appears in `gradle-project/build/_apps_androidApp/outputs/mapping/release/configuration.txt`).
   - **2026-10-29 (0.2.0):** On `amper-37` (phone) the release APK passed `start-workout`, `lift-equipment`,
     `settings` (the exported JSON read back whole), `system-back`, `history` and `presets`, and a process killed
     while backgrounded (`kill -9` as root, after the task's state was saved) brought back a running workout and
     the Presets page.
   - **2026-11-05 (0.4.0):** Ran every `journeys/*.xml` on the release APK. `widget.xml` found the one rule the app
     needs: Glance's WorkManager 2.7.1 keeps its input mergers' classes but not their constructors under R8's full
     mode, so the widget never drew (0.3.0 shipped so). Run `widget.xml` on every release build. Tagged `v0.4.0`
     the same day; CI's `release` job attached `interval-v0.4.0.aab`.
3. **On-device checks CI cannot run.**
   - `journeys/settings.xml`: Export opens the share sheet with `interval-<date>.json`.
   - `journeys/lift-rest-alert.xml` and `journeys/lift-emom-screen-off.xml`: a rest and an EMOM keep counting on the lock screen and beep with the screen off.
   - Backup: `adb shell bmgr backupnow dev.justinsmith.interval`, then `pm clear` and a restore, brings history
     and presets back.
   - TalkBack's headings navigation reaches every section of Today, Train, History and Settings.
4. **Health Connect on Play.** The listing needs a privacy policy URL whose words match the app's Privacy page
   (`apps/app/src/about/PrivacyScreen.kt`): `PRIVACY.md`, at the repository's root, is that text, and its GitHub
   page (https://github.com/smithjustinn84-netizen/interval/blob/main/PRIVACY.md) is the URL to give Play. And
   the Play Console's Health apps declaration must name the four data types in the manifest: exercise and total
   calories burned (write), and body weight and heart rate (read). Without the declaration Play blocks the release's
   review.
5. **Tag and upload.** `git tag v<version> && git push origin v<version>` (jj has no tags). The tag must be `v` +
   `versionName` (`tools/check_versions.py --tag` fails the job before anything is signed). CI attaches
   `interval-v<version>.aab` to the workflow run; upload it to the Play Console's internal testing track. Play
   vitals reports crashes and ANRs for a signed upload, so no crash-reporting SDK is needed.

## Sprint History & Backlog

Current and past sprints:
- [Sprint 0.5](sprint-0.5.md): Multi-block sequences, Today, Train's three pillars, sport days, mobility flows, first-run Set up, and the iOS Home Screen widget.
- [Sprint 0.4](sprint-0.4.md): Precomputed courses, sets of rounds, ladders, per-store `IntervalRepository` split, iOS quick actions, and Android 15+ widget picker preview (`v0.4.0`).
- [Architecture Sprint](sprint-architecture.md): Process-scoped Koin container, `WorkoutSession` and `SessionTimers` process owners, narrow store ports, and `SessionViewModel`/`LiftRepository` splits.
- [Sprint 0.3](sprint-0.3.md): Android 16 Live Updates, launcher shortcuts, "How was it?", JSON import, Health Connect reads, Apple Health (`HealthKitStore`), warm-up/cool-down, muscle balance, and the Android Glance widget (`v0.3.0`).
- **Other workspaces' stacks.** Commits made in another jj workspace above an old `main` rebase onto the
  new one with `jj rebase -d main`.

## Left out on purpose

- **Wear OS**: a platform project of its own. `HealthStore` (`core-platform`) writes workouts and reads body weight
  and a connected watch's heart rate through Health Connect and Apple Health.
- **iOS Home Screen widget**: a Swift-only WidgetKit extension fed by `WeekStandingSource`, tracked as ticket 5 in
  [sprint-0.5.md](sprint-0.5.md) (the Android Glance widget shipped in 0.3).
- **AppFunctions**: `androidx.appfunctions` is still alpha; deferred until stable, over the start routes the
  widget and launcher shortcuts already use.
- **Localisation**: the built-in presets' and mobility flows' names are stored/spoken data by design, and both text
  parsers are English-keyword; a second locale is a sprint, not a ticket.
- **Import atomicity across both databases (`E5`)**: Interval's and Lift's Room databases commit in separate atomic
  transactions; re-importing the same file safely completes any interrupted second half without duplicating the first.
- **iOS signing and an Apple team**, screenshot tests, coverage: out of the Android-only store goal or blocked by
  the toolchain (see [testing.md](testing.md)).
