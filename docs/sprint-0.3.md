# Sprint 0.3 — "The phone's citizen: your data moves, your training shows up everywhere"

Two weeks, 2026-11-02 to 2026-11-13, one developer and Claude Code, about ten working days. The goal is
a shippable 0.3.0 on Android that carries the rest of what the 2026-10-31 review found worth doing, now
that Health Connect's write side is in. iOS keeps building and testing in CI; nothing here needs an Apple
team. What landed in 0.2 and how a release goes: [release.md](release.md) and [CHANGELOG.md](../CHANGELOG.md).

## Context

0.2.0 is feature-complete on `main`: signed bundle from CI, changelog, backup, export, share, headings,
rest alerts off screen, the exercise trend, plans that hold Lift sessions, and Health Connect writes.
It is not yet tagged or uploaded; that is ticket 0, and the only ticket that is mostly the user's.

The review ranked ten improvements. One is done (Health Connect write). This sprint takes the seven that
fit two weeks, in the order that keeps `main` releasable and lets each ticket reuse the last's work.
The three left over open 0.4, named at the end so nothing is lost.

## Definition of done (every ticket)

- `./kotlin build` (full, links iOS), `./kotlin check`, `python3 tools/check_module_rules.py` and
  `python3 tools/check_ios_shards.py` pass. A `-p jvm` pass alone is not done.
- Logic in the module's `test/`, screen behaviour in `test@jvm/`, a user-facing journey in an acceptance
  spec first (`apps/app/test@jvm/acceptance/` or `lift/lift-nav/test@jvm/acceptance/`); strings in that
  module's `strings.xml`.
- A journey in `journeys/` added or updated when on-device behaviour changed, and run on `amper-37`.
- A line under `## [0.3.0] — Unreleased` in `CHANGELOG.md`; a schema change commits its `schemas/` JSON
  and bumps the export's `SCHEMA` when the bundle changes.
- One commit per ticket on `main` (`jj`), message in the repo's `Area: what changed` style, linear history.

Sizes: XS ≤ half a day, S ≈ 1 day, M ≈ 2 days, L ≈ 3 days. The cut line is after ticket 7.

## The backlog, in order

### Week 1 — ship 0.2, then the workout on the lock screen and the launcher

**0. Release 0.2.0** · S, mostly the user
- The four repository secrets, the hosted privacy policy URL (the text of
  `apps/app/src/about/PrivacyScreen.kt`), the Play Console's Health apps declaration (exercise, total
  calories burned), `git tag v0.2.0`, upload the AAB to internal testing. Steps: [release.md](release.md).
- Claude Code meanwhile runs `journeys/health-connect.xml` on `amper-37` (Android 14+ has Health Connect
  built in) against the release build, then bumps `versionName` to `0.3.0` and `versionCode` to 3 in
  `apps/androidApp/module.yaml` and the pbxproj, and opens `## [0.3.0] — Unreleased` in the changelog.
- Done when: CI's `release` job has attached `interval-v0.2.0.aab`, Play's review has the declaration,
  and `main` says 0.3.0.

**1. Live Updates: the workout and the rest as a status-bar chip** · S
- On Android 16 and later an ongoing notification built with `NotificationCompat.ProgressStyle` and
  `setRequestPromotedOngoing(true)` is promoted: a chip in the status bar with the clock
  (`setShortCriticalText`), and a progress bar on the lock screen and in the shade. `androidx.core` 1.18
  (already in `libs.versions.toml`) has both; `compileSdk` is 37; the manifest adds
  `android.permission.POST_PROMOTED_NOTIFICATIONS` (normal, no prompt).
- `interval/interval-platform/src@android/platform/WorkoutService.kt`: `notification(status)` becomes
  a `ProgressStyle` whose segments are the workout's phases in `PhaseColors`, with the tracker at the
  elapsed point and a point per round; the chip text is `status.clock`. Below 16 the builder ignores the
  style's extras and the notification is what it is today, so one code path.
- As built: the system draws at most 10 segments (past that it merges them into one in its default colour)
  and 4 points, and promotes no colourised notification. So `StatusBar` (`interval-platform`, tested) draws a
  segment per interval only up to 10 and one segment in the phase's colour past that (the default Tabata's
  16), marks rounds only when 4 or fewer follow the first, and `setColorized` is kept below 16 only. The
  outline is `WorkoutOutline.of(state)` in `interval-domain`, in whole seconds so the notification still
  changes once a second. `RestAlerts.start` takes the rest's start too.
