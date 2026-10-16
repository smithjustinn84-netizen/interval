package dev.justinsmith.interval.workout

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The screen's view of the engine: what it derives for the countdown emphasis. */
class WorkoutUiStateTest {
    private val work = WorkoutEngine.advance(WorkoutEngine.start(IntervalConfig(workSeconds = 20)).state).state

    /** The work interval [elapsedMillis] in, fed to the engine in ticks it accepts whole. */
    private fun after(elapsedMillis: Long) = generateSequence(0L) { it + WorkoutEngine.MAX_TICK_MILLIS }
        .takeWhile { it < elapsedMillis }
        .fold(work) { state, start ->
            WorkoutEngine.tick(state, minOf(WorkoutEngine.MAX_TICK_MILLIS, elapsedMillis - start)).state
        }

    @Test
    fun `seconds left round up as the clock shows them`() {
        val state = after(elapsedMillis = 16_100).toUiState(running = true)
        assertEquals(Phase.Work, state.phase)
        assertEquals("0:04", state.clock)
        assertEquals(4, state.secondsLeft)
    }

    @Test
    fun `the last three seconds count down only while running`() {
        assertFalse(after(16_100).toUiState(running = true).countingDown, "4 seconds left")
        assertTrue(after(17_100).toUiState(running = true).countingDown, "3 seconds left")
        assertTrue(after(19_900).toUiState(running = true).countingDown, "1 second left")
        assertFalse(after(19_900).toUiState(running = false).countingDown, "paused")
    }

    @Test
    fun `a finished workout counts nothing down`() {
        val done = generateSequence(work) { WorkoutEngine.advance(it).state }.first { it.isDone }
        val state = done.toUiState(running = false)
        assertEquals(0, state.secondsLeft)
        assertFalse(state.countingDown)
    }
}
