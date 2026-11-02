# Architecture sprint: owned by the process, not the screen

Two weeks, 2026-11-16 to 2026-11-27, one developer and Claude Code, about ten working days. It follows 0.3 and
comes before 0.4's richer interval shapes on purpose: those change `IntervalConfig`, `WorkoutEngine` and the
workout's ViewModel, and this sprint first moves the running workout out of that ViewModel.

The findings come from [architecture-review.md](architecture-review.md); each ticket cites their ids (**A1**,
**E1**, …). The architecture as it stands is in [architecture.md](architecture.md).

## Context

The 2026-10-31 review found the architecture sound: enforced layers, pure domains, platform services behind
interfaces, ordered writes, real acceptance tests. It traced what is weak to four root causes:

- **A.** The Koin container lives as long as the Activity, not the process.
- **B.** Long-running work (the workout's ticker; Lift's rest, EMOM and hold) is owned by ViewModels.
- **C.** The two repositories are concrete classes with no interfaces, and every ViewModel takes the whole of one.
- **D.** `SessionViewModel` and `WorkoutViewModel` each do too much.

**A** and **B** cause the user-visible bugs:
- an EMOM goes silent with the screen off
- reminders stop after two weeks without opening the app
- Lift's timers are lost to a process kill.

**A** and **B** are also what **C** and **D** build on. So this sprint fixes three things in this order:
1. the data bugs that lose work today
2. the container's lifetime
3. the two long-running owners.

**C** and **D** sit just below the cut line, and are ready to start the moment the first six tickets land.

This sprint adds no feature. What a user notices is in its tickets' changelog lines: an EMOM and a hold heard
with the screen off, reminders that keep coming, a plan edit never lost.

## Definition of done (every ticket)

- `./kotlin build` (full, links iOS), `./kotlin check lint detekt`, `./kotlin test -p jvm -p android`,
  `python3 tools/check_module_rules.py` and `python3 tools/check_ios_shards.py` pass. A `-p jvm` pass alone is not
  done.
- **Behaviour is pinned before it moves.** Logic goes in the module's `test/` and screens in `test@jvm/`. Write
  a failing test that reproduces each bug before the fix. A pure refactor keeps every existing acceptance spec
  green, unchanged.
- When on-device behaviour changed, the affected `journeys/*.xml` is updated and run on `amper-37`.
- A changelog line under the next unreleased version (`### Fixed` or `### Changed`) wherever a user would notice.
- **Docs follow the code.** A changed convention updates `CLAUDE.md`'s Conventions and `docs/architecture.md` in
  the same commit. A resolved finding is marked `Resolved in <commit>` in `docs/architecture-review.md`.
- No new `@Suppress("detekt.TooManyFunctions")` or `LongParameterList`. A class this sprint splits drops its
  suppression.
- One commit per ticket on `main` (`jj`), message in the repo's `Area: what changed` style, the finding ids in the
  body.

Sizes: XS ≤ half a day, S ≈ 1 day, M ≈ 2 days, L ≈ 3 days. Tickets 0 to 5 come to about nine and a half days.
The cut line is after ticket 5.

## The backlog, in order

### Week 1: stop losing work, then one container per process

**0. Data fixes that can lose work** · S · E1, E2 (transactions), B4
- **The plan race (E1).** `LiftRepository.setSessionPlan` stops writing `_active` optimistically on Main. Its
  read-modify-write moves into `writer.persist`: read `sessionDao.active()`, apply the change, write. The edit
  that follows then builds on what is stored.
  - If the screen must show the change before the write lands, keep a `pendingPlan: MutableStateFlow` overlay
    that the collector clears once an emission carries it.
  - `changeBlock`/`stepNextLoad` in `SessionViewModel` take the plan from that same source.
- **Transactions (E2).** `@Transaction` DAO methods in `lift/lift-data/src/data/LiftDatabase.kt` for
  `discardSession`, `deleteProgram`, `finishSession` (count, then finish) and `removeBand`. The repository calls
  one method per job.
  - `removeBand` asks `EXISTS(SELECT 1 FROM lift_set WHERE band = :id)` rather than loading every set.
  - The foreign key is not this sprint: it needs a table rebuild (see the end).
