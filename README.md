<div align="center">

# ⏱️ Interval

**An offline-first, privacy-focused interval workout timer and strength training tracker.**  
Built with **Compose Multiplatform** for **Android**, **iOS**, and **Desktop (JVM)**, powered by the **Kotlin Toolchain** (Amper-based).

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-Android_|_iOS_|_Desktop-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.0-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Build System](https://img.shields.io/badge/Build_System-Kotlin_Toolchain_(Amper)-0095D5)](https://kotlin-toolchain.org/latest/)
[![Zero Gradle](https://img.shields.io/badge/Gradle-Zero_Daemon_|_Zero_Scripts-brightgreen)](#prerequisites)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25_Offline_|_No_Tracking-success)](#-privacy-first--offline)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<br/>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-app-showcase">App Showcase</a> •
  <a href="#-architecture--tech-stack">Architecture</a> •
  <a href="#-getting-started">Getting Started</a> •
  <a href="#-testing">Testing</a> •
  <a href="#-documentation">Docs</a>
</p>

</div>

---

## 📱 App Showcase

### ⏱️ High-Intensity Interval Timer

| 1. Precision Timer Setup | 2. Natural Language Input | 3. Active Workout Ring | 4. Workout Summary |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01_timer_setup.png" width="220" alt="Timer Setup"/> | <img src="docs/screenshots/10_describe_workout.png" width="220" alt="Describe in Words"/> | <img src="docs/screenshots/02_active_workout.png" width="220" alt="Active Workout"/> | <img src="docs/screenshots/03_workout_summary.png" width="220" alt="Workout Summary"/> |
| *Work, rest, rounds, optional warm-up & cool-down with calorie estimate* | *Type "5 min warm-up, 40/20 x8 on rower" for instant parsing* | *Vibrant circular ring, phase countdown & controls* | *Detailed statistics, calories burned, "How was it?" effort & notes* |

### 🏋️ Strength & Kettlebell Tracking (Lift)

| 1. Lift Dashboard | 2. Active Session & Rest Clock | 3. Exercise Library | 4. Interval Presets |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/05_lift_today.png" width="220" alt="Lift Dashboard"/> | <img src="docs/screenshots/06_lift_session.png" width="220" alt="Active Session"/> | <img src="docs/screenshots/07_exercise_library.png" width="220" alt="Exercise Library"/> | <img src="docs/screenshots/04_presets_library.png" width="220" alt="Preset Library"/> |
| *Weekly goal ring, 4-week muscle balance & quick start* | *Sets, weight × reps logging & automatic rest clock* | *100+ movements with muscle filters & form cues* | *Tabata, HIIT, EMOM, Hangboard & custom cards* |

### 📅 Unified Training Log & Adaptive Layouts

| Unified Training Log & Calendar | Adaptive Tablet & Desktop Experience |
|:---:|:---:|
| <img src="docs/screenshots/08_history_calendar.png" width="320" alt="Training Log & Calendar"/> | <img src="docs/screenshots/09_tablet_adaptive.png" width="520" alt="Tablet & Desktop Multi-pane Layout"/> |
| *Unified history uniting cardio intervals and strength sessions with workout-kind bars, perceived effort ratings, notes, and 12-week volume charts* | *Adaptive two-pane layout built with Navigation 3 and Compose Adaptive Scaffold that scales seamlessly from phones to tablets and desktop* |

---

## ✨ Features

### ⏱️ Precision Interval Timer
- **Customizable Intervals**: Precise configuration for work, rest, and round durations with rapid steppers and quick-pick sheets.
- **Warm-Up & Cool-Down**: Optional warm-up before interval rounds and cool-down stretch after, configured on the timer, in presets, or described in plain text.
- **Natural Language Parsing**: Describe workouts in plain English (e.g., `"5 min warm-up, 40/20 x8 on rower, 3 min cool down"`, `"tabata"`, `"hangboard 7/3 x6"`), automatically parsed into timer configurations.
- **Audio & Haptic Cues**: High-contrast audible beeps, spoken phase cues via system text-to-speech, halfway warnings, and vibration pulses.
- **Visual Timelines**: Scaled phase rings and progress timelines that dynamically adjust to any aspect ratio.
- **Android 16 Live Updates**: Running workouts and rest timers show as interactive status-bar chips with real-time countdown, phase-colored progress on the lock screen, and notification controls.
- **Background Persistence**: Accurately tracks when backgrounded or locked — Android uses a foreground service with lock-screen controls; iOS schedules local notifications ahead per phase transition.
- **App Shortcuts**: Long-press launcher shortcuts on Android to quickly "Start a workout" or "Start a Lift session" without interrupting any active workout.

### 🏋️ Strength & Kettlebell Training (Lift)
- **Built-in & Custom Programs**: 29 built-in programs across 6 training families (Simple & Sinister, Rite of Passage, Quick & the Dead, etc.), with an interactive block and rep-range editor.
- **100+ Exercise Library**: Extensive movement catalog categorized by muscle groups (kettlebell, barbell, bodyweight, bands) complete with form cues and custom movement creation.
- **Session Tracking**: Sets, weights, reps, EMOM timers, hold clocks, and automatic rest intervals.
- **Muscle Balance**: 4-week balance card tracking sets across 7 key muscle groups (chest, shoulders, back, arms, core, glutes & hamstrings, quads & calves), separating primary and assisting volume and highlighting unworked areas.
- **Screen-Off Rest Alerts**: Android counts rest on the lock screen with Live Update chips and beeps when finished; iOS displays in-app banners when open and local notifications when locked.
- **Personal Records (PRs)**: Automatic PR detection and volume load progression tracking.

### 📅 Unified History, Planning & Streaks
- **Integrated Training Log**: Daily calendar view, 12-week volume charts, weekly goals, workout kind breakdowns, and streak tracking that unifies cardio and strength workouts.
- **Post-Workout Reflection ("How was it?")**: Log perceived exertion (Easy, Steady, Hard, Very hard, All out) and a custom note upon completing any interval or Lift workout; reflected in history, exports, summaries, and Health Connect notes.
- **Routines & Reminders**: Schedule recurring workouts by weekday with reboot-resilient notification reminders.
- **Calorie Estimation & Health Connect**: Dynamic calorie expenditure calculations based on exercise type, duration, and body weight. Syncs completed interval workouts and Lift sessions to Health Connect as exercise sessions with notes and calories, and provides one-tap reading of latest weight without unwanted background syncing.

### 🔒 Privacy-First & Offline
- **Zero Tracking**: No accounts, no sign-ups, no ads, and zero analytics or telemetry.
- **Local SQLite Storage**: Encapsulated Room multiplatform database running locally on device storage with atomic transactions.
- **Full Data Ownership & Portability**: Export your entire workout histories, presets, routines, settings, programs, and gear to standard JSON (schema 3), and import files back safely with duplicate prevention and domain range validation.
- **Android Backup Support**: Native integration with Android system backup and device-to-device migration.
- **Accessibility**: Semantic headings, full TalkBack and VoiceOver navigation, and reduced-motion mode.

---

## 🏗️ Architecture & Tech Stack

Interval is designed around modern multiplatform architecture principles, cleanly separated into 29 modules in five folders:

```
interval/
├── apps/                # The app and its hosts
│   ├── app/             # Composition root: Navigation 3 back stack, AppKoin, About, export
│   ├── androidApp/      # Android host & manifest
│   ├── desktopApp/      # Desktop JVM host (Compose Hot Reload ready)
│   └── iosApp/          # iOS Swift host & Xcode wrapper
│
├── core/                # What both sides build on
│   ├── core/            # Design tokens, IntervalTheme, NavigationArea, components
│   ├── core-platform/   # Clocks, timestamp formatter, storage plumbing (zero Compose dependencies)
│   ├── core-time/       # Pure calendar maths (zero dependencies)
│   └── core-testing/    # MainDispatcherTest, UI test harness, fake platform services
│
├── interval/            # The interval timer
│   ├── interval-domain/   # Timer engine, workout state machine, calorie math, parsers
│   ├── interval-data/     # Room database (AppDatabase), DataStore settings, repository
│   ├── interval-platform/ # Audio/speech cues, Android Foreground Service, iOS notifications
│   ├── interval-ui/       # Shared workout widgets, Timeline, PhaseColors, Splash
│   ├── interval-testing/  # FakeStorage, RecordingCuePlayer, fake DAOs
│   ├── interval-timer/    # Timer tab and natural language describe sheet
│   ├── interval-workout/  # Live workout screen, circular countdown readout, summaries
│   ├── interval-presets/  # Preset editor, built-ins, custom preset cards
│   ├── interval-history/  # History calendar, training log, routine planner & reminders
│   └── interval-settings/ # Settings sheet, cues toggle, theme selector, data export
│
├── lift/                # Lift, the strength side (code in dev.justinsmith.lift)
│   ├── lift-domain/     # Exercises, programs, EMOM/hold clocks, PRs
│   ├── lift-data/       # Room database (LiftDatabase), repository, the ports to the interval side
│   ├── lift-platform/   # Beeper, rest alert service, platform Koin module
│   ├── lift-ui/         # Lift widgets, exercise formatters, cues mapping
│   ├── lift-testing/    # FakeLiftStorage, RecordingBeeper, fake DAOs
│   ├── lift-today/      # Lift tab, gear inventory (bells, bar, bands, records)
│   ├── lift-session/    # Active session logger, rest clocks, programs & progress
│   ├── lift-library/    # Exercise library, search, filters, exercise editor
│   └── lift-nav/        # Lift's route keys and nav entries
│
├── tooling/             # Build plugins
│   ├── detekt-plugin/   # Detekt with Compose rules
│   └── lint-plugin/     # ktlint checks & formatting
│
├── journeys/            # End-to-end on-device user flows (android CLI)
└── tools/               # Icon generators & version parity validation scripts
```

### Key Libraries & Tools
- **UI Framework**: [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) (Desktop, Android, iOS)
- **Navigation**: [Jetpack Navigation 3](https://developer.android.com/guide/navigation) with adaptive multi-pane layouts
- **Dependency Injection**: [Koin](https://insert-koin.io/) & `koin-compose` with process-scoped initialization, feature modules, and compile-time verification (`VerifyAppModulesTest`)
- **Database & Persistence**: [Room KMP](https://developer.android.com/kotlin/multiplatform/room) with SQLite driver + AndroidX DataStore
- **Health Platform**: [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) on Android for exercise sessions, calorie logs, and weight reads
- **Build Toolchain**: [Kotlin Toolchain (Amper)](https://kotlin-toolchain.org/latest/) — **No Gradle daemon, zero `build.gradle.kts` files**
- **Architecture Guide**: For in-depth rules and layer boundaries, see [docs/architecture.md](./docs/architecture.md).

---

## 🚀 Getting Started

### Prerequisites

The repository includes self-bootstrapping wrappers (`./kotlin` for macOS/Linux, `kotlin.bat` for Windows) for the **Kotlin Toolchain**. The wrapper automatically provisions the pinned toolchain version, Java runtime, and core dependencies on its first run.

- **Desktop (JVM) & Android**: JDK and Android SDK components are auto-provisioned by the toolchain.
- **iOS**: Requires macOS with Xcode installed to compile Kotlin/Native frameworks.
- **Tools**: Python 3 is required for release and version validation scripts (`tools/check_versions.py`).

### Common Commands

```sh
# Build everything (including iOS link on macOS)
./kotlin build

# Fast compile loop (common + JVM only)
./kotlin build -p jvm

# Run all checks (unit tests + ktlint + Detekt)
./kotlin check

# Run code formatters
./kotlin do format

# Run Detekt static analysis
./kotlin check detekt
```

---

## 🏃 Running the Apps

### Desktop (JVM)
```sh
# Standard run
./kotlin run -m desktopApp

# Fast UI development with Compose Hot Reload (updates code on save without restarting)
./kotlin run -m desktopApp --compose-hot-reload
```

### Android
```sh
# Run on a connected Android device or running emulator
./kotlin run -m androidApp

# Or launch directly with the android CLI:
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
```

### iOS
```sh
# Build and run the iOS app on an iOS simulator
./kotlin run -m iosApp
```

---

## 🧪 Testing

Interval follows a strict testing pyramid designed for fast local iteration and multiplatform correctness:

```sh
# Fast test loop: domain logic and Compose UI tests on desktop JVM (~10s)
./kotlin test -p jvm

# Run tests for a specific module
./kotlin test -m interval-history -p jvm
./kotlin test -m lift-domain -p jvm
./kotlin test -m lift-nav -p jvm

# Run common tests against Android local JVM environment
./kotlin test -p android

# Run common tests in iOS Simulator (macOS only)
./kotlin test -p iosSimulatorArm64
```

- **Logic & ViewModels**: Tested in `<module>/test/` across JVM, Android, and iOS simulator.
- **Compose UI Tests**: Located in `<module>/test@jvm/` to render off-screen with Skiko via the `core-testing` harness.
- **Acceptance Specs**: End-to-end user flows validated on the JVM (`apps/app/test@jvm/acceptance/`).
- **On-Device Journeys**: XML-driven end-to-end flows in `journeys/*.xml` executed via the `android` CLI.

For testing architecture, harnesses, and test fakes, see [docs/testing.md](./docs/testing.md).

---

## 📚 Documentation

- [Architecture Guide](./docs/architecture.md): System architecture, Navigation 3 backstack, Room multiplatform, and background execution.
- [Testing Strategy](./docs/testing.md): Compose UI desktop harness, fakes, and on-device journeys.
- [Release Guide](./docs/release.md): Release preparation, R8 optimization, keystore setup, and deployment checklist.
- [Sprint 0.3](./docs/sprint-0.3.md): The current sprint's tickets, order and definition of done.
- [Developer Guide (CLAUDE.md)](./CLAUDE.md): Code conventions, architecture invariants, and toolchain command reference.
- [Changelog](./CHANGELOG.md): History of notable changes and milestone releases.

---

## 🔀 Version Control

This repository uses [Jujutsu (jj)](https://github.com/martinvonz/jj) colocated with Git (`.jj/` alongside `.git/`).
```sh
jj st                  # Check working copy status
jj describe            # Set commit message
jj new                 # Create a new working-copy commit
jj git push            # Push commits to remote Git branches
```
*(A detached Git `HEAD` is expected and normal in colocated repositories).*

---

<div align="center">
  <sub>Built with ❤️ using Compose Multiplatform & Kotlin Toolchain.</sub>
</div>
