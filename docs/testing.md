# Testing

How interval is tested, what runs where, and what the Kotlin Toolchain (0.13) can't do yet. The
strategy follows Android's `testing-setup` skill (`android skills add testing-setup`), adapted to a
Compose Multiplatform project with no Gradle.

## What runs where

| Layer | Where | Runs on | Framework |
|---|---|---|---|
| Logic and ViewModels | `shared/test/` | JVM, Android (local JVM), iOS simulator | `kotlin.test`, `kotlinx-coroutines-test` |
| Compose behavior (one screen) | `shared/test@jvm/<feature>/XScreenTest.kt` | Desktop JVM | `runDesktopComposeUiTest` |
| Navigation (whole app) | `shared/test@jvm/AppNavigationTest.kt` | Desktop JVM | `AppContent` over fake DI |
| End to end, on a device | `journeys/*.xml` | Android emulator, run by an agent | `android` CLI journeys |

**Compose UI tests go in `test@jvm/`, never `test/`.** Common `test/` also runs as Android local
tests, which have no Robolectric here, so `runComposeUiTest` would fail there. The desktop JVM runs
the same common UI code.

## Commands

```sh
./kotlin test -p jvm                         # Logic + Compose UI + navigation, the fast loop
./kotlin test -m shared -p jvm --include-classes '*ScreenTest'   # Just the screen tests
./kotlin test -p android                     # Common tests as Android local tests
./kotlin test -p iosSimulatorArm64           # Common tests in the iOS simulator (macOS)
./kotlin check                               # Tests + ktlint + detekt
./kotlin build                               # Full build incl. the iOS link; catches opt-in leaks
```

CI (`.github/workflows/ci.yml`) runs the JVM and Android tests on Linux and the iOS tests on macOS.
The UI tests render offscreen with Skiko and need no display, including on the Linux runner.

## Test infrastructure

- **Fakes** live in `shared/test/fakes/Fakes.kt`, so every platform's tests can use them:
  - `FakeStorage`: an `InMemoryDataStore` plus `FakeHistoryDao` and `FakePresetDao`. Its
    `repository()` runs writes unconfined over fakes that never suspend, so a write has landed by the
    time the call returns. Two repositories over one `FakeStorage` are the app before and after a
    restart.
  - `RecordingCuePlayer`: records beeps, `Speech` lines and haptics.
  - `FixedTimestampFormatter`: time-zone free; `date()` is `"day N"`.
  - `fakePlatformModule(...)` binds the player and formatter for Koin; `fakeStorageModule(...)` binds a
    `FakeStorage` in place of the real `storageModule`.
  - `testKoin(...)`: the app's real container over those two, with the `WallClock` fixed at `now`.

  Never load the real platform or storage module in a test: on the JVM they write to the user's
  app-data directory and play sound.
- **Real storage** is tested in `shared/test@jvm/data/StorageTest.kt`: the repository over a DataStore
  and a Room database in a temp directory, with writes on `Dispatchers.IO`, including a restart.
  `AppKoinStorageTest` wires the real `storageModule` and rebuilds the container straight after closing
  it, as Android does when the Activity reopens in the same process. Both are
  JVM-only because Room's Android in-memory builder needs a `Context`, and common tests also run as
  Android local tests without Robolectric.
- **`AppContent(koin)`** (`shared/src/App.kt`) is `App()` with its container passed in. Navigation
  tests compose it with `testKoin()`: real ViewModels, repository and navigation over fake platform
  services. `App()` stays parameterless for the entry points.
- **`shared/test@jvm/ui/UiTestHarness.kt`**:
  - `runUiTest(Window.Phone | PhoneSideways | Tablet)`: sized windows that match the previews. The
    window size drives the same layout decisions as on a device (bar vs rail, Settings as a
    supporting pane).
  - `setThemedContent { }`: content inside `IntervalTheme`, light.
  - `StateRestoration`: saves and restores `rememberSaveable` state. Use it instead of
    `StateRestorationTester`, which throws `NotImplementedError` on desktop.
- The JVM test classpath adds:
  - `$compose.uiTest`
  - `$compose.desktop.currentOs`: Skiko natives, see KTC-5525.
  - `kotlinx-coroutines-swing`: provides `Dispatchers.Main` for `viewModelScope` when real routes are
    composed.

## Writing UI tests

- Drive the stateless `XScreen(state, callbacks)` with a fixed state and callbacks that record
  calls. Use `AppContent` only for navigation.
- Match on semantics first: text, `contentDescription`, `hasClickAction()`, `isDialog()`. Add a
  `testTag` only when an element would otherwise take more than three matchers. None is needed so far.
- Assert the English text literally ("Next: rest 0:15"), not through `getString`: an expectation read
  from the same key can't catch a screen showing the wrong one. This is where the words are tested;
  common ViewModel tests assert typed state and never load a resource (Android local tests can't).
- Use `androidx.compose.ui.test.v2.*`, because the v1 `runComposeUiTest` is deprecated and
  `allWarningsAsErrors` rejects it. Opt in per file with `@file:OptIn(ExperimentalTestApi::class)`,
  never module-wide.

## Journeys (on-device end to end)

`journeys/*.xml` describe user flows in plain steps, in the format of the `android-cli` skill's
`references/journeys.md`. An agent runs them against the emulator with `android layout`,
`android screen capture` and `adb shell input`, then reports a JSON pass/fail per step. They are
not run in CI.

```sh
./kotlin build -m androidApp
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
adb shell pm clear dev.justinsmith.interval      # Most journeys expect a fresh install
```

| Journey | Covers |
|---|---|
| `start-workout.xml` | Edit work time via the sheet, start, pause/resume, skip, end with nothing saved |
| `history.xml` | End after a round, find it in History, clear History |
| `presets.xml` | Save the current settings from the Presets tab, put a built-in on the Timer and start one from its sheet, edit and delete the saved one |
| `settings.xml` | Toggle a cue, switch to Dark and back, settings persist |
| `system-back.xml` | System back from a tab returns to Timer; during a workout it pauses and asks |

On the `amper-37` image, the first tap into a text field may open Gboard's "Try out your stylus"
tutorial. Cancel it and type again. It's the emulator's keyboard, not the app.

## Not available under the Kotlin Toolchain (0.13)

| Skill step | Status |
|---|---|
| Instrumented tests (`androidTest`) | Not supported. Journeys cover on-device behavior instead. |
| Robolectric | No support: no merged manifest or resources for local tests (KTC-4241). |
| Screenshot tests (Compose Preview Screenshot Testing, Paparazzi, Roborazzi, Dropshots) | Need AGP or Gradle plugins. Desktop options exist but were skipped for now. Goldens rendered on macOS won't match Linux CI. |
| Coverage (JaCoCo, Kover) | No support yet (KTC-416). A JVM agent through `settings@jvm: jvm: test: freeJvmArgs` would work, but was skipped. |
| Database tests | Room and DataStore over temp files in `test@jvm/data/` (JVM only: Android's Room builder needs a `Context`). |
| Hilt, Mockk | N/A: DI is Koin, and hand-written fakes cover every injected interface. |
