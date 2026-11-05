package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntervalConfigTest {
    @Test
    fun `stepping clamps to each field's range`() {
        assertEquals(0, IntervalConfig(prepSeconds = 0).stepped(ConfigField.Prep, -1).prepSeconds)
        assertEquals(5, IntervalConfig(workSeconds = 5).stepped(ConfigField.Work, -1).workSeconds)
        assertEquals(99, IntervalConfig(rounds = 99).stepped(ConfigField.Rounds, 1).rounds)
        assertEquals(20, IntervalConfig(restSeconds = 15).stepped(ConfigField.Rest, 1).restSeconds)
    }

    @Test
    fun `a value out of its range is refused`() {
        assertFailsWith<IllegalArgumentException> { IntervalConfig(rounds = 0) }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(workSeconds = 1) }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(coolDownSeconds = -1) }
    }

    @Test
    fun `sanitized takes each value to the nearest in its range`() {
        val config = IntervalConfig.sanitized(prepSeconds = -5, workSeconds = 99_999, restSeconds = 15, rounds = 0)
        assertEquals(IntervalConfig(prepSeconds = 0, workSeconds = 3600, restSeconds = 15, rounds = 1), config)
        assertTrue(config.totalSeconds > 0)
    }

    @Test
    fun `a ladder that would fall below five seconds is refused`() {
        IntervalConfig(workSeconds = 45, rounds = 5, workStepSeconds = -10)
        assertFailsWith<IllegalArgumentException> {
            IntervalConfig(workSeconds = 40, rounds = 5, workStepSeconds = -10)
        }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(workStepSeconds = 305) }
        assertFailsWith<IllegalArgumentException> {
            IntervalConfig(workSeconds = 3500, rounds = 3, workStepSeconds = 60)
        }
    }

    @Test
    fun `sanitized shortens a ladder's step so the last round fits`() {
        assertEquals(-5, IntervalConfig.sanitized(workSeconds = 40, rounds = 5, workStepSeconds = -10).workStepSeconds)
        assertEquals(50, IntervalConfig.sanitized(workSeconds = 3500, rounds = 3, workStepSeconds = 60).workStepSeconds)
        assertEquals(45, IntervalConfig.sanitized(workSeconds = 3510, rounds = 3, workStepSeconds = 60).workStepSeconds)
        assertEquals(0, IntervalConfig.sanitized(workSeconds = 10, rounds = 8, workStepSeconds = -5).workStepSeconds)
        assertEquals(-10, IntervalConfig.sanitized(workSeconds = 45, rounds = 5, workStepSeconds = -10).workStepSeconds)
    }

    @Test
    fun `less work or more rounds on a falling ladder shorten its step rather than fail`() {
        val ladder = IntervalConfig(workSeconds = 60, rounds = 5, workStepSeconds = -10)
        assertEquals(-5, ladder.withValue(ConfigField.Work, 30).workStepSeconds)
        assertEquals(-5, ladder.withValue(ConfigField.Rounds, 10).workStepSeconds)
        assertEquals(-10, ladder.stepped(ConfigField.Work, 1).workStepSeconds)
        assertEquals(-10, ladder.withValue(ConfigField.WorkStep, -20).workStepSeconds)
    }

    @Test
    fun `a ladder's step shows its sign`() {
        assertEquals("+0:10", ConfigField.WorkStep.format(10))
        assertEquals("\u22121:00", ConfigField.WorkStep.format(-60))
        assertEquals("0:00", ConfigField.WorkStep.format(0))
    }

    @Test
    fun `a ladder is part of the shape`() {
        val config = IntervalConfig(workSeconds = 20, rounds = 5)
        assertTrue(!config.sameShape(config.copy(workStepSeconds = 10)))
    }
}
