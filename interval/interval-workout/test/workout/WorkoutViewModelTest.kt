@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The page's view of the session: what it maps and hands on. How a workout runs is [WorkoutSessionTest]'s. */
class WorkoutViewModelTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val lifts = FakeLiftStorage().repository()
    private val background = RecordingBackgroundWorkout()
    private val health = RecordingHealthStore(HealthStatus.Connected)
    private val tickers = mutableListOf<WorkoutTicker>()
    private val viewModels = mutableListOf<WorkoutViewModel>()

    @BeforeTest
    fun setUp() {
        repository.settings.updateConfig {
            IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2)
        }
    }

    @AfterTest
    fun stop() {
        viewModels.forEach { it.viewModelScope.cancel() }
        tickers.forEach(WorkoutTicker::close)
    }

    /** The process's session over [repository]; a new one stands for the process started again. */
    private fun TestScope.workoutSession(repository: IntervalRepository = this@WorkoutViewModelTest.repository) =
        WorkoutSession(
            repository.settings,
            repository.settings,
            RecordingCuePlayer(),
            { 7L },
            background,
            WorkoutRecorder(
                repository.history,
                repository.presets,
                repository.flows,
                repository.settings,
                lifts.sessions,
                FixedTimestampFormatter,
                { 7L },
                health,
            ),
            WorkoutTicker(testScheduler.timeSource, tickerScope()).also { tickers += it },
            repository.flows,
            repository.presets,
        )

    private fun workoutViewModel(
        session: WorkoutSession,
        repository: IntervalRepository = this.repository,
        id: Long = WORKOUT_ID,
        flow: String? = null,
    ) = WorkoutViewModel(id, flow, session, repository.presets, repository.flows, repository.history, lifts.sessions)
        .also { viewModels += it }

    /**
     * Where a ticker ticks: on the test's scheduler, under its background scope, so a workout still running as the
     * test ends stops with it rather than ticking on through the test's last wait.
     */
    private fun TestScope.tickerScope() =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    /** Runs the ticker for [millis] of the scheduler's time. */
    private fun TestScope.run(millis: Long) {
        advanceTimeBy(millis + WorkoutTicker.TICK_MILLIS)
        runCurrent()
    }

    @Test
    fun `a sport day logged this week counts in the week the summary shows`() = runTest(dispatcher) {
        // The clock stands on day 0: the class today counts beside the workout, last week's does not.
        repository.history.logSport(Sport.Bjj, epochDay = 0)
        repository.history.logSport(Sport.Bjj, epochDay = -7)
        val viewModel = workoutViewModel(workoutSession())
        repeat(3) { viewModel.skip() }
        runCurrent()

        assertEquals(2, viewModel.state.value.week?.count)
    }

    @Test
    fun `a summary offers saving only a shape no preset has`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(workoutSession())
        repeat(3) { viewModel.skip() }
        runCurrent()
        assertEquals("0:00", viewModel.state.value.elapsed)
        assertTrue(viewModel.state.value.canSavePreset)
        assertEquals(null, viewModel.state.value.presetName)
        viewModel.saveAsPreset(Preset("Short", viewModel.state.value.config))
        runCurrent()
        assertFalse(viewModel.state.value.canSavePreset)
        assertEquals("Short", viewModel.state.value.presetName)
    }

    @Test
    fun `a preset saved from the summary shows there once its write lands`() = runTest(dispatcher) {
        val repository = FakeStorage().repository(scope = deferredStorageScope())
        runCurrent()
        repository.settings.updateConfig {
            IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2)
        }
        val viewModel = workoutViewModel(workoutSession(repository), repository)
        repeat(3) { viewModel.skip() }
        runCurrent()
        assertTrue(viewModel.state.value.canSavePreset)

        viewModel.saveAsPreset(Preset("Short", viewModel.state.value.config))
        advanceUntilIdle()

        assertEquals("Short", viewModel.state.value.presetName)
        assertFalse(viewModel.state.value.canSavePreset)
    }

    @Test
    fun `a preset saved from the summary goes on the Timer too`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(workoutSession())
        repeat(3) { viewModel.skip() }
        runCurrent()
        val reshaped = viewModel.state.value.config.copy(workSeconds = 50)
        viewModel.saveAsPreset(Preset("Longer", reshaped))
        assertEquals(50, repository.settings.config.value.workSeconds)
    }

    @Test
    fun `the summary's effort and note are kept with the workout in History`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(workoutSession())
        run(15_000)
        assertTrue(viewModel.state.value.isDone)

        viewModel.setEffort(PerceivedEffort.Hard)
        viewModel.setNote("  Felt strong ")
        runCurrent()

        assertEquals(PerceivedEffort.Hard, viewModel.state.value.effort)
        assertEquals("  Felt strong ", viewModel.state.value.note)
        val entry = repository.history.history.value.single()
        assertEquals(PerceivedEffort.Hard, entry.effort)
        assertEquals("Felt strong", entry.note)
    }

    /** The Timer set to a short workout of kettlebell snatches, a hand a work, saved as a preset of the user's. */
    private fun snatchWorkout() {
        val config =
            IntervalConfig(
                prepSeconds = 0,
                workSeconds = 5,
                restSeconds = 5,
                rounds = 2,
                activity = Activity.Kettlebell,
            )
        repository.presets.savePreset(Preset("Snatches", config, alternateHands = true))
        repository.settings.updateConfig { config }
    }

    @Test
    fun `only a workout of kettlebell snatches asks how many a set`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(workoutSession())
        run(15_000)
        assertTrue(viewModel.state.value.isDone)
        assertNull(viewModel.state.value.snatches)
    }

    @Test
    fun `the snatches counted on the summary are kept in History and opened on next time`() = runTest(dispatcher) {
        snatchWorkout()
        runCurrent()
        val first = workoutViewModel(workoutSession())
        // Not asked while it runs.
        assertNull(first.state.value.snatches)
        run(15_000)

        val asked = assertNotNull(first.state.value.snatches)
        assertNull(asked.count)
        assertEquals(SnatchCount(SnatchCount.USUAL_PER_SET, SnatchCount.USUAL_BELL_KG), asked.opensOn)
        assertNull(repository.history.history.value.single().snatches)

        first.countSnatches(SnatchCount(9, 20.0))
        runCurrent()
        assertEquals(SnatchCount(9, 20.0), first.state.value.snatches?.count)
        assertEquals(SnatchCount(9, 20.0), repository.history.history.value.single().snatches)
        first.countSnatches(null)
        runCurrent()
        assertNull(repository.history.history.value.single().snatches)
        first.countSnatches(SnatchCount(9, 20.0))
        runCurrent()
        tickers.forEach(WorkoutTicker::close)

        // The process dies on the summary: the count comes back with it.
        val restored = workoutViewModel(workoutSession())
        runCurrent()
        assertEquals(SnatchCount(9, 20.0), restored.state.value.snatches?.count)

        // The next one opens on it, counting nothing until said.
        restored.restart()
        run(15_000)
        val next = assertNotNull(restored.state.value.snatches)
        assertNull(next.count)
        assertEquals(SnatchCount(9, 20.0), next.opensOn)
    }

    @Test
    fun `the summary shows a watch's heart rate once the store has it and keeps it in History`() = runTest(dispatcher) {
        val viewModel = workoutViewModel(workoutSession())
        // Asked before the workout is over, there is nothing to read.
        viewModel.readHeartRate()
        runCurrent()
        assertTrue(health.heartRateReads.isEmpty())
        run(15_000)

        viewModel.readHeartRate()
        runCurrent()
        assertNull(viewModel.state.value.heartRate)

        health.heartRate = HeartRate(averageBpm = 150, peakBpm = 176)
        viewModel.readHeartRate()
        runCurrent()

        assertEquals(HeartRate(150, 176), viewModel.state.value.heartRate)
        assertEquals(150, repository.history.history.value.single().averageBpm)
        viewModel.readHeartRate()
        runCurrent()
        assertEquals(2, health.heartRateReads.size)
    }

    @Test
    fun `an effort and a note survive a restore of the summary`() = runTest(dispatcher) {
        val first = workoutViewModel(workoutSession())
        run(15_000)
        first.setEffort(PerceivedEffort.Steady)
        first.setNote("Legs heavy")
        runCurrent()
        // The process dies: its ticker with it, and nothing is cleared.
        tickers.forEach(WorkoutTicker::close)

        val restored = workoutViewModel(workoutSession())
        runCurrent()
        assertTrue(restored.state.value.isDone)
        assertEquals(PerceivedEffort.Steady, restored.state.value.effort)
        assertEquals("Legs heavy", restored.state.value.note)

        // Still the same entry: what changes now lands on it, not on a new one.
        restored.setEffort(PerceivedEffort.AllOut)
        runCurrent()
        assertEquals(PerceivedEffort.AllOut, repository.history.history.value.single().effort)
    }

    @Test
    fun `a ViewModel built again for the workout running shows it as it stands`() = runTest(dispatcher) {
        val session = workoutSession()
        workoutViewModel(session)
        run(2_000)

        val again = workoutViewModel(session)
        assertEquals("0:02", again.state.value.elapsed)
        assertTrue(again.state.value.running)
        assertEquals(1, background.starts)
    }

    @Test
    fun `the workout runs on when its ViewModel is cleared with the screen`() = runTest(dispatcher) {
        val session = workoutSession()
        val viewModel = workoutViewModel(session)
        run(2_000)

        ViewModelStore().apply { put("workout", viewModel) }.clear()
        run(1_000)

        assertTrue(background.kept)
        assertEquals("0:03", assertNotNull(session.state.value).toUiState(emptyList(), emptyList()).elapsed)
        assertEquals(WORKOUT_ID, repository.settings.workoutProgress.value?.id)
    }
}
