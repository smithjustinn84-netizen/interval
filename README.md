<div align="center">

# ⏱️ Interval

**An offline-first, privacy-focused training companion for interval conditioning, kettlebell & strength training, guided mobility flows, and weekly planning.**  
Built with **Compose Multiplatform** for **Android**, **iOS**, and **Desktop (JVM)**, powered by the **Kotlin Toolchain** (Amper-based).

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-Android_|_iOS_|_Desktop-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Build System](https://img.shields.io/badge/Build_System-Kotlin_Toolchain_0.13_(Amper)-0095D5)](https://kotlin-toolchain.org/0.13/)
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

### ⏱️ Conditioning & Precision Interval Timer

| 1. Precision Timer Setup | 2. Natural Language Input | 3. Active Workout Ring | 4. Workout Summary |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01_timer_setup.png" width="220" alt="Timer Setup"/> | <img src="docs/screenshots/10_describe_workout.png" width="220" alt="Describe in Words"/> | <img src="docs/screenshots/02_active_workout.png" width="220" alt="Active Workout"/> | <img src="docs/screenshots/03_workout_summary.png" width="220" alt="Workout Summary"/> |
| *Work, rest, rounds, sets, ladders & warm-up/cool-down with calorie estimate* | *Type "5 min warm-up, 40/20 x8 on rower" for instant parsing* | *Vibrant circular ring, phase countdown & controls* | *Detailed statistics, calories burned, "How was it?" effort & notes* |

### 🏋️ Strength, Kettlebell & Conditioning (`Train`)

| 1. Strength Pillar | 2. Active Session & Rest Clock | 3. Exercise Library | 4. Conditioning Presets |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/05_lift_today.png" width="220" alt="Strength Pillar"/> | <img src="docs/screenshots/06_lift_session.png" width="220" alt="Active Session"/> | <img src="docs/screenshots/07_exercise_library.png" width="220" alt="Exercise Library"/> | <img src="docs/screenshots/04_presets_library.png" width="220" alt="Preset Library"/> |
| *Program next day, estimated duration, 4-week muscle balance & gear* | *Sets, weight × reps logging & automatic rest clock* | *100+ movements with muscle filters & form cues* | *Tabata, HIIT 30/30, 15:15 MVO2, Hangboard & custom sequences* |

### 📅 Unified Training Log & Adaptive Layouts

| Unified Training Log & Calendar | Adaptive Tablet & Desktop Experience |
|:---:|:---:|
| <img src="docs/screenshots/08_history_calendar.png" width="320" alt="Training Log & Calendar"/> | <img src="docs/screenshots/09_tablet_adaptive.png" width="520" alt="Tablet & Desktop Multi-pane Layout"/> |
| *Unified history uniting cardio intervals, strength sessions, mobility flows, and sport days with workout-kind bars, effort ratings, notes, and 12-week volume charts* | *Adaptive two-pane layout built with Navigation 3 and Compose Adaptive Scaffold that scales seamlessly from phones to tablets and desktop* |

---

## ✨ Features

### 🧭 Guided Setup, `Today` Home & Three-Pillar `Train` Hub
- **First-Run Week Setup (`Set up`)**: Opens once on a fresh install (and anytime from Today or Settings). Answer four questions — what else you train (BJJ, climbing, running, a custom sport, or just the bell), on which days, which kettlebells you own, and your session length — to automatically pick a fitting single-bell program, spread strength days evenly around your sport with a rest day, save your equipment, and set your weekly goal.
- **`Today` Home Tab**: Opens directly to this week's progress against your goal, **Up next** (today's planned workout, pre-class mobility flow, or your followed program's next day with estimated duration) under one Start button, **"Also today"** when pairing strength with a class day, and **"Or pick by time"** (presets and mobility flows that fit 5, 10, 15, 20, or 30 minutes).
- **Unified `Train` Hub**: One tab organized into three pillars — **Strength** (programs, muscle balance, exercises, equipment), **Conditioning** (interval presets and the precision Timer), and **Mobility** (guided follow-along flows) — remembering the pillar you last trained on.

### ⏱️ Conditioning & Precision Interval Timer
- **Sets, Ladders & Multi-Block Sequences**:
  - **Sets of Rounds**: Group rounds into sets with dedicated rest between sets (e.g., `4 × (8 × 20/10)` with `1:00` between sets).
  - **Climbing & Falling Ladders**: Step work up or down each round (`+10s` or `-5s` a round), resetting each set.
  - **Multi-Block Sequences**: Chain multiple blocks back-to-back — each with its own work, rest, rounds, sets, and ladder, plus rest between blocks — built in the visual block editor or straight from the Timer.
- **Warm-Up & Cool-Down**: Optional warm-up before rounds and cool-down stretch after, configured on the Timer, in presets, or in plain text.
- **Natural Language Parsing**: Describe workouts in plain English (e.g., `"5 min warm-up, 40/20 x8 on rower, 3 min cool down"`, `"4 sets of 8 rounds 20/10, 1:00 between"`, `"ladder 20 to 60 by 10"`, `"15:15 mvo2"`, `"hangboard 7/3 x6"`).
- **Built-in & Custom Presets**: 5 built-in protocols (*Tabata*, *HIIT 30/30*, *Hangboard*, *Long efforts*, and Kenneth Jay's *15:15 MVO2* kettlebell snatch protocol) alongside custom single-block and sequence cards with scaled visual timelines.
- **Audio & Haptic Cues**: High-contrast audible beeps, spoken cues via system text-to-speech (including set numbers, block transitions, and rest between sets/blocks), halfway warnings, and vibration pulses.
- **Android 16 Live Updates & Background Persistence**: Running workouts and rest timers display as interactive status-bar chips with real-time countdowns and per-round, per-set, or per-block progress bars on the lock screen. Android uses a foreground service with lock-screen Pause/End controls; iOS schedules local notifications ahead per phase transition.
- **Shortcuts & Home-Screen Widget**: Long-press app shortcuts on **Android and iOS** to quickly "Start a workout" or "Start a Lift session" without interrupting an active workout, plus an **Android Glance home-screen widget** showing your weekly goal ring, streak, and one-tap start actions (with Android 15+ widget picker previews).

### 🏋️ Strength & Kettlebell Training (`Lift`)
- **31 Built-in Programs & Custom Editor**: 31 programs across 6 training families (Beginner, Classic, Dan John including *Armor Building Formula*, *Easy Strength*, and *One-stop shop*, Grappling, Mixed, and Barbell), with an interactive block and rep-range editor.
- **Session Duration Estimates**: Displays estimated workout times (`"about 20 min"`, `"about 12–15 min"`) across Today, program cards, and day sheets — calculated from sets, reps, movement pace, EMOM minutes, and your configured rest between sets.
- **Make It Yours**: Clone any built-in program into a customizable copy (`(yours)`) to modify rep ranges, timed holds, resistance bands, double bells, and schedules while keeping your current day and progression.
- **Rep Progression & Balance Preview**: Programs build volume within a rep range before stepping up to the next bell or band; "Very hard" or "All out" exertion ratings hold step-ups back until approved. Program sheets preview weekly muscle balance and highlight untargeted groups.
- **100+ Exercise Library & Loaded Carries**: 102 built-in movements across kettlebells (including 18/22/26/30 kg competition bells, half snatch, push press, and timed farmer's, suitcase, rack, and bottoms-up carries), barbell, bodyweight, bands, and mat Pilates, complete with form cues, best-per-session history charts, and custom exercise creation.
- **Session Tracking & Muscle Balance**: Log sets, loads (single/double bells, barbell plates, bodyweight + load, bands), reps, EMOM timers, hold clocks, and automatic rest intervals, with a 4-week balance card across 7 muscle groups separating primary and assisting volume.
- **Screen-Off Timers, Survivability & PRs**: Android counts rests, EMOMs, and timed holds on the lock screen with Live Update chips, wake locks, and beeps; iOS posts local notifications when locked. Active sessions, timers, and drafts survive background process death, with automatic PR detection (including heaviest and longest carry records).

### 🧘 Guided Mobility Flows
- **5 Follow-Along Flows**: Two pre-training warm-ups (*Hips and shoulders* 8 min, *Wrists and fingers* 5 min) and three post-training or off-day flows (*Spine and hamstrings* 10 min, *Core on the mat* 18 min, *Full reset* 25 min).
- **Move-by-Move Execution**: Preview every move's duration and cue on its sheet, then follow along on the workout screen with Hold/Move prompts, 10-second transitions between moves, 5-second side switches, spoken move and side announcements, and Live Update notifications — leaving your Timer settings untouched.
- **Smart Today Pairing & Stretching Sync**: Automatically leads Today with a pre-class flow on sport days (*Wrists and fingers* before climbing, *Hips and shoulders* before BJJ/running), offers time-matched flows in "Or pick by time", tracks mobility count in weekly summaries, and writes sessions to Apple Health and Health Connect as stretching.

### 📅 Unified History, Sport Days & Health Sync
- **Sport Days & Off-App Training**: Plan classes and days away from the app (BJJ, Climbing, Running, or any custom sport) weekly or on specific dates. One-tap **"Log it"** from Today or History (including past days, with Undo) counts toward your weekly goal, streak, and widget, while unlogged class days are never penalized as missed workouts.
- **Integrated Training Log**: Daily calendar view, 12-week volume charts, weekly goals, workout-kind breakdowns (lifts, intervals, mobility, sport days), and streak tracking.
- **Post-Workout Reflection ("How was it?")**: Log perceived exertion (Easy, Steady, Hard, Very hard, All out) and a note upon completing any workout or Lift session; reflected in history, share sheets, exports, Apple Health, and Health Connect.
- **Routines & Reminders**: Schedule recurring interval workouts, Lift program days, and sport days by weekday with reboot-resilient, self-extending daily reminders.
- **Health Platform Sync (Apple Health & Health Connect)**: Dynamic calorie estimates based on activity, duration, and body weight. Syncs completed workouts, mobility flows, and Lift sessions (with intervals/sets, pauses, calories, and effort notes) to Apple Health (iOS) and Health Connect (Android), and reads smartwatch average/peak heart rate and 30-day scale weight on opt-in.

### 🔒 Privacy-First & Offline
- **Zero Tracking**: No accounts, no sign-ups, no ads, and zero analytics or telemetry.
- **Local SQLite Storage**: Encapsulated Room multiplatform databases running locally on device storage with atomic transactions and foreign-key integrity.
- **Full Data Ownership & Portability**: Export your entire workout history, sport days, presets, sequences, routines, settings, Lift sessions, programs, exercises, and gear to versioned JSON (schema 7), and import files from schemas 1–7 safely with duplicate prevention and domain range validation.
- **Android Backup Support**: Native integration with Android system backup and device-to-device migration.
- **Accessibility**: Semantic headings, full TalkBack and VoiceOver navigation, and reduced-motion mode.

---

## 🏗️ Architecture & Tech Stack

Interval is designed around modern multiplatform architecture principles, cleanly separated into 32 modules in five folders:

```
interval/
├── apps/                # The app and its hosts
│   ├── app/             # Composition root: Navigation 3 back stack, AppKoin, About, export/import, Set up
│   ├── androidApp/      # Android host, manifest, launcher shortcuts & Glance home-screen widget
│   ├── desktopApp/      # Desktop JVM host (Compose Hot Reload ready)
│   └── iosApp/          # iOS Swift host, Home Screen quick actions & Xcode wrapper
│
├── core/                # What both sides build on
│   ├── core/            # Design tokens, IntervalTheme, NavigationArea, components
│   ├── core-platform/   # Clocks, timestamp formatter, HealthStore, storage plumbing (zero Compose dependencies)
│   ├── core-time/       # Pure calendar & clock maths (zero dependencies)
│   └── core-testing/    # MainDispatcherTest, UI test harness, fake platform services
│
├── interval/            # Conditioning, mobility, Today & history
│   ├── interval-domain/       # Timer engine, multi-block courses, mobility flows, sport days, calories, parsers
│   ├── interval-data/         # Room database (AppDatabase v13), DataStore settings, per-store repositories
│   ├── interval-platform-api/ # What features ask of the platform: cue player, workout off screen, reminders
│   ├── interval-platform/     # Audio/speech cues, Android Foreground Service, iOS notifications
│   ├── interval-ui/           # Shared workout widgets, Timeline, PhaseColors, animated Splash
│   ├── interval-testing/      # FakeStorage, RecordingCuePlayer, fake DAOs, in-memory stores
│   ├── interval-timer/        # Precision Timer page and natural language describe sheet
│   ├── interval-workout/      # Live workout & flow screen, circular countdown readout, summaries
│   ├── interval-presets/      # Train's Conditioning pillar: presets, sequence block editor, custom cards
│   ├── interval-mobility/     # Train's Mobility pillar: the flows, their sheets, and the editor of your own
│   ├── interval-history/      # Today home tab, History calendar & training log, routine & sport day planner
│   └── interval-settings/     # Settings page (from every tab), Health Connect / Apple Health, export & import
│
├── lift/                # Lift, the strength side (code in dev.justinsmith.lift)
│   ├── lift-domain/     # Exercises, programs, duration estimates, starter program, EMOM/hold/rest clocks, PRs
│   ├── lift-api/        # The ports between sides (LiftActivity, IntervalActivity)
│   ├── lift-data/       # Room database (LiftDatabase v16), per-store repositories
│   ├── lift-platform/   # Beeper, TimerService keep-alive, platform Koin module
│   ├── lift-ui/         # Lift widgets, exercise formatters, BalanceBars, cues mapping
│   ├── lift-testing/    # FakeLiftStorage, RecordingBeeper, LiftTestClock, fake DAOs
│   ├── lift-today/      # Train's Strength pillar, muscle balance, gear inventory (bells, bar, bands, records)
│   ├── lift-session/    # Active session logger, SessionTimers, programs, block editor & progress
│   ├── lift-library/    # Exercise library, search, filters, exercise editor
│   └── lift-nav/        # Lift's route keys and nav entries
│
├── tooling/             # Build plugins
│   ├── detekt-plugin/   # Detekt with Compose rules
│   └── lint-plugin/     # ktlint checks & formatting
│
├── journeys/            # End-to-end on-device user flows (android CLI)
└── tools/               # Icon generators, module boundary, iOS shard & version parity scripts
```

### Key Libraries & Tools
- **UI Framework**: [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) (Desktop, Android, iOS) + [AndroidX Glance](https://developer.android.com/develop/ui/compose/glance) for the Android home-screen widget
- **Navigation**: [Jetpack Navigation 3](https://developer.android.com/guide/navigation) with adaptive multi-pane layouts and pillar transitions
- **Dependency Injection**: [Koin](https://insert-koin.io/) & `koin-compose` with process-scoped initialization, narrow store ports, feature modules, and compile-time verification (`VerifyAppModulesTest`)
- **Database & Persistence**: [Room KMP](https://developer.android.com/kotlin/multiplatform/room) with bundled SQLite driver + AndroidX DataStore
- **Health Platforms**: [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) on Android and [HealthKit / Apple Health](https://developer.apple.com/documentation/healthkit) on iOS for workouts, mobility flows, sets, calorie logs, heart rate, and weight reads
- **Build Toolchain**: [Kotlin Toolchain (Amper)](https://kotlin-toolchain.org/0.13/) — **No Gradle daemon, zero `build.gradle.kts` files**
- **Architecture Guide**: For in-depth rules and layer boundaries, see [docs/architecture.md](./docs/architecture.md).

---

## 🚀 Getting Started

### Prerequisites

The repository includes self-bootstrapping wrappers (`./kotlin` for macOS/Linux, `kotlin.bat` for Windows) for the **Kotlin Toolchain**. The wrapper automatically provisions the pinned toolchain version, Java runtime, and core dependencies on its first run.

- **Desktop (JVM) & Android**: JDK and Android SDK components are auto-provisioned by the toolchain.
- **iOS**: Requires macOS with Xcode installed to compile Kotlin/Native frameworks.
- **Tools**: Python 3 is required for architecture rule validation (`tools/check_module_rules.py`), iOS test shard checks (`tools/check_ios_shards.py`), and version parity checks (`tools/check_versions.py`).

### Common Commands

```sh
# Build everything (including iOS link on macOS — run before calling work done)
./kotlin build

# Fast compile loop (common + JVM only)
./kotlin build -p jvm

# Fast local CI check (~3 min: ktlint + Detekt + JVM & Android tests)
./kotlin check lint detekt && ./kotlin test -p jvm -p android

# Run code formatters
./kotlin do format

# Run ktlint or Detekt individually
./kotlin check lint
./kotlin check detekt

# Verify architectural module boundaries, diagram sync, iOS shards & version parity
python3 tools/check_module_rules.py
python3 tools/check_ios_shards.py
python3 tools/check_versions.py
```
*(Avoid running a bare `./kotlin check` locally: it also links all 19 iOS test binaries in Release mode without caching).*

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
- **Acceptance Specs**: End-to-end user flows validated on the JVM (`apps/app/test@jvm/acceptance/` and `lift/lift-nav/test@jvm/acceptance/`).
- **On-Device Journeys**: XML-driven end-to-end flows in `journeys/*.xml` executed via the `android` CLI.

For testing architecture, harnesses, and test fakes, see [docs/testing.md](./docs/testing.md).

---

## 📚 Documentation

- [Architecture Guide](./docs/architecture.md): System architecture, Navigation 3 backstack, Room multiplatform, and background execution.
- [Testing Strategy](./docs/testing.md): Compose UI desktop harness, fakes, and on-device journeys.
- [Release Guide](./docs/release.md): Release preparation, R8 optimization, keystore setup, and deployment checklist.
- [Sprint 0.5](./docs/sprint-0.5.md): The current sprint's tickets, order and definition of done; [Sprint 0.4](./docs/sprint-0.4.md), [Sprint 0.3](./docs/sprint-0.3.md) and the [architecture sprint](./docs/sprint-architecture.md) before it.
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
