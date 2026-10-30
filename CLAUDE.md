# interval — Claude Code Guide

Compose Multiplatform app for **Android, iOS and desktop (JVM)**, built with the **Kotlin Toolchain**
(`./kotlin`, Amper-based, version pinned in the `kotlin` wrapper). Docs: https://kotlin-toolchain.org/0.13/
Full architecture guide: [docs/architecture.md](docs/architecture.md).

> **NO GRADLE.** There is no `gradlew`, `build.gradle.kts` or `settings.gradle.kts`. Never run or suggest
> Gradle commands. Build configuration lives in `project.yaml` and each module's `module.yaml`.

## Commands

```sh
./kotlin build                      # Everything. The only build that links iOS. Run before calling work done
./kotlin build -p jvm               # Fast compile loop (common + JVM)
./kotlin test -p jvm                # Fast test loop (logic + Compose UI tests)
./kotlin test -m bells-session -p jvm   # One module's tests (a feature's, a domain's, Lift's flows in `bells`)
./kotlin check lint                 # ktlint over every module
./kotlin do format                  # ktlint auto-format
./kotlin check detekt               # detekt + Compose rules over every module (config: detekt.yml)
./kotlin check                      # All checks (tests + lint + detekt)
./kotlin run -m desktopApp --compose-hot-reload   # Desktop window that reloads on every save
./kotlin package -m androidApp -f aab   # Release bundle (R8 on; rules in androidApp/proguard-rules.pro).
                                        # Unsigned until a keystore is set up (`./kotlin tool generate-keystore`)
python3 tools/check_versions.py         # Verify Android and iOS version parity (run in CI)
python3 tools/check_ios_shards.py       # Every module with common tests is on one line of ios-test-shards.txt (run in CI)
uv run --with pillow tools/generate_icons.py  # Regenerate app icons and launch screens from the vector mark
./kotlin show modules | settings | dependencies | tasks
```

`-m` takes the **module name**, the directory's (`core`, `interval-history`, `bells-session`, `androidApp`, …;
`./kotlin show modules` lists them).

Running on the Android emulator or iOS simulator: the `run-on-device` skill.

**UI and UX work: reach for Compose Hot Reload first.** Run the desktop app with `--compose-hot-reload` (from
your own jj workspace) and leave it up while you edit: each save recompiles and swaps the code in place, so a
layout or motion change shows in seconds without losing the screen you are on. Size the window to the form
factor you are checking (`InitialSize` in `desktopApp/src/main.kt`, reverted before committing). To see the
window yourself you need either a computer-use grant for the running app (`MainKt`, `com.jetbrains.jbr.java`)
or the toolchain's `./kotlin compose-hot-reload-mcp-server` registered as an MCP server. Without either, render
the screen from a throwaway `test@jvm` test (`onRoot().captureToImage()` to a PNG at a harness `Window`) and
delete the test after. The emulator is for what only a device shows: insets, the keyboard, system bars.

## Layout

Amper source layout, not Gradle's:
- `<module>/src/` common code; `src@android/`, `src@ios/` (both iOS targets), `src@jvm/` platform code
- `<module>/test/` common tests; `test@jvm/` etc. for platform tests
- `androidApp/res/` Android resources; `<module>/composeResources/` a module's multiplatform resources (`Res.*`)

**Never create `src/commonMain/kotlin/`.** Amper ignores it silently.

### Modules

The module is the toolchain's unit of work: each has its own compile, KSP, detekt, lint and test tasks, rerun
only when something in it changes. The app is split along its layers so a screen edit recompiles that
feature and the shell, Room's KSP runs only when storage changes, and the pure domains compile and test with
nothing else on the classpath. Every library applies a template from `templates/` (`lib`: warnings as errors,
lint, detekt; `tested-lib`: + the test libraries and `core-testing`; `compose-lib`: + Compose, lifecycle,
Koin, and `settings.compose`; `room-lib`: + Room, the bundled driver, `-Xexpect-actual-classes`, JSON).
Modules are flat directories with unique names (`-m` takes the name); `project.yaml` lists them.

