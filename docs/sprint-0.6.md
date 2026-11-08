# Sprint 0.6 — "Lift's coaching, part two: rounds against the clock, and 15:15's snatches counted"

Two weeks, 2026-11-09 to 2026-11-20, one developer and Claude Code, ten working days. The goal is a shippable
0.6.0 that finishes what [kettlebell-coaching.md](kettlebell-coaching.md) still plans. Section 4 plans two
things: the Armor Building Complex's second protocol, as many rounds as you can in a time cap; and 15:15's
snatches, done on the interval side, counting toward Lift's snatch totals, snatch test, pace and grip. A
release (a tag, the release journeys, a dated changelog) happens only when the user asks for one, never as a
ticket. What landed before and how a release goes: [release.md](release.md) and [CHANGELOG.md](../CHANGELOG.md).

## Context

0.5's tickets 0, 1, 2, 3 and 5 are built, and so is everything listed under its "Added during the sprint". Ticket 4
(Describe reads "then") was below the cut line and carries over here. "Lift speaks" (coaching section 2:
one `Speaker` in `core-platform`, cues at an EMOM's count-in and at the end of a rest, faults after a set) is on
`main` (`6549a89d`) under 0.5.0, with CI green. Nothing is tagged after `v0.4.0`; 0.5.0 is tagged when the user
asks for the release.

**A density block.** `BlockKind` is `Sets`, `Emom` or `Ladder` (`lift-domain`, `Programs.kt`). An EMOM is
a set each minute for N minutes; the ABC's other protocol caps the time and counts the rounds instead. The
shape is the EMOM's own, turned around:
- **The block.** `rounds` is the cap in minutes and `reps` the reps of one round.
- **The clock.** `DensityClock` is `EmomClock`'s count-in, then a single countdown, with no minutes to rest out.
  The runner logs a set each time a round is done, as `EmomRunner` logs a minute's set.
- **Storage.** A block is stored by kind name in JSON (`StoredPrograms.kt`, the session's `plan`, the export's
  `BlockExport.kind`), so the new kind needs no Room migration. It does need a new export schema: an older build
  silently drops a block whose kind it doesn't know (`StoredBlock.toBlock`), so it must refuse the file.
- **What reads it already.** Benchmarks, density and pace read only the sets logged, so the rounds count toward
  the ABC challenge's "most in any twenty minutes" with no change. `Estimate.kt`, `Autoregulate.kt`,
  `Progression.kt`, the block editor and `ProgramWords.kt` each branch on the kind, and each needs a branch for
  the new one.

**15:15's snatches.**
- `snatchIntervals` ("15:15 MVO2", `Preset.kt`) names no exercise, and a `HistoryEntry` carries no reps.
- `IntervalActivity` gives Lift an `IntervalWorkout` of an id, its end and its length, nothing more. So Lift's
  totals, challenges, grip load and pace never see a snatch done on the Timer, though *20 minutes beside BJJ* puts
  15:15 in the week.
- The layering decides where the count lives:
  - The interval side stores what the lifter said: about how many a set, and the bell, on the `HistoryEntry`.
  - `IntervalActivity` passes it across.
  - Lift turns it into sets in `lift-domain`.
- Nothing on the interval side writes Lift's store, and Lift's pure functions take the extra sets beside the
  sessions'.

## Definition of done (every ticket)

- `./kotlin build` (full, links iOS), `./kotlin check lint detekt`, `./kotlin test -p jvm -p android`,
  `python3 tools/check_module_rules.py` and `python3 tools/check_ios_shards.py` pass. A `-p jvm` pass alone is
  not done.
- A user-facing journey starts with a failing acceptance spec (`apps/app/test@jvm/acceptance/`, or Lift's in
  `lift/lift-nav/test@jvm/acceptance/`). Logic is tested in the module's `test/`, screens in `test@jvm/`, and
  strings go in that module's `strings.xml`.
- A `journeys/*.xml` is added or updated when on-device behaviour changed, and run on `amper-37`.
- A line under `## [0.6.0] — Unreleased` in `CHANGELOG.md`.
- A schema change bumps the `@Database` version with a migration and commits the `schemas/` JSON. A change to the
  bundle bumps the export's `SCHEMA` once per release: the whole sprint ships as schema 11.
- A changed convention updates `CLAUDE.md` and `docs/architecture.md` in the same commit. A coaching feature moves
  from "Planned" to built in `kettlebell-coaching.md`.
- One commit per ticket on `main` (`jj`), its message in the repo's `Area: what changed` style, with linear history.

Sizes: XS ≤ half a day, S ≈ 1 day, M ≈ 2 days, L ≈ 3 days. Tickets 1 to 5 come to eight and a half days. The cut
line is after ticket 5; ticket 6 sits below it.

## The backlog, in order

### Week 1 — rounds against the clock

**0. (Dropped) Release 0.5.0.** Releases are not sprint tickets; one happens when the user asks. "Lift speaks"
landed on `main` and CI passed. An R8 smoke run of 28 journeys on the minified build (2026-11-07) found no crash
and no R8 signature; the one open question is `health-connect.xml`, whose finished workout didn't show under
Exercise in Health Connect. Check it before the next release. Ticket 4 of 0.5 moves here as ticket 6.

**1. Open 0.6.0** · XS
- `versionName` 0.6.0 and `versionCode` 6 in `apps/androidApp/module.yaml` and the pbxproj (both targets,
  `tools/check_versions.py`), and `## [0.6.0] — Unreleased` in the changelog.
- Done when: `main` says 0.6.0.

**2. A density block: the domain and storage** · M
- `BlockKind.Density`:
  - `rounds` is the cap in minutes, 5 to 30.
  - `reps` is one round's reps.
  - `plannedSets()` gives the first round only. The count is open, so `DayProgress` moves on when the cap ends,
    not after N sets.
- `DensityClock` (`lift-domain`, beside `Emom.kt`):
  - `at(elapsed, rounds)` gives a `DensityState`: GetReady, Work or Done, the seconds left and the rounds done.
  - `cuesBetween` covers the count-in and the last ten seconds, like `EmomClock`'s.
  - It is pure, a function of time.
- The readers:
  - `Estimate.kt`: the block is exactly its minutes plus the count-in, with no rest after it, like an EMOM.
  - `Autoregulate.kt`: on a taxed day it takes `EASIER_EMOM_SHARE` of the minutes.
  - `Progression.kt`: a density block steps by its rounds. When the last two sessions beat the best by a round,
    the reps of a round go up one, and the bell waits, as section 4's ladder has it.
  - `ProgramDraft.asKind`: a held lift can't be one.
- `SavedTimer.Density(startedAt, exercise, load, reps, minutes, rounds)`, stored as `"density"` in `StoredTimer`,
  so a session restored mid-block resumes by the wall clock. One that is over is closed, never played out.
- The export takes `BlockExport.kind = "Density"`, and `SCHEMA` goes to 11. A schema-10 file reads as before, and
  a 0.5 build refuses a schema-11 file with "update the app".
- Done when:
  - `DensityClockTest` pins the count-in, the cap and the cues.
  - `EstimateTest`, `AutoregulateTest` and `ProgressionTest` each cover the kind.
  - `StoredProgramsTest` and `AppDataImporterTest` round-trip it.
  - `BenchmarksTest` counts a density block's rounds toward the ABC challenge.

**3. A density block: the session and the editor** · M
- `SessionTimers.startDensity` runs a `DensityRunner` like `EmomRunner`:
  - It beeps through the `Beeper`.
  - It logs a set through `setStore.logSet` at each "Round done".
  - It keeps the session alive as a `TimerRun.Density` (Android's `TimerService` counts down in the notification;
    iOS schedules one notification for the cap).
  - It ends with the session, as the others do.
- `DensityScreen` (`lift-session`, beside `EmomScreen`) shows:
  - the time left in the ring;
  - the rounds done, rolling with `RollingText`;
  - the best for this block, "Best: 14";
  - one big "Round done".
- Lift's voice says one of the lift's cues at the count-in, then "Five minutes", "One minute", and, once the cap
  ends, "Time. 14 rounds". It never speaks mid-round.
- The block editor offers the kind ("Time cap", short so the picker never cuts it), its minutes from the same stepper as an EMOM's, and
  `ProgramWords` says "As many rounds as you can in 20 min · 1 a round".
- The summary says the rounds, and "New best: 15 rounds in 20 min" when it beats the block's best.
- Done when:
  - `DensityAcceptanceTest` (Lift's) runs a 5-minute block, logs rounds and sees the summary's best.
  - A screen test drives `DensityScreen`'s states.
  - `journeys/lift-density.xml` runs one with the screen off on `amper-37` and hears the cap.

**4. The ABC either way** · S
- The Armor Building Formula's day 1 and *20 minutes beside BJJ*'s day 1 keep the EMOM. The day's plan sheet
  offers "As many as you can" in its place: a density block of the same minutes, changed in `SessionPlan` as the
  plan sheet changes a day today, so the program itself is untouched.
- Section 5's ABC challenge says which protocol its best came from.
- Done when: `journeys/lift-plan-changes.xml` swaps the protocol, and a ViewModel test sees the plan's block
  change and the program stay the same.

### Week 2 — 15:15's snatches in Lift

**5. Snatches from 15:15** · L
- The interval side:
  - `HistoryEntry` gains `snatches: SnatchCount?`, a `SnatchCount` of the reps a set (about 7 to 9) and the bell
    in kg.
  - Storage: `AppDatabase` version 16 adds `snatches_per_set` and `snatch_bell_kg` (both nullable) on `history`
    by auto-migration, with `schemas/16.json`, mapped in `IntervalMappers.kt`.
  - `HistoryStore.countSnatches(uid, count)`.
  - The export's `WorkoutExport` takes both fields under schema 11.
- The summary:
  - A workout whose preset `alternateHands` and is Kettlebell (15:15 MVO2, or one of the user's own with the
    switch on) gets "About how many snatches a set?" above How was it.
  - It is a stepper that opens on the last count, and a bell picked from those Lift knows: `LiftActivity` gains
    `bells`, the bells owned, opening on the bell used last.
  - Leaving it unset counts nothing.
  - A finished workout's page in the Log can set it later.
- The port: `IntervalWorkout` gains `snatches` and its work intervals done, so Lift knows how many sets there were.
  The `RepositoryIntervalActivity` adapter maps them.
- Lift: `intervalSets(workouts)` (`lift-domain`, `Benchmarks.kt`) makes one `SetLog` of `Snatch` per work
  interval done, at the bell, alternating sides, each logged at its interval's end. They count toward:
  - lifetime totals and the 1,000 snatches milestone;
  - the snatch test's "most in any five minutes" (ten sets of eight in five minutes is 80);
  - pace;
  - the grip load, and through it readiness.

  `TodayViewModel`, `FinishSession` and the exercise library pass them beside the sessions' sets, so no Lift
  session is made and nothing is counted twice.
- Done when:
  - `SnatchAcceptanceTest` (the app's, both sides in one container) finishes 15:15, says 8 a set at 16 kg, and
    sees Lift's snatch total and the snatch test's bar move.
  - `BenchmarksTest`, `GripTest` and `DensityTest` take interval sets.
  - `StorageTest` reads a version-15 database at 16.
  - The import round-trips the count.
  - `journeys/lift-challenges.xml` gains the step.
- As built: the type that crosses is `IntervalSnatches` (`lift-domain`, so `lift-api`'s `IntervalWorkout` carries
  it), with each work interval's end (`HistoryEntry.workEnds`) and the preset's name. The snatch test names that
  preset ("from 15:15 MVO2"). The Log page and the session's exercise sheet count them too, and a lift's page shows
  its reps in all with only 15:15's snatches behind them. The question lives in `interval-ui` (`SnatchQuestion`),
  which now depends on `lift-domain` for the bell sizes.

**7. The bell waits** (added during the sprint) · M
- Fixed bell sizes, in one ordered ladder ([kettlebell-coaching.md](kettlebell-coaching.md), section 4):
  `rungOf` in `Progression.kt` replaces `stepUp`'s branching.
- The steps, before the next bell: reps up the range; denser (less rest, or an EMOM's reps a minute); more (a
  set, a minute, a ladder, then a rung); and before a big jump, a harder lift at the same bell (`Variations.kt`).
  The tier is graded by how big the jump to the next bell is.
- `Block.origin` keeps where the block started, so the bell step goes back to it. That covers the lift it stood
  in for, too. It's one nullable JSON field on the stored block, the session's plan and the export (schema 11).
- Ladders step now. Each rung is checked against what it planned, where before any ladder passed.
- The summary's Next time says why a step is the one it is. A harder lift and the return name both lifts.
- Done when:
  - `LiftPlanChangesAcceptanceTest`'s four swing stages pass;
  - `ProgressionTest` pins the tiers (24→26, 16→18, 20→24, 16→20) and every rung's cap;
  - `VariationsTest` pins the table;
  - `journeys/lift-plan-changes.xml` sees the first rung on the device.

**8. Three more bell lifts** (added during the sprint) · S
- The library checked against a hardstyle list (the Big 6, carries, anti-movement core, pulls and lunges). It held
  every lift but three, which are now built-ins:
  - `WalkingLunge`: the bell at the side, two with a pair.
  - `RenegadeRow`: a plank on two bells, so always a pair, and never in a one-bell program.
  - `KettlebellDeadBug`: one bell held over the chest. The bodyweight `DeadBug` keeps its key.
- Each has its movement, muscles, name, cues and figure.
- Done when:
  - the library reads 105 exercises and "Kettlebell · 30" (`ExerciseLibraryAcceptanceTest`,
    `journeys/exercise-library.xml`);
  - `TrainingTest` pins the keys;
  - `BellDemosTest` holds the renegade row's far bell on the floor and the dead bug's bell still.

**9. Log a past workout** (added during the sprint) · M
- A workout done away from the app, or without the Timer, goes into the Log after the fact. The day sheet, on today
  or a day gone by, gets "Log a workout" (the one button a day gone by has; today keeps "Open plan" and "Add
  workout" over it), which opens `LogWorkoutSheet`: Interval (the Timer's workout, then the presets), Mobility (the
  flows) or Sport, with no Weekly/Once and "Log it" to confirm. A planned interval workout's row that is Missed
  (a day gone by) or Open (today) has "Log it" too, as a planned sport day has. Nothing of Lift's: a session is its
  sets, and finishing one assumes it ends now.
- Logged in full: `HistoryEntry.loggedByHand(config, finishedAt)` runs the whole course (`totalSeconds`, not
  ended early, work estimated from the plan, the start run back from the finish). A start kept with no work time
  is the mark (`loggedByHand`), so no column is added; a finished workout's page reads no heart rate for one.
  `HistoryStore.logWorkout` keeps it and `unlogWorkout(uid)` is the notice's Undo (`HistoryDao.deleteByUid`, a
  tombstone, by uid because the id isn't known until the write lands). Nothing goes to the health store.
- Dated by `TimestampFormatter.middayOf(epochDay)` on a day gone by, midday because a daylight-saving change can
  skip or double midnight; today's at the moment, never ahead of now. `TrainingLog` then matches its plan by day and
  shape as any workout's, so a week gone by can change its count and streak, and a plan matched today leaves Up
  next and drops its reminder: intended.
- Done when:
  - `LogPastWorkoutAcceptanceTest` logs yesterday's Tabata from its day and undoes it from the notice, logs a flow
    there and sees it count as mobility, and logs today's plan from its row to "Done" with nothing more due
    (`journeys/log-past-workout.xml`);
  - `HistoryEntryTest` holds the whole course run back from the finish and the mark; `JavaTimeFormatterTest` keeps
    midday on its day where midnight is skipped; `IntervalRepositoryTest` dates it as given and tombstones the undo;
    `HistoryViewModelTest` marks a plan done on a day gone by and dates today's now; `WorkoutDetailViewModelTest`
    reads no heart rate for one; `HistoryScreenTest` drives the sheet's three kinds and the row's Log it.

**10. Plan's routines read sequences** (added during the sprint, from below the cut line) · XS
- `describeRoutine` (`RoutineDescription.kt`) no longer breaks clauses at "then". A clause is split at "then" or
  "followed by" only where the words before and after it both name days ("tabata mon then 40/20 x8 wed" is two
  workouts); otherwise its pieces stay joined, so `describeWorkout` reads them as a sequence ("8 × 20/10 then
  5 × 1:00/0:30 mon" is one, named by `defaultPresetName`), with its bookends and rest between blocks.
- Done when: `RoutineDescriptionTest` reads a sequence on its day, days said first, the rest between blocks,
  "followed by", one block from "40 on then 20 off", and a sequence beside another workout; and
  `SequenceAcceptanceTest` plans one from Plan's Describe a routine.

**11. The week on iOS's Lock Screen** (added during the sprint, from below the cut line) · XS
- `WeekWidget.swift` takes the three accessory families beside small and medium, from the same `WeekSnapshot`:
  - circular, the ring alone as a capacity gauge with "3/5" in it, or the count on the system's disc with no goal;
  - rectangular, the ring beside "This week", how far to go and the streak;
  - inline, "3/5 this week · 2-week streak" over the clock, the figure alone where the streak won't fit.
- The words are the Home Screen's (`WeekSnapshot.caption`, `streakWords`, `spoken`), drawn in the system's
  foreground so the Lock Screen's tint takes them, the ring as the tinted part.
- No Workout or Lift there: the whole widget is one tap, and it opens the app as it was, since a glance at the Lock
  Screen shouldn't start anything.
- Done when: checked on the simulator, each size in the gallery and on the Lock Screen. No journey changes:
  `journeys/` run on `amper-37`, and Android has no Lock Screen widgets.
- As built: checked on iOS 27's simulator, the three sizes in the gallery with a goal and a streak, and the circular
  and rectangular on the Lock Screen with no goal ("1 this week", "This week · 1 workout"). The rectangular's ring is
  drawn smaller than the circular size so "2-week streak" fits beside it, and with no goal it says the count in words.

**12. Lift's safety lines** (added during the sprint, from the backlog) · S
- Coaching section 2's last planned item: "Groove five light first" before a ballistic lift's first set at a bell new
  to it, and "Stop a rep early" on a Taxed day.
- `firstAtBell` (`lift-domain`, `Safety.kt`): a ballistic lift at a bell, or a pair, heavier than any it was done at,
  15:15's snatches with the sessions' sets, with a lighter bell owned. A lift never done is new at any bell.
- Each line takes a set-up cue's place (a rest's last ten seconds, an EMOM's or density block's count-in), since
  the speaker cuts a line short when the next one starts: the groove once for each lift and bell a session, then
  stopping early once a session. `SessionTimers` hears of the set coming (`ComingSet`) and the day
  (`EasierDay.taxed`, the offer's own reading, which holds after the first set and on a free session).
- A session opening before its first set says the line due at once (`SessionTimers.opened`), never after a killed
  process; a free session's says only "Stop a rep early", as its card's lift may not be the one done.
- Done when: `SafetyTest` pins the rule, `SessionTimersTest` the cue each takes the place of, and
  `LiftVoiceAcceptanceTest` hears the groove as a program day opens and "Stop a rep early" after a very hard
  session (`journeys/lift-voice.xml`).

--- cut line ---

**6. Describe reads "then"** (0.5's ticket 4, unchanged) · S
- `describeWorkout` (`WorkoutDescription.kt`) splits on "then" and "followed by" into blocks, each read by
  today's reading of a shape.
- The bookends are read from the whole text, so "5 min warm-up, then 40/20 x8" keeps its meaning. A part that is
  a bookend alone is no block.
- "1:00 between blocks" names the block rest.
- Routines keep "then" as a clause break (until ticket 10).
- Done when: `WorkoutDescriptionTest` reads "8 × 20/10 then 5 × 1:00/0:30" as two blocks, and every existing case
  reads as before.
- As built: the rest between blocks is read before the parts, so "1:00 between blocks" is never a set rest. A later
  block takes the work, rest and rounds of the one before where its words leave them out, but not its sets or
  ladder. A preset's name in a part starts that block, and only a description of one part names a preset. The
  sheet shows a row a block (`DescribeSheet`, its field up to 160 characters), and `SequenceAcceptanceTest` types
  one onto the Timer. A part giving a rest and no work joins the one before ("40 on then 20 off x8" is one block),
  and in a sequence a bare "1:00 between" said of a block of one set is the rest between blocks.

Also below it:
- A density block for the interval side, the Timer's own "as many rounds as you can".

## Explicitly not this sprint

Everything waiting for a sprint is in [backlog.md](backlog.md), each item with why it waits and what would bring
it in: AppFunctions, iOS signing, E5, localisation, Wear OS, and Lift's safety lines.

## Suggested order and calendar

| Day | Tickets |
|---|---|
| 1 | 1 open 0.6.0 |
| 2–3 | 2 the density block's domain |
| 4–5 | 3 its session and editor |
| 6 | 4 the ABC either way |
| 7–9 | 5 snatches from 15:15 |
| 10 | full `./kotlin build` and the journeys the sprint touched; 6 if a day is left. No tag unless asked |

The order follows the dependencies:
- 2 comes before 3 because the screen runs what the domain keeps.
- 4 needs the kind.
- 5 touches neither of the others, but it is the one with a Room migration, so it goes last, when the schema bump
  can be checked once for both.

## Risks

- **An older build drops a block it doesn't know.** `StoredBlock.toBlock` returns null for an unknown kind.
  - Mitigation: export schema 11 is refused by a 0.5 build. On the device, a downgrade is not supported.
- **Counting twice.** If 15:15's snatches became Lift sets in Lift's store, a re-import or an edit would double
  them.
  - Mitigation: they stay on the `HistoryEntry` and are derived in `lift-domain` at every read, so there is one
    source.
- **A guessed count in a best.** "About eight a set" is an estimate, and the snatch test is a best.
  - Mitigation: the snatch test and pace take interval sets as logged-as-done only when the count was set by the
    lifter, never defaulted. The challenge names its source ("from 15:15").
- **The layering.** Lift reading the interval side's reps.
  - Mitigation: they cross only through `IntervalActivity`, and `check_module_rules.py` still holds `lift/` away
    from `interval/`.
- **A density round tapped late.** A late tap moves the pace, not the count.
  - Mitigation: density's `loggedAsDone` already ignores overlapping sets, and a round's time is its tap.

## Verification of the sprint as a whole

1. `./kotlin check lint detekt`, `./kotlin test -p jvm -p android` and `./kotlin build` are green on `main`, and
   CI is green on it.
2. `amper-37` passes the journeys the sprint added or touched, including `lift-density.xml`,
   `lift-plan-changes.xml` and `lift-challenges.xml`.
3. A 0.5 export imports with nothing lost. A 0.6 export imports again and adds nothing. A 0.6 file on a 0.5 build
   is refused with "update the app".
4. The ABC run as "As many as you can" for 20 minutes logs its rounds, says the best, and moves the ABC challenge.
5. A finished 15:15 counted at 8 a set moves Lift's snatch total, the snatch test and the week's grip load, and a
   second import doesn't double them.
6. `CHANGELOG.md`'s 0.6.0 lists tickets 2 to 5 (and 6 if it lands). About shows 0.6.0 on Android and iOS.
