package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeCapTest {
    private val cap = IntervalConfig(prepSeconds = 10, workSeconds = 600, restSeconds = 0, rounds = 1, capped = true)

    private fun pinned(config: IntervalConfig) =
        Course.of(config).joinToString(" ") { "${it.phase} ${it.block}:${it.seconds}" }

    @Test
    fun `a cap runs as one work interval of its minutes`() {
        assertEquals("Prep 0:10 Work 0:600", pinned(cap))
        assertEquals(610, cap.totalSeconds)
    }

    @Test
    fun `a cap's rest falls between it and the next block`() {
        val twoCaps = cap.withBlocks(listOf(Block.capOf(600, restSeconds = 120), Block.capOf(600)))
        assertEquals("Prep 0:10 Work 0:600 Rest 0:120 Work 1:600", pinned(twoCaps))
    }

    @Test
    fun `a cap is a minute to an hour in whole minutes of one round`() {
        assertFailsWith<IllegalArgumentException> {
            Block(workSeconds = 90, restSeconds = 0, rounds = 1, capped = true)
        }
        assertFailsWith<IllegalArgumentException> {
            Block(workSeconds = 600, restSeconds = 0, rounds = 3, capped = true)
        }
        assertEquals(Block.capOf(120), Block.sanitized(100, 0, 8, sets = 3, workStepSeconds = 5, capped = true))
        assertEquals(3600, Block.capOf(99_999).workSeconds)
        assertEquals(60, Block.capOf(1).workSeconds)
    }

    @Test
    fun `switching a block's kind gives that kind's own default`() {
        val capped = IntervalConfig(workSeconds = 30, restSeconds = 45).withCapped(true)
        assertTrue(capped.capped)
        assertEquals(IntervalConfig.DEFAULT_CAP_SECONDS, capped.workSeconds)
        assertEquals(0, capped.restSeconds)
        val back = capped.withCapped(false)
        assertFalse(back.capped)
        assertEquals(IntervalConfig().blocks.first(), back.blocks.first())
    }

    @Test
    fun `the cap field steps by a minute`() {
        assertEquals(660, cap.stepped(ConfigField.Cap, 1).workSeconds)
        assertEquals(listOf(ConfigField.Cap, ConfigField.Rest), ConfigField.blockFields(cap.blocks.first()))
    }

    @Test
    fun `a cap is never the same shape as plain work of its length`() {
        assertFalse(cap.sameShape(cap.copy(capped = false)))
        assertTrue(cap.hasCap)
        assertTrue(IntervalConfig().withBlocks(listOf(IntervalConfig().blocks.first(), Block.capOf(300))).hasCap)
    }

    @Test
    fun `a mobility cap is no flow`() {
        assertFalse(cap.copy(activity = Activity.Mobility).isFlowShaped)
    }

    @Test
    fun `a kettlebell cap that switches hands is never asked about snatches`() {
        val bell = cap.copy(activity = Activity.Kettlebell)
        assertFalse(listOf(Preset("Cap", bell, id = 1, alternateHands = true)).asksSnatches(bell))
    }

    @Test
    fun `a cap is named by its minutes`() {
        assertEquals("10m cap", cap.defaultPresetName())
    }

    @Test
    fun `a cap calls five minutes and one minute left`() {
        var state = WorkoutEngine.start(cap.copy(prepSeconds = 0)).state
        val cues = mutableListOf<Cue>()
        repeat(600) {
            val step = WorkoutEngine.tick(state, 1_000)
            state = step.state
            cues += step.cues
        }
        assertEquals(listOf<Cue>(Cue.Left(5), Cue.Left(1)), cues.filterIsInstance<Cue.Left>())
    }

    @Test
    fun `a cap no longer than five minutes calls only the last minute`() {
        val short = cap.copy(prepSeconds = 0, workSeconds = 300)
        var state = WorkoutEngine.start(short).state
        val cues = mutableListOf<Cue>()
        repeat(300) {
            val step = WorkoutEngine.tick(state, 1_000)
            state = step.state
            cues += step.cues
        }
        assertEquals(listOf<Cue>(Cue.Left(1)), cues.filterIsInstance<Cue.Left>())
    }

    @Test
    fun `a cap's work is never called the last round`() {
        val start = WorkoutEngine.tick(WorkoutEngine.start(cap).state, 10_000)
        val work = start.cues.filterIsInstance<Cue.PhaseStarted>().single { it.phase == Phase.Work }
        assertFalse(work.lastRound)
    }

    @Test
    fun `plain work calls no minutes left`() {
        val plain = IntervalConfig(prepSeconds = 0, workSeconds = 600, restSeconds = 0, rounds = 1)
        val step = WorkoutEngine.tick(WorkoutEngine.start(plain).state, 599_000)
        assertTrue(step.cues.none { it is Cue.Left })
    }
}
