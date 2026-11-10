package dev.justinsmith.interval.workout

import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.builtInFlows
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

    // The screen recomposes as its state changes, and the ring's progress, which moves every tick, is apart from it.
    @Test
    fun `the screen's state stands still between ticks of one second`() {
        assertEquals(after(16_100).toUiState(running = true), after(16_900).toUiState(running = true))
    }

    @Test
    fun `a flow's state stands still between ticks of one second`() {
        val flow = builtInFlows.first()
        val held = WorkoutEngine.advance(WorkoutEngine.start(flow.config).state).state
        fun shown(elapsedMillis: Long) =
            WorkoutEngine.tick(held, elapsedMillis).state.toUiState(running = true, flows = builtInFlows)
        assertEquals(flow.name, shown(2_100).flow?.name)
        assertEquals(shown(2_100), shown(2_900))
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

    @Test
    fun `a sequence says its block and what comes after each`() {
        val sequence = IntervalConfig(
            prepSeconds = 0,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 2,
            then = listOf(Block(workSeconds = 40, restSeconds = 20, rounds = 3, sets = 2, setRestSeconds = 45)),
            blockRestSeconds = 30,
        )
        val course = generateSequence(WorkoutEngine.start(sequence).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .map { it.toUiState(running = true) }
            .toList()
        // Work, rest, work, then the rest before the second block.
        assertEquals(NextUp.BlockRest(2, "0:30"), course[2].next)
        val blockRest = course[3]
        assertTrue(blockRest.inBlockRest)
        assertEquals(NextUp.Block(2, 2, "0:40"), blockRest.next)
        assertEquals(2, blockRest.completedRounds)
        val second = course[4]
        assertEquals(Triple(1, 1, 1), Triple(second.block, second.set, second.round))
        assertEquals(2 to 3, second.sets to second.setRounds)
        assertEquals(2, second.blocks)
        assertEquals(2 + 6, second.rounds)
        assertEquals(0, second.completedSetRounds)
        assertTrue(!second.inBlockRest)
        // The second block's own set rest is a set's, not a block's.
        val setRest = course.first { it.phase == Phase.SetRest && it.block == 1 }
        assertTrue(!setRest.inBlockRest)
    }
}
