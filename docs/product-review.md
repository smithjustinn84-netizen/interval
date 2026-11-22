# Product & project review — 2026-11-22

A product, UX, and project-management review of Interval (35 modules, ~86k lines of Kotlin and Swift, as of commit `0f3cd076`) focused on what to fix before releasing, what dead weight to cut, where daily user friction lives, and what takes the app from a strong `0.6.0` craftsman build to a flagship `1.0.0` release.

Each item carries a stable id (**B1**, **C1**, **P1**, **F1**, …) so future sprint plans and [backlog.md](backlog.md) can cite it directly. File paths and counts reflect commit `0f3cd076`.

---

## Summary

The engineering and visual craft are already well above indie-release bar: the **Track & Chalk** design system, SkSL/AGSL chronograph ring and heart-rate trace shaders, zero-Gradle Kotlin Toolchain (Amper) build, strict 35-module layering enforced by `tools/check_module_rules.py`, offline-first privacy, and kettlebell coaching intelligence are genuine differentiators.

The risks holding the app back from `1.0.0` trace to **two product-management tensions**:

| Tension | How it shows up |
|---|---|
| **1. Scope drift vs. shipping discipline.** | `v0.5.0` was never tagged, and `sprint-0.6.md` expanded from a 5-ticket, 2-week sprint into 15+ tickets plus Polar BLE integration, custom shaders, and a 3D icon—producing 20 Room versions (`AppDatabase`), 19 (`LiftDatabase`), and 16 export schemas before `v1.0`. |
| **2. Personal power-tool vs. broader athlete product.** | Key flows assume one very specific setup (BJJ + metric kettlebells + Polar armband on Android), leaving high-intent adjacent users (US `lb` lifters, bodyweight/barbell athletes, standard BLE strap owners, iOS sensor users) blocked or underserved despite the underlying engine already supporting most of them. |

---

## 1. Ship blockers & release hygiene

Fix these before tagging `v0.6.0` or uploading to Play.