```
core                 the theme, tokens, motion, components, lanes, `Tab` and `push` (nav/), and the Composable
                     platform hooks the UI needs (`SystemServices.kt`); exports `core-platform` and `core-time`.
                     Packages `dev.justinsmith.interval.ui`, `.platform`, `.nav`; resources
                     `dev.justinsmith.core.resources`
core-platform        what the platform gives that isn't drawing, with no Compose: the clocks (`WallClock`,
                     `epochMillisNow`, `realElapsedTime`, `days`), `TimestampFormatter`, `AppInfo`, `Sharer`,
                     Android's notification helpers, and `storage/Storage.kt` (`SerialWriter`, `storageScope`,
                     `orOnError`/`orEmptyOnError`); packages `.platform`, `.storage`; exports `core-time`. The
                     data modules depend on this, not `core`, so a theme edit neither recompiles them nor reruns
                     their Room KSP (interval-data's test KSP still does, through `interval-testing`)
core-time            calendar/clock maths (`CalendarDays`, `formatClock`, `WeekStanding`), package `.timer`. No
                     dependencies, so the domains compile without Compose
core-testing         `MainDispatcherTest`, `FixedTimestampFormatter`, `FakeAppInfo`, the tests' calendar;
                     src@jvm: the UI harness (`runUiTest`, `setThemedContent`, `StateRestoration`, `waitForText`)

interval-domain      shared/timer + model: `IntervalConfig`, `Preset`, `WorkoutEngine`, calories, the describe
                     parser, `AppSettings`, `HistoryEntry`, `PlannedWorkout`, `LiftWorkout`, `WorkoutProgress`;
                     over `core-time` and `bells-domain` (for `asLiftWorkouts`), not `core`
interval-data        `IntervalRepository`, `AppDatabase` (Room, KSP, `schemas/`), the settings DataStore,
                     `PlatformStorage`, `storageModule`/`dataModule` (di/)
interval-platform    `CuePlayer`, `WorkoutControls`, `BackgroundWorkout`, `PlanReminders` (+ `reminderWords`),
                     `SpeechQueue`, `playCue`, and each platform's `rememberPlatformModule` with its storage,
                     player, `WorkoutService` and `PlanReminderReceiver` (android)
interval-testing     `FakeStorage` and the fake DAOs, `RecordingCuePlayer`, `RecordingBackgroundWorkout`,
                     `RecordingPlanReminders`, `fakePlatformModule`/`fakeStorageModule`, the presets tests lean on
interval-ui          the workout's shared words and widgets (`ConfigText`, `FieldRow`, `ValueSheet`, `Timeline`,
                     `WeekdayForm`, `ActivitySheet`, `StartButton`, `DescribeHint`), `PhaseColorsOf`, the intro
                     (splash/: `WithIntro`, `IntervalMark`)
interval-presets     the Presets page, sheet and editor (`PresetEditorHost`, `PresetDraft`)
interval-home        the Timer tab and its describe sheet
interval-workout     the running workout, its readout, controls and summary
interval-settings    Settings and the weight sheet
interval-history     History (log, calendar, summaries, a finished workout) and Plan: one module, each needs
                     the other; `PlanReminderScheduler` (`historyModule`, di/) keeps the platform's reminders
                     in step with the plan
shared               the app: `App.kt` (the back stack and every entry), `di/AppKoin.kt` (`appModules`,
                     `rememberAppKoin`, and the `IntervalActivity` Lift reads, over `IntervalRepository`),
                     `nav/Routes.kt` (the app's own keys), `ui/AppNavigation.kt`, about/,
                     `app_name` and `app_icon`. What `androidApp`, `iosApp` and `desktopApp` depend on

bells-domain         Lift's pure domain (`dev.justinsmith.bells.domain`): exercises, programs, sets and
                     sessions, records, `EmomClock`/`HoldClock`/`RestClock`, suggestions. No dependencies
bells-data           `BellsRepository`, `BellsDatabase` (Room, KSP, `schemas/`), `IntervalActivity` (what Lift
                     reads of the interval side; `shared` binds it), `LiftActivity` (what the interval side reads
                     of Lift; `BellsRepository` is it), `bellsModules` (di/). No Compose
bells-platform       `Beeper`, `RestAlerts` (+ Android's `RestService`), the rest notification's words, and each
                     platform's `rememberBellsPlatformModule` (the database builder too); Lift's `interval-platform`
bells-testing        `FakeBellsStorage` and the fake DAOs, `FakeIntervalActivity`, `RecordingBeeper`,
                     `bellsTestKoin`
bells-ui             Lift's words and widgets (`exerciseName`, `setLine`, `programName`, `LoadPicker`, `Stepper`,
                     `SessionStats`), the built-ins' cues (`cues.xml`, mapped in `ExerciseCues.kt`); most of
                     Lift's strings
bells-library        the exercise library, each exercise's page and editor, the picker
bells-session        the session (log sheet, EMOM, hold and rest timers, plan sheets, summary), the programs
                     and their editor and progress (they share the block editor), a finished session's page
bells-today          the Lift tab and the gear (bells, bar, bands, records)
bells                Lift's entry point: its navigation keys (`nav/LiftRoutes.kt`) and the entry for each
                     (`liftEntries`), which `App` registers and composes; its flow tests drive those entries

androidApp           Android host application: AndroidManifest.xml, res/, launch theme, ProGuard rules, keystore
desktopApp           Desktop host application: main.kt entry point, Swing integration, window sizing
iosApp               iOS host application: ViewController.kt, iosApp.swift, Xcode project, Info.plist, Assets
detekt-plugin        Toolchain task plugin: runs detekt with Compose rules across modules (`./kotlin check detekt`)
lint-plugin          Toolchain task plugin: runs ktlint checks and auto-formatting (`./kotlin check lint`, `./kotlin do format`)
```