- **The rest's wake lock (B4).** `RestService.countDown` in
  `lift/lift-platform/src@android/platform/RestAlerts.android.kt` releases and re-acquires for
  `endAt - now + LINGER_MILLIS + margin` on every start, so back-to-back rests never outlive the lock.
- Done when:
  - a `LiftRepositoryTest` that logs a set and then changes the plan, with the fake DAO emitting the stale row in
    between, keeps the plan
  - `LiftRoomTest` shows a discard leaves no sets, and a band in use is archived without `allSets()`
  - `journeys/lift-rest-alert.xml` runs two rests back to back with the screen off and hears both.

- As built: `setSessionPlan(plan)` became `changeSessionPlan(change: (LiftSession) -> SessionPlan?)`. The writer reads
  the session in progress and its sets (`SessionDao.sets`), hands it to the change, and writes only a plan that
  differs (null leaves it). No `pendingPlan` overlay: nothing depends on what is drawn, and the database's next
  emission redraws the change. `today()` and `programDay()` take the session the change is given. Each transaction is
  on the DAO of the table it ends in: `SessionDao.finishActive`/`discardActive`, `CustomProgramDao.deleteProgram`
  (with the `following` delete), `BandDao.removeOrArchive` (with `inUse`, the `EXISTS`); the fakes of the last two
  take the fake holding the other table. The rest's lock is not reference-counted, and is acquired for the rest's
  time left, the linger and 5 s at each start. The lock's lapse after 15 minutes can't be waited out in a journey,
  so it was checked by reading; the journey checks the lock is held through a second rest and let go after.

**1. Guards at the edges** · XS · E3, E5 (review), E6, D5
- **E3.** `IntervalConfig` gets `init { require(...) }` over its `*_RANGE`s, and a `sanitized()` factory that
  coerces into them. Every mapper that builds one from stored or imported data uses `sanitized()`:
  `Preferences.toConfig()`, `StoredProgress.toDomain()`, the Room preset and plan mappers, and `ImportPlanner`,
  which still refuses a file out of range.
- **E5.** `SettingsViewModel.reviewImport` catches an unexpected failure the way `confirmImport` does, so the
  step returns to idle with the notice rather than staying on `Reading`.
- **E6.** `IntervalRepository` keeps `_customPresets` alone and derives `presets` from it (built-ins + custom,
  `stateIn` on the storage scope), so the two are never seen apart.
- **D5.** `ProgramEditorViewModel` persists the draft after `_state.update {}`, not inside it.
- Done when:
  - `IntervalConfigTest` shows `rounds = 0` refused and `sanitized()` coercing it
  - `StorageTest` shows a corrupt stored config read back in range
  - `SettingsViewModelTest` covers a review that throws.

- As built: `sanitized(...)` is a companion factory over raw values (a config out of range can't be made to
  sanitize afterwards), every parameter defaulted as the constructor's are, so `LongParameterList` doesn't apply.
  `ImportPlanner` is unchanged: it refuses a value out of range rather than coercing it. E6 runs the other way from
  the plan above: `presets` is the one list kept and `customPresets` is derived from it, so the list every screen
  reads is never behind `ready`; only `customPresets`, read by Export, can trail it for a moment.

