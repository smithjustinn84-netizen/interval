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

    /** The work interval [elapsedMillis] in. */
    private fun after(elapsedMillis: Long) = WorkoutEngine.tick(work, elapsedMillis).state

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

    @Test
    fun `the warm-up and cool-down sit outside the rounds and say what follows`() {
        val config =
            IntervalConfig(
                prepSeconds = 10,
                workSeconds = 20,
                restSeconds = 10,
                rounds = 2,
                warmUpSeconds = 60,
                coolDownSeconds = 90,
            )
        val states = generateSequence(WorkoutEngine.start(config).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .map { it.toUiState(running = true) }
            .toList()
        assertEquals(
            listOf(Phase.Prep, Phase.WarmUp, Phase.Work, Phase.Rest, Phase.Work, Phase.CoolDown),
            states.map { it.phase },
        )
        assertEquals(listOf(0, 0, 0, 1, 1, 2), states.map { it.completedRounds })
        assertEquals(
            listOf(
                NextUp.WarmUp("1:00"),
                NextUp.Work("0:20"),
                NextUp.Rest("0:10"),
                NextUp.Round(2, 2),
                NextUp.CoolDown("1:30"),
                null,
            ),
            states.map { it.next },
        )
        assertTrue(states.last().hasProgress)
    }

    @Test
    fun `a workout of sets says the rest between them and the set after it`() {
        val sets =
            IntervalConfig(
                prepSeconds = 0,
                workSeconds = 20,
                restSeconds = 10,
                rounds = 2,
                sets = 3,
                setRestSeconds = 60,
            )
        val course = generateSequence(WorkoutEngine.start(sets).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .map { it.toUiState(running = true) }
            .toList()
        // Work, rest, work, then the set's rest.
        assertEquals(NextUp.SetRest("1:00"), course[2].next)
        val setRest = course[3]
        assertEquals(Phase.SetRest, setRest.phase)
        assertEquals(NextUp.Set(2, 3), setRest.next)
        assertEquals(2, setRest.completedRounds)
        assertEquals(6, setRest.rounds)
        val secondSet = course[4]
        assertEquals(2 to 1, secondSet.set to secondSet.round)
        assertEquals(0, secondSet.completedSetRounds)
        assertEquals(NextUp.Rest("0:10"), secondSet.next)
        assertEquals(NextUp.LastRound, course.last().next)
    }

    @Test
    fun `a ladder says the coming round's length where it differs`() {
        val ladder =
            IntervalConfig(
                prepSeconds = 0,
                workSeconds = 20,
                restSeconds = 10,
                rounds = 3,
                sets = 2,
                workStepSeconds = 10,
            )
        val course = generateSequence(WorkoutEngine.start(ladder).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .map { it.toUiState(running = true) }
            .toList()
        assertEquals("0:20", course[0].clock)
        assertEquals(NextUp.Round(2, 3, "0:30"), course[1].next)
        assertEquals("0:30", course[2].clock)
        // The ordinary rest after the set's last round, then the next set starting over.
        assertEquals(NextUp.Set(2, 2, "0:20"), course[5].next)
        val flat = ladder.copy(workStepSeconds = 0)
        val rest = WorkoutEngine.advance(WorkoutEngine.start(flat).state).state.toUiState(running = true)
        assertEquals(NextUp.Round(2, 3), rest.next)
    }
}