### B1. Missing third-party license notices
- **Where**: `apps/app/src/about/Credits.kt`
- **Issue**: `interval/interval-platform/module.yaml` pulls `com.github.polarofficial:polar-ble-sdk:8.6.0` (along with transitive RxJava, Retrofit, and Protobuf), and `apps/androidApp` / `core/core-platform` ship AndroidX Glance and Health Connect—none of which are listed in `libraryCredits` or bundled license texts.
- **Fix**: Audit shipped runtime artifacts across Android and iOS, add Polar BLE SDK (and any required transitive notices) plus Glance and Health Connect to `Credits.kt`, and include required license files in `apps/app/composeResources/files/`.
- **Done (2026-11-22)**: audited from `./kotlin show dependencies -m androidApp` and each artifact's POM (iOS ships a subset of the same). `libraryCredits` now has Glance, WorkManager and Health Connect beside Jetpack's, and Polar's SDK and what it brings: Protocol Buffers (BSD 3-Clause, also inside DataStore, Glance and Health Connect), RxJava, Reactive Streams (CC0), Retrofit and OkHttp, Gson and FlatBuffers, Apache Commons IO (with its NOTICE), Guava and the Checker Framework qualifiers (MIT), and the annotation jars. Polar's own agreement asks for its notice in every copy, so its text ships whole (`files/polar-sdk-license.txt`, taken from the SDK's 8.6.0 tag), as do Protobuf's, Checker's and Commons IO's. A credit whose licence text opens with its holder's copyright names its own file (`Credit.file`).

### B2. Unreleased changelog pile-up
- **Where**: `CHANGELOG.md`, `docs/sprint-0.6.md`
- **Issue**: `CHANGELOG.md` holds two unreleased headers (`[0.5.0] — Unreleased` and `[0.6.0] — Unreleased`), while `v0.4.0` remains the latest git tag.
- **Fix**: Merge the unreleased `0.5.0` and `0.6.0` sections into one clean `0.6.0` release entry (or tag `0.6.0` directly), archive `sprint-0.6.md` under `docs/archive/`, and enforce one tagged release per sprint going forward.

### B3. Unverified Polar hardware & R8 release surface
- **Where**: `interval/interval-platform/src@android/platform/PolarClassSensor.kt`, `PolarRecorders.kt`, `PolarHeartStream.kt`, `apps/androidApp/proguard-rules.pro`
- **Issue**: Per [backlog.md](backlog.md), full-length mat recordings, H10 exercise recordings, BLE reconnection, contact-loss handling, and battery reporting have not yet been verified on physical sensors. Polar's SDK also pulls Retrofit, RxJava, and Protobuf into the R8 minification surface (`docs/release.md`).
- **Fix**: Run a full R8 release APK smoke test with real Verity Sense and H10 hardware (pair, live stream, reconnect, record, sync) before shipping a public Play build with the sensor enabled.

### B4. Documentation & metric drift
- **Where**: `README.md`, `docs/index.md`
- **Issue**: Numbers in top-level docs lag behind the codebase:
  - Export schema is documented as **15** in `README.md`, but `ExportBundle.SCHEMA` is **16** (`apps/app/src/export/ExportBundle.kt`).
  - `LiftDatabase` is documented as **v18** in `README.md`, but `LiftDatabase` is **v19** (`lift/lift-data/src/data/LiftDatabase.kt`).
  - Built-in programs are documented as **27** in `README.md` and `docs/index.md`, while `BuiltInPrograms.kt` defines **34** `ProgramId`s (**29** active after filtering the 5 `retiredPrograms`).
- **Fix**: Sync counts across `README.md` and `docs/index.md`, or avoid repeating volatile counts in prose where a single source of truth suffices.
- **Done (2026-11-22)**: the programs read 17 offered since **C3**; `README.md` now says export schema 16 and `LiftDatabase` v19.

---

## 2. Clean up & drop dead weight

Reduce maintenance drag before it calcifies after `1.0`.

### C1. Batch schema & export bumps per release
- **Where**: `interval/interval-data/src/data/AppDatabase.kt` (v20), `lift/lift-data/src/data/LiftDatabase.kt` (v19), `apps/app/src/export/ExportBundle.kt` (schema 16)
- **Issue**: Bumping Room versions and export schemas on every feature commit creates dozens of schema JSONs and migration edges (`AddActivityIfMissing`, `sibling_session` drop, 16 export schemas) before the first wide public release.
- **Action**: Keep migrations intact for installed users, but adopt a strict rule going forward: **bump `ExportBundle.SCHEMA` at most once per release**, folding all fields added during an unreleased cycle into that release's schema version.

### C2. Prune legacy navigation shims after migration window
- **Where**: `apps/app/src/nav/Routes.kt` (`Presets` route), `apps/app/src/BackStack.kt` (`rootAtToday`, `timerUnderTrain`)
- **Issue**: `Presets` is a dead route kept solely to strip pre-`0.5` saved back stacks, and `Timer` carries `@SerialName("dev.justinsmith.interval.nav.TimerTab")`.
- **Action**: Once active installs are past `0.6.0`, schedule removal of `Presets` and `timerUnderTrain` (keep `@SerialName` only as long as older saved states exist).

### C3. Curate the built-in program catalogue
- **Where**: `lift/lift-domain/src/domain/BuiltInPrograms.kt`, `BarbellPrograms.kt`
- **Issue**: 29 active programs (plus 5 `retiredPrograms` still defined in code) across 6 families create choice paralysis—especially Barbell programs that `Set up` cannot recommend.
- **Action**: Trim overlapping programs down to a tighter, opinionated set (~12–16 standout programs with clear equipment/goal tags) and place Barbell behind an equipment filter so kettlebell/bodyweight users aren't shown irrelevant programs.
- **Done (2026-11-22)**: 17 offered (15 without a bar), 12 more retired (kept in code, since a program is stored by its id), each offered one with a "who it's for" line, and Barbell folded behind "Show barbell programs" unless a bar is owned. `ProgramsTest` pins the list.

### C4. Make an explicit call on `desktopApp`
- **Where**: `apps/desktopApp/src/main.kt` (43 lines), `README.md`, `docs/index.md`
- **Issue**: Desktop lacks Health sync, HR sensors, notifications, widgets, and app-icon switching, yet is advertised alongside Android and iOS.
- **Decided 2026-11-22: Option A.** The desktop is the developer harness; README, the docs site and `mkdocs.yml`
  no longer offer it to users, and a platform feature needs no desktop actual beyond a no-op. Option B is kept
  below as declined.
- **Action**: Choose one clear identity instead of staying in limbo:
  - **Option A (Lean)**: Position `desktopApp` explicitly as a developer Hot Reload & UI-testing harness, removing user-facing desktop promises from `README.md` and the docs homepage.
  - **Option B (Product)**: Make it a purposeful desk/gym-TV companion—keyboard shortcuts across Timer and Lift, compact always-on-top timer window, full-screen Gym TV mode, and CSV export.

### C5. Consolidate duplicate tooling & UI boilerplate
- **Merge `scripts/` into `tools/`**: `scripts/render_store_frames.py` is the only file in `scripts/`, while all other automation (including `tools/render_icon_3d.py`) lives in `tools/`. Move it into `tools/` and remove `scripts/`.
- **Unify `EmomScreen` and `DensityScreen`**: `lift/lift-session/src/session/EmomScreen.kt` (485 lines) and `DensityScreen.kt` (503 lines) duplicate screen chrome, ring/clock layout, and live heart-rate badge wiring. Extract a shared `TimedBlockScaffold`.
- **Deduplicate shared copy helpers**: `EffortWords.kt` and `ShareWords.kt` are mirrored across `interval-history` and `lift-session`. Extract shared effort labels into `core` so wording stays consistent across both sides.
- **Done (2026-11-22)**: `render_store_frames.py` is in `tools/` and `scripts/` is gone; `TimedBlockScaffold` draws both pages, `EmomScreen` and `DensityScreen` now 126 and 160 lines mapping their state onto it; the effort words are core's `effortWord(level)`, the three copies gone. `ShareWords` stays on each side: the workout summary's, a finished workout's and a session's word different things and share no wording.

### C6. Isolate or lighten Polar SDK dependency
- **Where**: `interval/interval-platform/module.yaml`
- **Issue**: `polar-ble-sdk` on JitPack brings RxJava, Retrofit, and Protobuf into `androidApp`, supports only two Polar models, and has no iOS implementation.
- **Action**: Decouple live heart-rate streaming onto standard BLE GATT (`0x180D`, see **F1**) so live HR has zero third-party SDK overhead on both Android and iOS, leaving the Polar SDK scoped strictly to on-sensor class recording (or an optional flavor/module).
- **Done (2026-11-22), the module half**: Polar's SDK is held to `interval-polar-platform`, an Android-only module of `PolarClassSensor`, `PolarHeartStream`, `PolarRecorders` and `polarSensorModule`, which the app folds into the interval platform's on Android alone; `interval-platform` no longer carries the SDK or names JitPack. Nothing behaves differently. The SDK still ships in `androidApp`, since class recording needs it. Live heart rate over GATT `0x180D` is left to **F1**, with pairing for any strap, the iOS side and the hardware testing it needs. **The other half, on Android (2026-11-22)**: the live heart rate is off the SDK and on GATT, every sensor's (F1's note).