**2. One container per process** · M · A1 (a to e)
- **Decision.** Production starts Koin once per process with `startKoin` from a shared `initAppKoin(platform)`
  in `apps/app/src/di/AppKoin.kt`. Tests keep their isolated containers: `AppContent(koin)` still takes a
  `KoinApplication`, and `testKoin()` is unchanged.
  - The original reason for isolation ("`startKoin` would need an Application class and initialization in each
    entry point") is exactly what this ticket adds.
  - Services and receivers can then use `KoinComponent`.
  - Rejected: a hand-rolled holder object, which is the same global with less tooling.
- **Android.** A new `IntervalApplication : Application` in `apps/androidApp/src/` calls
  `initAppKoin(androidPlatformModules(this))` in `onCreate`; the manifest adds `android:name`.
  - `rememberCorePlatformModule`, `rememberPlatformModule` and `rememberLiftPlatformModule` become plain
    functions of a `Context`. Each already uses only `applicationContext`.
  - `@Composable expect` becomes `expect fun corePlatformModule(...)` etc., taking a `PlatformContext` (Android's
    `Context`; nothing on iOS and the JVM).
- **iOS.** `ViewController()` calls `initAppKoin` once; a file-level `lazy` guards it.
- **Desktop.** `main()` calls it before `application {}`. On `exitApplication` it drains both repositories with
  the bounded `runBlocking`, off the UI thread: the one platform where the process ends with the window.
- **Removed.**
  - `KoinHolder` and its `runBlocking` (A1a).
  - The `preferenceStores` map: the DataStore becomes a plain `single` (A1b).
  - The database reopening on every relaunch (A1c).
  - With the container never closed on Android, the late-write race on teardown goes too (A1e).
- **`App()` stays usable from Hot Reload.** `App(koin: KoinApplication = GlobalContext.get()...)`, so
  `@DevelopmentEntryPoint` still works with no arguments.
- Done when:
  - `AppKoinStorageTest` builds the container from the new function
  - `AppNavigationTest` and every acceptance spec pass unchanged
  - on `amper-37`: back out of the app and reopen it mid-workout, and the workout is still running and the
    database was opened once (one `Room` open in logcat)
  - rotation and "Don't keep activities" keep the back stack and the workout
  - `CLAUDE.md` (Conventions: DI) and `architecture.md` describe the process-scoped container.

- As built: `initAppKoin` starts the container once per process from `IntervalApplication` (Android), `main()`
  (Desktop), and iOS's first frame. Platform modules are plain functions (`androidPlatformModules(this)`).
  `KoinHolder`, `preferenceStores`, and main-thread `runBlocking` are removed; databases remain open for the life
  of the process. Desktop drains both repositories off the UI thread on exit. Detekt `ForbiddenImport` ensures
  `startKoin`, `stopKoin`, and `GlobalContext` stay confined to `apps/`.

**3. Reminders that keep themselves going** · S · A2, A1d
- `PlanAlarms` stops being an `object` with its own DataStore and becomes a class in the container.
  `PlanReminderReceiver` gets it, and `PlanReminderScheduler`, through `KoinComponent` (now possible after
  ticket 2).
- On each reminder's alarm the receiver runs the scheduler once (`goAsync`, as now), which moves the 14-day
  window forward. A user who never opens the app keeps being reminded.
- The alarm carries the day, not the words: the receiver words the notification at delivery through
  `reminderWords`, so a language change shows the new words.
- Boot, time and time-zone changes run the scheduler rather than replaying a record. The record stays only as
  long as it is needed to cancel what was set.
- Done when:
  - `PlanReminderSchedulerTest` shows a delivered reminder extends the window to day 15
  - `RemindersTest` words a reminder at delivery
  - `journeys/reminders.xml` advances the emulator's clock past 14 days with the app closed and gets the
    reminder.

- As built: `PlanReminderSource` (`interval-platform`, beside `PlanReminders`) is the port the receiver injects, as
  `interval-platform` can't see History; the scheduler binds it. `reminderOn(day)` reads the day's open plans past
  their minute (`plannedReminders` drops today's once its minute has come, so a lookup has its own pure
  `reminderOn` in `Reminders.kt`), and `reschedule()` calls the platform in the caller's coroutine, past the
  collector's `distinctUntilChanged`, so the receiver's `goAsync` holds until the alarms are set, and a set the same
  as before is still set afresh after the clock moves. The scheduler waits for Lift's `ready` too (`LiftActivity`
  gained it), so a cold start at boot doesn't remind of a Lift plan already done. The action, extra, request code and
  record file are unchanged, so alarms a 0.3 build set reach the new receiver and are cancelled by the first replace.
  iOS is unchanged.

### Week 2: the running workout and Lift's timers outside the screen

