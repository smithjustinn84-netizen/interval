package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalConfigTest {
    @Test
    fun `stepping clamps to each field's range`() {
        assertEquals(0, IntervalConfig(prepSeconds = 0).stepped(ConfigField.Prep, -1).prepSeconds)
        assertEquals(5, IntervalConfig(workSeconds = 5).stepped(ConfigField.Work, -1).workSeconds)
        assertEquals(99, IntervalConfig(rounds = 99).stepped(ConfigField.Rounds, 1).rounds)
        assertEquals(20, IntervalConfig(restSeconds = 15).stepped(ConfigField.Rest, 1).restSeconds)
    }
}