- `RestService` in `lift/lift-platform/src@android/platform/RestAlerts.android.kt`: the same for the
  rest, one segment counting down. The `WorkoutStatus` the service receives already carries everything needed; if the rest needs
  its length, add it to what `RestAlerts` passes, never read the clock in the service.
- Done when: on `amper-37` (API 37) a running workout shows the chip and the lock-screen bar, Pause and
  End still work from it, and `journeys/start-workout.xml` and `journeys/lift-rest-alert.xml` say so.
  There is no pre-16 emulator here (only `amper-37`), so the fallback is checked by reading: the system
  ignores the style's extras below 16 and the builder's other calls are today's.

**2. App shortcuts: Start last workout, Lift session** · S
- Static `apps/androidApp/res/xml/shortcuts.xml` with two shortcuts (long-press the icon, or pinned),
  declared on `MainActivity` in the manifest. Each launches the app with an extra naming a start route.
- `apps/app/src/App.kt` takes an optional `startAt: NavKey?` that `AppContent` pushes once over the
  tab when the back stack is first built; `androidApp` reads the intent's extra into it, and
  `onNewIntent` too, so a warm app also jumps. "Start last workout" pushes `Workout` with the Timer's
  current config (what the Timer tab holds, `IntervalRepository`'s saved settings); "Lift session" pushes
  Lift's session route, which starts or resumes as the Lift tab's button does.
- As built: `App(shortcut: Shortcut?, onShortcutTaken)` with a `Shortcut` enum (`apps/app/src/nav/`) rather
  than a `NavKey`, since each names its tab and the workout's key needs the clock. A static shortcut always
  starts its activity with `CLEAR_TASK`, which would end a running workout, so the shortcuts target an
  invisible `ShortcutActivity` (its own task affinity) that hands the extra to `MainActivity` with
  `SINGLE_TOP | CLEAR_TOP` (`onNewIntent`). A workout already on the back stack wins over either shortcut. The
  hand-off sends the launcher's own intent (MAIN, LAUNCHER), so a task it starts has the root the notifications'
  `openAppIntent` brings forward, which is single top besides: the release smoke test found a cold shortcut start
  followed by a tap on the workout's notification opening a second app over the first.
- iOS gets the matching `UIApplicationShortcutItem`s for free only if someone wires the scene delegate;
  not this sprint, note it in `docs/release.md`'s follow-ups.
- Done when: `appTest` asserts each start key lands on its screen over the right tab and system Back
  returns to the tab; `journeys/shortcuts.xml` long-presses the icon on `amper-37` and starts a workout.

**3. How was it: effort and a note at the finish** · M
- Domain: a `PerceivedEffort` of five words (Easy, Steady, Hard, Very hard, All out) in
  `interval/interval-domain/src/model/`, stored as 1–5; `HistoryEntry` gains `effort: PerceivedEffort?`
  and `note: String?`; `LiftSession` the same two (`lift/lift-domain/src/domain/Training.kt`). Both Room
  databases bump a version with an additive migration and commit the schema JSON.
- Both summaries (`interval/interval-workout/src/workout/WorkoutSummary.kt`, Lift's `SummaryScreen`)
  gain a "How was it?" row: a `SegmentedPicker` of the five words and a one-line note field
  (`NameField`), saved as they change, so leaving the summary loses nothing. History's day log and
  workout page, and Lift's session page, show the word and the note; the share words append the note.
- As built: `HowWasIt` in `core` is the row on both sides (`SegmentedPicker` takes no selection, and drops its
  check and wraps a label on this one, so "Very hard" fits five across a phone). The workout summary's ring
  gives way to it: it takes the height the details leave (`RingOverDetails`, scaled whole in layout), down to
  `IntervalSizes.summaryRingLeast`, after the release smoke test found the row clipped under Repeat on a phone. `PerceivedEffort` is in both
  domains, as the sides share none. The summary hands the screen-worded note to its ViewModel, which writes
  the workout to Health Connect again as the summary goes, with `clientRecordVersion` the time it is written:
  Health Connect keeps the stored record unless the version is higher. The workout's summary keeps the
  effort and note across a process restart (in `WorkoutProgress`, with the History entry's uid); Lift's
  summary is not restored after a kill (as before), but what was said is already in the session's row. History's
  log shows the word and note on interval rows only; Lift's rows there stay as they were.
- Export: `WorkoutExport` and Lift's session export carry both, `SCHEMA` becomes 3 (ticket 4's reader
  takes 1–3 from the start). Health Connect: `HealthWorkout` gains `notes`, which
  `ExerciseSessionRecord(notes = …)` already takes; the effort word goes in the note text, there is no
  field for it.
- Done when: `WorkoutViewModelTest` and `SessionViewModelTest` keep an effort and a note across a
  restore; `IntervalRepositoryTest` and Lift's round-trip them through Room; a screen test per summary
  finds the row; the acceptance specs `start-workout` and Lift's session finish add the step.

### Week 2 — your data comes back in, and what Health Connect can tell you

**4. Import the export** · L — the half of "your data is yours" that 0.2 left out
- `apps/app/src/export/AppDataImporter.kt` beside the exporter: reads an `ExportBundle` of schema 1–3
  (refuses newer, with the message naming the app version it needs), validates ranges through the
  domain's own (`IntervalConfig`'s `*_RANGE`, `WeightUnit.range`), and merges:
  - presets by name (an existing name wins, the file's is skipped), history by `finishedAtEpochMillis` +
    shape, plan rows by day + what they hold, Lift's sessions and custom exercises by `uid`, programs by
    key, gear as a union. Nothing is deleted or overwritten; the result is counts of added and skipped.
  - settings are not imported unless the review sheet's "Also settings" is on (a fresh install wants
    them, a second phone's owner may not).
- `IntervalRepository.importAll(…)` and `LiftRepository.importAll(…)`, each one transaction, so a bad
  file half-way leaves nothing. Both take plain models, as `snapshot()` returns them.
- Reading a file is a platform hook: `FileOpener` in `core/core-platform/src/platform/`
  (`suspend fun open(mimeTypes): ShareableFile?`), with `rememberFileOpener()` in `core`'s
  `SystemServices.kt` like `rememberHealthConnector`: Android's `ActivityResultContracts.OpenDocument`,
  iOS's `UIDocumentPickerViewController`, desktop's `FileDialog`. `RecordingFileOpener` in `core-testing`.
- As built: `readImport` (`apps/app/src/export/ImportPlanner.kt`) is pure and plans everything before a write:
  the schema is read first (newer: "update the app"; not `app: interval` or damaged: refused), every value goes
  through the domain's ranges (one out of them refuses the file), and the file's ids are never stored (they are
  the other phone's): workouts match by when and what they were and get fresh uids, presets by name (a
  built-in's name counts as here), plans by day and what they hold and link to a preset by name, Lift's
  sessions, exercises and programs by uid, bands by id, bells by weight. The bar and the program followed
  come in only where this phone has none; Lift's rest comes with "Also settings". A set of an exercise neither
  here nor in the file keeps its key, as a set of one deleted does. Each side writes in one Room transaction
  (an `ImportDao` per database, `SerialWriter.await` so the notice follows the commit); the two databases
  can't share one, so a failure between them leaves the first side in, and importing again finishes the job.
  `FileOpener` is a callback (`rememberFileOpener(onOpen)` in `core`), not a suspend call, so Android's answer
  reaches a screen recreated behind the picker (checked by rotating with the picker open); the picker accepts
  `application/json`, `application/octet-stream` and `text/*`, and the contents decide. iOS's picker is built
  but not run on the simulator. The file names no device, so the sheet says the date and the version only.
- Settings > Your data > Import: pick a file, a review sheet with the counts and the date it was exported
  ("From this phone on 14 Oct: 12 workouts and 3 presets are new, 40 are already here"), Import, a notice
  with what came in. A corrupt or foreign file says so and changes nothing.
- Done when: `AppDataImporterTest` proves export → import on an empty store equals the source's
  `snapshot()`, importing a store's own export adds nothing, a schema-1 fixture (commit one under
  `apps/app/test/fixtures/`) reads, and a newer schema and a corrupt file are refused;
  `HealthConnect`-style acceptance spec `ImportAcceptanceTest`; `journeys/settings.xml` gains Import
  through the system picker.

**5. Health Connect: take your weight from it** · S
- The one read worth doing first: a scale app or a watch writes `WeightRecord`, and the calorie estimate
  depends on the weight. `HealthStore` gains `suspend fun latestWeightKg(): Double?` (null when
  disconnected or none in the last 30 days; older needs `READ_HEALTH_DATA_HISTORY`, which the app does
  not ask for). `HealthConnectStore` adds `HealthPermission.getReadPermission(WeightRecord::class)` to
  `PERMISSIONS`; the manifest adds `android.permission.health.READ_WEIGHT`; `NoHealthStore` returns null.
- Settings' weight sheet gets a "Use Health Connect's: 82 kg" chip while connected and a reading exists;
  tapping it sets the weight (in the user's unit) and the note under the sheet says where it came from.
  No automatic sync: a reading taken by surprise would change every estimate silently.
- Privacy page and `docs/release.md`'s Play declaration add "body weight (read)"; `RecordingHealthStore`
  gains `weightKg`. The Play declaration must be updated before the 0.3 upload, or review blocks it.
- Done when: `SettingsViewModelTest` takes the reading into the weight and shows none when
  disconnected; `SettingsScreenTest` finds the chip; `journeys/health-connect.xml` writes a weight in
  Health Connect's own app and takes it in Settings.
- As built: connected still means the two write permissions alone. `READ_WEIGHT` is asked on the same sheet
  (`PERMISSIONS` is the union) but checked on its own before each read, so adding it doesn't drop a user
  connected in 0.2 to disconnected and stop their writes; such a user turns the switch off and on to be asked.
  `SettingsViewModel` reads the weight each time the status becomes connected (`flatMapLatest`), keeps it in
  kilograms, and offers it in the current unit, none outside the unit's range. The sheet's "chip" is a
  `SecondaryButton`; once the weight set is Health Connect's it stays, disabled, as "From Health Connect: 82 kg"
  (one button either way, so the sheet doesn't shift under the finger), worked out from the two values rather
  than stored, so nothing new is persisted or exported. `useHealthWeight` keeps the reading as weighed, not
  rounded to the unit, so switching units still shows it as Health Connect's.

**6. Lift balance: sets per muscle over the last four weeks** · M
- A "Balance" card on the Lift tab (`lift/lift-today/src/today/Today.kt`) under the week: a bar per muscle
  group (`Muscles.kt` already maps every built-in to what it works and assists; a custom exercise's
  `focus` stands in), counting sets over the last 28 days, worked sets full and assisting sets lighter,
  drawn with `core/core/src/ui/Bars.kt` as the trend card is. A muscle not trained in four weeks is
  named below the bars, the one line of advice the card gives.
- Pure `MuscleBalance.of(sessions, today): List<MuscleLoad>` in `lift/lift-domain/src/domain/` with
  tests over worked, assisting, custom and empty cases; `TodayViewModel` exposes it; the card hides
  until two sessions exist, as the trend does.
- Done when: `MuscleBalanceTest` covers the four cases; a screen test in `lift-today`'s `test@jvm/`
  finds the card with sessions and not without; `journeys/lift-equipment.xml` scrolls to it.
- As built: a bar per muscle group, not per muscle. Eighteen unlabelled bars say nothing on a phone, and a
  custom exercise's `focus` names a region rather than a muscle, so `MuscleGroup` (lift-domain) folds the
  `Muscle`s into seven: chest, shoulders, back, arms, core, glutes and hamstrings, quads and calves. `Bars.kt`
  draws unlabelled columns, so the card (`BalanceCard`, lift-today) draws a labelled row each instead, in a
  fixed order so a gap is always in the same place: the sets that worked a group most in the accent, those that
  only assisted it lighter after (`Opacity.LIGHT_SHARE`), the number the worked ones. A set counts once in a
  group; a one-sided set counts half, so a left and a right weigh as one two-handed set. A custom exercise's
  sets count as assisted in every group of its focus (all seven for full body); an exercise this build doesn't
  know counts nowhere. The window is the 28 days to today by finish day, the open session's sets wait for it to
  finish, and the card shows once two sessions are in the window (not two ever: two sessions long ago would
  draw empty bars). The line under the bars names the groups no set worked or assisted. It sits after the last
  session rather than under the week: the tab's order is what's next before what's past.

**7. iOS follow-ups from 0.2** · S (two XS, no Android risk)
- Notification permission for a user who only lifts: `AskToShowNotifications()` moves from
  `interval-platform` to `core`'s `SystemServices.kt` (its iOS actual with it; Android's asks for
  `POST_NOTIFICATIONS`), so `SessionScreen` can ask on a session's first rest as the workout does on
  start. The interval side keeps calling the same name.
- A plan reminder due while the app is open: a `UNUserNotificationCenterDelegate` in
  `apps/iosApp/src/iosApp.swift` whose `willPresent` returns banner and sound for the
  `plan-reminder-` identifiers only; the workout's and the rest's stay silent in the foreground, the
  app beeps itself.
- Done when: `SessionViewModelTest` asks once per session; both checked on the iOS simulator
  (`run-on-device`), and `docs/release.md`'s follow-ups drop the two entries.
- As built: `SessionUiState.asksToShowNotifications` turns on as the session's first rest starts (`RestTimer.rested`)
  and stays on, so `SessionScreen` composes `AskToShowNotifications()` once rather than at each rest. Android asks
  there too now (`POST_NOTIFICATIONS`, for `RestService`'s notification), so `journeys/lift-rest-alert.xml` allows
  it. `interval-platform` drops `androidx.activity.compose` with the hook. `AppDelegate` (`iosApp.swift`) is the
  notification centre's delegate from launch; `willPresent` matches `PlanReminders.ios.kt`'s `REQUEST_PREFIX`,
  each side's comment naming the other. On the simulator: a fresh install was asked on the first rest and not on
  the next; a reminder set for the next quarter hour showed as a banner over Plan; a rest ending with the app
  open showed nothing.

--- cut line ---

**8. Health Connect: a watch's heart rate on the summary** · M (stretch)
- After a workout or session finishes, read `HeartRateRecord` over its window and show average and peak
  on the summary and the History page, keeping them on the entry (two nullable columns, additive
  migration). `READ_HEART_RATE` joins the permissions and the Play declaration. Nothing to show without a
  wearable, so the card hides when the read is empty.
- As built: `HealthStore.heartRate(start, end)` (core-platform's `HeartRate`, average and peak) is one aggregate
  read (`BPM_AVG`, `BPM_MAX`) under `READ_HEART_RATE`, which joins the permission sheet but, like the weight's, is
  checked on its own, so a user connected before keeps writing and sees none until they connect again. Health
  Connect answers only an app on show, and a watch syncs minutes late, so the read is the screens': the summary
  and a finished workout's or session's page ask each time they come into view (`LifecycleStartEffect`) until one
  is found, which is then kept (`average_bpm`, `peak_bpm`; the first found stays) and never asked for again. A
  summary restored after a kill reads again rather than saving it. History's entry also keeps when the workout
  began (`started_at`), so the page reads the window the summary did; an older one's is run back from its finish.
  On the workout's summary it is one line under the stats ("Heart rate 142 avg · 171 peak"): a row of tiles
  pushed "How was it?" under Repeat on a phone. A workout's page shows two tiles in its figures, and Lift's
  `SessionStats` two tiles on both its summary and its page, which scroll. The export carries both figures in schema 3 (still unreleased,
  so no bump), checked on import as a pair a heart can beat at (`HeartRate.BPM_RANGE`); an import still matches a
  workout by when and what, never by its heart rate. Interval's database is at 9, Lift's at 15. `amper-37` has no
  watch, so the journey checks only that nothing shows without one; the reads are covered by
  `HealthConnectAcceptanceTest` over the recording store.

**9. HealthKit: the iOS actual of `HealthStore`** · L (stretch, only if 0–7 land early)
- `HKWorkoutBuilder` with `.highIntensityIntervalTraining` / `.traditionalStrengthTraining`, a
  `HKWorkoutActivity` per segment, an `activeEnergyBurned` sample for the calories; the HealthKit
  entitlement and `NSHealthUpdateUsageDescription` in the Xcode project. The simulator has HealthKit,
  so it can be checked without a device; Play's declaration has no Apple counterpart until an App
  Store listing exists.
- As built: `HealthKitStore` (core-platform's src@ios), the reads too (the latest `bodyMass` in 30 days, a
  `HKStatisticsQuery` of the heart rate's average and peak), so iOS has what Android has. Only a multisport workout
  mixes kinds of activity, so each segment but a pause is an activity of the workout's own kind with its move and
  reps in its metadata (`IntervalMove`, `IntervalRepetitions`); a pause is a pause and a resume event, which
  HealthKit leaves out of the duration. Title and notes go in metadata (`IntervalTitle`, `IntervalNotes`), as
  HealthKit has no field for them. The workout and its energy sample carry `HKMetadataKeySyncIdentifier` and
  `…SyncVersion`, so the summary's rewrite replaces both. The mapping (`HealthKitWorkout.kt`) is pure and tested
  in `test@ios`. iOS can't revoke a permission, so Settings' off is the app's own switch in the user defaults,
  stopping the reads too; HealthKit hides whether a read was allowed, so a refused one reads as nothing found.
  The sheet asks once per question: a user who refused the writes is sent to the Health app, and the status is
  read again as the app comes to the front. The toolchain builds with signing off (no team), yet Xcode still puts
  the entitlement in the simulator binary's `__entitlements` section, which is all HealthKit there checks. Screens
  name the store by core's `healthStoreName` ("Health Connect", "Apple Health"), and the privacy policy says
  "workout" rather than Health Connect's "exercise session". Checked on the iPhone 18 Pro simulator: the sheet,
  a workout with a pause written and rewritten (the Health database holds one, the first version deleted), a Lift
  session as strength training with its reps, off and on again, and a refusal (the switch stays off; the next tap
  opens Health). The weight and heart-rate reads are unchecked there, with no samples to read; there is no iOS
  journey runner.

**10. Warm-up and cool-down** · M (pulled forward from 0.4's richer shapes, on request)
- `IntervalConfig.warmUpSeconds`/`coolDownSeconds` (0 is off) and `Phase.WarmUp`/`Phase.CoolDown`, run as Get
  ready → Warm-up → the rounds → Cool-down. Part of the shape (`sameShape`), so presets, History, Plan and the
  export (schema 3) carry them; Room version 8. The Timer and the preset editor offer them under the rows; the
  describe parser reads "5 min warm-up"; cues, notifications and Health Connect (as the activity's move) know
  both. Ending in the cool-down finishes the workout. With both off, nothing looks or behaves differently.
- Done when: `WarmUpCoolDownAcceptanceTest` and `journeys/warm-up-cool-down.xml` run a workout through both.

## Explicitly not this sprint (0.4's candidates)

- **Richer interval shapes**: sets of rounds with a longer rest between, and ladders (warm-up and cool-down
  came forward as ticket 10). The most-asked
  feature of an interval timer, and it touches `IntervalConfig`, `WorkoutEngine`, the Timeline, the
  describe parser ("4 x (8 x 20/10), 1:00 between"), presets, export, History's estimate and Health
  Connect's segments. The headline of 0.4, a sprint of its own, not a ticket beside import.
- **A home-screen widget** (the week's ring and Start last workout, Glance): a module of its own on the
  Android side, after ticket 2's start routes exist to launch into. *Shipped after the sprint, in `androidApp`
  rather than a module of its own: it is host glue (a receiver, `res/xml`, an intent into `ShortcutActivity`),
  over a `WeekStandingSource` in `app`.*
- **AppFunctions** ("start a Tabata", "log a set"): the `androidx.appfunctions` API is still alpha and
  Android 16-only; worth a ticket once it is stable, over the same start routes.
- **Localisation**, **splitting the `TooManyFunctions` classes**, **iOS signing**: as in 0.2.

## Suggested order and calendar

| Day | Tickets |
|---|---|
| 1 | 0 release 0.2.0 (user: secrets, policy URL, Play declaration, tag; Claude: journey, bump to 0.3.0), start 1 |
| 2 | 1 Live Updates, 2 shortcuts |
| 3–4 | 3 effort and note |
| 5–7 | 4 import |
| 8 | 5 weight from Health Connect |
| 9 | 6 Lift balance |
| 10 | 7 iOS follow-ups, full `./kotlin build`, every journey on the release build, tag `v0.3.0` |

0 first so every later commit lands on a released `main`. 3 before 4 so the importer reads schema 3
from its first day rather than growing a field a day later. 1 and 2 early because they carry the
emulator time and the only platform risk (promotion eligibility, intent hand-off). 5 after 4 because
both change Settings > Your data and the Play declaration, and the declaration is edited once.

## Verification of the sprint as a whole

1. `./kotlin check` and `./kotlin build` green on `main`; CI green on `v0.3.0` with the AAB attached.
2. The release build on `amper-37` (`run-on-device`) passes every `journeys/*.xml`, including the new
   shortcuts journey and the Import and weight steps.
3. A fresh install imports 0.2's export and shows the same History, Presets, Plan and Lift data; importing
   it again adds nothing.
4. A workout started from the shortcut shows the status-bar chip, pauses and ends from it, and writes to
   Health Connect with its note.
5. `CHANGELOG.md` 0.3.0 lists tickets 1–7; About shows 0.3.0 on Android and iOS; the Play declaration
   names exercise, total calories burned and body weight.
