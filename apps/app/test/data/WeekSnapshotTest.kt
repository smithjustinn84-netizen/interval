package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.timer.WeekStanding
import kotlin.test.Test
import kotlin.test.assertEquals

// Thursday 15 October 2026: the week began on Monday the 12th, TODAY - 3.

class WeekSnapshotTest {
    @Test
    fun `the snapshot carries the week's Monday`() {
        val snapshot = WeekStanding(count = 3, goal = 5, streak = 2).snapshot(TODAY)
        assertEquals(WeekSnapshot(count = 3, goal = 5, streak = 2, weekStart = TODAY - 3), snapshot)
    }

    // WeekWidget.swift decodes these names: a change here is a change there.
    @Test
    fun `the JSON the widget reads`() {
        assertEquals(
            """{"count":3,"goal":5,"streak":2,"weekStart":20738}""",
            WeekSnapshot(count = 3, goal = 5, streak = 2, weekStart = 20738).toJson(),
        )
        assertEquals(
            """{"count":1,"goal":null,"streak":0,"weekStart":20738}""",
            WeekSnapshot(count = 1, goal = null, streak = 0, weekStart = 20738).toJson(),
        )
    }
}