Dependencies point down the list: a feature depends on its side's `*-ui`, `*-data` and `*-domain` (through
`exported`), `shared` on every feature and `bells`, the interval side on `bells-data`/`bells-domain`/`bells-ui`
where History and the workout count Lift's sessions (their ViewModels take `LiftActivity`, never the whole
`BellsRepository`), and `bells` never on anything of the interval side. The
`*-testing` modules are ordinary libraries listed under `test-dependencies` (the toolchain shares no test
sources between modules); a data module's tests may depend on its side's `*-testing` even though that depends
back on it.

Versions live in `libs.versions.toml` (`$libs.a.b.c` = alias `a-b-c`). Compose versions come from the
toolchain (`$compose.*`).

## Conventions

- **Navigation 3**, one back stack in `shared/src/App.kt`. Adding a screen takes three edits:
  1. `@Serializable data object/class X : NavKey` in `shared/src/nav/Routes.kt` (a Lift page: `bells/src/nav/LiftRoutes.kt`)
  2. `subclass(X::class)` in `navConfiguration` (App.kt; a Lift key in `liftRoutes()`). Without it saving the
     back stack fails on iOS/desktop.
  3. `entry<X> { XRoute() }` in the `entryProvider` (App.kt; a Lift page in `liftEntries`, `bells/src/nav/LiftEntries.kt`)
  A tab implements core's `Tab` (`core/src/nav/Tab.kt`, with the `push` helper every entry uses).
- **Screens** are split into `XRoute()` (resolves the ViewModel via `currentKoinScope()` + `viewModel { }`,
  collects state) and a stateless `XScreen(state, callbacks)` that previews and tests can drive directly.
