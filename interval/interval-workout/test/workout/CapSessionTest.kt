@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.model.CapRound
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A time cap on the session: its rounds tapped, taken back, kept through a killed process, said and recorded. */
class CapSessionTest : MainDispatcherTest(StandardTestDispatcher()) {
    private val repository = FakeStorage().repository(now = { 7L })
    private val lifts = FakeLiftStorage().repository()
    private val player = RecordingCuePlayer()
    private val health = RecordingHealthStore(HealthStatus.Connected)
    private val tickers = mutableListOf<WorkoutTicker>()

    /** Five seconds to get ready, then a five-minute cap. */
    private val cap = IntervalConfig(prepSeconds = 5, workSeconds = 300, restSeconds = 0, rounds = 1, capped = true)

    @AfterTest
    fun closeTickers() = tickers.forEach(WorkoutTicker::close)

    private fun TestScope.open(config: IntervalConfig = cap): WorkoutSession {
        repository.settings.updateConfig { config }
        val scope =
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))
        val ticker = WorkoutTicker(testScheduler.timeSource, scope).also { tickers += it }
        val recorder = with(repository) {
            WorkoutRecorder(history, presets, flows, settings, lifts.sessions, FixedTimestampFormatter, { 7L }, health)
        }
        return with(repository) {
            WorkoutSession(settings, settings, player, {
                7L
            }, RecordingBackgroundWorkout(), recorder, ticker, flows, presets)
        }.apply { open(1) }
    }

    private fun TestScope.run(seconds: Int) {
        advanceTimeBy(seconds * 1_000L + WorkoutTicker.TICK_MILLIS)
        runCurrent()
    }

    private val WorkoutSession.rounds: List<CapRound> get() = checkNotNull(state.value).capRounds

    @Test
    fun `a round counts only in a running cap`() = runTest(dispatcher) {
        val session = open()
        session.roundDone()
        assertEquals(emptyList(), session.rounds, "Not while getting ready")

        run(65)
        session.roundDone()
        session.togglePause()
        session.roundDone()
        assertEquals(1, session.rounds.size, "Not while paused")
        assertEquals(0, session.rounds.single().block)
        assertTrue(session.rounds.single().atMillis in 60_000L..61_000L)
    }

    @Test
    fun `a round taken back leaves the ones before`() = runTest(dispatcher) {
        val session = open()
        run(30)
        session.roundDone()
        run(30)
        session.roundDone()
        session.undoRound()
        assertEquals(1, session.rounds.size)
    }

    @Test
    fun `a cap's rounds come back after the process is killed`() = runTest(dispatcher) {
        val session = open()
        run(65)
        session.roundDone()
        session.roundDone()
        tickers.forEach(WorkoutTicker::close)

        val back = with(repository) {
            val recorder =
                WorkoutRecorder(history, presets, flows, settings, lifts.sessions, FixedTimestampFormatter, {
                    7L
                }, health)
            val ticker = WorkoutTicker(testScheduler.timeSource, backgroundScope).also { tickers += it }
            WorkoutSession(settings, settings, player, {
                7L
            }, RecordingBackgroundWorkout(), recorder, ticker, flows, presets)
        }
        back.open(1)
        assertEquals(session.rounds, back.rounds)
        assertEquals(2, back.rounds.size)
    }

    @Test
    fun `ending a cap early with rounds done records them`() = runTest(dispatcher) {
        val session = open()
        run(65)
        session.roundDone()
        session.end()
        runCurrent()

        val entry = repository.history.history.value.single()
        assertTrue(entry.endedEarly)
        assertEquals(session.rounds, entry.capRounds)
    }

    @Test
    fun `a cap's end says the rounds then records them`() = runTest(dispatcher) {
        val session = open()
        run(65)
        repeat(3) {
            session.roundDone()
            run(30)
        }
        run(300)

        assertEquals(Phase.Done, checkNotNull(session.state.value).engine.phase)
        assertTrue(Speech.Left(1) in player.spoken)
        assertEquals(Speech.CapOver(3, Speech.Finished), player.spoken.last())
        assertEquals(3, repository.history.history.value.single().capRounds?.size)
    }

    @Test
    fun `back from the block after a cap never goes into it again`() = runTest(dispatcher) {
        val then = cap.withBlocks(
            listOf(Block.capOf(60), IntervalConfig(workSeconds = 30, restSeconds = 15).blocks.first()),
        )
        val session = open(then)
        run(10)
        session.roundDone()
        session.roundDone()
        run(55)
        session.back()
        val engine = checkNotNull(session.state.value).engine
        assertEquals(1, engine.block)
        assertEquals(engine.phaseMillis, engine.remainingMillis)
        assertEquals(2, session.rounds.size)
    }

    @Test
    fun `a cap neither steps back nor takes added time`() = runTest(dispatcher) {
        val session = open()
        run(65)
        val before = checkNotNull(session.state.value).engine
        session.back()
        session.addTime()
        assertEquals(before.phaseMillis, checkNotNull(session.state.value).engine.phaseMillis)
        assertEquals(before.remainingMillis, checkNotNull(session.state.value).engine.remainingMillis)
    }

    @Test
    fun `two caps keep their own rounds and the rest between says the first's`() = runTest(dispatcher) {
        val two = cap.withBlocks(listOf(Block.capOf(60, restSeconds = 30), Block.capOf(60)))
        val session = open(two)
        run(10)
        session.roundDone()
        run(60)
        assertEquals(Speech.CapOver(1, Speech.Rest), player.spoken.last { it is Speech.CapOver })

        run(35)
        session.roundDone()
        session.roundDone()
        assertEquals(
            listOf(0 to 1, 1 to 2),
            session.rounds.groupBy {
                it.block
            }.map { (block, it) -> block to it.size },
        )
    }

    @Test
    fun `the summary beats the best of the same cap before and leaves its own out`() = runTest(dispatcher) {
        repository.history.recordWorkout(
            cap,
            elapsedSeconds = 305,
            startedAtEpochMillis = 1,
            capRounds = listOf(CapRound(0, 60_000), CapRound(0, 120_000)),
        )
        val session = open()
        run(65)
        repeat(3) {
            session.roundDone()
            run(10)
        }
        run(300)

        val run = checkNotNull(session.state.value)
        val result = run.toUiState(emptyList(), emptyList(), repository.history.history.value).caps.single()
        assertEquals(3, result.score.rounds)
        assertEquals(2, result.best)
        assertTrue(result.newBest)
    }

    @Test
    fun `the notification says the rounds done in the cap`() = runTest(dispatcher) {
        val session = open()
        run(65)
        session.roundDone()
        assertEquals(1, checkNotNull(session.state.value).status(emptyList()).capRounds)
    }
}
