@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.FlowSide
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A mobility flow on the session: opened by its key, said in its own words, kept as the flow it is. */
class FlowSessionTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val lifts = FakeLiftStorage().repository()
    private val player = RecordingCuePlayer()
    private val health = RecordingHealthStore(HealthStatus.Connected)
    private val tickers = mutableListOf<WorkoutTicker>()
    private val onTimer = IntervalConfig(prepSeconds = 5, workSeconds = 5, restSeconds = 5, rounds = 2)

    /** Wrist circles 0:30, two holds of 0:45, then finger pulls and a forearm stretch of 0:30 a side. */
    private val wrists = builtInFlows.first { it.key == "wrists-fingers" }

    @BeforeTest
    fun setUp() {
        repository.settings.updateConfig { onTimer }
    }

    @AfterTest
    fun closeTickers() = tickers.forEach(WorkoutTicker::close)

    private fun TestScope.openFlow(key: String? = wrists.key): WorkoutSession {
        val scope =
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))
        val ticker = WorkoutTicker(testScheduler.timeSource, scope).also { tickers += it }
        val recorder = with(repository) {
            WorkoutRecorder(history, presets, settings, lifts.sessions, FixedTimestampFormatter, { 7L }, health)
        }
        val background = RecordingBackgroundWorkout()
        return WorkoutSession(repository.settings, repository.settings, player, { 7L }, background, recorder, ticker)
            .apply { open(1, key) }
    }

    private val WorkoutSession.ui: WorkoutUiState get() = checkNotNull(state.value).toUiState(emptyList())

    private fun TestScope.run(seconds: Int) {
        advanceTimeBy(seconds * 1_000L + WorkoutTicker.TICK_MILLIS)
        runCurrent()
    }

    @Test
    fun `a flow opened by its key runs after the user's get-ready and leaves the Timer alone`() = runTest(dispatcher) {
        val session = openFlow()

        val state = session.ui
        assertEquals(Phase.Prep, state.phase)
        assertEquals(wrists.config(prepSeconds = 5), state.config)
        assertEquals("4:55", state.total)
        assertEquals("Wrists and fingers", state.presetName)
        assertFalse(state.canSavePreset)
        assertEquals(onTimer, repository.settings.config.value)
    }

    @Test
    fun `the screen is told the move coming then the move held and what follows`() = runTest(dispatcher) {
        val session = openFlow()

        // Get ready: the first move is coming, none is done, nothing follows yet.
        val coming = checkNotNull(session.ui.flow)
        assertEquals("Wrists and fingers", coming.name)
        assertEquals(Triple("Wrist circles", 1, 5), Triple(coming.step?.move?.name, coming.step?.number, coming.of))
        assertEquals(0, coming.done)
        assertNull(coming.next)

        run(5)
        val held = checkNotNull(session.ui.flow)
        assertEquals(Phase.Work, session.ui.phase)
        assertEquals("Wrist circles", held.step?.move?.name)
        assertEquals("Palms down, fingers back", held.next?.move?.name)

        // Into the change-over: the first move is done and the second is coming.
        run(30)
        assertEquals(Phase.SetRest, session.ui.phase)
        assertEquals(1, session.ui.flow?.done)
        assertEquals(2, session.ui.flow?.step?.number)
    }

    @Test
    fun `a move with sides is held on the left then the right`() = runTest(dispatcher) {
        val session = openFlow()
        // Past the get-ready and three moves with their change-overs, into the finger pulls.
        repeat(7) { session.skip() }

        val left = checkNotNull(session.ui.flow)
        assertEquals(Phase.Work, session.ui.phase)
        assertEquals("Finger pulls" to FlowSide.Left, left.step?.move?.name to left.step?.side)
        assertEquals("Finger pulls" to FlowSide.Right, left.next?.move?.name to left.next?.side)
        assertEquals(3, left.done)

        session.skip()
        assertEquals(Phase.Rest, session.ui.phase)
        assertEquals(FlowSide.Right, session.ui.flow?.step?.side)
        session.skip()
        assertEquals(FlowSide.Right, session.ui.flow?.step?.side)
        assertEquals("Forearm stretch", session.ui.flow?.next?.move?.name)
        // Both sides of the fourth move are behind it only once the right is.
        assertEquals(3, session.ui.flow?.done)
        session.skip()
        assertEquals(4, session.ui.flow?.done)
    }

    @Test
    fun `a flow is said in its own words with no halfway`() = runTest(dispatcher) {
        val session = openFlow()
        val countdown = listOf(Speech.Countdown(3), Speech.Countdown(2), Speech.Countdown(1))

        run(5 + 30 + 10 + 45)
        assertEquals(
            listOf(Speech.NextMove("Wrist circles")) + countdown + Speech.Begin + countdown +
                Speech.NextMove("Palms down, fingers back") + countdown + Speech.Begin + countdown +
                Speech.NextMove("Backs of the hands"),
            player.spoken,
        )

        // On through the third move to the finger pulls: a side each, and the change between them.
        player.spoken.clear()
        run(10 + 45 + 10 + 30 + 5)
        assertEquals(
            countdown + Speech.Begin + countdown + Speech.NextMove("Finger pulls") + countdown +
                Speech.Side(FlowSide.Left) + countdown + Speech.SwitchSides + countdown + Speech.Side(FlowSide.Right),
            player.spoken,
        )
        // The 0:45 holds are long enough for a halfway, which a hold doesn't get.
        assertFalse(Beep.Halfway in player.beeps)
        assertTrue(session.ui.running)
    }

    @Test
    fun `the notification is told the flow and its move`() = runTest(dispatcher) {
        val session = openFlow()
        repeat(7) { session.skip() }

        val status = checkNotNull(session.status.value)
        assertEquals(wrists, status.flow)
        assertEquals("Finger pulls" to FlowSide.Left, status.step?.move?.name to status.step?.side)
    }

    @Test
    fun `a finished flow is kept as mobility and named in the health store`() = runTest(dispatcher) {
        val session = openFlow()
        run(wrists.config(prepSeconds = 5).totalSeconds)

        val state = session.ui
        assertTrue(state.isDone)
        assertEquals(5 to 5, state.flow?.done to state.flow?.of)
        assertNull(state.flow?.step)
        assertEquals(Speech.Finished, player.spoken.last())
        val entry = repository.history.history.value.single()
        assertEquals(Activity.Mobility, entry.config.activity)
        assertEquals(wrists, builtInFlows.firstOrNull { it.config == entry.config.copy(prepSeconds = 10) })
        val workout = health.workouts.single()
        assertEquals(HealthWorkoutKind.Mobility, workout.kind)
        assertEquals("Wrists and fingers", workout.title)
    }

    @Test
    fun `a flow repeated from its summary is the flow again`() = runTest(dispatcher) {
        val session = openFlow()
        repeat(2) { session.skip() }
        session.end()
        assertTrue(session.ui.ended)

        session.restart()
        assertEquals(Phase.Prep, session.ui.phase)
        assertEquals("Wrist circles", session.ui.flow?.step?.move?.name)
    }

    @Test
    fun `a key no flow has runs the Timer's own workout`() = runTest(dispatcher) {
        val session = openFlow(key = "gone")

        assertEquals(onTimer, session.ui.config)
        assertNull(session.ui.flow)
        assertEquals(Speech.GetReady, player.spoken.single())
    }
}
