package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpcomingChangesTest {
    private val config = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 15, rounds = 3)

    @Test
    fun `lists every change from now to the end with its offset`() {
        val changes = WorkoutEngine.upcomingChanges(WorkoutEngine.start(config).state)
        assertEquals(
            listOf(
                UpcomingChange(Phase.Work, round = 1, offsetMillis = 10_000),
                UpcomingChange(Phase.Rest, round = 1, offsetMillis = 40_000),
                UpcomingChange(Phase.Work, round = 2, offsetMillis = 55_000),
                UpcomingChange(Phase.Rest, round = 2, offsetMillis = 85_000),
                UpcomingChange(Phase.Work, round = 3, offsetMillis = 100_000),
                UpcomingChange(Phase.Done, round = 3, offsetMillis = 130_000),
            ),
            changes,
        )
    }

    @Test
    fun `the last change lands at the session's end`() {
        val state = WorkoutEngine.tick(WorkoutEngine.start(config).state, 47_000).state
        val changes = WorkoutEngine.upcomingChanges(state)
        assertEquals(Phase.Done, changes.last().phase)
        assertEquals(state.sessionRemainingMillis, changes.last().offsetMillis)
        assertEquals(config.totalSeconds * 1_000L - 47_000, changes.last().offsetMillis)
    }

    @Test
    fun `offsets count from the time left in the current interval not its length`() {
        val state = WorkoutEngine.tick(WorkoutEngine.start(config).state, 3_000).state
        val changes = WorkoutEngine.upcomingChanges(state)
        assertEquals(UpcomingChange(Phase.Work, round = 1, offsetMillis = 7_000), changes.first())
        assertEquals(37_000L, changes[1].offsetMillis)
    }

    @Test
    fun `without rest the work intervals follow one another`() {
        val changes = WorkoutEngine.upcomingChanges(WorkoutEngine.start(config.copy(restSeconds = 0)).state)
        assertEquals(
            listOf(
                UpcomingChange(Phase.Work, round = 1, offsetMillis = 10_000),
                UpcomingChange(Phase.Work, round = 2, offsetMillis = 40_000),
                UpcomingChange(Phase.Work, round = 3, offsetMillis = 70_000),
                UpcomingChange(Phase.Done, round = 3, offsetMillis = 100_000),
            ),
            changes,
        )
    }

    @Test
    fun `in the last round's work only the end is ahead`() {
        val lastWork = WorkoutEngine.tick(WorkoutEngine.start(config).state, 100_000).state
        assertEquals(Phase.Work, lastWork.phase)
        assertEquals(3, lastWork.round)
        assertEquals(
            listOf(UpcomingChange(Phase.Done, round = 3, offsetMillis = 30_000)),
            WorkoutEngine.upcomingChanges(lastWork),
        )
    }

    @Test
    fun `added time moves every change back by as much`() {
        val state = WorkoutEngine.start(config).state
        val before = WorkoutEngine.upcomingChanges(state)
        val after = WorkoutEngine.upcomingChanges(WorkoutEngine.addTime(state))
        assertEquals(
            before.map { it.copy(offsetMillis = it.offsetMillis + WorkoutEngine.ADD_TIME_MILLIS) },
            after,
        )
    }

    @Test
    fun `a finished workout has nothing ahead`() {
        val done = WorkoutEngine.tick(WorkoutEngine.start(config).state, 200_000).state
        assertTrue(done.isDone)
        assertTrue(WorkoutEngine.upcomingChanges(done).isEmpty())
    }

    @Test
    fun `a long workout has more changes than a phone will schedule`() {
        val changes = WorkoutEngine.upcomingChanges(WorkoutEngine.start(config.copy(rounds = 99)).state)
        assertEquals(2 * 99, changes.size)
        assertEquals(changes.sortedBy { it.offsetMillis }, changes)
    }
}
