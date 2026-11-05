# Sprint 0.5 — "Sequences, and the week on an iPhone's Home Screen"

Two weeks, 2026-11-09 to 2026-11-20, one developer and Claude Code, ten working days. The goal is a shippable
0.5.0 whose headline is the shape 0.4 left out on purpose: a workout as a sequence of blocks ("3 min easy, then
8 × 20/10, then 5 × 1:00/0:30"), each block the shape a workout has today, sets and ladders included. Beside it,
the week the Android widget shows, on iOS's Home Screen. What landed before and how a release goes:
[release.md](release.md) and [CHANGELOG.md](../CHANGELOG.md).

## Context

Every ticket of [sprint-0.4.md](sprint-0.4.md) is built, the cut line's too, and 0.4.0 is tagged (`v0.4.0`). Every finding of the 2026-10-31 review is resolved but E5, the documented trade-off.

0.4 gave the engine a course to walk: `Course.of(config)` lays out every interval, prep to cool-down, and
`WorkoutEngine`, the outline, the totals, the calories and the Timeline read it. A sequence is a longer course,
so the engine needs little; what still reads the config's flat fields is the readout's counts
(`WorkoutUiState`'s `rounds`, `setRounds`, `sets`, `nextUp`, `completedRounds`), `isLastRound`, the set cue,
the status bar's set boundaries, the Timeline's round marks, `ConfigText`, every store of a config and the
describe parser. And a place in the course is found by (set, round, phase) alone (`Interval.comparedTo`), which a
second block would make ambiguous. The first code ticket moves those onto the course, a pure refactor, before
the shape changes.

The shape: **the bookends around a list of blocks**. A `Block(workSeconds, restSeconds, rounds, sets,
setRestSeconds, workStepSeconds)` is exactly today's shape. `IntervalConfig` keeps its flat fields as the first
block and gains `then: List<Block>` (empty is today's workout) and `blockRestSeconds` (0..3600, 0 is none). Every
config, column, DataStore key and export that exists stays valid, and storage needs one JSON column, not a child
table. Whatever walks the blocks (`Course.of`, the editor, `ConfigText`) works on one derived `config.blocks`
(the first block, then `then`) and writes back through one `withBlocks(list)`, so no reader special-cases the
first. The rest between blocks is a `Phase.SetRest`: every `when (phase)` already knows that long rest (its
colour, `StatusColors`, Health Connect's and HealthKit's rest segments, recovery for calories); only its words
differ, chosen by the course, "Rest before block two" where the next interval's block changes.

The iOS widget was 0.4's "after the Android one has been used for a release" candidate. Nothing of it exists: the
Xcode project has one target, no App Group, no URL handling, and the toolchain's `xcode-integration` allows a
single target with the Kotlin build phase, so a WidgetKit extension is Swift alone, fed a snapshot by the app.
Its ticket opens with a spike.

## Definition of done (every ticket)

- `./kotlin build` (full, links iOS), `./kotlin check lint detekt`, `./kotlin test -p jvm -p android`,
  `python3 tools/check_module_rules.py` and `python3 tools/check_ios_shards.py` pass. A `-p jvm` pass alone is
  not done.
- A user-facing journey starts with a failing acceptance spec (`apps/app/test@jvm/acceptance/`, or Lift's);
  logic in the module's `test/`, screens in `test@jvm/`; strings in that module's `strings.xml`.
- A `journeys/*.xml` added or updated when on-device behaviour changed, and run on `amper-37`.
- A line under `## [0.5.0] — Unreleased` in `CHANGELOG.md`. A schema change bumps the `@Database` version with
  a migration and commits the `schemas/` JSON; a change to the bundle bumps the export's `SCHEMA` once per
  release (the whole sprint ships as schema 5).
- A changed convention updates `CLAUDE.md` and `docs/architecture.md` in the same commit.
- One commit per ticket on `main` (`jj`), message in the repo's `Area: what changed` style, linear history.

Sizes: XS ≤ half a day, S ≈ 1 day, M ≈ 2 days, L ≈ 3 days. Tickets 0, 1, 2, 3 and 5 come to ten days. The cut
line is after ticket 5; ticket 4 sits below it.

## The backlog, in order

### Week 1 — the course walks blocks, then sequences

**0. Open 0.5.0** · XS
- `versionName` 0.5.0 and `versionCode` 5 in `apps/androidApp/module.yaml` and the pbxproj
  (`tools/check_versions.py`), `## [0.5.0] — Unreleased` in the changelog.
- Done when: `main` says 0.5.0.

**1. The course walks blocks** · M, a pure refactor
- `Interval` gains `block` (0-based), and `Interval.comparedTo` (`interval-domain/src/timer/Course.kt`) orders by
  block, then set, round and phase. `WorkoutState` gains `block`, 0 for every workout today.
- `isLastRound` and `started`'s set cue (`WorkoutEngine.kt`) ask the course, not `config.rounds` and
  `config.sets`. The readout's counts come from the course: `WorkoutUiState`'s `rounds`, `setRounds`, `sets`,
  `nextUp` and `completedRounds`, and through them the summary, the share words, `WorkoutService`'s notification
  and iOS's scheduled ones (`PlatformServices.ios.kt`).
- `StatusBar`'s set boundaries and the Timeline's choice of round marks (`config.rounds <= MAX_ROUND_MARKS`) read
  the course.
- `StoredProgress` saves `block` with a default of 0, so a workout saved by 0.4 restores.
- Done when: every existing test and acceptance spec passes unchanged; `CourseTest` pins the order of
  positions across blocks.

**2. Sequences: the domain, the engine and storage** · L — the headline
- Domain: `Block`, `IntervalConfig.then` (at most 9 blocks after the first), `blockRestSeconds`
  (`BLOCK_REST_RANGE`), `blocks` and `withBlocks`; `init`, `sanitized` and `sameShape` cover every block, the
  ladder's last-round check per block. `Course.of` lays out each block as today's shape, a `SetRest` between
  blocks when `blockRestSeconds` is above 0, a plain `Rest` of the block's own when it is 0.
- The workout: `RoundStatus` says "Block 2 of 3 · Round 3 of 8" (a set within a block too: "Block 2 of 3 · Set 1
  of 2 · Round 3 of 8"; one block shows as today), `RoundDots` are the block's set's rounds, `NextUp` gains
  `Block(block, of, shape)`, the summary's rounds are the course's work intervals. Cues: `Speech.BlockRest`
  ("Rest before block two"), and the block's first work names it ("Block two. Work"), worded in `SpeechQueue`.
  `WorkoutStatus` carries the block, so the notifications say it.
- `ConfigText` reads "8 × 20/10, then 5 × 1:00/0:30" (each block as `configDescription` says it, joined), and the
  default preset name follows ("20/10 ×8, then 1:00/0:30 ×5").
- `StatusBar`: a segment per block, when ten or fewer, else today's single segment; the points mark the blocks'
  starts.
- Storage: `AppDatabase` version 12, `then_blocks` (TEXT, default `'[]'`, a JSON list) and
  `block_rest_seconds` (default 0) on `history`, `preset` and `planned` by auto-migration, `schemas/12.json`,
  mapped in `IntervalMappers.kt`; the two DataStore keys; `StoredProgress` saves both; `PresetEditor`'s
  field-by-field `saved()`/`restoredConfig()` takes them. Export: `ConfigExport.then = []` and
  `blockRestSeconds = 0`, `SCHEMA` 5; `ImportPlanner` reads 1–5 and checks each block's ranges; a 0.4 build
  refuses a schema-5 file with "update the app".
- Done when: `WorkoutEngineTest` and `CourseTest` run a three-block sequence, one block of sets with a ladder,
  through its block rests and its last round; `StatusBarTest` draws the per-block bar; `IntervalConfigTest`
  refuses a block out of range and `sanitized` clamps it; the repository tests and `AppDataImporterTest`
  round-trip the two fields, and a schema-4 fixture reads as one block; `AppMigrationTest` covers 11 → 12;
  `SequenceAcceptanceTest` starts a two-block preset, hears the block rest and finishes. `journeys/sequence.xml`
  moved to ticket 3: on a device a sequence can't be made before its editor.
- As built: the migration is covered in `StorageTest` (a version-11 database read at 12), as 10 and 11 were, and
  the schema-1 fixture stands for the older files; the rest between blocks is a `SetRest` told apart by
  `IntervalConfig.isBlockRest`, so its colour, Health Connect segment and calories needed nothing.

### Week 2 — building one, then the iOS widget

**3. Building a sequence** · L
- The preset editor: "Add a block" under `EditorShapeCard` (`PresetEditor.kt`); each block a card of the same
  `FieldRow`s (shape, ladder, sets), with remove and move up or down; a "Between blocks" row once there are two.
  The total counts every block.
- The Timer: a sequence on the Timer shows its blocks as a card (a `ConfigText` per block) in place of the
  steppers; a single block keeps today's steppers. "Edit" opens the editor in a new `EditorMode.Timer` (today's
  Edit, New, Copy and SaveCurrent all save a preset): Done writes the Timer's config and makes no preset, and
  Save as preset is still there. "Add a block" on a one-block Timer opens the same.
- `Timeline` and `MiniTimeline` draw the whole course, a gap at each block rest.
- `RoutineDescription` (Plan) still names a sequence by its preset through `sameShape`.
- Done when: a screen test adds, moves and removes a block and sees the total follow; `SequenceAcceptanceTest`
  builds its sequence in the editor from the Timer; `journeys/sequence.xml` builds one on `amper-37`, runs it and
  reads "Block 2 of 2" in the notification.
- As built: "Add a block" on the Timer is on the Sets sheet, under its quick picks, so the Timer's three rows keep
  their heights; a sequence on the Timer replaces them with one card of its blocks, and the Sets chip with the
  bookends alone. The editor edits a block's own fields through `valueOf`/`withValue`/`stepped` with the block's
  index (`ConfigField.BlockRest` is the whole workout's), and `withBlockAdded`, `withoutBlock` and
  `withBlockMoved` reshape the list. `RoutineDescription` needed nothing.

**5. The week on iOS's Home Screen** · L
- Spike first, half a day: a second, Swift-only target in `apps/iosApp/module.xcodeproj` beside `app` (the
  toolchain allows one target with its Kotlin phase, and the extension can't link `KotlinModules`), and an App
  Group on the simulator with `DEVELOPMENT_TEAM = ""`. If either fails the ticket drops below the cut line, and
  `release.md`'s "Left out on purpose" says why.
- A WidgetKit extension (`apps/iosApp/widget/`, SwiftUI), small and medium as `WeekWidget`'s tall and wide
  layouts: the ring, "count/goal", "This week", "Goal met" or "N to go", the streak, and Workout and Lift. Its
  `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` follow the app's, and `tools/check_versions.py` checks both
  targets.
- Data: `WeekStandingSource` (`apps/app/src/data/`) is asked for on iOS too. A collector in `iosApp`'s Kotlin
  writes each `WeekStanding` as JSON to the App Group's `NSUserDefaults(suiteName:)` and calls a reload hook the
  Swift side registers (`WidgetCenter` is Swift-only). The timeline adds an entry at local midnight, in place of
  Android's `WidgetDayAlarm`.
- Taps: `widgetURL` and `Link` to `interval://shortcut/<id>`, Android's form; `SceneDelegate` handles
  `openURLContexts` and a launch's `urlContexts` through `openShortcut(type:)` (`ViewController.kt`), so a
  workout running is kept, as the quick actions do.
- Done when: checked on the simulator (`run-on-device`): the widget shows the week and follows a finished
  workout, both taps work cold and warm, a workout running is kept; a Kotlin test pins the snapshot's JSON.

--- cut line ---

**4. Describe reads "then"** · S
- `describeWorkout` (`WorkoutDescription.kt`) splits on "then" and "followed by" into blocks, each read by
  today's shape reading; the bookends are read from the whole text, so "5 min warm-up, then 40/20 x8" keeps its
  meaning (a part that is a bookend alone is no block). "1:00 between blocks" names the block rest.
- Routines are unchanged: `RoutineDescription`'s `CLAUSE_BREAK` still breaks on "then", so a planned sequence
  comes from a preset.
- Done when: `WorkoutDescriptionTest` reads "8 × 20/10 then 5 × 1:00/0:30" as two blocks and every existing case
  as before; `RoutineDescriptionTest` is unchanged.

Also below it: Plan's routines reading sequences, and the iOS widget's Lock Screen (accessory) sizes.

## Explicitly not this sprint (0.6's candidates)

- **AppFunctions** ("start a Tabata", "log a set"): `androidx.appfunctions` is still alpha; a ticket once it is
  stable, over the start routes the widgets and the shortcuts use.
- **E5, import across the two stores**, stays the documented trade-off.
- **Localisation**, **Wear OS**, **iOS signing and an Apple team**: as in 0.4.

## Suggested order and calendar

| Day | Tickets |
|---|---|
| 1 | 0, start 1 |
| 2 | 1 the course walks blocks |
| 3–5 | 2 sequences |
| 6–7½ | 3 building one |
| 7½ | 5's spike |
| 8–10 | 5 the iOS widget; full `./kotlin build`, every journey on the release build, tag `v0.5.0`; 4 if a day is left |

1 before 2 because a refactor with every test green is cheaper before the shape changes. 2 before 3 because the
editor writes what storage keeps. 5 last of the five because it touches nothing of the others, and its spike
decides whether it stays.

## Risks

- **A place in the course without its block.** `comparedTo` finds a position by set, round and phase. Mitigation:
  ticket 1 adds the block before any second block exists, pinned by `CourseTest`.
- **A restore across versions.** `StoredProgress` and the three tables gain fields with defaults, so 0.4's saved
  workout, presets and plans read as one block. Mitigation: `AppMigrationTest` and a schema-4 fixture.
- **"then" means two things.** In Plan it starts a new workout. Mitigation: routines keep it; only Describe on the
  Timer reads it as a block, and that ticket is below the cut line.
- **The status bar's ten segments.** Mitigation: a segment per block, decided in `StatusBar` (tested).
- **A second Xcode target and an App Group without a team.** Mitigation: the spike, before any widget code.

## Verification of the sprint as a whole

1. `./kotlin check lint detekt`, `./kotlin test -p jvm -p android` and `./kotlin build` green on `main`; CI green
   on `v0.5.0` with the AAB attached.
2. The release build on `amper-37` passes every `journeys/*.xml`, `sequence.xml` and `widget.xml` included.
3. A 0.4 export imports with every workout, preset and plan as one block; a 0.5 export imports again adding
   nothing; a 0.5 file on a 0.4 build is refused with "update the app".
4. "5 min warm-up, 8 × 20/10, 2:00, then 4 × 1:00/1:00" built in the editor runs through its block rest with the
   chip saying the block, writes to Health Connect with its rests, and its Timeline shows both blocks.
5. On the iOS simulator the widget shows the week and opens a workout and a Lift session.
6. `CHANGELOG.md` 0.5.0 lists tickets 2, 3 and 5 (and 4 if it lands); About shows 0.5.0 on Android and iOS.