---

## 3. High-impact UX & product polish

Targeted fixes for the sharpest daily user friction.

### P1. Support pounds (`lb`) across Lift and `Set up`
- **Where**: `lift/lift-domain/src/domain/Load.kt`, `apps/app/src/weeksetup/WeekSetupScreen.kt`, `lift/lift-ui/`
- **Issue**: Settings already supports `WeightUnit.Lb` for body weight (`WeightSheet.kt`), but `Set up` ("Which bells do you own, in kg?") and every Lift picker, plate loader, PR, and benchmark are hardcoded to kilograms.
- **Improvement**: Keep internal storage in `kg` (or canonical bell weights) and add display/picker support for standard `lb` kettlebells (18, 26, 35, 44, 53, 70 lb) and `lb` barbell plates (45 lb bar), respecting the user's unit preference.
- **Done (2026-11-22)**: storage stays in kg (both databases and the export, no schema bump); `WeightUnit` is in `core-time`, `LocalWeightUnit` in core, and Lift reads it through `IntervalActivity.weightUnit`. Bells show as US sizes (18, 26, 35, 44, 53, 70 lb and the rest of the 19), bars as 25, 35 and 45 lb with plates by the pound, and barbell steps, an easier day's step down, sets, records and tonnage follow the unit. Set up asks kg or lb above the bells, and Settings has a Weight unit row. Checked by the Lift, Set up and settings tests and by drawing the Lift screens in pounds.

