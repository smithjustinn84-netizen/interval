package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutEngineTest {
    private val config = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 15, rounds = 3)

    /** Ticks [millis] in whole-second steps and returns the final state plus every cue produced. */
    private fun run(state: WorkoutState, millis: Long): Step {
        var current = state
        val cues = mutableListOf<Cue>()
        var left = millis
        while (left > 0) {
            val step = WorkoutEngine.tick(current, minOf(1_000L, left))
            current = step.state
            cues += step.cues
            left -= 1_000L
        }
        return Step(current, cues)
    }

    @Test
    fun `starts in prep with the whole session ahead`() {
        val step = WorkoutEngine.start(config)
        assertEquals(Phase.Prep, step.state.phase)
        assertEquals(10_000L, step.state.remainingMillis)
        assertEquals(config.totalSeconds * 1_000L, step.state.sessionRemainingMillis)
        assertEquals(listOf<Cue>(Cue.PhaseStarted(Phase.Prep, lastRound = false)), step.cues)
    }

    @Test
    fun `skips prep when it is zero`() {
        val step = WorkoutEngine.start(config.copy(prepSeconds = 0))
        assertEquals(Phase.Work, step.state.phase)
        assertEquals(1, step.state.round)
    }

    @Test
    fun `counts down 3 2 1 then starts work`() {
        val step = run(WorkoutEngine.start(config).state, 10_000)
        assertEquals(
            listOf(
                Cue.Countdown(3),
                Cue.Countdown(2),
                Cue.Countdown(1),
                Cue.PhaseStarted(Phase.Work, lastRound = false),
            ),
            step.cues,
        )
        assertEquals(Phase.Work, step.state.phase)
        assertEquals(30_000L, step.state.remainingMillis)
    }

    @Test
    fun `work leads to rest in the same round then rest to the next round`() {
        val work = run(WorkoutEngine.start(config).state, 10_000).state
        val rest = run(work, 30_000).state
        assertEquals(Phase.Rest, rest.phase)
        assertEquals(1, rest.round)
        val nextWork = run(rest, 15_000).state
        assertEquals(Phase.Work, nextWork.phase)
        assertEquals(2, nextWork.round)
    }

    @Test
    fun `the whole session ends in done after its total time`() {
        val step = run(WorkoutEngine.start(config).state, config.totalSeconds * 1_000L)
        assertEquals(Phase.Done, step.state.phase)
        assertEquals(Cue.Finished, step.cues.last())
        assertTrue(Cue.PhaseStarted(Phase.Work, lastRound = true) in step.cues)
    }

    @Test
    fun `halfway cue only for intervals longer than 30 seconds`() {
        val short = run(WorkoutEngine.start(config.copy(prepSeconds = 0)).state, 30_000)
        assertTrue(Cue.Halfway !in short.cues)

        val long = run(WorkoutEngine.start(config.copy(prepSeconds = 0, workSeconds = 40)).state, 20_000)
        assertEquals(listOf<Cue>(Cue.Halfway), long.cues)
    }

    @Test
    fun `zero rest goes straight to the next work interval`() {
        val noRest = config.copy(prepSeconds = 0, restSeconds = 0)
        val step = WorkoutEngine.advance(WorkoutEngine.start(noRest).state)
        assertEquals(Phase.Work, step.state.phase)
        assertEquals(2, step.state.round)
    }

    @Test
    fun `back restarts an interval more than two seconds in`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val partWay = run(work, 5_000).state
        val back = WorkoutEngine.back(partWay)
        assertEquals(Phase.Work, back.phase)
        assertEquals(30_000L, back.remainingMillis)
    }

    @Test
    fun `back near an interval's start goes to the previous one`() {
        val rest = WorkoutEngine.advance(WorkoutEngine.advance(WorkoutEngine.start(config).state).state).state
        assertEquals(Phase.Rest, rest.phase)
        val back = WorkoutEngine.back(rest)
        assertEquals(Phase.Work, back.phase)
        assertEquals(1, back.round)
        assertEquals(30_000L, back.remainingMillis)
    }

    @Test
    fun `halfway plays once even when added time lifts the clock back above it`() {
        val start = WorkoutEngine.start(config.copy(prepSeconds = 0, workSeconds = 40)).state
        val past = run(start, 25_000)
        val added = WorkoutEngine.addTime(past.state)
        val after = run(added, 10_000)
        assertEquals(listOf<Cue>(Cue.Halfway), (past.cues + after.cues).filterIsInstance<Cue.Halfway>())
    }

    @Test
    fun `adding time keeps progress within bounds`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val added = WorkoutEngine.addTime(work)
        assertEquals(40_000L, added.remainingMillis)
        assertEquals(1f, added.progress)
    }

    @Test
    fun `a long gap runs through the intervals it covers and cues only where it lands`() {
        val prep = WorkoutEngine.start(config).state
        // 10 s of prep, 30 s of work, 15 s of rest, then 5 s into round two's work.
        val step = WorkoutEngine.tick(prep, 60_000)
        assertEquals(Phase.Work, step.state.phase)
        assertEquals(2, step.state.round)
        assertEquals(25_000L, step.state.remainingMillis)
        assertEquals(listOf(Cue.PhaseStarted(Phase.Work, lastRound = false)), step.cues)
        assertEquals(60_000L, step.activeMillis)
        assertEquals(35_000L, step.workMillis)
    }

    @Test
    fun `a gap past the end finishes the workout and counts no time beyond it`() {
        val prep = WorkoutEngine.start(config).state
        val step = WorkoutEngine.tick(prep, 200_000)
        assertTrue(step.state.isDone)
        assertEquals(listOf(Cue.Finished), step.cues)
        assertEquals(130_000L, step.activeMillis)
        assertEquals(90_000L, step.workMillis)
    }

    @Test
    fun `a gap landing in an interval's last seconds cues the seconds left`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        // Through the rest of the work interval and 13.5 s of the 15 s rest.
        val step = WorkoutEngine.tick(work, 43_500)
        assertEquals(Phase.Rest, step.state.phase)
        assertEquals(listOf(Cue.PhaseStarted(Phase.Rest, lastRound = false), Cue.Countdown(2)), step.cues)
    }

    @Test
    fun `a tick that crosses an interval's end carries the remainder into the next`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val nearEnd = WorkoutEngine.tick(work, 29_950).state
        val step = WorkoutEngine.tick(nearEnd, 100)
        assertEquals(Phase.Rest, step.state.phase)
        assertEquals(14_950L, step.state.remainingMillis)
        assertEquals(listOf(Cue.PhaseStarted(Phase.Rest, lastRound = false)), step.cues)
        assertEquals(100L, step.activeMillis)
        assertEquals(50L, step.workMillis)
    }

    @Test
    fun `back near the start of round one's work goes to the get-ready countdown`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val back = WorkoutEngine.back(work)
        assertEquals(Phase.Prep, back.phase)
        assertEquals(10_000L, back.remainingMillis)
    }

    @Test
    fun `back without rest goes to the previous round's work`() {
        val noRest = config.copy(restSeconds = 0)
        val secondWork = WorkoutEngine.advance(WorkoutEngine.advance(WorkoutEngine.start(noRest).state).state).state
        assertEquals(2, secondWork.round)
        val back = WorkoutEngine.back(secondWork)
        assertEquals(Phase.Work, back.phase)
        assertEquals(1, back.round)
    }

    @Test
    fun `the session's time left counts what follows the current interval`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        // 30s of work, then two more rounds of 15s rest and 30s work.
        assertEquals(120_000L, work.sessionRemainingMillis)
        assertEquals(110_000L, run(work, 10_000).state.sessionRemainingMillis)
        val rest = WorkoutEngine.advance(work).state
        // 15s of rest, two more works and the rest between them.
        assertEquals(90_000L, rest.sessionRemainingMillis)
    }

    @Test
    fun `a long rest gets its halfway cue too`() {
        val longRest = config.copy(prepSeconds = 0, workSeconds = 10, restSeconds = 40)
        val rest = WorkoutEngine.advance(WorkoutEngine.start(longRest).state).state
        assertEquals(Phase.Rest, rest.phase)
        assertEquals(listOf<Cue>(Cue.Halfway), run(rest, 20_000).cues)
    }

    @Test
    fun `a tick of no time or less changes nothing`() {
        val prep = WorkoutEngine.start(config).state
        assertEquals(Step(prep), WorkoutEngine.tick(prep, 0))
        assertEquals(Step(prep), WorkoutEngine.tick(prep, -500))
    }

    @Test
    fun `nothing moves once the workout is done`() {
        val done = WorkoutEngine.advance(WorkoutEngine.start(config.copy(prepSeconds = 0, rounds = 1)).state).state
        assertEquals(Phase.Done, done.phase)
        assertEquals(done, WorkoutEngine.addTime(done))
        assertEquals(done, WorkoutEngine.back(done))
        assertEquals(Step(done), WorkoutEngine.tick(done, 1_000))
    }
}