- **Page layouts**: a pushed page that scrolls is a `ScrollPage` (Back is `BackAction`), a tab of cards a
  `TabGrid`. Any screen that saves or edits a preset opens it through `PresetEditorHost`
  (`PresetDraft.saveCurrent(config)` for the settings on the Timer).
  The navigation bar or rail is an overlay (`NavigationAreaBar`, `core/src/ui/NavigationArea.kt`) that slides
  away as a page is pushed over a tab; a tab's scene reserves its room through `NavigationArea.tabScenes()`, so a
  tab screen pads nothing itself and a pushed page pads `safeDrawing`.
- **ViewModels** expose `StateFlow<UiState>` from a private `MutableStateFlow`. They are entry-scoped by
  `rememberViewModelStoreNavEntryDecorator`.
- **DI is Koin DSL** (no annotations/KSP) in an isolated container assembled in `shared/src/di/AppKoin.kt`
  (`appModules`) from each side's modules: `storageModule`/`dataModule` in `interval-data/src/di/`,
  `historyModule` in `interval-history/src/di/`, `bellsModules` in `bells-data/src/di/`, and the platform
  modules each platform builds in composition (`interval-platform`, `bells-platform`). Add a
  module per domain. Constructor-inject everything; keep platform calls (the clock, cues, storage) behind
  injected interfaces (`WallClock`, `CuePlayer`, `PlatformStorage`) so tests can fake them.