**4. `WorkoutSession`: the running workout owned by the process** · L · B1, D2, A1e
- **New `WorkoutSession`** in `interval/interval-workout/src/workout/`, a plain class bound as a Koin `single`
  in a new `workoutModule`. It owns what `WorkoutViewModel`'s mutable `var`s hold today:
  - the `WorkoutEngine` state
  - the course spans
  - the active and work millis
  - the cues
  - the checkpoint to `WorkoutProgress`
  - the ticker, on its own scope with an injected `TimeSource.WithComparableMarks` and dispatcher.

  It exposes `state: StateFlow<WorkoutSessionState?>` and `start(id)`, `restore(id)`, `togglePause()`,
  `skip()`, `end()`.
- **New `WorkoutRecorder`** writes the history entry and the `HealthWorkout` as the session ends, and again after
  the reflection, which moves out of `onCleared`.
- **New `ActiveWorkout` port** in `interval-platform` (`status: StateFlow<WorkoutStatus?>`, `togglePause()`,
  `end()`, `upcomingChanges()`), which `WorkoutSession` implements.
  - `WorkoutService` injects it (`KoinComponent`) and collects `status`.
  - The companion's `status` and `controls` are deleted, and `BackgroundWorkout.start` no longer takes controls.
- **`WorkoutViewModel` becomes a viewer.** It maps `WorkoutSession.state` to `WorkoutUiState` and forwards the
  screen's actions, with no `var`s and at most four dependencies.
- **Unchanged behaviour.** The workout ends when its page is left, or when the task is swiped away
  (`onTaskRemoved`). Restore by the route's id works as today; iOS's notifications ahead and desktop's nothing are
  as they are.
- Done when:
  - most of `WorkoutViewModelTest` moves to `WorkoutSessionTest` with the same cases; what stays is the mapping
  - `WorkoutSessionTest` drives pause, end and a restore with no ViewModel at all, which is what the service
    relies on
  - the acceptance specs `StartWorkoutAcceptanceTest` and `WarmUpCoolDownAcceptanceTest` pass unchanged
  - `journeys/start-workout.xml` pauses and ends from the notification with the screen locked
  - `WorkoutViewModel` drops its `@Suppress`
  - the `CLAUDE.md` paragraph "The workout off screen" is rewritten.

- As built: the run itself is `WorkoutRun`, an immutable value whose changes (`turned`, `stepped`, `restored`,
  `toProgress`, `status`) are pure, and `WorkoutSession.state` is a `StateFlow<WorkoutRun?>` rather than a type of its
  own. `open(id)` takes the place of `start`/`restore`: the workout held if it is this one, else the one saved under
  the id, else a new one (ending any held before). The ticker is `WorkoutTicker`, a single the container closes;
  `WorkoutRecorder` keeps the finished workout and rewrites it for Health with the summary's words. `ActiveWorkout`
  gained `leave()`, and `BackgroundWorkout` lost `update`: Android's service and iOS's keeper (which takes the port
  lazily, as the session takes it) both follow `status`. One behaviour did change: the workout no longer ends with its
  ViewModel, which Android clears when it destroys the activity in the background, the service still running. It ends
  when the `Workout` key leaves the back stack (`App.kt` calls `leave()` once none is on it, whichever way the page
  went), when the task is swiped away (`onTaskRemoved`), or when a new workout opens. The screen's End and the
  notification's are one (`end()`). On `amper-37`: Pause from the shade, the chip, End's summary, and a swipe from
  Recents ending the workout and its notification with the process still alive. "Don't keep activities" and the lock
  screen weren't run there (Developer options off, no screen lock); `WorkoutLifetimeTest` rebuilds the app from saved
  state over the same container instead.

**5. `SessionTimers`: Lift's rest, EMOM and hold kept alive** · M · B2, B5, D1 (timers)
- **New `SessionTimers`** in `lift/lift-session/src/session/`, a Koin `single`, owns the three runs.
  `RestTimer`, `EmomRunner` and `HoldTimer` move under it, built with injected dependencies rather than with
  `new` in `SessionViewModel`.
  - It ticks on its own scope, measuring with the injected monotonic `TimeSource`, not `WallClock` (B5).
  - It plays the cues through `Beeper`.
  - It exposes `clocks: StateFlow<SessionClocks>`.
