# interval — Claude Code Guide

Compose Multiplatform app for **Android, iOS and desktop (JVM)**, built with the **Kotlin Toolchain**
(`./kotlin`, Amper-based, version pinned in the `kotlin` wrapper). Docs: https://kotlin-toolchain.org/0.13/
Full architecture guide: [docs/architecture.md](docs/architecture.md). The current sprint: [docs/sprint-0.6.md](docs/sprint-0.6.md);
what no sprint holds yet, and why: [docs/backlog.md](docs/backlog.md). Finished sprints and reviews: `docs/archive/`.

> **NO GRADLE.** There is no `gradlew`, `build.gradle.kts` or `settings.gradle.kts`. Never run or suggest
> Gradle commands. Build configuration lives in `project.yaml` and each module's `module.yaml`.

## Commands

```sh
./kotlin build                      # Everything. The only build that links iOS. Once per piece of work, before
                                    # calling it done; never per iteration
./kotlin build -p jvm               # Fast compile loop (common + JVM)
./kotlin test -m lift-session -p jvm # One module's tests (a feature's, a domain's, Lift's flows in `lift-nav`)
./kotlin test -m lift-domain -p jvm --include-classes 'dev.justinsmith.lift.domain.DensityTest'
                                    # One class (or a `*` pattern); the loop while iterating, about 12 s
./kotlin test -p jvm --exclude-classes '*AcceptanceTest'   # Every module's tests but the journey specs
./kotlin test -p jvm                # Everything on the JVM (logic, Compose UI, flows, acceptance): before a commit
./kotlin check lint                 # ktlint over every module
./kotlin do format                  # ktlint auto-format
./kotlin check detekt               # detekt + Compose rules over every module (config: detekt.yml)
./kotlin check lint detekt          # ktlint + detekt, before a commit
./kotlin test -p android -m <module>    # Only when `src@android/` or an Android dependency changed; CI runs it all
./kotlin test -p iosSimulatorArm64 -m interval-domain   # iOS tests (Debug) for the modules you touched
./kotlin check                      # All checks; avoid locally: it also links every module's iOS tests
                                    # in Release (~12 min a link, 19 at once), which CI never runs
./kotlin run -m desktopApp --compose-hot-reload   # Desktop window that reloads on every save
./kotlin package -m androidApp -f aab   # Release bundle (R8 on; rules in apps/androidApp/proguard-rules.pro).
                                        # Unsigned until a keystore is set up (`./kotlin tool generate-keystore`)
python3 tools/check_versions.py         # Verify Android and iOS version parity (run in CI)
python3 tools/check_ios_shards.py       # Every module with common tests is on one line of ios-test-shards.txt (run in CI)
python3 tools/check_module_rules.py     # Every module dependency keeps the layering below, and docs/architecture.md's
                                        # diagram draws the modules as they are (run in CI); --write-diagram redraws it
uv run --with pillow tools/generate_icons.py  # Regenerate app icons and launch screens from the vector mark
./kotlin show modules | settings | dependencies | tasks
```

`-m` takes the **module name**, the directory's (`core`, `interval-history`, `lift-session`, `androidApp`, …;
`./kotlin show modules` lists them).

Running on the Android emulator or iOS simulator: the `run-on-device` skill.

**UI and UX work: reach for Compose Hot Reload first.** Run the desktop app with `--compose-hot-reload` (from
your own jj workspace) and leave it up while you edit: each save recompiles and swaps the code in place, so a
layout or motion change shows in seconds without losing the screen you are on. Size the window to the form
factor you are checking (`InitialSize` in `apps/desktopApp/src/main.kt`, reverted before committing). To see the
window yourself you need either a computer-use grant for the running app (`MainKt`, `com.jetbrains.jbr.java`)
or the toolchain's `./kotlin compose-hot-reload-mcp-server` registered as an MCP server. Without either, render
the screen from a throwaway `test@jvm` test (`onRoot().captureToImage()` to a PNG at a harness `Window`) and
delete the test after. The emulator is for what only a device shows: insets, the keyboard, system bars.

## Layout

Amper source layout, not Gradle's:
- `<module>/src/` common code; `src@android/`, `src@ios/` (both iOS targets), `src@jvm/` platform code
- `<module>/test/` common tests; `test@jvm/` etc. for platform tests
- `apps/androidApp/res/` Android resources; `<module>/composeResources/` a module's multiplatform resources (`Res.*`)

**Never create `src/commonMain/kotlin/`.** Amper ignores it silently.

### Modules

The module is the toolchain's unit of work: each has its own compile, KSP, detekt, lint and test tasks, rerun
only when something in it changes. The app is split along its layers so a screen edit recompiles that
feature and the shell, Room's KSP runs only when storage changes, and the pure domains compile and test with
nothing else on the classpath. Every library applies a template from `templates/` (`lib`: warnings as errors,
lint, detekt; `tested-lib`: + the test libraries and `core-testing`; `compose-lib`: + Compose, lifecycle,
Koin, and `settings.compose`; `room-lib`: + Room, the bundled driver, `-Xexpect-actual-classes`, JSON).
Modules sit in a folder per side, one level deep: `core/`, `interval/`, `lift/`, `apps/` and `tooling/`. A
module's name is its own folder's, unique across the project, so `-m` takes it (`-m lift-data`). A dependency is
its path from the root (`//lift/lift-data`), and `project.yaml` lists every path. `./kotlin show modules` gives
each one's `description:`. Lift's code is in the `dev.justinsmith.lift` packages (`LiftRepository`,
`LiftDatabase`, `liftAppTest`). It was called Bells, which still shows in three places, each kept on purpose:
the database file (`bells.db`), the serial names that its route keys are saved under (`@SerialName`, in
`LiftRoutes.kt`) and the schema history in `LiftDatabase`.

