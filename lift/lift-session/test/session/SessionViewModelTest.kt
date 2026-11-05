@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BAR_KG_STEP
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.RecordingKeepAlive
import dev.justinsmith.lift.fakes.logSet
import dev.justinsmith.lift.platform.TimerRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

/** The sheet's draft, which the Next set card composes: what a lift picked starts from, and how it steps. */
class SessionViewModelTest {
    private val storage = FakeLiftStorage()
    private var now = 100_000L
    private val repository = with(storage) {
        LiftRepository(
            sessions,
            bells,
            following,
            programs,
            exercises,
            bands,
            imports,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            clock = { now },
        )
    }

    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(main)

    private val keepAlive = RecordingKeepAlive()

    // The timers tick on a dispatcher nothing advances: these tests drive the session, not the time.
    private val timerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())

    /** The process's timers: every ViewModel a test makes shares them, as every session in a process does. */
    private val timers =
        SessionTimers(repository.sets, WallClock { now }, TestTimeSource(), RecordingBeeper(), keepAlive, timerScope)

    @AfterTest
    fun resetMainDispatcher() {
        timers.close()
        healthScope.cancel()
        Dispatchers.resetMain()
    }

    private val intervals = FakeIntervalActivity()
    private val health = RecordingHealthStore(HealthStatus.Connected)

    private val filterStore = ExerciseFilterStore()

    /** Where the health store's second write waits for the summary's words: the process's, not the screen's. */
    private val healthScope = TestScope()

    /** The session's page as the app opens it: over a session started first, as the Lift tab's button does. */
    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): SessionViewModel {
        repository.sessions.startSession()
        return opened(savedState)
    }

    /** The page's ViewModel alone, over whatever is stored. */
    private fun opened(savedState: SavedStateHandle = SavedStateHandle()) = SessionViewModel(
        repository.sessions,
        repository.sets,
        repository.gear,
        repository.programs,
        repository.exercises,
        filterStore,
        TodayPlanEditor(repository.sets, repository.programs, repository.gear),
        FinishSession(
            repository.sessions,
            repository.programs,
            repository.gear,
            intervals,
            FixedTimestampFormatter,
            { now },
            SessionHealth(health, { now }, healthScope, settleMillis = SETTLE_MILLIS),
        ),
        timers,
        FixedTimestampFormatter,
        { now },
        savedState,
        computation = main,
    )

    private inline fun <T> SessionViewModel.use(block: SessionViewModel.() -> T): T = try {
        block()
    } finally {
        viewModelScope.cancel()
    }

    /** A set of [exercise] in a session finished before now, [kg] × [reps] on [side], with [band] if any. */
    private fun earlier(
        id: Long,
        exercise: Exercise,
        kg: Double,
        reps: Int = 8,
        side: Side? = null,
        band: String? = null,
    ) {
        storage.sessions.sessions.update { all ->
            if (all.any { it.uid == EARLIER }) all else all + SessionEntity(EARLIER, 0, 60_000, "Earlier", 60_000)
        }
        storage.sessions.sets.update {
            it + SetEntity(
                id,
                EARLIER,
                exercise.key,
                kg,
                reps,
                side?.name,
                loggedAtEpochMillis = id,
                equipment = exercise.equipment.name,
                band = band,
            )
        }
    }

    @Test
    fun `the page starts no session of its own`() {
        opened().use {
            assertNull(repository.sessions.active.value)
            assertFalse(sessionGone)
        }
    }

    @Test
    fun `a page a killed process left on its summary has no session to go on with`() {
        val savedState = SavedStateHandle()
        viewModel(savedState).use {
            logSet()
            assertTrue(finish("Press"))
            assertFalse(sessionGone)
        }
        // The process is killed on the summary: the page comes back over the same saved state, its session done.
        opened(savedState).use {
            assertTrue(sessionGone)
            assertNull(repository.sessions.active.value)
        }
    }

    @Test
    fun `a page a killed process left in its session goes on with it`() {
        val savedState = SavedStateHandle()
        viewModel(savedState).use { logSet() }
        opened(savedState).use {
            assertFalse(sessionGone)
            assertEquals(1, state.value.session?.sets?.size)
        }
    }

    @Test
    fun `a rest a killed process left goes on as the session opens`() {
        repository.sessions.startSession()
        repository.sets.saveTimer(SavedTimer.Rest(now - 20_000, 60))
        viewModel().use {
            assertEquals(RestClock(60).at(20_000), clocks.value.rest)
            assertEquals(TimerRun.Rest(now - 20_000, now + 40_000), keepAlive.runs.last())
        }
    }

    @Test
    fun `an EMOM a killed process left goes on with its minutes done`() {
        repository.sessions.startSession()
        val started = now - EmomClock.COUNT_IN_MILLIS - EmomClock.MINUTE_MILLIS - 20_000
        repository.sets.saveTimer(
            SavedTimer.Emom(started, BuiltInExercise.Press, Load.Bell(16.0), 5, 3, Side.Left, setOf(1)),
        )
        viewModel().use {
            assertEquals(setOf(1), state.value.emom?.done)
            assertEquals(2, clocks.value.emomState?.minute)
            timers.emomSetDone()
            assertEquals(listOf(Side.Right), repository.sessions.active.value?.sets?.map { it.side })
        }
    }

    @Test
    fun `the sheet comes back as it was over the last set`() {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.Press, 12.0, 5, Side.Left)
        val savedState = SavedStateHandle()
        viewModel(savedState).use {
            draft.update { it.copy(exercise = BuiltInExercise.Snatch, bellKg = 20.0, reps = 7) }
        }
        // The process is killed: the next screen's ViewModel is made over the same saved state.
        viewModel(savedState).use {
            assertEquals(BuiltInExercise.Snatch, state.value.draft.exercise)
            assertEquals(20.0, state.value.draft.bellKg)
            assertEquals(7, state.value.draft.reps)
        }
    }

    @Test
    fun `the log sheet's filters narrow its list`() = viewModel().use {
        filterStore.update { it.copy(equipment = setOf(Equipment.Kettlebell)) }
        assertEquals(setOf(Equipment.Kettlebell), state.value.library.filters.equipment)
    }

    @Test
    fun `picking a lift suggests the bell it was last done with and shows that set as last time`() {
        earlier(1, BuiltInExercise.Press, 12.0, side = Side.Left)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.Press) }
            assertEquals(12.0, state.value.draft.bellKg)
            assertEquals(1L, state.value.draftLastTime?.id)
        }
    }

    @Test
    fun `picking a lift never done keeps the bell in hand`() {
        viewModel().use {
            draft.update { it.copy(bellKg = 24.0) }
            draft.update { it.copy(exercise = BuiltInExercise.Press) }
            assertEquals(24.0, state.value.draft.bellKg)
            assertNull(state.value.draftLastTime)
        }
    }

    @Test
    fun `picking band work suggests its last band`() {
        val heavy = repository.gear.bands.value.last()
        earlier(1, BuiltInExercise.BandPullApart, 0.0, band = heavy.id)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.BandPullApart) }
            assertEquals(heavy.id, state.value.draft.band.id)
        }
    }

    @Test
    fun `picking a bodyweight lift suggests the weight last added`() {
        earlier(1, BuiltInExercise.PushUp, 8.0)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.PushUp) }
            assertEquals(8.0, state.value.draft.addedKg)
        }
    }

    @Test
    fun `the load steps among the owned bells and stops at the ends`() {
        repository.gear.setOwned(16.0, owned = true)
        repository.gear.setOwned(24.0, owned = true)
        viewModel().use {
            assertEquals(16.0, state.value.draft.bellKg)
            draft.stepLoad(1)
            assertEquals(24.0, state.value.draft.bellKg)
            draft.stepLoad(1)
            assertEquals(24.0, state.value.draft.bellKg)
            draft.stepLoad(-1)
            draft.stepLoad(-1)
            assertEquals(16.0, state.value.draft.bellKg)
        }
    }

    @Test
    fun `the bar steps by its plates and never below the bar`() {
        repository.gear.setBar(20.0)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.BarbellDeadlift) }
            assertEquals(20.0, state.value.draft.barKg)
            draft.stepLoad(-1)
            assertEquals(20.0, state.value.draft.barKg)
            draft.stepLoad(1)
            assertEquals(20.0 + BAR_KG_STEP, state.value.draft.barKg)
        }
    }

    @Test
    fun `a band steps among the bands offered and the mat has no load to step`() {
        val offered = repository.gear.bands.value
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.BandPullApart) }
            val at = offered.indexOfFirst { it.id == state.value.draft.band.id }
            draft.stepLoad(1)
            assertEquals(offered[at + 1].id, state.value.draft.band.id)

            draft.update { it.copy(exercise = BuiltInExercise.Hundred) }
            val before = state.value.draft
            draft.stepLoad(1)
            assertEquals(before, state.value.draft)
        }
    }

    @Test
    fun `logging the card's set keeps the draft and flips the side of a one-sided lift`() {
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.Press, side = Side.Left) }
            logSet()
            assertEquals(1, repository.sessions.active.value?.sets?.size)
            assertEquals(BuiltInExercise.Press, state.value.draft.exercise)
            assertEquals(Side.Right, state.value.draft.side)
        }
    }

    @Test
    fun `the platform keeps each rest to its end and lets go of a skipped one`() {
        viewModel().use {
            logSet()
            assertEquals(listOf(now + RestClock.DEFAULT_SECONDS * 1_000L), keepAlive.runs.map { it.endAtEpochMillis })
            stepRest(1)
            val longer = now + (RestClock.DEFAULT_SECONDS + RestClock.STEP_SECONDS) * 1_000L
            assertEquals(longer, keepAlive.runs.last().endAtEpochMillis)
            assertEquals(0, keepAlive.stops)
            timers.skipRest()
            assertEquals(1, keepAlive.stops)
            timers.skipRest()
            assertEquals(1, keepAlive.stops)
        }
    }

    @Test
    fun `an EMOM started over a rest is kept with no gap`() {
        viewModel().use {
            logSet()
            startDraftEmom()
            assertNull(clocks.value.rest)
            assertEquals(TimerRun.Emom(now, SetDraft.DEFAULT_MINUTES), keepAlive.runs.last())
            assertEquals(0, keepAlive.stops)
        }
    }

    @Test
    fun `leaving the session ends its rest`() {
        viewModel().use {
            logSet()
            leave()
            assertNull(clocks.value.rest)
            assertEquals(1, keepAlive.stops)
        }
    }

    @Test
    fun `a session opened after another asks to show notifications at its own first rest`() {
        viewModel().use {
            logSet()
            assertTrue(state.value.asksToShowNotifications)
            leave()
        }
        viewModel().use {
            assertFalse(state.value.asksToShowNotifications)
            logSet()
            assertTrue(state.value.asksToShowNotifications)
        }
    }

    @Test
    fun `logging a set starts a rest of the length last chosen`() {
        viewModel().use {
            assertNull(clocks.value.rest)
            logSet()
            assertEquals(RestState(RestClock.DEFAULT_SECONDS, RestClock.DEFAULT_SECONDS, 0f), clocks.value.rest)
            stepRest(1)
            val longer = RestClock.DEFAULT_SECONDS + RestClock.STEP_SECONDS
            assertEquals(longer, clocks.value.rest?.seconds)
            assertEquals(longer, storage.bells.rest.value?.seconds)
            timers.skipRest()
            assertNull(clocks.value.rest)
            logSet()
            assertEquals(longer, clocks.value.rest?.seconds)
        }
    }

    @Test
    fun `the session asks to show notifications from its first rest on and once`() {
        viewModel().use {
            assertFalse(state.value.asksToShowNotifications)
            logSet()
            assertTrue(state.value.asksToShowNotifications)
            // It stays asked as the rest ends and another starts, so the screen asks once, not each rest.
            timers.skipRest()
            assertTrue(state.value.asksToShowNotifications)
            logSet()
            assertTrue(state.value.asksToShowNotifications)
        }
    }

    @Test
    fun `a rest steps no shorter than its least`() {
        viewModel().use {
            repeat(RestClock.DEFAULT_SECONDS / RestClock.STEP_SECONDS + 2) { stepRest(-1) }
            assertEquals(RestClock.MIN_SECONDS, repository.sets.restSeconds.value)
        }
    }

    @Test
    fun `timing a hold ends the rest`() {
        viewModel().use {
            logSet()
            draft.update { it.copy(exercise = BuiltInExercise.Plank) }
            startHold()
            assertNull(clocks.value.rest)
        }
    }

    @Test
    fun `a resumed session starts from its last set on the other side`() {
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.Press, bellKg = 24.0, reps = 5, side = Side.Left) }
            logSet()
        }
        viewModel().use {
            val draft = state.value.draft
            assertEquals(BuiltInExercise.Press, draft.exercise)
            assertEquals(24.0, draft.bellKg)
            assertEquals(5, draft.reps)
            assertEquals(Side.Right, draft.side)
        }
    }

    @Test
    fun `a free session is free and a program day is not`() {
        viewModel().use { assertTrue(state.value.free) }
        repository.sessions.discardSession()
        val program = ProgramKey.BuiltIn(ProgramId.FirstHundredSwings)
        repository.programs.follow(program)
        repository.sessions.startSession(program)
        viewModel().use {
            assertFalse(state.value.free)
            assertNotNull(state.value.upNext)
            assertNull(state.value.draftLastTime)
        }
    }

    @Test
    fun `last time is the lift's last set before this session not one just logged`() {
        earlier(1, BuiltInExercise.Press, 12.0)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.Press) }
            logSet()
            assertEquals(1L, state.value.draftLastTime?.id)
        }
    }

    @Test
    fun `a set becomes a record once the session holding the record is deleted`() {
        // A lighter session first, then the one holding the record the set logged here falls short of.
        storage.sessions.sessions.update { it + SessionEntity(OLDER, 0, 30_000, "Older", 30_000) }
        storage.sessions.sets.update {
            it + SetEntity(2, OLDER, BuiltInExercise.TwoHandSwing.key, 12.0, 5, null, loggedAtEpochMillis = 2)
        }
        earlier(1, BuiltInExercise.TwoHandSwing, 24.0, reps = 12)
        viewModel().use {
            logSet()
            assertTrue(state.value.recordSetIds.isEmpty())

            repository.sessions.deleteSession(EARLIER)

            val logged = assertNotNull(repository.sessions.active.value?.sets?.single())
            assertEquals(setOf(logged.id), state.value.recordSetIds)
        }
    }

    @Test
    fun `a lift's last time follows a session deleted while this one is open`() {
        earlier(1, BuiltInExercise.Press, 12.0)
        viewModel().use {
            draft.update { it.copy(exercise = BuiltInExercise.Press) }
            assertEquals(1L, state.value.draftLastTime?.id)

            repository.sessions.deleteSession(EARLIER)

            assertNull(state.value.draftLastTime)
        }
    }

    private companion object {
        const val EARLIER = "earlier"
        const val OLDER = "older"
        const val SETTLE_MILLIS = 1_000L
    }

    @Test
    fun `finishing the program's last day says which program it finished`() {
        val goblets = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        storage.following.row.value = FollowingEntity(programId = goblets.stored, startedAtEpochMillis = 0)
        storage.sessions.sessions.value = (1..7).map { day ->
            val at = day * 1_000L
            SessionEntity("d$day", at, at + 1, "Day", at + 1, programId = goblets.stored, programDay = day)
        }
        assertEquals(8, repository.programs.programDay.value?.number)
        repository.sessions.startSession(goblets)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        viewModel().use {
            assertTrue(finish("Day 8"))
            assertEquals(program(ProgramId.GobletSquatRow), state.value.finished?.lastDayOf)
        }
    }

    @Test
    fun `changes to today's plan one after another each build on the last`() {
        val goblets = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(goblets)
        repository.sessions.startSession(goblets)
        viewModel().use {
            val start = assertNotNull(state.value.upNext).load.kg
            stepNextLoad(1)
            val once = assertNotNull(state.value.upNext).load.kg
            stepNextLoad(1)
            val twice = assertNotNull(state.value.upNext).load.kg
            plan.changeBlock(0) { it.copy(rounds = it.rounds + 1) }
            assertTrue(start < once && once < twice)
            val block = repository.sessions.active.value?.plan?.blocks?.first()?.block
            assertEquals(twice, block?.load?.kg)
            assertEquals(4, block?.rounds)
        }
    }

    @Test
    fun `finishing a day before the last names no program finished`() {
        val goblets = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        storage.following.row.value = FollowingEntity(programId = goblets.stored, startedAtEpochMillis = 0)
        repository.sessions.startSession(goblets)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        viewModel().use {
            assertTrue(finish("Day 1"))
            assertNull(state.value.finished?.lastDayOf)
        }
    }

    @Test
    fun `a finished session goes to the health store with its sets and the app's calorie estimate`() {
        intervals.caloriesPerSecond = 0.1
        now = 100_000
        repository.sessions.startSession(null)
        now = 130_000
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        val uid = repository.sessions.active.value!!.uid
        now = 160_000
        viewModel().use { assertTrue(finish("Squats")) }

        val workout = health.workouts.single()
        assertEquals("lift-$uid", workout.id)
        assertEquals(HealthWorkoutKind.Strength, workout.kind)
        assertEquals("Squats", workout.title)
        assertEquals(100_000, workout.startedAtEpochMillis)
        assertEquals(160_000, workout.endedAtEpochMillis)
        assertEquals(6, workout.calories)
        assertEquals(listOf(HealthSegment(100_000, 130_000, HealthMove.Squat, repetitions = 10)), workout.segments)
    }

    @Test
    fun `a program day goes to the health store by the program's name and the session keeps its own`() {
        val goblets = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(goblets)
        repository.sessions.startSession(goblets)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        viewModel().use {
            assertEquals(1, state.value.programDay?.number)
            assertTrue(finish("Goblet squat", healthTitle = "Goblet squat and row · day 1"))
        }
        assertEquals("Goblet squat and row · day 1", health.workouts.single().title)
        assertEquals("Goblet squat", repository.sessions.sessions.value.single().title)
    }

    @Test
    fun `the summary's effort and note are kept with the session and shown on it`() {
        repository.sessions.startSession(null)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        viewModel().use {
            assertTrue(finish("Squats"))
            summary.setEffort(PerceivedEffort.Hard)
            summary.setNote("  Felt strong ")

            val finished = assertNotNull(state.value.finished)
            assertEquals(PerceivedEffort.Hard, finished.session.effort)
            assertEquals("Felt strong", finished.session.note)
            assertEquals("  Felt strong ", finished.note)
        }
        val kept = repository.sessions.sessions.value.single()
        assertEquals(PerceivedEffort.Hard, kept.effort)
        assertEquals("Felt strong", kept.note)
    }

    @Test
    fun `the summary's words reach the health store after its screen is gone`() {
        repository.sessions.startSession(null)
        repository.sets.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
        val viewModel = viewModel()
        viewModel.finish("Squats")
        viewModel.summary.setEffort(PerceivedEffort.AllOut)
        viewModel.summary.describeForHealth("All out")
        ViewModelStore().apply { put("session", viewModel) }.clear()

        now = 999_000
        healthScope.testScheduler.advanceTimeBy(SETTLE_MILLIS + 1)
        healthScope.testScheduler.runCurrent()

        assertEquals(2, health.workouts.size)
        assertEquals(health.workouts.first().id, health.workouts.last().id)
        assertEquals("All out", health.workouts.last().notes)
        assertEquals(999_000, health.workouts.last().version)
    }

    @Test
    fun `a session with no sets is discarded and goes nowhere`() {
        repository.sessions.startSession(null)
        viewModel().use { assertFalse(finish("Nothing")) }
        assertTrue(health.workouts.isEmpty())
    }
}