- **`RestAlerts` becomes `SessionKeepAlive`** (`start(run: TimerRun)`, `stop()`), with Android's `RestService`
  renamed `TimerService`. It is held for **any** running timer: rest, EMOM or hold.
  - The notification names which and counts it down (an EMOM's minute and round, a hold's seconds).
  - The wake lock is timed to the run's end, as ticket 0 does for the rest.
  - The manifest entry and its special-use description change with it.
  - iOS schedules the run's end, or an EMOM's last minute, as it does a rest's.
- Starting an EMOM no longer ends the keep-alive with `rest.skip()`. One run at a time, as now; the keep-alive
  follows whichever run it is.
- `SessionViewModel` takes `SessionTimers` and collects `clocks` for the tiles only, which also closes F3.
- Done when:
  - `RestTimerTest`, `EmomRunnerTest` and a new `HoldTimerTest` run against a test `TimeSource`
  - `SessionViewModelTest` shows an EMOM start keeps the keep-alive
  - a new `journeys/lift-emom-screen-off.xml` locks the screen through a 3-minute EMOM and hears every minute
  - `journeys/lift-rest-alert.xml` is updated
  - the `CLAUDE.md` "Lift's rest between sets" paragraph is rewritten.

- As built:
  - **Names.** `RestAlerts` became `SessionKeepAlive` with `start(run: TimerRun)` (`Rest`, `Emom`, `Hold`, each
    with its end), and `RestService` became `TimerService`. Its channel keeps the id `"rest"`, so a user's settings
    for it carry over, and its wake lock is tagged `interval:timer`.
  - **Clocks.** The timers read a `RunClock`: monotonic time to run by, and the wall clock only to tell the
    platform when a run ends. Each run keeps its epoch start beside its monotonic one, ready for ticket 6.
    `realElapsedTime` moved from `dataModule` to core's `corePlatformModule`.
  - **Ticking.** `SessionTimers` ticks only while a run goes. It holds the keep-alive edge-triggered, as
    `WorkoutSession.hold` does, so a hold logged into its rest, or an EMOM started over one, never stops between.
  - **Logging.** It logs an EMOM's sets and a hold itself; a hold then starts the rest. The screen hears of a hold
    logged through `heldSets`, to go on with the other side.
  - **Ending.** The port `ActiveTimers.stopAll()` ends every run. The session calls it on finish, discard and the
    Back guard's Leave (which until now let a rest count on in the service). `App.kt` calls it once the `Lifting`
    key leaves the back stack, and `TimerService` as the task is swiped away.
  - **`SessionViewModel`.** It takes `SessionTimers` in place of the beeper and the alerts, and keeps a wall-clock
    tick of its own for the session's time and the time since the last set, which move between runs. The
    "asks once" latch is its own, so a second session in the process asks again.
  - **F3.** The clocks are `SessionViewModel.clocks`, read through a lambda by the tiles and the hold's countdown.
  - **Tests.** `HoldTimerTest` and `SessionTimersTest` are new. Lift's flow tests move the wall clock and the
    timers' time together through `LiftTestClock`.

--- cut line ---

**6. Lift's runs survive a process kill** · S · B3
- `SessionTimers` saves the running timer (kind, start as epoch millis, config) on the active session's row: a
  nullable `timer` JSON column, additive migration, `schemas/` committed.
- On restore, the run resumes with the wall-clock difference as the elapsed time, as `WorkoutProgress` does. Past
  its end, it is finished and not replayed.
- The set draft is kept in `SavedStateHandle`, as the Lift editors keep theirs.
- Done when:
  - `LiftMigrationTest` covers the new version
  - `SessionViewModelTest` restores a rest and an EMOM mid-run
  - `journeys/lift-rest-alert.xml` kills the process (`adb shell am kill`) mid-rest and returns to the rest still
    counting.
