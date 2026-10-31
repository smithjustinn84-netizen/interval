package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutOutlineTest {
    private val config = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 15, rounds = 3)

    @Test
    fun `lays every interval end to end from the start`() {
        val outline = WorkoutOutline.of(WorkoutEngine.start(config).state)
        assertEquals(
            listOf(
                OutlinedInterval(Phase.Prep, round = 1, seconds = 10),
                OutlinedInterval(Phase.Work, round = 1, seconds = 30),
                OutlinedInterval(Phase.Rest, round = 1, seconds = 15),
                OutlinedInterval(Phase.Work, round = 2, seconds = 30),
                OutlinedInterval(Phase.Rest, round = 2, seconds = 15),
                OutlinedInterval(Phase.Work, round = 3, seconds = 30),
            ),
            outline.intervals,
        )
        assertEquals(0, outline.elapsedSeconds)
        assertEquals(config.totalSeconds, outline.totalSeconds)
    }

    @Test
    fun `counts the elapsed time in whole seconds`() {
        val state = WorkoutEngine.tick(WorkoutEngine.start(config).state, 47_600).state
        assertEquals(47, WorkoutOutline.of(state).elapsedSeconds)
    }

    @Test
    fun `without prep or rest only the work intervals are laid out`() {
        val outline = WorkoutOutline.of(WorkoutEngine.start(config.copy(prepSeconds = 0, restSeconds = 0)).state)
        assertEquals(listOf(Phase.Work, Phase.Work, Phase.Work), outline.intervals.map { it.phase })
        assertEquals(90, outline.totalSeconds)
    }

    @Test
    fun `added time lengthens the current interval only`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val state = WorkoutEngine.addTime(WorkoutEngine.tick(work, 5_000).state)
        val outline = WorkoutOutline.of(state)
        // As the screen's ring: the interval becomes the time left, 25 s and the 10 added, and starts again.
        assertEquals(35, outline.intervals[1].seconds)
        assertEquals(30, outline.intervals[3].seconds)
        assertEquals(10, outline.elapsedSeconds)
        assertEquals(state.sessionRemainingMillis / 1_000, (outline.totalSeconds - outline.elapsedSeconds).toLong())
    }

    @Test
    fun `a skip puts the point at the next interval's start`() {
        val prep = WorkoutEngine.tick(WorkoutEngine.start(config).state, 4_000).state
        assertEquals(10, WorkoutOutline.of(WorkoutEngine.advance(prep).state).elapsedSeconds)
    }

    @Test
    fun `back puts the point at the interval's start`() {
        val work = WorkoutEngine.tick(WorkoutEngine.start(config).state, 25_000).state
        assertEquals(10, WorkoutOutline.of(WorkoutEngine.back(work)).elapsedSeconds)
    }

    @Test
    fun `over it is the whole bar`() {
        val done = WorkoutEngine.tick(WorkoutEngine.start(config).state, 999_000).state
        val outline = WorkoutOutline.of(done)
        assertEquals(outline.totalSeconds, outline.elapsedSeconds)
    }
}
