# interval — Claude Code Guide

Compose Multiplatform app for **Android, iOS and desktop (JVM)**, built with the **Kotlin Toolchain**
(`./kotlin`, Amper-based, version pinned in the `kotlin` wrapper). Docs: https://kotlin-toolchain.org/0.13/

> **NO GRADLE.** There is no `gradlew`, `build.gradle.kts` or `settings.gradle.kts`. Never run or suggest
> Gradle commands. Build configuration lives in `project.yaml` and each module's `module.yaml`.

## Commands

```sh
./kotlin build                      # Everything. The only build that links iOS. Run before calling work done
./kotlin build -p jvm               # Fast compile loop (common + JVM)
./kotlin test -p jvm                # Fast test loop (logic + Compose UI tests)
./kotlin check lint                 # ktlint over every module
./kotlin do format                  # ktlint auto-format
./kotlin check detekt               # detekt + Compose rules over every module (config: detekt.yml)
./kotlin check                      # All checks (tests + lint + detekt)
./kotlin run -m desktopApp --compose-hot-reload   # Desktop window that reloads on every save
./kotlin package -m androidApp -f aab   # Release bundle (R8 on; rules in androidApp/proguard-rules.pro).
                                        # Unsigned until a keystore is set up (`./kotlin tool generate-keystore`)
./kotlin show modules | settings | dependencies | tasks
```