- As built:
  - **Saved.** `SavedTimer` (lift-domain: `Rest`, `Emom` with its minutes done, `Hold` with the set it logs) is the
    session in progress's `timer` (`StoredTimer.kt`'s JSON, version 14, an auto-migration). `SessionTimers` writes it
    through `LiftRepository.saveTimer` when it changes, never at a tick; any end (`stopAll` included) writes null,
    and finishing a session clears it in the same statement.
  - **Restored.** `SessionTimers.restore` times the run again from its epoch start, plays no cue for the time away,
    and drops one already over (no set logged, no beep). `SessionViewModel` calls it once as it opens, from the
    session read then, not a collector (the session re-emits at each save). It stays on the screen's side: it
    starts `TimerService`, which Android refuses to start from the background.
  - **Draft.** `SessionViewModel` takes the entry's `SavedStateHandle` and keeps the new set's draft in it
    (`SavedDraft.kt`, field by field as the editors do); a restored draft wins over the last set.
  - **The journey's kill.** `am kill` leaves a process holding a foreground service, so the journey kills the
    process with `kill -9` under `adb root` and checks the PID changed; `force-stop` would drop the saved state.
  - **On `amper-37`:** a 3:00 rest killed with 2:58 left and reopened 88 seconds later showed 1:32, its notification
    back; one that ended while the process was gone was dropped. Reopened through the launcher intent (the icon,
    Recents), the back stack comes back with the session on top and the Log sheet's draft in it, as do Settings and a
    running workout. An app opened on the Timer tab after the kill was the relaunch's doing, not the restore's:
    `am start -n` names the activity without the launcher's action and category, so Android
    starts a second, fresh `MainActivity` over the restored one (`dumpsys activity activities` shows the task's `sz=2`;
    Back reveals the session). The app's own ways in reach the activity it has: notifications use the launch intent
    with `SINGLE_TOP` (`Notifications.kt`), shortcuts `CLEAR_TOP | SINGLE_TOP`.

**7. Narrow ports; ViewModels declared in Koin** · M · C3, C4, C2, G4
- **Role interfaces**, which the existing classes implement (no split yet):
  - interval side (`interval-data`): `SettingsStore`, `PresetStore`, `HistoryStore`, `PlanStore`,
    `WorkoutProgressStore`
  - Lift side (`lift-data`): `SessionStore`, `ProgramStore`, `ExerciseStore`, `GearStore`
  - each ViewModel takes only what it uses (`GearViewModel` takes `GearStore`; `TimerViewModel` takes
    `SettingsStore` and `PresetStore`).
- **ViewModels in Koin.** Each feature gets a Koin module (`timerModule`, `presetsModule`, …, `liftSessionModule`)
  with `viewModelOf(::XViewModel)`, or `viewModel { (id: Long) -> ... }` where a route passes a key. Routes
  resolve with `koinViewModel()` and `parametersOf(...)`, which ends the positional `scope.get()` chains.
  `appModules` lists the feature modules.
- `ProgramsViewModel`, `ExerciseLibraryViewModel` and `ExerciseEditorViewModel` move to files of their own (G4).
- A test `verifyAppModules` (Koin's `verify()`) checks the whole graph in `apps/app/test@jvm/`.
- Done when:
  - no `scope.get()` is left in a Route (`grep`)
  - `verifyAppModules` passes
  - at least `GearViewModelTest` and `TimerViewModelTest` run over a narrow in-memory fake
  - `CLAUDE.md`'s Screens and DI conventions say `koinViewModel`.

- As built (Koin half): Each feature declares its ViewModels in a Koin module (`timerModule`, `presetsModule`,
  `workoutModule`, `settingsModule`, `historyModule`, `liftTodayModule`, `liftSessionModule`, `liftLibraryModule`).
  Routes resolve them via `koinViewModel`, passing keys with `parametersOf`. Positional `scope.get()` calls are
  removed. `VerifyAppModulesTest` validates the entire DI graph using Koin's `verify()`.
- As built (ports half): the nine stores as listed and a tenth, `SetStore`, split from `SessionStore` (detekt's eleven
  functions an interface): the sets, plan, timer and rest of the session in progress, all `SessionTimers` takes. They
  are in a `Stores.kt` beside each repository, each bound to the same single (`binds`). A ViewModel that uses several
  takes each, so `HistoryViewModel` takes four and `GearViewModel` `GearStore` with `SessionStore` (its records);
  `SessionViewModel` keeps `LiftRepository` until ticket 8, since five stores beside its other six parameters would be
  a long list the split removes anyway. What only the app does (`ready`, `writeErrors`, `importAll`, `close`) is on no
  store: the shell, export, import and `PlanReminderScheduler` (it waits on `ready`) take the repository.
  `startSession`'s default moved from `LiftActivity` to `SessionStore` (a member inherited twice takes its default
  from one). The fakes are `InMemorySettingsStore` and `InMemoryPresetStore` (`interval-testing`) and
  `InMemoryGearStore` (`lift-testing`), in memory alone; the repositories' own tests keep the storage behaviour (a
  band archived or deleted, preset names).

