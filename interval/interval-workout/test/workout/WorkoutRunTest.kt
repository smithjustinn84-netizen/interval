package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.builtInFlows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRunTest {
    private val config = IntervalConfig(prepSeconds = 0, workSeconds = 5, restSeconds = 5, rounds = 2)
    private val begun =
        WorkoutRun(id = 1, engine = WorkoutEngine.start(config).state, running = true, startedAt = 1_000)

    @Test
    fun `a pause closes the stretch under way and opens one of its own`() {
        val paused = begun.copy(running = false).turned(phase = null, now = 3_000)
        val resumed = paused.copy(running = true).turned(Phase.Work, now = 4_000)

        assertEquals(
            listOf(CourseSpan(Phase.Work, 1_000, 3_000), CourseSpan(null, 3_000, 4_000)),
            resumed.spans,
        )
        assertEquals(4_000, resumed.openSince)
        assertEquals(Phase.Work, resumed.openPhase)
    }

    @Test
    fun `a turn at the moment the stretch opened adds none`() {
        assertTrue(begun.turned(phase = null, now = 1_000).spans.isEmpty())
    }

    @Test
    fun `a step into the next interval counts its time and turns the course`() {
        val stepped = begun.stepped(WorkoutEngine.tick(begun.engine, 5_000), now = 6_000)

        assertEquals(Phase.Rest, stepped.engine.phase)
        assertEquals(5_000, stepped.activeMillis)
        assertEquals(5_000, stepped.workMillis)
        assertTrue(stepped.turnedFrom(begun.engine))
        assertEquals(listOf(CourseSpan(Phase.Work, 1_000, 6_000)), stepped.spans)
    }

    @Test
    fun `work that runs straight into the next block's work turns the course`() {
        val backToBack = IntervalConfig(
            prepSeconds = 0,
            workSeconds = 5,
            restSeconds = 0,
            rounds = 1,
            then = listOf(Block(workSeconds = 5, restSeconds = 0, rounds = 1)),
        )
        val first = begun.copy(engine = WorkoutEngine.start(backToBack).state)
        val second = first.stepped(WorkoutEngine.tick(first.engine, 5_000), now = 6_000)

        // The same phase and round, in the next block: a new stretch all the same.
        assertEquals(Phase.Work to 1, second.engine.phase to second.engine.block)
        assertTrue(second.turnedFrom(first.engine))
        assertEquals(listOf(CourseSpan(Phase.Work, 1_000, 6_000)), second.spans)
    }

    @Test
    fun `a step to the end stops the timer and leaves the course for the finish to close`() {
        val done = begun.stepped(WorkoutEngine.tick(begun.engine, 60_000), now = 61_000)

        assertTrue(done.over)
        assertFalse(done.running)
        assertEquals(15, done.activeSeconds)
        assertEquals(1_000, done.openSince)
    }

    @Test
    fun `a run saved and restored is the same run`() {
        val body = BodySettings(weightKg = 70.0)
        val paused = begun.stepped(WorkoutEngine.tick(begun.engine, 5_000), now = 6_000)
            .copy(running = false)
            .turned(phase = null, now = 7_000)
            .copy(body = body)

        val restored = WorkoutRun.restored(paused.toProgress(now = 9_000), keepScreenOn = false, body = body)

        assertEquals(paused, restored)
        assertNull(restored.openPhase)
    }

    @Test
    fun `the notification's status says the block of a sequence and its rest`() {
        val sequence = config.copy(
            then = listOf(Block(workSeconds = 10, restSeconds = 5, rounds = 3)),
            blockRestSeconds = 20,
        )
        val started = begun.copy(engine = WorkoutEngine.start(sequence).state)
        assertEquals(emptyList(), begun.status(builtInFlows).blocks)
        assertEquals(sequence.blocks, started.status(builtInFlows).blocks)
        // Work, rest, work, then the rest before the second block, and its first work.
        val states = generateSequence(started.engine) { WorkoutEngine.advance(it).state }.take(5).toList()
        val blockRest = started.copy(engine = states[3]).status(builtInFlows)
        assertEquals(Phase.SetRest to true, blockRest.phase to blockRest.blockRest)
        val second = started.copy(engine = states[4]).status(builtInFlows)
        assertEquals(Triple(1, 1, 3), Triple(second.block, second.round, second.rounds))
        assertFalse(second.blockRest)
    }
}
