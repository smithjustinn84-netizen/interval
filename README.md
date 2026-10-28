# Interval

Interval is an offline-first, privacy-focused interval workout timer and strength training tracker for **Android**,
**iOS**, and **Desktop (JVM)**. Built with [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) and
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/) (Amper-based).

> **Zero Gradle**: The build system uses `project.yaml` and modular `module.yaml` files via `./kotlin`. No Gradle daemon,
> no `build.gradle.kts`.

---

## Features

### ⏱️ Interval Timer
- **Precision Timing**: Configurable work, rest, and round durations with quick steppers, value sheets, and calorie estimation.
- **Natural Language Parsing**: Describe workouts in plain English (e.g. `"40/20 x8 on the rower"`, `"tabata"`, `"hangboard 7/3 x6"`).
- **Audio & Haptic Cues**: Sound beeps, spoken phase cues via system text-to-speech, halfway warnings, and vibration ticks.
- **Visual Timelines**: Scaled phase rings and progress timelines that adapt dynamically to any screen size.
- **Background Persistence**: Keeps tracking accurately when backgrounded or locked — Android uses a foreground service
  with lock-screen controls; iOS schedules local notifications ahead per phase change.

### 🏋️ Lift (Kettlebell & Strength)
- **Built-in & Custom Programs**: 29 built-in programs across 6 training families, with an interactive block and rep-range editor.
- **Extensive Exercise Library**: Over 100 built-in exercises with muscle group categorizations and form cues; search, filter, and create custom movements.
- **Session Tracking**: Sets, EMOMs, hold clocks, and automatic rest timers.
- **Screen-Off Rest Alerts**: Android counts rest on the lock screen and beeps when done; iOS fires local notifications.
- **Progress & PRs**: Automatic personal record tracking and volume load progression charts.

### 📅 History, Plan & Local Privacy
- **Training Log**: Daily calendar view, 12-week volume charts, weekly goals, and streak tracking (unifying both cardio and lift sessions).
- **Routines & Reminders**: Schedule recurring workouts by weekday with reboot-resilient reminders.
- **Offline & Private**: Zero user tracking, accounts, or analytics. Full data export to JSON (schema 1) and support for Android system backups.
- **Accessible**: Semantic headings, full TalkBack and VoiceOver navigation, and reduced-motion support.

---

## Project Structure & Modules

The project is modularized across 25 focused modules. In every module, `src/` contains common multiplatform code, while
`src@<platform>/` holds platform-specific implementations.

```
interval/
├── androidApp/          # Android application host & manifest
├── desktopApp/          # Desktop JVM application host
├── iosApp/              # iOS Swift host & Xcode wrapper
├── shared/              # Application container, Navigation 3, AppKoin
│
├── core/                # Design tokens, IntervalTheme, NavigationArea, utilities
├── core-testing/        # MainDispatcherTest, UI test harness, fakes
│
├── interval-domain/     # Timer engine, workout models, calorie math, parsers
├── interval-data/       # Room database (AppDatabase), DataStore settings, repository
├── interval-platform/   # Audio/speech cues, Android Foreground Service, iOS notifications
├── interval-ui/         # Shared workout widgets, Timeline, PhaseColors, Splash
├── interval-home/       # Timer tab and workout describe sheet
├── interval-workout/    # Running workout screen, controls, summaries
├── interval-presets/    # Preset editor, built-ins, custom preset cards
├── interval-history/    # History calendar, training log, routine planner & reminders
├── interval-settings/   # Settings sheet, cues toggle, theme selector, export
├── interval-testing/    # FakeStorage, RecordingCuePlayer, fake DAOs
│
├── bells-domain/        # Lift domain: exercises, programs, EMOM/hold clocks, PRs
├── bells-data/          # Lift Room database (BellsDatabase), rest alert service
├── bells-ui/            # Lift widgets, exercise formatters, cues mapping
├── bells-library/       # Exercise library, search, filters, exercise editor
├── bells-session/       # Active session logger, rest clocks, program progress
├── bells-today/         # Lift tab, gear inventory (bells, bar, bands, records)
├── bells/               # Lift navigation entry point & route definitions
├── bells-testing/       # FakeBellsStorage, RecordingBeeper, fake DAOs
│
├── detekt-plugin/       # Custom toolchain plugin for Detekt analysis
├── lint-plugin/         # Custom toolchain plugin for ktlint checks & formatting
├── journeys/            # End-to-end on-device user flows (android CLI)
└── tools/               # Icon generators & version parity validation
```

