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
}