`-m` takes the **leaf module name** (`core`, `shared`, `bells`, `androidApp`, `iosApp`, `desktopApp`, `lint-plugin`,
`detekt-plugin`).

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
- `androidApp/res/` Android resources; `shared/composeResources/` multiplatform resources (`Res.*`)
- `core/` is what `shared` and `bells` both build on: the theme, tokens, motion, components, lanes, the
  platform hooks the UI needs (`SystemServices.kt`), calendar/clock maths, and the plumbing both repositories
  run on (`storage/Storage.kt`: `SerialWriter`, the queue every write goes through, and `orOnError`/
  `orEmptyOnError`, the rule for a failed read). It keeps the packages it had in
  `shared` (`dev.justinsmith.interval.ui`, `.platform`, `.timer`) and has its own resources
  (`dev.justinsmith.core.resources`: the Barlow fonts and its components' few words). `shared` exports it.
- `bells/` is the kettlebell side behind the **Lift** tab (package `dev.justinsmith.bells`), a library
  `shared` depends on: log sets of reps with a bell or a barbell, owned bells, the bar and bands (`gear/Gear.kt`, the bands in
  `domain/Bands.kt`, the plates a side in `domain/Barbell.kt`) and personal records, built-in programs
  (`domain/BuiltInPrograms.kt`, data not words: names are strings) whose next day lays out the session
  (`domain/DayProgress.kt`; what to load from `domain/Suggestions.kt`), EMOMs timed by the pure `EmomClock`
  and run by `EmomRunner` with beeps through `Beeper`, and a finished session's page (`history/LiftDetail.kt`).
  `domain/` is pure: no Room, Compose or Koin.
  Same conventions as `shared` (`XRoute`/`XScreen`, Koin, Room, strings in its own `composeResources`), but
  no navigation of its own: its routes are wired in `shared/src/App.kt` (`LiftTab`, `Lifting`, `Programs`,
  `OwnedBells`, `LiftDetail`). It owns its database, `bells.db` (`BellsDatabase`, schemas in
  `bells/schemas/`), and can't see `shared`: it reads the interval workouts and the weekly goal through
  `IntervalActivity`, which `shared` binds. `shared` reads its sessions from `BellsRepository`, so History,
  the week, the streak and the goal count both kinds of workout. Its Koin modules (`bellsModules`) join the
  app's container in `appModules`; `App` waits for both repositories to be ready.

**Never create `src/commonMain/kotlin/`.** Amper ignores it silently.

Versions live in `libs.versions.toml` (`$libs.a.b.c` = alias `a-b-c`). Compose versions come from the
toolchain (`$compose.*`).

## Conventions

- **Navigation 3**, one back stack in `shared/src/App.kt`. Adding a screen takes three edits:
  1. `@Serializable data object/class X : NavKey` in `shared/src/nav/Routes.kt`
  2. `subclass(X::class)` in `navConfiguration` (App.kt). Without it saving the back stack fails on iOS/desktop.
  3. `entry<X> { XRoute() }` in the `entryProvider` (App.kt)
- **Screens** are split into `XRoute()` (resolves the ViewModel via `currentKoinScope()` + `viewModel { }`,
  collects state) and a stateless `XScreen(state, callbacks)` that previews and tests can drive directly.
- **Page layouts**: a pushed page that scrolls is a `ScrollPage` (Back is `BackAction`), a tab of cards a
  `TabGrid`. Any screen that saves or edits a preset opens it through `PresetEditorHost`
  (`PresetDraft.saveCurrent(config)` for the settings on the Timer).
- **ViewModels** expose `StateFlow<UiState>` from a private `MutableStateFlow`. They are entry-scoped by
  `rememberViewModelStoreNavEntryDecorator`.
- **DI is Koin DSL** (no annotations/KSP) in an isolated container: `shared/src/di/AppKoin.kt`. Add a module
  per domain to `appModules()`. Constructor-inject everything; keep platform calls (the clock, cues, storage)
  behind injected interfaces (`WallClock`, `CuePlayer`, `PlatformStorage`) so tests can fake them.
- **Storage**: `IntervalRepository` (`shared/src/data/`) is the only thing that touches it. What it stores
  is described by plain models in `shared/src/model/` (`AppSettings`, `HistoryEntry`, `PlannedWorkout`,
  `LiftWorkout`, `LoggedWorkout`); Room entities and DataStore keys stay in `data/`, behind its mappers. Settings live
  in DataStore Preferences (`SettingsPreferences.kt`); history and custom presets in Room
  (`AppDatabase.kt`, bundled SQLite, generated by KSP for every platform). Settings are held in memory and
  written through in the background, so change them with `updateX { transform }`. `AppContent` draws
  nothing until `repository.ready`. The DataStore lives for the process (one per file, DataStore's rule),
  not the Koin container, which Android rebuilds whenever the Activity reopens. A schema change bumps the `@Database` version, adds a migration, and
  commits the new `shared/schemas/` JSON.
- **Text**: every user-facing string lives in `shared/composeResources/values/strings.xml` (a core
  component's own words in `core/composeResources/values/strings.xml`). ViewModels and
  domain code expose typed data (enums, sealed types like `NextUp`/`HistoryDay`, numbers, clock strings
  like `"0:30"`); screens put it into words with `stringResource`/`pluralStringResource`. Spoken cues are
  the one non-composable path: `CuePlayer.speak(Speech)`, worded by `SpeechQueue` with `getString`. Preset
  names are stored data, not labels: the built-ins' names live in `builtInPresets`. Never load a resource
  from `shared/test/`: common tests also run as Android local tests.
- **Theme**: `IntervalTheme` in `core/src/ui/Theme.kt` (`PhaseColors.of(phase)` in `shared/src/ui/PhaseColorsOf.kt`). Fixed tokens (`Spacing`, `Radius`, `Sizes`, `Opacity`,
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
  (`shared/src/splash/`) draws the same mark in code (`IntervalMark.kt`, keep its numbers in step) and
  opens on exactly Android's splash and iOS's launch screen, so the hand-off is seamless.
- **Platform code**: `expect`/`actual` in `src/` + `src@<platform>/`. Prefer an interface injected via Koin
  over `expect class`.

## Testing

Strategy, commands and limits: [docs/testing.md](docs/testing.md).
- Logic and ViewModel tests go in `shared/test/` and run on JVM, Android and iOS. Kotlin/Native rejects
  a backticked test name with a comma in it ("name contains illegal characters"), and only the full
  `./kotlin build` compiles the iOS tests, so keep commas out of common test names.
- **Compose UI tests go in `shared/test@jvm/` only.** Common tests also run as Android local tests,
  where `runComposeUiTest` fails (no Robolectric).
- Use `androidx.compose.ui.test.v2.*` and the harness in `test@jvm/ui/UiTestHarness.kt`:
  `runUiTest(Window.X)`, `setThemedContent`, and `StateRestoration`. Desktop's
  `StateRestorationTester` is not implemented.
- Fakes live in `shared/test/fakes/`. Whole-app tests compose `AppContent(testKoin())`. Never load
  the real platform module in tests.
- On-device end-to-end flows are android-CLI journeys in `journeys/*.xml`, run by an agent on
  `amper-37`, not in CI.

## Gotchas

- **`allWarningsAsErrors` is on everywhere.** Opt in per file (`@file:OptIn(...)`), **never** with module-wide
  `optIns`. Since toolchain 0.13 a module's flags reach the Kotlin/Native cache build of every dependency, and
  an unresolved marker there fails the iOS link. Only a full `./kotlin build` catches it; `-p jvm` passes.
- **`-Xexpect-actual-classes` is on in `shared`** for Room's KSP-generated `actual object
  AppDatabaseConstructor`; without it the Beta warning fails the build.
- **Room's `sqlite-bundled` is pinned separately**: keep it at the version `room-runtime` resolves
  (`./kotlin show dependencies -m shared`) when bumping Room.
- **Desktop needs `kotlinx-coroutines-swing`** (already in `desktopApp`). Without it `Dispatchers.Main` is
  missing and `viewModelScope` crashes at runtime.
- **detekt is 2.0 alpha** (`dev.detekt`), and `compose-rules` is built against one exact detekt version: bump
  the two together. `detekt.yml` holds only overrides of detekt's defaults and is validated, so an unknown
  key fails the check. Rules run without type resolution (`--analysis-mode light`). Suppress with
  `@Suppress("detekt.RuleName")`. HTML/SARIF reports land in `build/tasks/_<module>_detekt@detekt-plugin/`.
- **The lint plugin needs `slf4j-nop`**: ktlint's rules bind kotlin-logging to slf4j at class-init time.
- `./kotlin clean` deletes the tracked `iosApp/module.xcodeproj/xcshareddata/xcschemes/app.xcscheme`; the next
  iOS build fails once, regenerates it unchanged, then passes.
- iOS `ViewController()` is PascalCase on purpose (Swift calls it by that name) and is suppressed for ktlint.

## Version control: jj, colocated with git

`.jj/` sits beside `.git/`. Use `jj` (`jj st`, `jj describe`, `jj new`, `jj git push`). A detached git HEAD
is normal; don't "fix" it or create git branches.