**8. `SessionViewModel`, split** · M · D1, D3, D4, F2
- `SetDraftEditor`: a pure state holder for the draft, its clamping and suggestions.
- `TodayPlanEditor`: change, skip, unskip and swap, over ticket 0's plan path.
- `FinishSession`: a use case for records, plan changes, topped out, week standing and Health, run on finish,
  not in `onCleared` (D4).
- D3: `setsBefore` and `upNext`'s inputs come from the combined flows, never `.value`, so an import or a deleted
  session mid-session corrects records and suggestions.
- F2: the `untimed` combine runs `flowOn` an injected computation dispatcher, as `TodayViewModel`'s does.
- Done when:
  - each new piece has its own test file
  - `SessionViewModel` is under 300 lines with no `@Suppress`
  - `SessionFlowTest` and Lift's acceptance specs pass unchanged.

**9. Cheap first states** · S · F1
- `HistoryViewModel` and `PlanViewModel` start from a `Loading` state (the screens show what they show while
  `ready` is false today), build on the computation dispatcher, and use `WhileSubscribed(5_000)`.
- History's combine drops `repository.config`, or narrows it to what History reads with
  `map {}.distinctUntilChanged()`.
- Done when:
  - `HistoryViewModelTest` shows a Timer step rebuilds nothing
  - the screen tests pass with `waitForText`.
- As built: `HistoryUiState.Loading` and `PlanUiState.Loading` (`loading = true`) are the first values; while
  loading each screen draws its title bar and nothing under it, so History never shows its first-run card for a
  moment, and Plan's reminder is off so it asks nothing. The two keep `SharingStarted.Eagerly`: the cost was the
  first state built on the caller, and `WhileSubscribed` would rebuild (and show Loading) on each return after five
  seconds, for no saving a tab's ViewModel needs. `choices` (`planChoices` over the presets and `config`) is a flow
  of its own joined to the built state last, so a Timer step costs a `copy`; the tests check that the sections,
  week, routine and days are the same instances after one.

**10. Write errors shown** · S · E4
- `SerialWriter` publishes failures on `errors: SharedFlow<StorageError>` as well as logging them. Both
  repositories expose it.
