package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.platform.Recovery
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutRunHeartTest {
    private val config = IntervalConfig(prepSeconds = 0, workSeconds = 60, restSeconds = 60, rounds = 3)

    /** A run begun at 0 with a minute of work then a minute of rest, [rounds] times, each a span closed. */
    private fun run(rounds: Int, samples: List<Int> = emptyList()): WorkoutRun {
        val spans = (0 until rounds).flatMap { round ->
            val at = round * 120_000L
            listOf(CourseSpan(Phase.Work, at, at + 60_000L), CourseSpan(Phase.Rest, at + 60_000L, at + 120_000L))
        }
        return WorkoutRun(
            id = 1L,
            engine = WorkoutEngine.start(config).state,
            running = false,
            startedAt = 0L,
            spans = spans,
            openSince = rounds * 120_000L,
            openPhase = null,
            ended = true,
            heartRateSamples = samples.takeIf { it.isNotEmpty() },
            liveSamples = samples,
        )
    }

    /** A sample every five seconds: 170 through each work, then falling 3 a sample through each rest. */
    private fun samples(rounds: Int) = List(rounds) { List(12) { 170 } + List(12) { 170 - 3 * (it + 1) } }.flatten()

    @Test
    fun `a rest runs from a work's end to the next work's start`() {
        assertEquals(listOf(60..119, 180..239), run(rounds = 3).rests)
    }

    @Test
    fun `a pause between two works is part of the rest`() {
        val paused = run(rounds = 1).copy(
            spans = listOf(
                CourseSpan(Phase.Work, 0L, 60_000L),
                CourseSpan(Phase.Rest, 60_000L, 90_000L),
                CourseSpan(null, 90_000L, 150_000L),
                CourseSpan(Phase.Work, 150_000L, 210_000L),
            ),
        )

        assertEquals(listOf(60..149), paused.rests)
    }

    @Test
    fun `the summary reads recovery over the rests and the load`() {
        val state = run(rounds = 3, samples(rounds = 3)).toUiState(emptyList(), emptyList())

        assertEquals(Recovery(listOf(36, 36), seconds = 60), state.recovery)
        // Three minutes at 170 (zone 4 of 190) and three falling through zones 4 to 2.
        assertEquals(true, (state.heartLoad ?: 0) > 0)
    }

    @Test
    fun `with no samples the summary reads neither`() {
        val state = run(rounds = 3).toUiState(emptyList(), emptyList())

        assertNull(state.recovery)
        assertNull(state.heartLoad)
    }

    @Test
    fun `each work so far is marked along the trace`() {
        val marks = run(rounds = 2, samples(rounds = 2)).liveMarks

        assertEquals(listOf(0f..0.25f, 0.5f..0.75f), marks)
    }
}
