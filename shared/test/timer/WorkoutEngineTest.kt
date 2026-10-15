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
    fun `adding time keeps progress within bounds`() {
        val work = WorkoutEngine.advance(WorkoutEngine.start(config).state).state
        val added = WorkoutEngine.addTime(work)
        assertEquals(40_000L, added.remainingMillis)
        assertEquals(1f, added.progress)
    }

    @Test
    fun `a long gap advances at most one second`() {
        val prep = WorkoutEngine.start(config).state
        val step = WorkoutEngine.tick(prep, 60_000)
        assertEquals(Phase.Prep, step.state.phase)
        assertEquals(9_000L, step.state.remainingMillis)
    }

    @Test
    fun `stepping clamps to each field's range`() {
        assertEquals(0, IntervalConfig(prepSeconds = 0).stepped(ConfigField.Prep, -1).prepSeconds)
        assertEquals(5, IntervalConfig(workSeconds = 5).stepped(ConfigField.Work, -1).workSeconds)
        assertEquals(99, IntervalConfig(rounds = 99).stepped(ConfigField.Rounds, 1).rounds)
        assertEquals(20, IntervalConfig(restSeconds = 15).stepped(ConfigField.Rest, 1).restSeconds)
    }

    @Test
    fun `formats clocks and short durations`() {
        assertEquals("0:05", formatClock(5))
        assertEquals("5:55", formatClock(355))
        assertEquals("1m", shortDuration(60))
        assertEquals("45s", shortDuration(45))
        assertEquals("20s on · 10s off · ×8", builtInPresets.first().config.description)
        assertEquals(1, ceilSeconds(1))
        assertEquals(0, ceilSeconds(0))
    }
}