- `AppShell` shows a notice ("Couldn't save. Try again.") through `LocalNotices`.
- `recordWorkout`, `finishSession` and `logSet` use `await`, so their callers know whether the write landed.
- Done when a `StorageTest` with a failing DAO emits the error, and an acceptance step shows the notice.
- As built: `StorageError` (its `cause`) on `SerialWriter.errors`, a few kept for a collector busy with the last;
  `IntervalRepository.writeErrors` and `LiftRepository.writeErrors`; `AppContent` collects both while the app is
  composed and shows `save_failed` through its `Notices`. The flow replays nothing: a failure while no app is
  composed (Android's activity gone, a workout ending behind it) is logged only. Only `persist` publishes: an `await`'s failure is its
  caller's, so the import's own "couldn't import" stays the one notice. `recordWorkout`, `finishSession` and
  `logSet` stay `persist`, by choice: making them `await` turns them `suspend` up through `WorkoutRecorder`, the
  timers' callbacks and the session's finish, for callers that would do nothing with the answer yet. The fakes'
  `FakeHistoryDao.failWrites` and `FakeSessionDao.failWrites` refuse writes; `SaveFailedAcceptanceTest` ends a
  workout with History refusing and sees the notice (no journey: a full disk can't be made on the emulator).

## Explicitly not this sprint (the next one's candidates)

- **Splitting `LiftRepository` into four** (C1). After ticket 7 its ports exist, so the split becomes an
  implementation detail behind them.
- **A sealed `Load`** in place of the `Equipment` branches (G1). An L of its own, best done before a new kind of
  equipment is.
- **A foreign key from `lift_set` to `session`, `ON DELETE CASCADE`** (E2). It is a table rebuild with a manual
  migration and its test. Ticket 0's transactions stop new orphans meanwhile.
- **`lift-api` and `interval-platform-api` modules; colours passed to the service as ARGB** (G2, G3). A rule
  for them in `check_module_rules.py`.
- **An `IdGenerator`; `WallClock` in the reminders** (C5). **History's view in `SavedStateHandle`** (F4).
- **The architecture diagram generated** from `module.yaml` by `check_module_rules.py`, so it can't go stale
  again.

## Suggested order and calendar

| Day | Tickets |
|---|---|
| 1 | 0 data fixes; 1 guards |
| 2–3 | 2 one container per process |
| 4 | 3 reminders |
| 5–7 | 4 `WorkoutSession` |
| 8–9 | 5 `SessionTimers` and the keep-alive |
| 10 | Buffer: full `./kotlin build`, every journey on the release build; start 6 if green |

- **0 and 1 first:** they are the only tickets that fix lost data, they are small, and they touch nothing later
  tickets move.
- **2 before 3, 4 and 5:** each of those needs a service or receiver to reach the container.
- **4 before 5:** it sets the pattern (a process-scoped owner, a port in the platform module, a service that
  injects it, a viewer ViewModel) that 5 repeats on Lift.
- **6 to 10 are ordered by value.** 6 finishes 5's story. 7 makes 8 a refactor behind narrow ports. 9 and 10 are
  independent and can go to whoever has a free afternoon.

## Risks

- **Changing the container's lifetime can surface hidden assumptions:** a `single` that expected to be rebuilt
  with the Activity, or a test that leaned on a fresh container. Mitigation: ticket 2 changes no `single`'s
  definition, only where the container is built. `AppNavigationTest` and the acceptance specs run before and
  after.
- **`startKoin` vs isolated tests.** A test that touches `GlobalContext` would leak between tests. Mitigation:
  only the three entry points call `initAppKoin`; tests keep `AppContent(koin)`. A detekt `ForbiddenMethodCall`
  rule bans `GlobalContext` outside `apps/`.
- **iOS and Native.** `initAppKoin` runs on the main thread before the first frame. Only a full `./kotlin build`
  links it, so every ticket's DoD runs one.
- **The keep-alive's notification text for an EMOM** must keep within the special-use description Play reviews.
  Update the manifest's property in the same commit and re-read it against the policy.

## Verification of the sprint as a whole

1. `./kotlin check lint detekt`, `./kotlin test -p jvm -p android` and `./kotlin build` green on `main`; CI green.
2. On `amper-37`, on the release build, every `journeys/*.xml` passes, including the new
   `lift-emom-screen-off.xml` and the reminders journey past 14 days.
3. Back out of the app mid-workout and reopen it: the same workout, still counting, and one database open.
4. `grep -rn "WorkoutService.controls\|runBlocking\|preferenceStores" apps interval lift core` finds nothing in
   main sources (desktop's exit drain excepted).
5. `docs/architecture-review.md` marks A1, A2, B1, B2, B4, B5, D2, E1, E3, E5, E6 and D5 resolved, plus whatever
   below the cut landed. `CLAUDE.md` and `architecture.md` describe the process-scoped container and the two
   session owners.