### P2. Make `Set up` equipment-aware beyond kettlebells + BJJ
- **Where**: `apps/app/src/weeksetup/BuiltWeek.kt`, `WeekSetupScreen.kt`, `lift/lift-domain/src/domain/StarterProgram.kt`
- **Issue**: `BuiltWeek.kt` returns `program = null` whenever `answers.bells.isEmpty()`, leaving bodyweight, barbell, band, and conditioning-only users with no starter program even though the app already ships those programs. Climbing and Running also fall back to generic beginner KB programs.
- **Improvement**: Ask what equipment the user has (Kettlebells, Barbell, Bands, Pull-up bar, Bodyweight / Floor, Conditioning only) and pair Climbing and Running with tailored starter tracks.
- **Done (2026-11-22)**: built in `3992a313` (Set up asks the equipment, and Climbing and Running have tracks of their own).

### P3. Post-workout editing & Log search/filtering
- **Where**: `interval/interval-history/src/history/WorkoutDetailViewModel.kt`, `lift/lift-session/src/history/LiftDetailViewModel.kt`, `HistoryScreen.kt`
- **Issue**: Once a workout or Lift session is saved, users cannot edit the effort rating, note, or correct a mistyped set/rep on the detail page. The Log's *All time* view also has no search or kind filter once months of entries accumulate.
- **Improvement**: Add inline editing for effort, notes, and logged sets on detail pages, plus a compact filter bar (Strength / Conditioning / Mobility / Sport / PRs) and text search in Log.
- **Done (2026-11-22)**: built in `169bdd11` (editing on both sides' detail pages, and the All-time Log's search and filters).

