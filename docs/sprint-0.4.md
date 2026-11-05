# Sprint 0.4 — "Richer shapes: sets and ladders"

Two weeks, 2026-11-30 to 2026-12-11, one developer and Claude Code, about ten working days. The goal is a
shippable 0.4.0 on Android whose headline is what an interval timer is most asked for and this one can't yet do:
sets of rounds with a longer rest between them, and ladders whose work climbs or falls a step each round. It
comes after the architecture sprint on purpose: that sprint moved the running workout out of its ViewModel
(`WorkoutSession`, `WorkoutRun`), so the engine can change under one owner. What landed before and how a release
goes: [release.md](release.md) and [CHANGELOG.md](../CHANGELOG.md).

## Context

Every ticket of [sprint-0.3.md](sprint-0.3.md) and [sprint-architecture.md](sprint-architecture.md) is built,
their leftover candidates too, and the home-screen widget 0.3 named as 0.4's candidate shipped after its sprint
(Glance, in `androidApp`). Nothing has shipped to a store: no tag exists, so the changelog's 0.2.0 and 0.3.0 both
go out as `v0.3.0`. That is ticket 0, and the only one mostly the user's.

The shape today is flat: `IntervalConfig` holds prep, work, rest, rounds, warm-up and cool-down, and
`WorkoutEngine` steps from one phase to the next by a `when`, taking each length from the config, with
`sessionRemainingMillis` worked out in closed form on the assumption that every round is the same. Sets and
ladders break that assumption, so the first code ticket gives the engine a course to walk, a pure refactor
with every test green, and the two shapes then build on it. The parser already reads "sets" as a synonym
for rounds; a second count is what makes it sets of rounds.

The one open finding of the 2026-10-31 review is **C2**, `IntervalRepository` doing six jobs; Lift's twin was
split in the last sprint, and the same split is the last ticket above the cut line.

## Definition of done (every ticket)

- `./kotlin build` (full, links iOS), `./kotlin check lint detekt`, `./kotlin test -p jvm -p android`,
  `python3 tools/check_module_rules.py` and `python3 tools/check_ios_shards.py` pass. A `-p jvm` pass alone is
  not done.
