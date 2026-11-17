package dev.justinsmith.interval.data

import dev.justinsmith.interval.platform.LiveActivityContent
import dev.justinsmith.interval.platform.LiveActivitySegment
import dev.justinsmith.interval.platform.LiveActivitySpan
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveActivityStateTest {
    // IntervalActivity.swift's ContentState decodes these names: a change here is a change there.
    @Test
    fun `the JSON the Live Activity reads`() {
        val content = LiveActivityContent(
            title = "Work",
            detail = "Round 2 of 8",
            staleTitle = "Open the app",
            colorArgb = -16711936,
            interval = LiveActivitySpan(1_000, 21_000),
            run = LiveActivitySpan(0, 240_000),
            runLabel = "Workout",
            progress = 0.25,
            segments = listOf(LiveActivitySegment(10, -1), LiveActivitySegment(20)),
            toggleLabel = "Pause",
            endLabel = "End",
        )
        assertEquals(
            """{"title":"Work","detail":"Round 2 of 8","staleTitle":"Open the app","color":-16711936,""" +
                """"paused":false,"pausedClock":null,"intervalStart":1000,"intervalEnd":21000,"runStart":0,""" +
                """"runEnd":240000,"runLabel":"Workout","progress":0.25,""" +
                """"segments":[{"length":10,"color":-1},{"length":20,"color":null}],""" +
                """"toggleLabel":"Pause","endLabel":"End"}""",
            content.state().toJson(),
        )
    }

    @Test
    fun `a paused one has no spans`() {
        val content = LiveActivityContent(title = "Rest", paused = true, pausedClock = "0:12", progress = 0.5)
        assertEquals(
            """{"title":"Rest","detail":null,"staleTitle":null,"color":null,"paused":true,"pausedClock":"0:12",""" +
                """"intervalStart":null,"intervalEnd":null,"runStart":null,"runEnd":null,"runLabel":null,""" +
                """"progress":0.5,"segments":[],"toggleLabel":null,"endLabel":null}""",
            content.state().toJson(),
        )
    }
}