- **Storage**: `IntervalRepository` (`interval-data/src/data/`) is the only thing that touches it. What it stores
  is described by plain models in `interval-domain/src/model/` (`AppSettings`, `HistoryEntry`, `PlannedWorkout`,
  `LiftWorkout`, `LoggedWorkout`); Room entities and DataStore keys stay in `data/`, behind its mappers. Settings live
  in DataStore Preferences (`SettingsPreferences.kt`); history and custom presets in Room
  (`AppDatabase.kt`, bundled SQLite, generated by KSP for every platform). Settings are held in memory and
  written through in the background, so change them with `updateX { transform }`. `AppContent` draws
  nothing until `repository.ready`. The DataStore lives for the process (one per file, DataStore's rule),
  not the Koin container, which Android rebuilds whenever the Activity reopens. A schema change bumps the `@Database` version, adds a migration, and
  commits the new `interval-data/schemas/` (or `bells-data/schemas/`) JSON.
- **Text**: every user-facing string lives in the `composeResources/values/strings.xml` of the module whose
  screens say it, under its own `Res` (`settings.compose.resources.packageName` is
  `dev.justinsmith.<module, dots for dashes>.resources`; `exposedAccessors` only where another module reads it).
  A key two modules say is in both files. ViewModels and
  domain code expose typed data (enums, sealed types like `NextUp`/`HistoryDay`, numbers, clock strings
  like `"0:30"`); screens put it into words with `stringResource`/`pluralStringResource`. Spoken cues are
  the one non-composable path: `CuePlayer.speak(Speech)`, worded by `SpeechQueue` with `getString`
  (`interval-platform`). Preset
  names are stored data, not labels: the built-ins' names live in `builtInPresets`. Never load a resource
  from a common `test/`: common tests also run as Android local tests.
- **Theme**: `IntervalTheme` in `core/src/ui/Theme.kt` (`PhaseColors.of(phase)` in `interval-ui/src/ui/PhaseColorsOf.kt`). Fixed tokens (`Spacing`, `Radius`, `Sizes`, `Opacity`,
  `Motion`) in `Tokens.kt`; never write a raw dp, duration or scale in a screen. The one exception is the
  intro's sizes in `splash/Splash.kt`, which follow the platform launch screens.
- **Motion**: durations are `Motion.*` tokens, easings and springs live in `core/src/ui/Motion.kt` with the
  shared moves: `pressScale` on anything tappable (`surfaceButton` adds the clip, fill and tap),
  `rememberEntrance` + `enterRising` for content that arrives, `RollingText` for a value that changes
  in place, `animateColorAsState` for a control following its state. Page transitions are `pageTransition`/`pagePopTransition` (`Navigation.kt`);
  iOS keeps its native slide (`platformPageTransitions`). Looping, decorative motion (lanes, comets,
  the paused hint's breathing) checks `prefersReducedMotion()`; one-shot animations need not, Android
  scales them with the system setting. Haptics go through `LocalHapticFeedback` (steppers tick,
  picks and toggles confirm), never a platform call.
- **Icons and launch**: every app icon and the iOS launch image are generated by
  `uv run --with pillow tools/generate_icons.py`; edit the script, not its outputs. The animated intro
  (`interval-ui/src/splash/`) draws the same mark in code (`IntervalMark.kt`, keep its numbers in step) and
  opens on exactly Android's splash and iOS's launch screen, so the hand-off is seamless.
- **Platform code**: `expect`/`actual` in `src/` + `src@<platform>/`. Prefer an interface injected via Koin
  over `expect class`.
- **The workout off screen**: `WorkoutViewModel` ticks from `realElapsedTime` (counts through sleep) and
  `WorkoutEngine.tick` takes a step of any length, so time away is caught up, never capped. Its course is
  saved as `WorkoutProgress` in the repository at each change and restored by the `Workout` route's id. On
  Android `BackgroundWorkout` starts `WorkoutService` (`interval-platform/src@android/platform/`, declared in
  `androidApp`'s manifest as `specialUse`), whose notification's Pause and End drive the ViewModel through
  `WorkoutControls`. iOS schedules a local notification per phase change ahead (`WorkoutEngine.upcomingChanges`,
  pure, through `WorkoutControls.upcomingChanges`) as the app leaves the screen, and drops them on return; its
  actual says why not background audio. Desktop keeps nothing.
  Lift's rest between sets goes the same way through `RestAlerts` (`bells-platform/src/platform/`): Android's
  `RestService` holds the CPU awake and counts the rest down in a notification while the session's own clock
  beeps; iOS schedules one notification for the rest's end; desktop nothing. The notification helpers both
  services share are `core-platform/src@android/platform/Notifications.kt`.
- **Reminders for the plan**: `AppSettings.reminderMinuteOfDay` (set on Plan, null is off). `PlanReminderScheduler`
  (`interval-history/src/plan/`, an eager single) recomputes `plannedReminders` (the next 14 days with a plan
  still open, from `TrainingLog.upNext`) on every change and at midnight, and hands the whole set to
  `PlanReminders.schedule`, which replaces what is pending: Android arms one inexact alarm per day
  (`PlanAlarms`, recorded in a DataStore of its own so `PlanReminderReceiver` can re-arm after a reboot or clock
  change), iOS one calendar-triggered local notification per day (the workout's notifications remove only their
  own identifiers, and leave `PLAN_REMINDER_DAYS` of the 64 pending slots free). Words come from
  `reminderWords`, the same on both. `AskToShowNotifications()` asks for the permission on Plan while it is on.

## Testing

Strategy, commands and limits: [docs/testing.md](docs/testing.md).
- A test lives in the module of what it tests: logic and ViewModel tests in its `test/`, which runs on JVM,
  Android and iOS. Kotlin/Native rejects
  a backticked test name with a comma in it ("name contains illegal characters"), and only the full
  `./kotlin build` compiles the iOS tests, so keep commas out of common test names.
- **Compose UI tests go in `test@jvm/` only.** Common tests also run as Android local tests,
  where `runComposeUiTest` fails (no Robolectric). A screen test drives its stateless `XScreen` in the
  feature's module; Lift's flows (`bellsAppTest`) live in `bells/test@jvm/`, the app's (`appTest`, over
  `AppContent(testKoin())`) in `shared/test@jvm/`.
- **Acceptance tests (ATDD)**: every journey that needs no device has a spec, the interval side's in
  `shared/test@jvm/acceptance/` (`acceptanceScenario`, `IntervalAcceptanceDriver`), Lift's in
  `bells/test@jvm/acceptance/` (`liftScenario`, `LiftAcceptanceDriver`). A user-facing change starts with
  a failing spec or step (outer loop), then unit tests drive the logic (inner loop); a change to a
  journey's behavior updates its spec and its `.xml` together. Driver rules: docs/testing.md.
- Use `androidx.compose.ui.test.v2.*` and the harness in `core-testing/src@jvm/ui/UiTestHarness.kt`:
  `runUiTest(Window.X)`, `setThemedContent`, `waitForText`, and `StateRestoration`. Desktop's
  `StateRestorationTester` is not implemented.
- Fakes live in the `*-testing` modules (`core-testing`, `interval-testing`, `bells-testing`), ordinary
  libraries a module lists under `test-dependencies`; `testKoin()` in `shared/test/fakes/`. Never load
  the real platform module in tests.
- On-device end-to-end flows are android-CLI journeys in `journeys/*.xml`, run by an agent on
  `amper-37`, not in CI.
- **CI's iOS tests are tiered**: a push links only the quick shard (line 1 of `tools/ios-test-shards.txt`,
  the pure domains); every shard runs nightly, on tags, on `gh workflow run CI --ref <bookmark>`, and on a
  push touching `src@ios/` or the build (`tools/ios_test_plan.py`). So a native-only failure in a feature
  module's common tests can surface a night late: run `./kotlin build` before calling work done.

## Gotchas

- **`allWarningsAsErrors` is on everywhere.** Opt in per file (`@file:OptIn(...)`), **never** with module-wide
  `optIns`. Since toolchain 0.13 a module's flags reach the Kotlin/Native cache build of every dependency, and
  an unresolved marker there fails the iOS link. Only a full `./kotlin build` catches it; `-p jvm` passes.
- **Moving files between modules can leave the Kotlin/Native incremental cache stale** ("File … is not found
  in cache … per-file-cache" on an iOS compile). Delete `build/tasks/*/kotlin-native-ic-cache` and build again;
  not `./kotlin clean`.
- **A sealed type can't be extended from another module**: `Tab` is a plain interface for that reason. An
  `internal` declaration another module needs becomes public.
- **`-Xexpect-actual-classes` is on in the data modules** (through `room-lib`) for Room's KSP-generated
  `actual object XDatabaseConstructor`; without it the Beta warning fails the build.
- **Room's `sqlite-bundled` is pinned separately**: keep it at the version `room-runtime` resolves
  (`./kotlin show dependencies -m interval-data`) when bumping Room.
- **`kotlin.test.BeforeTest` in a library's main sources** (core-testing's `MainDispatcherTest`) needs
  `$kotlin.test.junit5` on the JVM and Android, not just `$kotlin.test`.
- **Desktop needs `kotlinx-coroutines-swing`** (already in `desktopApp`). Without it `Dispatchers.Main` is
  missing and `viewModelScope` crashes at runtime.
- **detekt is 2.0 alpha** (`dev.detekt`), and `compose-rules` is built against one exact detekt version: bump
  the two together. `detekt.yml` holds only overrides of detekt's defaults and is validated, so an unknown
  key fails the check. Rules run without type resolution (`--analysis-mode light`). Suppress with
  `@Suppress("detekt.RuleName")`. HTML/SARIF reports land in `build/tasks/_<module>_detekt@detekt-plugin/`.
  Its package rule takes `dev.justinsmith.interval` as the root and excludes `bells*`.
- **The lint plugin needs `slf4j-nop`**: ktlint's rules bind kotlin-logging to slf4j at class-init time.
- `./kotlin clean` deletes the tracked `iosApp/module.xcodeproj/xcshareddata/xcschemes/app.xcscheme`; the next
  iOS build fails once, regenerates it unchanged, then passes.
- iOS `ViewController()` is PascalCase on purpose (Swift calls it by that name) and is suppressed for ktlint.

## Version control: jj, colocated with git

`.jj/` sits beside `.git/`. Use `jj` (`jj st`, `jj describe`, `jj new`, `jj git push`). A detached git HEAD
is normal; don't "fix" it or create git branches.
