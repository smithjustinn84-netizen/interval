package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A warm-up before the first round and a cool-down after the last, through the engine and what reads it. */
class WarmUpCoolDownTest {
    private val config = IntervalConfig(
        prepSeconds = 10,
        workSeconds = 30,
        restSeconds = 15,
        rounds = 2,
        warmUpSeconds = 60,
        coolDownSeconds = 120,
    )

    @Test
    fun `the total counts the warm-up and the cool-down`() {
        assertEquals(10 + 60 + 2 * 30 + 15 + 120, config.totalSeconds)
    }

    @Test
    fun `the warm-up follows get ready and the cool-down the last work`() {
        val changes = WorkoutEngine.upcomingChanges(WorkoutEngine.start(config).state)
        assertEquals(
            listOf(
                UpcomingChange(Phase.WarmUp, round = 1, offsetMillis = 10_000),
                UpcomingChange(Phase.Work, round = 1, offsetMillis = 70_000),
                UpcomingChange(Phase.Rest, round = 1, offsetMillis = 100_000),
                UpcomingChange(Phase.Work, round = 2, offsetMillis = 115_000),
                UpcomingChange(Phase.CoolDown, round = 2, offsetMillis = 145_000),
                UpcomingChange(Phase.Done, round = 2, offsetMillis = 265_000),
            ),
            changes,
        )
    }

    @Test
    fun `with no get ready the workout opens on the warm-up`() {
        val step = WorkoutEngine.start(config.copy(prepSeconds = 0))
        assertEquals(Phase.WarmUp, step.state.phase)
        assertEquals(listOf<Cue>(Cue.PhaseStarted(Phase.WarmUp, lastRound = false)), step.cues)
    }

    @Test
    fun `none of either runs the rounds as before`() {
        val plain = config.copy(warmUpSeconds = 0, coolDownSeconds = 0)
        val phases = WorkoutEngine.upcomingChanges(WorkoutEngine.start(plain).state).map { it.phase }
        assertEquals(listOf(Phase.Work, Phase.Rest, Phase.Work, Phase.Done), phases)
    }

    @Test
    fun `the last work's end starts the cool-down and the cool-down's end finishes`() {
        val lastWork = WorkoutEngine.tick(WorkoutEngine.start(config).state, 116_000).state
        assertEquals(Phase.Work, lastWork.phase)
        val coolDown = WorkoutEngine.advance(lastWork)
        assertEquals(Phase.CoolDown, coolDown.state.phase)
        assertEquals(listOf<Cue>(Cue.PhaseStarted(Phase.CoolDown, lastRound = false)), coolDown.cues)
        val done = WorkoutEngine.advance(coolDown.state)
        assertTrue(done.state.isDone)
        assertEquals(listOf<Cue>(Cue.Finished), done.cues)
    }

    @Test
    fun `the session's time left counts the cool-down from anywhere before it`() {
        val start = WorkoutEngine.start(config).state
        assertEquals(config.totalSeconds * 1_000L, start.sessionRemainingMillis)
        val warmUp = WorkoutEngine.tick(start, 20_000).state
        assertEquals(Phase.WarmUp, warmUp.phase)
        assertEquals((config.totalSeconds - 20) * 1_000L, warmUp.sessionRemainingMillis)
        val rest = WorkoutEngine.tick(start, 105_000).state
        assertEquals(Phase.Rest, rest.phase)
        assertEquals((config.totalSeconds - 105) * 1_000L, rest.sessionRemainingMillis)
        val coolDown = WorkoutEngine.tick(start, 200_000).state
        assertEquals(Phase.CoolDown, coolDown.phase)
        assertEquals((config.totalSeconds - 200) * 1_000L, coolDown.sessionRemainingMillis)
    }

    @Test
    fun `work and rest in the warm-up and cool-down are told apart for calories`() {
        val step = WorkoutEngine.tick(WorkoutEngine.start(config).state, config.totalSeconds * 1_000L)
        assertTrue(step.state.isDone)
        assertEquals(config.totalSeconds * 1_000L, step.activeMillis)
        assertEquals(2 * 30_000L, step.workMillis)
    }