```
core/
  core               the theme, tokens, motion, components, lanes, `Tab` and `push` (nav/), the Composable
                     platform hooks the UI needs (`SystemServices.kt`), and each platform's `corePlatformModule`
                     (`CorePlatform*.kt`: the `WallClock`, `IdGenerator`, `TimestampFormatter`, `AppInfo`, `Sharer`
                     and `HealthStore` both sides' containers take, with each platform's `Sharer`) and
                     `rememberHealthConnector` (the store's permission sheet, for Settings); exports `core-platform`
                     and `core-time`.
                     Packages `dev.justinsmith.interval.ui`, `.platform`, `.nav`; resources
                     `dev.justinsmith.core.resources`
  core-platform      what the platform gives that isn't drawing, with no Compose: the clocks (`WallClock`,
                     `epochMillisNow`, `realElapsedTime`, `days`), `IdGenerator` (`Ids.kt`), `TimestampFormatter`,
                     `AppInfo`, `Sharer`, `Speaker` (the device's voice, both sides', and `SpokenLines`, which
                     words a side's lines one at a time), `ToneSynth` and `TonePlayer` (the app's six tones,
                     both sides', and each platform's player of them), `HealthStore` (`Health.kt`: the workout both sides hand it as they finish;
                     Android's is
                     `HealthConnectStore`, in src@android, writes workouts and reads the weight and the heart rate; iOS's is
                     `HealthKitStore`, Apple Health; desktop's `NoHealthStore`),
                     Android's notification helpers, and `storage/Storage.kt` (`SerialWriter`, `storageScope`,
                     `orOnError`/`orEmptyOnError`); packages `.platform`, `.storage`; exports `core-time`. The
                     data modules depend on this, not `core`, so a theme edit neither recompiles them nor reruns
                     their Room KSP (interval-data's test KSP still does, through `interval-testing`)
  core-time          calendar/clock maths (`CalendarDays`, `formatClock`, `WeekStanding`), package `.timer`. No
                     dependencies, so the domains compile without Compose
  core-testing       `MainDispatcherTest`, `FixedTimestampFormatter`, `FakeAppInfo`, `RecordingSharer`,
                     `RecordingHealthStore`, `fakeCorePlatformModule`, the tests' calendar;
                     src@jvm: the UI harness (`runUiTest`, `setThemedContent`, `StateRestoration`, `waitForText`)

interval/            the interval timer
  interval-domain    packages `.timer` and `.model`: `IntervalConfig` (its `blocks` of rounds,
                     `Block`), `Preset`, `Course` (every interval a config runs through, by block, set and
                     round, which `WorkoutEngine` walks), calories, the
                     describe parser, `AppSettings`, `HistoryEntry` (its `SnatchCount`, 15:15's snatches as
                     counted, and `workEnds`), `PlannedWorkout`, `LiftWorkout`,
                     `WorkoutProgress`, `Sport` (a class or a day on the wall, trained away from the app: planned as
                     `PlanWorkout.SportDay`, logged as a `LoggedSport`), `Flow` (a mobility session followed along,
                     `Flow.kt`, and the five shipped, `BuiltInFlows.kt`); over `core-time` alone, neither `core` nor
                     anything of Lift's (History makes a `LiftWorkout` from a Lift session, `asLiftWorkouts` in
                     interval-history)
  interval-data      `IntervalRepository`, the root of a repository per store (`SettingsRepository`, the
                     DataStore's settings and the workout under way; `PresetRepository`, `HistoryRepository`,
                     `PlanRepository`, over one `SerialWriter`; the mappers in `IntervalMappers.kt`), `AppDatabase`
                     (Room, KSP, `schemas/`), the settings DataStore, `PlatformStorage`, `storageModule`/`dataModule`
                     (di/)
  interval-platform-api  what the features ask of the platform, over `interval-domain` and no Compose:
                     `CuePlayer` (`Beep`, `Speech`, `Haptic`), `playCue`, `ActiveWorkout`, `BackgroundWorkout`,
                     `WorkoutStatus`, `StatusColors` (the notification's ARGB), `PlanReminders`, `PlanReminderSource`
  interval-platform  implements it: `reminderWords`, `SpeechQueue`, `StatusBar`, and each platform's
                     `platformModule` with its storage, player, `WorkoutService` and `PlanReminderReceiver`
                     (android). Over `core-platform`, not `core`; only `app` depends on it
  interval-ui        the workout's shared words and widgets (`ConfigText`, `FieldRow`, `ValueSheet`, `Timeline`,
                     `WeekdayForm`, `WeekdayPicker`, `ActivitySheet`, `StartButton`, `DescribeHint`, `SnatchQuestion`:
                     "About how many snatches a set?", the summary's and the Log page's, over `lift-domain`'s bell
                     sizes), a flow's moves shown by Lift's figure (`FlowMoves`, `MoveDetail`, `MoveFigureBand`,
                     `Move.demo`, over `lift-ui`), `PhaseColorsOf`, the intro (splash/: `WithIntro`, `IntervalMark`)
  interval-testing   `FakeStorage` and the fake DAOs, `RecordingCuePlayer`, `RecordingBackgroundWorkout`,
                     `RecordingPlanReminders`, `fakePlatformModule`/`fakeStorageModule`, the presets tests lean on
  interval-timer     the Timer, a page pushed over Train's Conditioning pillar, and its describe sheet (package
                     `dev.justinsmith.interval.home`)
  interval-workout   the running workout, its readout, controls and summary; a flow's move's figure over the ring
  interval-presets   the presets, sheet and editor (`PresetEditorHost`, `PresetDraft`): Train's Conditioning
                     pillar, a tab's page (`PresetsTab`), with the row that opens the Timer, saying what is on it
  interval-mobility  the flows by when each is for, a flow's sheet (its moves, then Start), and the editor of the
                     user's own (`FlowEditorScreen`, the library's moves in `LibraryMoves.kt`, over `lift-ui`'s names
                     and cues): Train's Mobility pillar, a tab's page (`MobilityPage`)
  interval-settings  Settings (its groups in `SettingsGroups.kt`), the weight sheet, export, import and clearing the log
                     (`DataExporter`, `DataImporter`, the review sheet); opened by every tab's gear
  interval-history   History (titled "Log" on screen; the code keeps History) (log, calendar, summaries, a finished
                     workout) and Plan: one module, each needs
                     the other; `PlanReminderScheduler` (`historyModule`, di/) keeps the platform's reminders
                     in step with the plan. And Today, the first tab (`today/`: `HomeRoute`, `HomeViewModel`,
                     named Home since Lift's tab is `TodayRoute`): the week, what is up next and the presets that
                     fit a few minutes, with the flow that does (`QuickPicks.kt`), over the same log (`history/UpNext.kt`)

lift/                Lift, the strength side (packages `dev.justinsmith.lift.*`)
  lift-domain        Lift's pure domain (`dev.justinsmith.lift.domain`): exercises, programs, sets and
                     sessions, a set's `Load` (sealed: bell, bar, bodyweight, band, mat) and a block's (null
                     for none named; `Load.named`, `Block.withLoad`), records,
                     `EmomClock`/`HoldClock`/`RestClock`/`DensityClock` (a density block, `BlockKind.Density`:
                     as many rounds as fit a cap, planned as its first round, which takes every round logged within
                     the cap, `plannedRounds`, and steps a rep a round by `densityStep`; the ABC's two protocols, `AbcProtocol`), suggestions, the ladder past fixed bell sizes (`Progression.kt`'s `rungOf`: reps, denser, more, a harder lift
                     at the same bell from `Variations.kt`, then the next bell back at the block's `origin`, by how big
                     the jump is), about how long a program's day takes
                     (`Estimate.kt`, from its sets, each lift's pace and the rest between sets), the program to
                     start someone on with one bell (`StarterProgram.kt`). The coaching (docs/kettlebell-coaching.md):
                     how each lift moves (`Movement.kt`: pattern, tempo ballistic/grind/hold, grip, lifts in a rep),
                     the faults it is prone to, from that (`Faults.kt`, which the rests say), the grip's load (`Grip.kt`, BJJ days in it) and how ready the lifter is (`Readiness.kt`), a
                     taxed day's easier plan (`Autoregulate.kt`), the safety lines (`Safety.kt`: `SafetyLine`, and
                     `firstAtBell`, a thrown lift's first set at a bell new to it), density and pace (`Density.kt`, only from sets
                     logged as done) and the challenges and totals (`Benchmarks.kt`), all worked out from sets
                     logged, nothing stored. No dependencies
  lift-api           the ports between the sides, package `dev.justinsmith.lift.data`: `IntervalActivity` (what
                     Lift reads of the interval side, the days a sport was logged on among it, each a `SportDay`
                     saying whether it was grappling, each workout's snatches as counted (`IntervalSnatches`, read as
                     sets by `snatchSets`), and the body weight; `app` binds it),
                     `LiftActivity` (what the interval side reads of Lift, the bells owned among it;
                     `SessionRepository` is it). Over
                     `lift-domain` alone
  lift-data          `LiftRepository`, the root of a repository per store (`SessionRepository`, `SetRepository`,
                     `ProgramRepository`, `ExerciseRepository`, `GearRepository`, over one `SerialWriter`; the
                     mappers in `LiftMappers.kt`), `LiftDatabase` (Room, KSP, `schemas/`), `liftModules` (di/);
                     exports `lift-api`. No Compose
  lift-platform      `Beeper`, `LiftVoice` (the timers' typed `Coaching` lines, the safety lines among them in place of a set-up cue; `SpokenCoaching` in lift-session
                     words them, from lift-ui's `cues()` and each `Fault`'s `words()`), `SessionKeepAlive` and its
                     `TimerRun` (+ Android's `TimerService`), the `ActiveTimers` port, the timers' notification
                     words, and each platform's `liftPlatformModule` (the database builder too); Lift's
                     `interval-platform`
  lift-ui            Lift's words and widgets (`exerciseName`, `setLine`, `programName`, `LoadPicker`, `Stepper`,
                     `SessionStats`, `BalanceBars`), the built-ins' cues (`cues.xml`, mapped in `ExerciseCues.kt`), the
                     figure every built-in is shown by (`demo/`: `ExerciseFigure`, an `ExerciseDemo` a lift through
                     `Exercise.demo`, `rememberDemoPlayer`, `DemoTimeline`, and `Celebration`, the raised bell or bow
                     a figure on its feet gives a record, a one-rep demo from where it stood), and `Demonstration`,
                     the card of the figure over its timeline and cues lit in turn, which the exercise page and a
                     flow's move both show; most of Lift's strings
  lift-testing       `FakeLiftStorage` and the fake DAOs, `FakeIntervalActivity`, `RecordingBeeper`,
                     `RecordingKeepAlive`, `LiftTestClock` (the wall clock and the timers' time, moved together),
                     `liftTestKoin`
  lift-today         Train's Strength pillar, Lift's tab (`TodayRoute`, in groups: Following, the program's day
                     and how far through it; Beside the mat and the wall, `suggestedPrograms`, each opening the
                     `ProgramSheet` key; On your own; Lately, the last session and the balance, `MuscleBalance` in
                     lift-domain) and the gear (bells, bar, bands, records)
  lift-session       the session (log sheet, EMOM, hold, density and rest timers under `SessionTimers`, plan sheets,
                     summary; a free one takes a day of any program from `ProgramDaySheet`), the programs
                     and their editor and progress (they share the block editor), a finished session's page
  lift-library       the exercise library, each exercise's page and editor, the picker
  lift-nav           Lift's entry point: its navigation keys (`nav/LiftRoutes.kt`) and the entry for each
                     (`liftEntries`), which `App` registers and composes; its flow tests drive those entries

apps/
  app                the app: `App.kt` (the back stack and every entry), `BackStack.kt` (what the app does to
                     it: `selectTab`, `showTimer`, a shortcut's `open`, the fix-ups for a stack an older build saved), `di/AppKoin.kt` (`appModules`,
                     `initAppKoin`, the `IntervalActivity` Lift reads, over `IntervalRepository`, and the
                     `WeekStandingSource` the widget shows, over both sides' stores; `data/`),
                     `nav/Routes.kt` (the app's own keys), `ui/AppNavigation.kt`, about/, export/, weeksetup/
                     (Set up, the one page that writes both sides' stores, so the one feature the app holds),
                     `app_name` and `app_icon`. What `androidApp`, `iosApp` and `desktopApp` depend on
  androidApp         Android host application: `IntervalApplication` (starts the container, and keeps the widget
                     drawn), AndroidManifest.xml, res/, launch theme, ProGuard rules, keystore, `ShortcutActivity`,
                     and the home-screen widget (`widget/`: Glance's `WeekWidget` and its receiver, the ring drawn
                     on a bitmap in `PhaseColors`, `WidgetSizes`, `WidgetDayAlarm` for the day's turn, `WidgetPreview`
                     publishing the picker's preview once a version on Android 15+; its words in
                     `res/values/strings.xml`)
  desktopApp         Desktop host application: main.kt entry point, Swing integration, window sizing
  iosApp             iOS host application: ViewController.kt (and `openShortcut`, the quick actions' and the widget's
                     links'), iosApp.swift, Xcode project, Info.plist (the quick actions, as `UIApplicationShortcutItems`;
                     the `interval://` scheme), Assets, and the Home Screen and Lock Screen widget:
                     `widget/WeekWidget.swift`, a Swift-only WidgetKit target beside `app` (the toolchain allows one
                     target with its Kotlin phase), fed the week by
                     `WidgetFeed.kt` as a `WeekSnapshot` (app's `data/`) JSON in the App Group's defaults

tooling/
  detekt-plugin      Toolchain task plugin: runs detekt with Compose rules across modules (`./kotlin check detekt`)
  lint-plugin        Toolchain task plugin: runs ktlint checks and auto-formatting (`./kotlin check lint`, `./kotlin do format`)
```
Dependencies point toward `core/`: a feature depends on its side's `*-ui`, `*-data` and `*-domain` (through
`exported`), `app` on every feature and `lift-nav`, the interval side on `lift-api`/`lift-domain`/`lift-ui`
where History and the workout count Lift's sessions (their ViewModels take `LiftActivity`, never Lift's
storage) and `interval-ui` on `lift-ui` for the figure (a side's shared ui may take the other side's), the
interval features on `interval-platform-api` (only `app` sees `interval-platform`), and `lift-nav`
never on anything of the interval side. `tools/check_module_rules.py`
enforces this in CI: `lift/` never sees `interval/`, the pure layers (`core-time`, `core-platform`, the domains,
the `*-api` modules and the data modules) take no Compose, an `*-api` module depends on a domain alone, a
feature never depends on a platform module that has an `*-api`, the data modules depend on `core-platform` rather than `core`, the
`*-testing` modules appear only under `test-dependencies`, a feature depends on another only where its
`FEATURE_EDGES` lists the pair (`interval-timer` and `interval-workout` on `interval-presets`, `lift-session` on
`lift-library`), every library applies a template, and no source under `interval/` names `LiftRepository`. The
`*-testing` modules are ordinary libraries listed under `test-dependencies` (the toolchain shares no test
sources between modules); a data module's tests may depend on its side's `*-testing` even though that depends
back on it.

Versions live in `libs.versions.toml` (`$libs.a.b.c` = alias `a-b-c`). Compose versions come from the
toolchain (`$compose.*`).

## Conventions

- **Navigation 3**, one back stack in `apps/app/src/App.kt`. Adding a screen takes three edits:
  1. `@Serializable data object/class X : NavKey` in `apps/app/src/nav/Routes.kt` (a Lift page: `lift/lift-nav/src/nav/LiftRoutes.kt`)
  2. `subclass(X::class)` in `navConfiguration` (App.kt; a Lift key in `liftRoutes()`). Without it saving the
     back stack fails on iOS/desktop.
  3. `entry<X> { XRoute() }` in the `entryProvider` (App.kt; a Lift page in `liftEntries`, `lift/lift-nav/src/nav/LiftEntries.kt`)
  `TodayTab` is the start destination and the back stack's root: Back from Train or History returns to it
  (`selectTab`), and a stack saved by a build that opened on the Timer gets it put underneath (`rootAtToday`).
  A new install's first open has Set up over it (see Set up, below).
  A tab implements core's `Tab` (`core/core/src/nav/Tab.kt`, with the `push` helper every entry uses), and its
  title bar ends in core's `SettingsAction`; Lift's takes what it opens through `liftEntries`.
  Train is one destination of the bar in three tabs, its pillars (`apps/app/src/ui/Train.kt`): Strength is Lift's
  `LiftTab`, Conditioning the app's `ConditioningTab` (the presets), Mobility its `MobilityTab` (the flows). Each
  is a `TabGrid` titled "Train" whose `header` the app fills with `PillarPicker`; their entries share a
  `tabGroup`, so moving between them fades the new one in over the old with the title and picker standing still
  (`pillarSwap`, `Navigation.kt`), and the bar reopens the pillar last shown (`rememberTrainPillar`, saved as its
  place among `TrainPillars`).
  The Timer (`Timer`, saved under its old name `TimerTab`) is a page pushed over Conditioning: everything that
  shows it goes through `showTimer` (the presets' Timer row and "Put on Timer", Today, History, the
  `start-workout` shortcut), its Back and presets square through `leaveTimer`, and a stack saved while it was a
  tab is put right by `timerUnderTrain`. On a wide window Settings still opens beside it (`settingsOverTimer`),
  each pane giving up the window's inset on the edge they share. A page that isn't a tab pads `safeDrawing`
  itself, as the Timer does.
- **Screens** are split into `XRoute()` (resolves the ViewModel with `koinViewModel<XViewModel>()`, and a
  route's key with `{ parametersOf(id) }`; anything else with `koinInject()`; collects state) and a stateless
  `XScreen(state, callbacks)` that previews and tests can drive directly. Each feature declares its ViewModels in
  its own Koin module (`<feature>/src/di/`: `timerModule`, `liftSessionModule`, …): `viewModelOf(::X)` where every
  parameter is bound, else `viewModel { (id: Long) -> X(id, get(), …) }` (leave defaulted parameters out; a
  `SavedStateHandle` is `get()`, from the entry). `appModules` lists the interval side's, `lift-nav`'s
  `liftFeatureModules` Lift's (Lift's flow tests pass them to `liftTestKoin(features = …)`).
  `VerifyAppModulesTest` (`apps/app/test@jvm/di/`) checks the whole graph: a new route parameter goes in its
  `injectedParameters`.
- **Page layouts**: a pushed page that scrolls is a `ScrollPage` (Back is `BackAction`), a tab of cards a
  `TabGrid`. Any screen that saves or edits a preset opens it through `PresetEditorHost`
  (`PresetDraft.saveCurrent(config)` for the settings on the Timer; `PresetDraft.timer(config)` shapes the Timer's
  own workout, a sequence's blocks and all, and Done puts it back on the Timer without saving a preset).
  The navigation bar or rail is an overlay (`NavigationAreaBar`, `core/core/src/ui/NavigationArea.kt`) that slides
  away as a page is pushed over a tab; a tab's scene reserves its room through `NavigationArea.tabScenes()`, so a
  tab screen pads nothing itself and a pushed page pads `safeDrawing`.
- **ViewModels** expose `StateFlow<UiState>` from a private `MutableStateFlow`. They are entry-scoped by
  `rememberViewModelStoreNavEntryDecorator`.
- **DI is Koin DSL** (no annotations/KSP), **one container per process**, assembled in `apps/app/src/di/AppKoin.kt`
  (`appModules`) from each side's modules: `storageModule`/`dataModule` in `interval/interval-data/src/di/`,
  `historyModule` in `interval/interval-history/src/di/`, `liftModules` in `lift/lift-data/src/di/`, and the three
  platform modules: core's `corePlatformModule` (what both sides take: the clock, ids, formatter, `AppInfo`, `Sharer`),
  `interval-platform`'s `platformModule` and `lift-platform`'s `liftPlatformModule`, plain functions (Android's of a
  `Context`). `initAppKoin` starts it (`startKoin`, once): Android's `IntervalApplication.onCreate`, desktop's
  `main` (which drains both repositories and stops it as the window closes), iOS's `AppDelegate` at launch
  (through `startWidgetFeed`, the widget's feed; the first `App()` otherwise, as Hot Reload's entry point has it). It is never
  closed on Android or iOS, so services and receivers can reach it. Only `apps/` may import `startKoin`,
  `stopKoin` or `GlobalContext` (detekt's `ForbiddenImport`); tests build their own with `testKoin()` and pass it to
  `AppContent`. Neither side's modules
  bind anything the other needs. Add a module per domain or feature. Constructor-inject everything; keep platform calls (the clock, cues, storage) behind
  injected interfaces (`WallClock`, `IdGenerator`, `CuePlayer`, `PlatformStorage`) so tests can fake them.
- **Storage**: `IntervalRepository` (`interval/interval-data/src/data/`) and its repositories are the only things
  that touch it. A screen takes the store it uses rather than a repository: `SettingsStore`, `PresetStore`,
  `HistoryStore`, `PlanStore`, `WorkoutProgressStore` (`Stores.kt` beside it; Lift's `SessionStore`, `SetStore`,
  `ProgramStore`, `ExerciseStore`, `GearStore` beside `LiftRepository`), each a repository of its own under its
  side's root (`repository.settings`, `repository.history`, …) and bound to it; `InMemory*Store` fakes stand in for one. What it stores
  is described by plain models in `interval/interval-domain/src/model/` (`AppSettings`, `HistoryEntry`, `PlannedWorkout`,
  `LiftWorkout`, `LoggedWorkout`, `LoggedSport`); Room entities and DataStore keys stay in `data/`, behind its mappers.
  `HistoryStore` keeps the sport days logged beside the workouts (`logSport`, one of a sport a day, on the day itself
  rather than a moment): each counts as a workout in the week wherever one is counted (`TrainingLog`, the summaries'
  `weekStanding`, the widget's `WeekStandingSource`, Lift through `IntervalActivity.sportDays`). A workout of
  kettlebell intervals that switches hands (`asksSnatches`: 15:15 MVO2, or one of the user's with the switch on) is
  asked on its summary, or later on its page in the Log, about how many snatches a set and the bell
  (`HistoryStore.countSnatches`, `SnatchCount`); Lift reads each counted one as a set of snatches a work interval
  done (`intervalSets`, `lift-domain`) beside its own sets wherever they are counted (the snatch test, reps in all,
  pace, grip), never storing them, so nothing is counted twice. A sport day is
  planned from `AddToPlanSheet`'s Sport side (`plan/PlanChoices.kt`) and worded by `plan/SportWords.kt`; the week
  strip names it under its day (`DayCell.sport`). It is logged from Today (`HomeViewModel.logSport`) or from its
  day in History, each with Undo. A workout done away from the app, or without the Timer, is logged after the fact
  from its day's sheet in the Log, today or a day gone by ("Log a workout": a preset or the Timer's workout, a
  flow, or a sport day, `LogWorkoutSheet`; a planned interval workout's row has "Log it"): `HistoryStore.logWorkout`
  stores a plain `HistoryEntry.loggedByHand` (the whole course, work estimated, the start run back from the finish,
  which with no work time is the mark, `loggedByHand`; no column) dated `TimestampFormatter.middayOf(day)`, today's
  at the moment, and `unlogWorkout(uid)` is the notice's Undo. Nothing goes to the health store, and its page in
  the Log reads no heart rate. Nothing of Lift's is logged that way. `todayAgenda` (`history/TodayAgenda.kt`) is how Today and History lead with what
  is up next: on a day with a sport still to log, the program's unplanned day stands beside the lead
  (`today/AlsoToday.kt`) rather than taking it, and with no workout the user planned themself the lead is the
  flow to do before the sport (`UpNextItem.Flow`, which `upNext` adds until mobility is done that day and History
  leaves out), then the sport. Settings live
  in DataStore Preferences (`SettingsPreferences.kt`); history and custom presets in Room
  (`AppDatabase.kt`, bundled SQLite, generated by KSP for every platform). Settings are held in memory and
  written through in the background, so change them with `updateX { transform }`. A write that fails is logged and
  published on the repository's `writeErrors`, which `AppContent` shows as a "Couldn't save" notice. `AppContent` draws
  nothing until `repository.ready`. The DataStore is a `single` on a scope of its own (`PreferencesScope`): one
  per file is DataStore's rule, which a container per process keeps. A schema change bumps the `@Database` version, adds a migration, and
  commits the new `interval/interval-data/schemas/` (or `lift/lift-data/schemas/`) JSON.
- **Text**: every user-facing string lives in the `composeResources/values/strings.xml` of the module whose
  screens say it, under its own `Res` (`settings.compose.resources.packageName` is
  `<the module's code package>.resources`, such as `dev.justinsmith.interval.history.resources` and, for
  `lift-session`, `dev.justinsmith.lift.session.resources`; `exposedAccessors` only where another module reads it).
  A key two modules say is in both files. ViewModels and
  domain code expose typed data (enums, sealed types like `NextUp`/`HistoryDay`, numbers, clock strings
  like `"0:30"`); screens put it into words with `stringResource`/`pluralStringResource`. Spoken cues are
  the one non-composable path: `CuePlayer.speak(Speech)`, worded by `SpeechQueue` with `getString`
  (`interval-platform`). The six `Beep`s, and Lift's timers' `EmomCue`s, are the app's own tones, not the
  platform's: `ToneSynth` (`core-platform`) renders each as a few sines with no click at either end, under 400 ms,
  which core's `TonePlayer`, one per process like the `Speaker`, keeps and plays (Android an `AudioTrack` a tone on
  the media stream, iOS an `AVAudioPlayer` from a WAV in memory, desktop a `Clip`). Each side says which tone a cue
  is (`Beep.tone` in `interval-platform`, `EmomCue.tone` in `lift-platform`); a new tone is a `Tone` of `Note`s in
  `ToneSynth`, and `ToneSynthTest` holds their shape. A preset that `alternateHands` (15:15 MVO2, or a kettlebell preset with the editor's
  switch on) has every work after the first said as "Switch hands. Work": `WorkoutSession` reads the flag from the
  preset the workout `matching`s as each cue plays, so nothing beside the workout stores it. Preset
  names are stored data, not labels: the built-ins' names live in `builtInPresets`. Never load a resource
  from a common `test/`: common tests also run as Android local tests.
