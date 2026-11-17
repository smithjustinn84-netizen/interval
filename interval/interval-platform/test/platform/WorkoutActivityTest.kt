package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutOutline
import dev.justinsmith.interval.timer.WorkoutState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutActivityTest {
    private val colors = StatusColors(prep = 1, work = 2, rest = 3, done = 4, paused = 5)
    private val words = WorkoutActivityWords("Work", "Round 1 of 3", "Open the app", "Workout", "Pause", "End")
    private val now = 1_000_000L

    private fun state(config: IntervalConfig, elapsedMillis: Long): WorkoutState =
        WorkoutEngine.tick(WorkoutEngine.start(config).state, elapsedMillis).state

    private fun status(state: WorkoutState, paused: Boolean = false) = WorkoutStatus(
        phase = state.phase,
        paused = paused,
        clock = "0:25",
        round = state.round,
        rounds = state.config.rounds,
        outline = WorkoutOutline.of(state),
        set = state.set,
        block = state.block,
    )

    private val config = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 15, rounds = 3)

    @Test
    fun `the interval counts to the next change and the workout to the last`() {
        // Five seconds into the first work, 22 seconds in all.
        val state = state(config, 15_000)
        val activity = workoutActivity(status(state), WorkoutEngine.upcomingChanges(state), now, colors, words)
        assertEquals(LiveActivitySpan(now - 5_000, now + 25_000), activity.interval)
        // Prep, three works and two rests: 130 seconds, 15 of them gone.
        assertEquals(LiveActivitySpan(now - 15_000, now + 115_000), activity.run)
        assertEquals(colors.work, activity.colorArgb)
        assertNull(activity.pausedClock)
        assertEquals("Open the app", activity.staleTitle)
        assertEquals("Workout", activity.runLabel)
        assertEquals("Pause", activity.toggleLabel)
        assertEquals("End", activity.endLabel)
    }

    @Test
    fun `the bar is each interval in its phase's colour`() {
        val state = state(config, 0)
        val activity = workoutActivity(status(state), WorkoutEngine.upcomingChanges(state), now, colors, words)
        assertEquals(
            listOf(10 to 1, 30 to 2, 15 to 3, 30 to 2, 15 to 3, 30 to 2),
            activity.segments.map { it.length to it.colorArgb },
        )
    }

    @Test
    fun `a workout past what the bar draws apart is drawn as Android's`() {
        val long = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 30)
        val state = state(long, 0)
        val activity = workoutActivity(status(state), WorkoutEngine.upcomingChanges(state), now, colors, words)
        assertEquals(listOf(LiveActivitySegment(10 + 30 * 20 + 29 * 10, null)), activity.segments)
    }

    @Test
    fun `paused it stands still at its clock and how far it is`() {
        val state = state(config, 23_000)
        val activity = workoutActivity(status(state, paused = true), emptyList(), now, colors, words)
        assertEquals(true, activity.paused)
        assertEquals("0:25", activity.pausedClock)
        assertNull(activity.interval)
        assertNull(activity.run)
        assertEquals(colors.paused, activity.colorArgb)
        assertEquals(23.0 / 130, activity.progress)
        // Its buttons stay, to resume or end it.
        assertEquals("Pause", activity.toggleLabel)
        assertEquals("End", activity.endLabel)
    }

    @Test
    fun `done it has nothing to count`() {
        val state = state(config, 200_000)
        val activity = workoutActivity(status(state), WorkoutEngine.upcomingChanges(state), now, colors, words)
        assertEquals(Phase.Done, state.phase)
        assertNull(activity.run)
        assertEquals(1.0, activity.progress)
        assertEquals(colors.done, activity.colorArgb)
        assertNull(activity.toggleLabel)
        assertNull(activity.endLabel)
    }
}