See [docs/architecture.md](./docs/architecture.md) for the complete architecture guide and module dependency rules.

---

## Getting Started

### Prerequisites

The repository includes self-bootstrapping wrappers (`./kotlin` for macOS/Linux, `kotlin.bat` for Windows) for the
[Kotlin Toolchain](https://kotlin-toolchain.org/latest/). On first invocation, the wrapper automatically downloads the
pinned toolchain version, Java runtime, and core dependencies.

- **Desktop (JVM) & Android**: JDK and Android SDK components are auto-provisioned by the toolchain.
- **iOS**: Requires macOS with Xcode installed to compile Kotlin/Native frameworks and link the iOS application.
- **Tools**: Python 3 is required for version validation scripts (`tools/check_versions.py`).

### Common Commands

```sh
# Build everything (including iOS link on macOS)
./kotlin build

# Fast compile loop (common + JVM only)
./kotlin build -p jvm

# Run all checks (tests, ktlint, detekt)
./kotlin check

# Run lint checks or format code
./kotlin check lint
./kotlin do format

# Run Detekt static analysis
./kotlin check detekt
```

---

## Running the Applications

### Android
```sh
# Run on a connected Android device or running emulator
./kotlin run -m androidApp

# Or start the emulator and launch with the android CLI:
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
```

### Desktop (JVM)
```sh
# Standard run
./kotlin run -m desktopApp

# Fast UI development with Compose Hot Reload (recompiles and updates instantly on save)
./kotlin run -m desktopApp --compose-hot-reload
```

### iOS
```sh
# Build and run the iOS app on an iOS simulator
./kotlin run -m iosApp
```

---

## Testing

Interval employs a layered testing strategy designed for rapid execution and multiplatform reliability:

```sh
# Fast test loop (runs domain logic and Compose UI tests on desktop JVM)
./kotlin test -p jvm

# Run tests for a specific feature or domain module
./kotlin test -m interval-history -p jvm
./kotlin test -m bells-domain -p jvm
./kotlin test -m bells -p jvm

# Run common tests against Android local JVM environment
./kotlin test -p android

# Run common tests on the iOS simulator (macOS only)
./kotlin test -p iosSimulatorArm64
```

- **Logic & ViewModels**: Tested in `<module>/test/` across JVM, Android, and iOS simulator.
- **Compose UI Tests**: Placed in `<module>/test@jvm/` to render off-screen with Skiko via the `core-testing` harness.
- **On-Device Journeys**: End-to-end user flows in `journeys/*.xml` driven by the `android` CLI against the emulator.

For testing details, harnesses, and fakes, see [docs/testing.md](./docs/testing.md).

---

## Documentation Index

- [Architecture Guide](./docs/architecture.md): System architecture, Navigation 3, Koin DSL, Room multiplatform, and background execution.
- [Testing Strategy](./docs/testing.md): What runs where, Compose UI desktop harness, fakes, and on-device journeys.
- [Release Guide](./docs/release.md): Release preparation, R8 smoke tests, keystore management, and publishing checklists.
- [Developer Guide (CLAUDE.md)](./CLAUDE.md): Conventions, patterns, gotchas, and toolchain command reference.
- [Changelog](./CHANGELOG.md): History of notable changes and version milestones.

---

## Version Control

This repository uses [Jujutsu (jj)](https://github.com/martinvonz/jj) colocated with Git (`.jj/` alongside `.git/`).
Standard workflow commands:
```sh
jj st                  # Check working copy status
jj describe            # Set commit description
jj new                 # Create a new working-copy commit
jj git push            # Push commits to remote Git branches
```
A detached Git `HEAD` is expected and normal in colocated repositories.