    @Test
    fun `back from round one's work goes to the warm-up and from there to get ready`() {
        val work = WorkoutEngine.tick(WorkoutEngine.start(config).state, 70_500).state
        assertEquals(Phase.Work, work.phase)
        val warmUp = WorkoutEngine.back(work)
        assertEquals(Phase.WarmUp, warmUp.phase)
        assertEquals(60_000L, warmUp.remainingMillis)
        assertEquals(Phase.Prep, WorkoutEngine.back(warmUp).phase)
    }

    @Test
    fun `back from the cool-down goes to the last work`() {
        val coolDown = WorkoutEngine.tick(WorkoutEngine.start(config).state, 145_500).state
        assertEquals(Phase.CoolDown, coolDown.phase)
        val back = WorkoutEngine.back(coolDown)
        assertEquals(Phase.Work, back.phase)
        assertEquals(2, back.round)
    }

    @Test
    fun `a warm-up with no get ready is the first interval and back restarts it`() {
        val warmUp = WorkoutEngine.tick(WorkoutEngine.start(config.copy(prepSeconds = 0)).state, 500).state
        val back = WorkoutEngine.back(warmUp)
        assertEquals(Phase.WarmUp, back.phase)
        assertEquals(60_000L, back.remainingMillis)
    }

    @Test
    fun `the outline lays the warm-up and cool-down either side of the rounds`() {
        val outline = WorkoutOutline.of(WorkoutEngine.tick(WorkoutEngine.start(config).state, 30_000).state)
        assertEquals(
            listOf(
                OutlinedInterval(Phase.Prep, 1, 10),
                OutlinedInterval(Phase.WarmUp, 1, 60),
                OutlinedInterval(Phase.Work, 1, 30),
                OutlinedInterval(Phase.Rest, 1, 15),
                OutlinedInterval(Phase.Work, 2, 30),
                OutlinedInterval(Phase.CoolDown, 2, 120),
            ),
            outline.intervals,
        )
        assertEquals(30, outline.elapsedSeconds)
    }

    @Test
    fun `an old workout's planned work leaves the warm-up out`() {
        assertEquals(30, config.plannedWorkSeconds(elapsedSeconds = 10 + 60 + 30, endedEarly = true))
        assertEquals(60, config.plannedWorkSeconds(elapsedSeconds = 1_000, endedEarly = true))
    }

    @Test
    fun `a different warm-up or cool-down is a different shape`() {
        assertTrue(config.sameShape(config.copy(prepSeconds = 0)))
        assertFalse(config.sameShape(config.copy(warmUpSeconds = 0)))
        assertFalse(config.sameShape(config.copy(coolDownSeconds = 60)))
    }

    @Test
    fun `a preset brings its warm-up and cool-down to the timer`() {
        val onTimer = IntervalConfig(prepSeconds = 5, warmUpSeconds = 300).withPreset(Preset("Bookended", config))
        assertEquals(5, onTimer.prepSeconds)
        assertEquals(60, onTimer.warmUpSeconds)
        assertEquals(120, onTimer.coolDownSeconds)
    }

    @Test
    fun `the bookends step by half minutes and clamp to an hour`() {
        val stepped = IntervalConfig().stepped(ConfigField.WarmUp, 1).stepped(ConfigField.CoolDown, -1)
        assertEquals(30, stepped.warmUpSeconds)
        assertEquals(0, stepped.coolDownSeconds)
        assertEquals(3600, IntervalConfig().withValue(ConfigField.CoolDown, 9_999).coolDownSeconds)
    }

    @Test
    fun `words read a warm-up and a cool-down before anything else`() {
        val described = describeWorkout(
            "5 min warm-up then 40/20 x8 and cool down 3:00",
            IntervalConfig(),
            emptyList(),
        )!!
        assertEquals(300, described.config.warmUpSeconds)
        assertEquals(180, described.config.coolDownSeconds)
        assertEquals(40, described.config.workSeconds)
        assertEquals(20, described.config.restSeconds)
        assertEquals(8, described.config.rounds)
        assertTrue(ConfigField.WarmUp in described.understood)
        assertTrue(ConfigField.CoolDown in described.understood)
    }

    @Test
    fun `a warm-up said in minutes is not the workout's length`() {
        val described = describeWorkout("warmup 10 minutes 30 on 30 off x10", IntervalConfig(), emptyList())!!
        assertEquals(600, described.config.warmUpSeconds)
        assertEquals(10, described.config.rounds)
        assertEquals(30, described.config.workSeconds)
    }
}