- **Theme**: `IntervalTheme` in `core/core/src/ui/Theme.kt` (`PhaseColors.of(phase)` in `interval/interval-ui/src/ui/PhaseColorsOf.kt`). Fixed tokens (`Spacing`, `Radius`, `Sizes`, `Opacity`,
  `Motion`) in `Tokens.kt`, where `Sizes` holds what both sides draw; a size only one side uses is in its
  `IntervalSizes` (interval-ui) or `LiftSizes` (lift-ui), the app shell's in `AppSizes`. Never write a raw dp,
  duration or scale in a screen. The one exception is the
  intro's sizes in `splash/Splash.kt`, which follow the platform launch screens.
- **Motion**: durations are `Motion.*` tokens, easings and springs live in `core/core/src/ui/Motion.kt` with the
  shared moves: `pressScale` on anything tappable (`surfaceButton` adds the clip, fill and tap),
  `rememberEntrance` + `enterRising` for content that arrives, `RollingText` for a value that changes
  in place, `animateColorAsState` for a control following its state. The moments get their own (`Moments.kt`): `stampIn`
  for a tag landing (Record, with the next lift's figure raising its bell or bowing on the session card:
  `ExerciseFigure`'s `celebrating`, keyed by `AnnounceRecord` for the gesture's length), `popOnce` for a snap and
  spring back on a change (the goal ring closing, the
  streak's flame); then `countUp` (Entrance.kt) for a summary's numbers, `rememberCountdownPulse`
  (`CountdownClock.kt`) for an interval's last seconds, which the clock pops to and `ProgressRing`'s
  `strokeScale` swells to, `Burst` for rings out from a moment (a phase change from the clock, a record's
  row, the week's goal met; Start's launch, one ring of the accent out from the button over `Motion.LAUNCH_MILLIS`,
  the workout's rise, in `StartButton`), `StreakBadge` for the streak (rolling up with `grew`), `FinishHaptic` for a
  summary's landing under the thumb. Page transitions are `pageTransition`/`pagePopTransition` (`Navigation.kt`);
  iOS keeps its native slide (`platformPageTransitions`). Looping, decorative motion (lanes, comets,
  the paused hint's breathing) checks `prefersReducedMotion()`; one-shot animations need not, Android
  scales them with the system setting. Haptics go through `LocalHapticFeedback` (steppers tick,
  picks and toggles confirm, a record lands with two beats, a rest's last seconds tick), never a platform call;
  the interval workout's buzzes are cues (`Haptic.Countdown/Work/Rest/Finished` through `CuePlayer`), under
  the Vibrate switch, so they play with the screen off and never double a UI haptic. Tests record either:
  `RecordingHaptics` + `WithHaptics` (core-testing) and `RecordingCuePlayer`.
- **Icons and launch**: every app icon and the iOS launch image are generated by
  `uv run --with pillow tools/generate_icons.py`; edit the script, not its outputs. The animated intro
  (`interval/interval-ui/src/splash/`) draws the same mark in code (`IntervalMark.kt`, keep its numbers in step) and
  opens on exactly Android's splash and iOS's launch screen, so the hand-off is seamless.
- **Platform code**: `expect`/`actual` in `src/` + `src@<platform>/`. Prefer an interface injected via Koin
  over `expect class`.
- **The workout off screen**: the running workout is `WorkoutSession` (`interval-workout`, a Koin single), not
  its ViewModel, which only maps `WorkoutSession.state` (a `WorkoutRun`, pure, its changes worked out there) to
  `WorkoutUiState`. Its `WorkoutTicker` ticks from `realElapsedTime` (counts through sleep) and
  `WorkoutEngine.tick` takes a step of any length, so time away is caught up, never capped; `WorkoutRecorder`
  keeps a finished one in History and the health store. Its course is saved as `WorkoutProgress` in the repository
  at each change and restored by the `Workout` route's id (`WorkoutSession.open`). It ends when the `Workout` key
  leaves the back stack (`App.kt` calls `leave()`), or the task is swiped away, not when the screen is destroyed. On
  Android `BackgroundWorkout` starts `WorkoutService` (`interval/interval-platform/src@android/platform/`, declared in
  `androidApp`'s manifest as `specialUse`), which reaches the session as the `ActiveWorkout` port (`KoinComponent`):
  it shows its `status` and its Pause and End act on it. iOS schedules a local notification per phase change ahead
  (`WorkoutEngine.upcomingChanges`, pure, through `ActiveWorkout.upcomingChanges`) as the app leaves the screen,
  and drops them on return; its actual says why not background audio. Desktop keeps nothing.
  Lift's timers go the same way. A session is started by whatever pushes `Lifting` (`SessionStore.startSession`:
  Train's Strength pillar, History, the shortcut in `App.kt`), never by `SessionViewModel`, which only reads; its page
  restored with no session behind it closes (`sessionGone`). A session carries its program and day
  (`LiftSession.program`, `day`), and the day it is comes from them among every program (`List<Program>.dayOf`,
  `programDay` in `TodayPlanEditor.kt`), not from the one followed: so a free session may take a day of any program
  from "Do a program day" under its Next set card (`ProgramDaySheet`, `SetStore.setProgramDay`), numbered by
  `Program.pickedDayNumber` (the followed program's next day when it is that one, else the last time the day came
  round, so the program's place stays). The rest, an EMOM, a hold and a density block
  (`DensityRunner`, its page `DensityScreen`, rounds logged as they are tapped and the clock called aloud) are `SessionTimers`
  (`lift-session`, a Koin single), not `SessionViewModel`, which starts them and shows their `state`. They time by `realElapsedTime` (core's
  `corePlatformModule` binds it for both sides), tick only while one runs, beep and log as they go, and end with
  the session (finish, discard, Leave), when the `Lifting` key leaves the back stack (`App.kt`, through the
  `ActiveTimers` port) or when the task is swiped away. Whichever runs is kept off screen through `SessionKeepAlive`
  (`lift/lift-platform/src/platform/`, a `TimerRun` with its end): Android's `TimerService` holds the CPU awake and
  counts the run down in a notification while the timers beep; iOS schedules one notification for the run's end;
  desktop nothing. The run going is saved on the session in progress (`SavedTimer`, its `timer` column) at each
  change of course, and `SessionViewModel` restores it once as the session opens, by the wall clock (one over meanwhile is
  dropped, never played out); it does so from the screen because only an app on show may start the service. The
  Log sheet's draft is in the entry's `SavedStateHandle`. The notification helpers both services share are
  `core/core-platform/src@android/platform/Notifications.kt`.
- **Mobility flows**: a `Flow` (`interval-domain`, `timer/Flow.kt`) is a list of `Move`s, each named, timed, with
  a one-line cue, done on each side in turn when `sided`. It runs on the interval engine as the sequence
  `Flow.config` is (a move a block, a side a round, `Flow.SIDE_CHANGE_SECONDS` between sides and
  `MOVE_CHANGE_SECONDS` between moves), so nothing about a run is stored: the `Workout` key carries the flow's key
  for a new one (`WorkoutSession.open(id, flow)`, which leaves the Timer's settings alone), and everything after
  knows it again by its shape and its `Activity.Mobility` among the flows `FlowStore.flows` holds, the shipped ones
  (`BuiltInFlows.kt`) then the user's own (`flows.matching(config)`: the screen's `WorkoutUiState.flow`, the cues,
  the notification's `WorkoutStatus.flow`, a finished one's name through `nameOf(config, flows)` and History's
  `WorkoutNames`, the starts that run one as itself through `flowKeyOf`). `configDescription`'s "6 moves" goes by
  the shape alone (`isFlowShaped`), so a deleted flow's workouts still read as mobility. So no two flows may share
  a shape: `FlowTest` holds it of the shipped ones, and the editor refuses to save a draft that clashes with any
  flow or preset (`FlowDraft.clash`). A flow the user builds (`FlowRepository`, the `flow` table, its moves one JSON
  column) carries the `id` it is stored under and a key made of it (`ownFlowKey`); it is built in `FlowEditor` (an
  app key: new, `copyOf` a shipped one's key, or `id`) from the library's mat moves and floor holds, their name and
  first cue copied in as text, or moves typed in, and is exported (schema 8) and matched by name on import. A move
  that is one of Lift's exercises carries its key (`Move.exercise`, a plain string, no part of the shape; the Pilates
  flow's moves, and one picked from the library; stored in the moves' JSON and exported since schema 12): the
  interval side resolves it through `interval-ui`'s `Move.builtIn`/`Move.demo` (over `lift-ui`, which a ui module may
  take of the other side) and shows it by Lift's figure, a still at the end of its row (`FlowMoves`, `MoveThumb`), its
  page in place of the flow's on the sheet and Today's look before Start (`MoveDetail`: the words, then lift-ui's
  `Demonstration`, the card the exercise page shows), and the figure running in a band between the move's name
  and the ring (`MoveFigureBand`, `FlowFigure` in `WorkoutFlow.kt`; its room kept for the whole run when any move
  has one, `FlowNow.figures`; held while paused; a tap opens the page as a sheet). A stretch, which no built-in is
  yet, shows its words alone. A flow's words are
  data, like a built-in preset's name, so they can be spoken: `playCue` takes the `FlowStep` and says "Next:
  Cat-cow", "Left side", "Switch sides" (`Speech.NextMove` and the rest), with no halfway. The screen's part is
  `WorkoutFlow.kt`; the notifications' is `FlowStatusText.kt` (`interval-platform`). It is written to the health
  store as stretching (`HealthWorkoutKind.Mobility`). Train's Mobility pillar (`interval-mobility`) lists them and
  starts one from its sheet (`startWorkout(clock, flow)`, `BackStack.kt`). A finished flow is an ordinary
  `HistoryEntry`, so every week count has it already; the week's split says how many were mobility
  (`WeekGoal.mobility`, by the activity), and one repeated or planned starts as the flow again (`flowKeyOf`, which
  the Today and History routes ask before they put anything on the Timer). Today offers the longest flow that fits
  the minutes picked after the presets (`fitting`), and on a day with a sport still to log leads with the one for
  before it (`flowBefore`).
- **Set up**: one page (`apps/app/src/weeksetup/`, the `WeekSetup` key) that asks what else you train, on which
  days, which bells you own and how long a session is, shows the week those come to and builds it. It lives in
  `app` because building writes four stores across both sides (`PlanStore`, `SettingsStore`, Lift's `GearStore`
  and `ProgramStore`), which no feature may reach together. Its two decisions are pure: `starterProgram`
  (`lift-domain`, `StarterProgram.kt`) picks among the built-ins one bell is enough for (`Program.oneBell`: no
  lift with two bells, none under a bar or against a band, none that needs a bar to hang from or a bench;
  `StarterProgramTest` pins the set, so a new built-in says which side it is on), or with a pair of bells owned
  (`GearStore.pairs`, asked under the bells) those two are enough for (`Program.pairOfBells`), those whose longest
  day fits the session, a grappler's first for BJJ (with a pair, `BjjKettlebell` once it fits), then a beginner's,
  then any; `buildWeek` (`BuiltWeek.kt`) puts the sport on its days and the program's on the free ones, spread
  evenly with a rest day left (`spreadWeekdays`, interval-domain, the Plan sheet's too), beside `BjjKettlebell`
  with the sport on two days or fewer the 15:15 preset (`snatchIntervals`) on a third (`conditioningDay`), and
  says which plans a rebuild takes off first (the weekly days of the sport it opened with and of the program, and
  beside that program the weekly 15:15; everything else planned stays and its weekdays are taken). `WeekSetupViewModel` keeps the answers in its entry's
  `SavedStateHandle`, opens on what stands, and builds once: it never calls `ProgramStore.follow` while a program
  is followed, since following restarts it. The session's length is `AppSettings.sessionMinutes`, which Today's
  pick by time opens on (`HomeViewModel`, until a length is picked by hand). Today's empty card and Settings'
  Plan group open it, and a new install opens on it, once: `AppContent` starts the back stack at `TodayTab,
  WeekSetup` when it is Set up's turn (`isSetupTurn`, `FirstRun.kt`: the app offers it, the open is a plain one
  rather than a shortcut's, and `AppSettings.setupOffered` is unset) and there is nothing in the app
  (`nothingYet`: no workout, sport day, plan, preset, goal, session, program or bell), and marks the turn taken
  either way, so someone who had the app before never meets it unasked. `App()` passes `offerSetup = true`;
  `AppContent`'s default is off, so every whole-app test opens on Today unless it asks (`appTest(firstRun =
  true)`, `acceptanceScenario(firstRun = true)`). An import of settings sets `setupOffered`, which is not in the
  export.
- **Health Connect**: `HealthStore.record` takes a `HealthWorkout` (core-platform's `Health.kt`): `WorkoutViewModel`
  builds one from the course it keeps (`CourseSpan`s in `WorkoutProgress`, so a restart loses none) as the workout
  ends, `FinishSession` (`lift-session`) from the session's sets (`HealthWorkouts.kt` in each); each is written
  again with its summary's words, the workout's as the summary goes, the session's once they settle (`SessionHealth`). Android's `HealthConnectStore`
  writes an exercise session with segments and a total-calories record, keyed by the workout's id, and checks its
  permissions before every write. It reads two things: `latestWeightKg` (the last 30 days), which Settings' weight
  sheet offers as a button and never takes on its own, and `heartRate(start, end)` (a watch's, average and peak),
  which a summary and a finished workout's or session's page ask for each time they come into view until one is
  found, then keep on the entry (only an app on show may read, and a watch syncs late). Connected means the two
  write permissions; each read is asked on the same sheet but is a leave of its own, so refusing it (or having
  connected before it was asked) keeps the writes and shows nothing read. Settings' row connects through `rememberHealthConnector` (the
  sheet is Health Connect's) and disconnects by revoking. The manifest declares the four permissions and
  `PrivacyActivity` (the policy, `about/PrivacyScreen.kt`, also under About), which Health Connect opens for its
  rationale. Play's Health apps declaration must list the same four data types. iOS's `HealthKitStore` does the same
  through a back-dated `HKWorkoutBuilder` (sync identifier and version, so a rewrite replaces); the app carries the
  HealthKit entitlement (`apps/iosApp/src/iosApp.entitlements`) and both `NSHealth…UsageDescription`s. iOS can't
  revoke, so its switch off is the app's own (user defaults), and one refused on HealthKit's sheet is sent to the
  Health app. Screens name the store by core's `healthStoreName`, never a literal.
- **Reminders for the plan**: `AppSettings.reminderMinuteOfDay` (set on Plan, null is off). `PlanReminderScheduler`
  (`interval/interval-history/src/plan/`, an eager single) recomputes `plannedReminders` (the next 14 days with a plan
  still open, from `TrainingLog.upNext`) on every change and at midnight, and hands the whole set to
  `PlanReminders.schedule`, which replaces what is pending: Android arms one inexact alarm per day
  (`PlanAlarms`, a single in the container, recording the days so it can cancel them), iOS one calendar-triggered
  local notification per day (the workout's notifications remove only their own identifiers, and leave
  `PLAN_REMINDER_DAYS` of the 64 pending slots free). Words come from `reminderWords`, the same on both. Android's
  alarm carries only its day: `PlanReminderReceiver` (a `KoinComponent`) asks the scheduler, as
  `PlanReminderSource`, for the day's reminder as the plan now stands, words and posts it, then has it
  `reschedule()`, which moves the 14 days on with the app never opened; a reboot or clock change reschedules too.
  iOS words its notifications as they are scheduled, and its window moves only while the app runs. `AskToShowNotifications()` (core's `SystemServices.kt`) asks for the
  permission on Plan while it is on, as a workout starts and at a Lift session's first rest. iOS shows a reminder
  due with the app open (`willPresent` in `iosApp.swift`, by its `plan-reminder-` prefix) and nothing else then.

## Testing

Strategy, commands and limits: [docs/testing.md](docs/testing.md).
- **Run only what the change can break.** While iterating, one class: `./kotlin test -m <module> -p jvm
  --include-classes '<fqn or pattern>'` (or `--include-test` for one method). Before a commit, the modules you
  touched and those that depend on them (`./kotlin show dependencies`), `-m` each, then
  `./kotlin check lint detekt`. The whole JVM run (`./kotlin test -p jvm`) and `./kotlin build` come once, when
  the piece of work is done, not after every edit. `-p android` repeats every common test on a second JVM and
  `./kotlin build` links iOS for minutes, so neither is a loop: CI runs both on every push. The acceptance
  specs are the slow half of the JVM run (`--exclude-classes '*AcceptanceTest'` leaves them out, or run only
  the journey's own spec with `--include-classes`).
- A test lives in the module of what it tests: logic and ViewModel tests in its `test/`, which runs on JVM,
  Android and iOS. Kotlin/Native rejects
  a backticked test name with a comma in it ("name contains illegal characters"), and only the full
  `./kotlin build` compiles the iOS tests, so keep commas out of common test names.
- **Compose UI tests go in `test@jvm/` only.** Common tests also run as Android local tests,
  where `runComposeUiTest` fails (no Robolectric). A screen test drives its stateless `XScreen` in the
  feature's module; Lift's flows (`liftAppTest`) live in `lift/lift-nav/test@jvm/`, the app's (`appTest`, over
  `AppContent(testKoin())`) in `apps/app/test@jvm/`.
- **Acceptance tests (ATDD)**: every journey that needs no device has a spec, the interval side's in
  `apps/app/test@jvm/acceptance/` (`acceptanceScenario`, `IntervalAcceptanceDriver`), Lift's in
  `lift/lift-nav/test@jvm/acceptance/` (`liftScenario`, `LiftAcceptanceDriver`). A user-facing change starts with
  a failing spec or step (outer loop), then unit tests drive the logic (inner loop); a change to a
  journey's behavior updates its spec and its `.xml` together. Driver rules: docs/testing.md.
- Use `androidx.compose.ui.test.v2.*` and the harness in `core/core-testing/src@jvm/ui/UiTestHarness.kt`:
  `runUiTest(Window.X)`, `setThemedContent`, `waitForText`, and `StateRestoration`. Desktop's
  `StateRestorationTester` is not implemented.
- Fakes live in the `*-testing` modules (`core-testing`, `interval-testing`, `lift-testing`), ordinary
  libraries a module lists under `test-dependencies`; `testKoin()` in `apps/app/test/fakes/`. Never load
  the real platform module in tests.
- On-device end-to-end flows are android-CLI journeys in `journeys/*.xml`, run by an agent on
  `amper-37`, not in CI.
- **CI's iOS tests are tiered**: a push links only the quick shard (line 1 of `tools/ios-test-shards.txt`,
  the pure domains); every shard runs nightly, on tags, on `gh workflow run CI --ref <bookmark>`, and on a
  push touching `src@ios/` or the build (`tools/ios_test_plan.py`). So a native-only failure in a feature
  module's common tests can surface a night late: run `./kotlin build` once before calling work done.

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
  Its package rule takes `dev.justinsmith.interval` as the root and excludes `lift-*` (Lift's root is `dev.justinsmith.lift`).
- **The lint plugin needs `slf4j-nop`**: ktlint's rules bind kotlin-logging to slf4j at class-init time.
- **An edit to `apps/androidApp/proguard-rules.pro` doesn't rerun R8**: the release task doesn't take the file as an
  input. Delete `build/tasks/_androidApp_buildAndroidRelease` and build again (docs/release.md, the R8 smoke test).
- `./kotlin clean` deletes the tracked `apps/iosApp/module.xcodeproj/xcshareddata/xcschemes/app.xcscheme`; the next
  iOS build fails once, regenerates it unchanged, then passes.
- iOS `ViewController()` is PascalCase on purpose (Swift calls it by that name) and is suppressed for ktlint.

## Version control: jj, colocated with git

`.jj/` sits beside `.git/`. Use `jj` (`jj st`, `jj describe`, `jj new`, `jj git push`). A detached git HEAD
is normal; don't "fix" it or create git branches.