- A user-facing journey starts with a failing acceptance spec (`apps/app/test@jvm/acceptance/`, or Lift's);
  logic in the module's `test/`, screens in `test@jvm/`; strings in that module's `strings.xml`.
- A `journeys/*.xml` added or updated when on-device behaviour changed, and run on `amper-37`.
- A line under `## [0.4.0] — Unreleased` in `CHANGELOG.md`. A schema change bumps the `@Database` version with
  a migration and commits the `schemas/` JSON; a change to the bundle bumps the export's `SCHEMA` once per
  release (the whole sprint ships as schema 4).
- A changed convention updates `CLAUDE.md` and `docs/architecture.md` in the same commit; a resolved finding is
  marked in `docs/architecture-review.md`.
- One commit per ticket on `main` (`jj`), message in the repo's `Area: what changed` style, linear history.

Sizes: XS ≤ half a day, S ≈ 1 day, M ≈ 2 days, L ≈ 3 days. Tickets 0 to 4 come to ten days. The cut line is
after ticket 4.

## The backlog, in order

### Week 1 — ship, then the engine walks a course, then sets

**0. Release 0.3.0** · S, mostly the user
- The four repository secrets, the hosted privacy policy URL (`PRIVACY.md`'s GitHub page), the Play Console's
  Health apps declaration (exercise and total calories burned written; body weight and heart rate read),
  `git tag v0.3.0 && git push origin v0.3.0`, upload the AAB to internal testing. Steps: [release.md](release.md).
  Both `## [0.2.0]` and `## [0.3.0]` get the tag's date: 0.2.0 was never tagged, so this is its first release too.
- Claude Code meanwhile runs every `journeys/*.xml` on `amper-37` against the release build (R8 on), then bumps
  `versionName` to `0.4.0` and `versionCode` to 4 in `apps/androidApp/module.yaml` and the pbxproj, and opens
  `## [0.4.0] — Unreleased` in the changelog.
- Done when: CI's `release` job has attached `interval-v0.3.0.aab`, Play's review has the declaration, and
  `main` says 0.4.0.

**1. The engine walks a course** · M, a pure refactor
- `Course.of(config): List<Interval>` in `interval/interval-domain/src/timer/`, an `Interval(phase, set, round,
  seconds)` per phase the workout passes through, prep to cool-down, worked out once from the config. `WorkoutState`
  gains `set` (1-based, 1 for every workout today) beside `round`, and the engine finds its place in the course by
  (phase, set, round): `advance` and `before` become the next and previous interval, `enter` takes the interval's
  seconds, `sessionRemainingMillis` is the time left in this interval plus the sum of those after, and
  `upcomingChanges` and `WorkoutOutline.of` read the course rather than replaying `advance`. `isLastRound` means
  the last round of the last set.
- One source of truth for what a config looks like: `Timeline` (`interval-ui`) draws `Course.of(config)` instead
  of its own prep/warm-up/rounds/cool-down arithmetic, and `IntervalConfig.totalSeconds` is the course's sum.
  `plannedWorkSeconds` (the calorie fallback for older history) sums the course's work up to the time elapsed.
- `StoredProgress` (`WorkoutProgressPreferences.kt`) saves `set` with a default of 1, so a workout saved by 0.3
  restores; `WorkoutProgress`, `CourseSpan` and the export are unchanged.
- Done when: `WorkoutEngineTest`, `WarmUpCoolDownTest`, `UpcomingChangesTest`, `WorkoutOutlineTest`,
  `WorkoutRunTest` and every acceptance spec pass unchanged; a new `CourseTest` pins the course of the four
  built-in presets, with and without bookends; `StatusBarTest` is unchanged.

**2. Sets of rounds, with a rest between** · L — the headline
- Domain: `IntervalConfig.sets` (1..20, `SETS_RANGE`, 1 is today's workout) and `setRestSeconds` (0..3600,
  `SET_REST_RANGE`, 0 is none), both in `sameShape`, `sanitized` and `init`'s requires; `ConfigField.Sets` and
  `SetRest` with their steps and quick picks (sets 2, 3, 4, 5; between 30 s, 1:00, 1:30, 2:00, 3:00).
  `Phase.SetRest` between sets, drawn in its own colour (`PhaseColorsOf`), and `Course.of` lays the sets out:
  each set is the rounds, the ordinary rest between rounds, a set rest after every set but the last. A set rest
  of 0 with sets above 1 is a plain rest between the sets' rounds, which still count per set.
- Timer and editor: a "Sets" row under the three shape rows and, once sets are above 1, a "Between sets" row
  (`StepperRows` in `interval-timer`'s `TimerParts.kt`, `EditorShapeCard` in `PresetEditor.kt`, both over
  `ConfigField`); `ConfigText` says "20s on · 10s off · ×8 · 4 sets · 1m between"; the default preset name becomes
  "20/10 ×8, 4 sets"; the total counts the set rests.
- The workout: `RoundStatus` says "Set 2 of 4 · Round 3 of 8 · 4:03 left" (one set shows as today), `RoundDots`
  are the set's rounds, `NextUp` gains `SetRest(clock)` and `Set(set, of)`, the summary's rounds are of
  `sets × rounds`. Cues: "Rest between sets" as a set rest starts, "Set two" with the set's first work
  (`Speech.SetRest`, `Speech.Work` gains the set; `SpeechQueue` words them). `WorkoutStatus` carries the set, so
  Android's notification and iOS's scheduled ones say the set too. Health Connect: `SetRest` is a rest segment.
  Calories: a set rest is recovery.
- `StatusBar`: a course of sets outruns the system's ten segments at once, so with sets above 1 it draws a
  segment per set in the work colour and one per set rest, when those are ten or fewer, else today's single
  segment; the points mark the sets' starts (four at most).
- Describe: "4 x (8 x 20/10), 1:00 between" and "4 sets of 8 rounds 20/10 with 1 min between sets" read as
  sets of rounds; one count alone keeps today's meaning ("8 sets of 20/10" is eight rounds). "between" or
  "between sets" names the set rest; without it the set rest is the quick pick 1:00. `RoutineDescription`
  (Plan) follows through `sameShape`.
- Storage: `AppDatabase` version 10, `sets` (default 1) and `set_rest_seconds` (default 0) on `history`, `preset`
  and `planned` by auto-migration, `schemas/10.json`; the two DataStore keys; `StoredProgress` saves both.
  Export: `ConfigExport.sets = 1` and `setRestSeconds = 0`, `SCHEMA` 4; `ImportPlanner` reads 1–4 and checks the
  two ranges; a 0.3 build refuses a schema-4 file with "update the app", as it already does for a newer schema.
- Done when: `WorkoutEngineTest` and `CourseTest` run a 2 × 3 workout through its set rest and its last round;
  `WorkoutDescriptionTest` reads the forms above and keeps "8 sets of 20/10" as rounds; `StatusBarTest` draws the
  per-set bar; `IntervalRepositoryTest` and `AppDataImporterTest` round-trip the two fields, and a schema-3
  fixture reads with sets 1; `AppMigrationTest` covers 9 → 10; `SetsAcceptanceTest` sets 2 sets of 3 rounds on
  the Timer, starts, hears the set rest and finishes; `journeys/sets.xml` runs it on `amper-37` and reads the
  notification's "Set 2 of 2".

### Week 2 — ladders, then the interval repository split

**3. Ladders** · M
- Domain: `IntervalConfig.workStepSeconds` (−300..300 by 5, `WORK_STEP_RANGE`, 0 is off): round r works
  `workSeconds + (r − 1) × step`, climbing or falling, and starts over with each set. `init` requires every
  round's work in `WORK_RANGE` (a config that would fall below five seconds is refused, as rounds 0 is);
  `sanitized` shortens the step so the last round fits. In `sameShape`. `Course.of` gives each round its own work,
  so the engine, the Timeline (rising or falling steps), the outline, the status bar, Health Connect's segments
  and the calories (the course's work summed) follow with no change of their own.
- Timer and editor: a "Ladder" row (`ConfigField.WorkStep`, quick picks −10, −5, +5, +10, +15, +30) under the
  shape rows; `ConfigText` says "20s on, +10s a round · 10s off · ×5"; the default preset name "20→60/10 ×5".
  The readout's clock is the round's own length; `NextUp.Work` says the coming round's length when it differs.
- Describe: "ladder 20 to 60 by 10", "20/10 climbing 10 a round", "60 down to 20 by 10, 10 off"; a span with a
  step sets the rounds ((60 − 20) / 10 + 1), and a span the step doesn't divide is read to the last round that
  fits.
- Storage: `AppDatabase` version 11, `work_step_seconds` (default 0) on the three tables, `schemas/11.json`; a
  DataStore key; `StoredProgress`; `ConfigExport.workStepSeconds = 0` within schema 4 (unreleased with ticket
  2, so no bump); `ImportPlanner` checks the range and the last round.
- Done when: `CourseTest` climbs, falls and starts over per set; `IntervalConfigTest` refuses a ladder that falls
  below five seconds and `sanitized` shortens it; `WorkoutDescriptionTest` reads the three forms;
  `AppMigrationTest` covers 10 → 11; `LadderAcceptanceTest` sets a climbing ladder on the Timer and sees the second
  round's longer clock; `journeys/ladder.xml` runs it.

**4. `IntervalRepository`, a repository per store** · M · C2, a pure refactor
- As Lift's split (C1, `tzpmumrl`): `SettingsRepository` (the DataStore: config, cues, app settings and the
  workout's progress, so `SettingsStore` and `WorkoutProgressStore`), `PresetRepository`, `HistoryRepository`
  and `PlanRepository`, each over the DAOs of its kind, sharing one `SerialWriter` so writes keep their order;
  the mappers in `IntervalMappers.kt`. `IntervalRepository` stays the root for what only the app does (`ready`,
  `writeErrors`, `importAll`, `close`); Koin binds each store to its repository. It drops
  `@Suppress("detekt.TooManyFunctions")`.
- After 2 and 3 rather than before, so the new columns land in one mapper file once, not in a file the split
  then moves.
- Done when: `IntervalRepositoryTest` splits into a test per repository with the same cases; every acceptance
  spec passes unchanged; C2 is marked resolved in `architecture-review.md` and `architecture.md`'s debt list.

--- cut line ---

**5. iOS: the launcher shortcuts** · S
- The two `UIApplicationShortcutItem`s in `Info.plist` ("Start a workout", "Start a Lift session", the
  `Shortcut.id`s as their types), and `AppDelegate`'s `performActionFor` in `apps/iosApp/src/iosApp.swift`
  handing the type to `ViewController(shortcut:)`, which gives it to `App(shortcut = …)` as Android's
  `MainActivity` does, cold and warm. The follow-up `release.md` has carried since 0.3.
- Done when: checked on the simulator (`run-on-device`, a long-press on the icon, both shortcuts, cold and with
  a workout running), and `release.md`'s follow-up drops.

**6. Small debts** · XS each, whoever has the afternoon
- **D4**: `SessionViewModel` still starts a session in `init`; the Lift tab's button and the shortcut start it
  through `SessionStore.startSession` before pushing `Lifting`, so the ViewModel only reads.
- **F2**: `observeSets()` streams every set, those of deleted sessions too, and re-maps the whole history on each
  insert; a query over live sessions, mapped per session with `distinctUntilChanged`.
- **G1's remainder**: a program block's load is still `kg`/`band`; make it a `Load` as a set's is, with its
  export unchanged in form.
- As built, in one commit: **D4**, the shortcut starts the session as the Lift tab and History already did, the
  ViewModel starts none, and a page restored after the process died on its summary closes rather than opening an
  empty session (`sessionGone`). **F2**, the query joins `session` and `SessionMapping` keeps each session's
  mapping until its rows change. **G1**, `Block.load: Load?` with `Load.named`, `withLoad` and `withDouble`; a
  block's band is read with the user's bands (`toProgram`, `decodePlan` and `decodeDraftDays` take them), so the
  Programs list names a band the user added; `Equipment.takesKg`, `suggestedLoad` and `plannedBand` are gone.

## Explicitly not this sprint (0.5's candidates)

- **Free-form sequences**: an arbitrary list of blocks ("3 min easy, then 8 × 20/10, then 5 × 1:00/0:30"), a
  pyramid in one shape. Sets and ladders cover most of what is asked; a block list is a new editor, a new
  Timeline and a new parser, a sprint of its own if the first two leave people wanting it.
- **AppFunctions** ("start a Tabata", "log a set"): `androidx.appfunctions` is still alpha and Android 16-only;
  a ticket once it is stable, over the same start routes the widget and the shortcuts use.
- **An iOS widget**: a WidgetKit extension of its own, after the Android one has been used for a release.
- **E5, import across the two stores** stays the documented trade-off: two Room databases can't share a
  transaction, and importing again finishes the job.
- **Localisation**, **Wear OS**, **iOS signing and an Apple team**: as in 0.3.

## Suggested order and calendar

| Day | Tickets |
|---|---|
| 1 | 0 release 0.3.0 (user: secrets, policy URL, Play declaration, tag; Claude: journeys, bump to 0.4.0), start 1 |
| 2 | 1 the course |
| 3–5 | 2 sets |
| 6–7 | 3 ladders |
| 8–9 | 4 the repository split |
| 10 | Buffer: full `./kotlin build`, every journey on the release build, tag `v0.4.0`; start 5 if green |

0 first so every later commit lands on a released `main`. 1 before 2 and 3 because both need a course the
engine walks, and a refactor with every test green is cheaper before the tests grow. 2 before 3 because sets
decide what a ladder starts over at, and 2 carries the export's bump so 3 lands inside the same schema. 4 after
both because it moves the mappers the two add to, and it changes no behaviour, so it is the one to drop if the
shapes run long.

## Risks

- **The engine's closed forms.** `sessionRemainingMillis`, `plannedWorkSeconds`, the Timeline and the summary's
  ring each assume uniform rounds today. Mitigation: ticket 1 moves every one onto the course before a shape
  changes, pinned by the existing tests; a closed form left behind shows as a wrong "left" on a ladder.
- **A restore across versions.** `StoredProgress` and the three tables gain fields with defaults, so 0.3's saved
  workout, presets and plans read as one set with no ladder. Mitigation: `AppMigrationTest` for each version, and
  a schema-3 fixture through the importer.
- **"Sets" in the parser** already means rounds. Mitigation: only a second count makes sets of rounds; the
  existing `WorkoutDescriptionTest` cases stay as they are.
- **The status bar's ten segments.** A 4 × 8 course is 60-odd intervals. Mitigation: a segment per set, decided
  in `StatusBar` (tested), the service unchanged.
- **The widget and the shortcuts read the shape too**: the widget's week counts workouts, not rounds, so it is
  untouched; a shortcut starts whatever the Timer holds, sets and all.

## Verification of the sprint as a whole

1. `./kotlin check lint detekt`, `./kotlin test -p jvm -p android` and `./kotlin build` green on `main`; CI green
   on `v0.4.0` with the AAB attached.
2. The release build on `amper-37` (`run-on-device`) passes every `journeys/*.xml`, including `sets.xml` and
   `ladder.xml`.
3. A 0.3 export imports with every workout, preset and plan as one set and no ladder; a 0.4 export imports again
   adding nothing; a 0.4 file on a 0.3 build is refused with "update the app".
4. A 4 × 8 Tabata runs through its three set rests with the chip saying the set, writes to Health Connect with
   its rests, and shows "32 of 32 rounds" on its summary; a 20→60 ladder's Timeline rises.
5. `CHANGELOG.md` 0.4.0 lists tickets 1–3 (4 changes nothing a user sees); About shows 0.4.0 on Android and iOS.