### P4. Warm-up set tagging in Lift
- **Where**: `lift/lift-domain/src/domain/`, `lift/lift-session/src/session/LogSheet.kt`
- **Issue**: Lift's voice coaching instructs *"Groove five light first"*, yet logging warm-up sets currently counts them as working sets—distorting progression, density pace, and tonnage.
- **Improvement**: Add a single-tap **Warm-up** toggle on logged sets, excluded from working-set progression and pace calculations while preserved in session history.
- **Done (2026-11-22)**: a set carries `SetLog.warmUp` (`LiftDatabase` v20's `warm_up` column, exported in schema 16). The Log sheet has a Warm-up switch that turns off again once its set is logged, and the next-set card says "Next set · Warm-up" while it is on. A set logged is made a warm-up, or a working set again, from its row, in the session or on its page in the Log. A warm-up is listed as "12 kg × 10 · Warm-up". It counts toward no record, planned set ticked off, step up, density round, pace, challenge, test, suggested load, trend line or session total (`List<SetLog>.working()`, `LiftSession.workingSets`), but the grip's load, the muscles worked, a first set at a new bell and the time sent to the health store still take it.

### P5. Reorganize Settings as it scales
- **Where**: `interval/interval-settings/src/settings/SettingsGroups.kt` (636 lines, 9 stacked groups)
- **Issue**: Settings has grown to Cues, Workout, Calories, Health, Sensor, Plan, Appearance, Data, and About in one long scroll list.
- **Improvement**: Consolidate into tighter, scannable sections (*Audio & Cues*, *Body & Heart Rate* grouping weight, HR zones, Health Connect/Apple Health, and sensor, *Plan & Schedule*, *Appearance*, *Data & Backup*, *About*), moving dense groups (like Data & Backup or Audio & Cues) to dedicated sub-pages where helpful.
- **Done (2026-11-22)**: nine groups are five and About's foot. *Workout and cues* holds the prep, Keep screen on and the five cue switches in one card; *Body and heart rate* the weight, its unit, the max and resting heart rate and the effort, with the health store and the sensor in a card under it, each where the platform has one and no card where it has neither; *Plan*, *Appearance* and *Your data* stand as they were. No sub-pages: the densest group is seven rows, every row is a set-once tap that a page between would only add to, and Settings beside the Timer on a wide window would need navigation of its own. Checked by `SettingsScreenTest` and by drawing the page on a phone and a tablet with a store and a sensor.

### P6. Persist Lift session live heart-rate samples
- **Where**: `lift/lift-session/src/session/SessionViewModel.kt`, `lift/lift-data/src/data/LiftDatabase.kt`
- **Issue**: Documented in [backlog.md](backlog.md): Lift live HR samples currently live in ViewModel memory and are lost on process death, unlike Interval workouts which persist them with active progress.
- **Improvement**: Persist active session HR samples alongside the active timer/session row so background kills never drop the trace.
- **Done (2026-11-22)**: the session in progress keeps its samples in the `heart_rate_samples` column it already had (`SetStore.saveLiveSamples`, `SessionDao.setLiveSamples`, which leaves a finished session's alone), every six samples, half a minute, so a kill loses at most that much, and as the page closes over a session still going; the page opened again restores the sampler from them (`HeartRateSampler.restore(start, kept)`, as the workout does), its average and peak included. No schema change. `SessionViewModelTest` kills a session mid-trace and finishes it with the trace whole; `LiftRoomTest` holds the query.

---

## 4. Next-level features (1.0 & beyond)

Ordered by product leverage while staying 100% offline-first and privacy-preserving.

| ID | Priority | Feature | Value & fit |
|---|---|---|---|
| **F1** | **P1** | **Standard BLE Heart Rate (`0x180D`) on Android & iOS** | Connects any standard chest strap or armband (Garmin, Wahoo, Coospo, Polar, Coros) via native `BluetoothGatt` / `CoreBluetooth` on both platforms, unlocking the shader-rendered HR chart and rest-readiness guidance for almost every athlete without Polar-only lock-in. |
| **F2** | **P1** | **Program Completion Recap & Next-Cycle Coach** | Celebratory end-of-program summary (sessions completed, total tonnage, starting vs. ending bells/reps, PRs hit) with a one-tap coaching recommendation: *repeat heavier*, *deload week*, or *next program*. |
| **F3** | **P1** | **Shareable Track & Chalk Summary Cards (PNG)** | Render a clean, brand-styled image card (workout ring, interval waveform, HR zone trace, or Lift PR badge) for the native share sheet. Turns the app's best visual craft into organic word-of-mouth with zero cloud backend. **Done (2026-11-22)**: Share on either summary and on a workout's or a session's page in the Log opens core's `ShareCardSheet`, the card as it will be sent over Share picture and Share as text. The card (`ShareCard`, core) is laid out at 360 by 450 dp in a density of its own, dark, so it is 1080 by 1350 pixels everywhere: the interval side's (`WorkoutShareCard`, interval-ui) the timeline and the heart rate, Lift's (`SessionShareCard`) its records or lifts and the heart rate. It is captured from its graphics layer and encoded by `encodePng` (Skia, Android's `Bitmap`), and `Sharer.shareImage` offers it (iOS as a `UIImage`, so Save Image needs `NSPhotoLibraryAddUsageDescription`). Shared from the emulator as a 1080 by 1350 PNG; not yet checked: a card with a heart rate on Android (the chart's shader through the capture) and iOS's sheet. |
| **F4** | **P2** | **Unified "Progress / Trends" View in Log** | Brings together data already collected across the app: weekly volume and training load curve, 60-second HR recovery trend, PR timeline, and strength-test progression over time. |
| **F5** | **P2** | **Superset / Paired Blocks in Lift** | Lightweight `A1 / A2` alternating blocks (e.g., Press + Pull-up or Swing + Goblet Squat) with shared or alternating rest so common kettlebell pairings flow smoothly in a live session. |
| **F6** | **P2** | **CSV Export & Gentle Offline Backup Nudges** | Keep schema-versioned JSON for full restore; add flat CSV export for spreadsheets/coaches and a subtle monthly reminder (or Android SAF folder export) to back up local data. |
| **F7** | **P3** | **Audio Ducking & OEM Battery Reliability Check** | Ensure crisp audio focus ducking over podcasts/music during cues, plus an optional Settings check for aggressive Android OEM battery optimization (Samsung/Xiaomi/OnePlus) so screen-off timers never stall. |

- **F1, Android done (2026-11-22)**: any strap or armband of the standard Heart Rate service pairs on Android and streams over Android's own GATT (`AndroidHeartRateLink`, under core-platform's common `HeartRateStream` and `HeartRateService`), Polar's sensors too, so the SDK records classes alone; a strap that isn't a Verity Sense or an H10 is one that only streams, offered no class. A steady heart rate no longer leaves gaps in a trace (`LiveHeartRate.readings`). Built and tested against fakes; no sensor has met it yet (`journeys/live-heart-rate-strap.xml`). iOS's CoreBluetooth side is next.
- **F2 done (2026-11-22)**: a program's last day closes its summary onto the program's page ("See the recap" in place of Close), which once finished leads with the next cycle as a coach calls it (`programRecap`, lift-domain, pure): a few easy days first when under 75% of the plan was done, missed days' sets counted undone, or the sessions felt very hard or worse on average; a beginner's program done to 90% moves on to the next program for what is owned and the time a day took (`starterProgram` over the catalogue, no beginner's and no barbell without a bar), its button following it; any other goes again, saying so honestly: from the loads last lifted for a program naming none (its days take each lift's load from history), from its own loads otherwise. Under it each lift done twice or more, its best working set at the start beside the end ("16 kg × 10 → 24 kg × 10"), then the records. An easy week is advice only: nothing lightens the next run's loads.

---

## 5. Suggested 3-phase roadmap

### Phase 1 — Freeze, clean & ship `v0.6.0` *(~1 week, no new features)*
1. Complete **B1** (missing OSS licenses in `Credits.kt`).
2. Complete **B2** and **B4** (reconcile `CHANGELOG.md` into `0.6.0`, sync `README.md` / `docs/index.md` counts, archive `sprint-0.6.md`).
3. Move `scripts/render_store_frames.py` into `tools/` (**C5**).
4. Run full R8 release smoke test with real Polar hardware (**B3**).
5. Tag `v0.6.0` and upload to Play Internal Testing.

### Phase 2 — Broaden & polish for `v0.8.0` public beta *(2–3 weeks)*
1. Ship **`lb` support** across Lift and `Set up` (**P1**).
2. Make **`Set up` equipment-aware** for bodyweight, barbell, bands, and conditioning-only users (**P2**).
3. Add **post-workout editing**, **Log search/filters** (**P3**), and **warm-up set tagging** (**P4**).
4. Implement **standard BLE `0x180D` live HR** on Android & iOS (**F1**) and persist Lift HR samples across process death (**P6**).
5. Extract `TimedBlockScaffold` (`EmomScreen`/`DensityScreen`), deduplicate effort/share helpers (**C5**), and streamline Settings (**P5**).

### Phase 3 — Flagship `v1.0.0` launch *(2–3 weeks)*
1. Ship **Program Completion Recap** (**F2**), **Log Trends** (**F4**), and **Shareable Track & Chalk PNG cards** (**F3**).
2. Add **CSV export + backup nudges** (**F6**) and **Superset blocks** (**F5**).
3. Curate the built-in program catalogue (**C3**) and finalize the `desktopApp` scope (**C4**).
4. Complete iOS signing / TestFlight, verify the iOS Live Activity stale view on a physical iPhone (`backlog.md`), and launch `v1.0.0` across Android and iOS.
