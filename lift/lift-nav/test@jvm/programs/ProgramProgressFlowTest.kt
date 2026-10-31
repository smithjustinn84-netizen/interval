@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.programs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.scrollToText
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Program page over the real ViewModel. The page over fixed state is lift-session's
 * `ProgramProgressScreenTest`.
 */
class ProgramProgressFlowTest {
    private val goblets: Program = program(ProgramId.GobletSquatRow)

    @Test
    fun `Follow again starts the program at day 1 and keeps the run in History`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = goblets.key.stored, startedAtEpochMillis = 0)
            sessions.sessions.value = (1..8).map { day ->
                val at = day * DAY_MILLIS
                SessionEntity(
                    "d$day",
                    at,
                    at + 1,
                    "Goblet squat and row",
                    at + 1,
                    programId = goblets.key.stored,
                    programDay = day,
                )
            }
        },
    ) { app ->
        app.clock.set(9 * DAY_MILLIS)
        waitForText("Finished")
        onNodeWithText("Finished").performClick()
        waitForText("Days done")
        scrollToText("Follow again")
        onNodeWithText("Follow again").performClick()
        waitForText("Day 1 of 8 · week 1")
        assertEquals(9 * DAY_MILLIS, app.storage.following.row.value?.startedAtEpochMillis)
        assertEquals(8, app.storage.sessions.sessions.value.size)
    }
}

private const val DAY_MILLIS = 86_400_000L
