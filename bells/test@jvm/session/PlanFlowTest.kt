@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Changing a program day's plan in the session: a block's load, skipping one, and swapping its exercise. */
class PlanFlowTest {
    /** Goblet squat and row: 3 × 10 goblet squats, then 3 × 10 rows a side. */
    private fun followed() = FakeBellsStorage().apply {
        following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
    }

    private fun DesktopComposeUiTest.startDay() {
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 9 sets")
    }

    @Test
    fun `a block given another bell is up next and logged with it`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithText("16 kg × 10").assertExists()
        onAllNodesWithText("Change").onFirst().performScrollTo().performClick()
        waitForText("Exercise")
        onNodeWithText("28").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("28 kg × 10")
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 2 of 9 sets")
        assertEquals(28.0, app.storage.sessions.sets.value.single().bellKg)
        assertTrue(app.storage.sessions.sessions.value.single().plan.orEmpty().contains("\"kg\":28.0"))
    }

    @Test
    fun `skipping a block moves on to the next`() = bellsAppTest(followed()) {
        startDay()
        onNodeWithText("Skip").performScrollTo().performClick()
        waitForText("Up next · 1 of 6 sets")
        onNodeWithText("Row").assertExists()
        onNodeWithText("Today's plan").performScrollTo().performClick()
        waitForText("Skipped")
        onNodeWithText("0 of 0 done").assertExists()
    }

    @Test
    fun `swapping part way keeps the sets done`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 2 of 9 sets")
        onAllNodesWithText("Change").onFirst().performScrollTo().performClick()
        waitForText("Exercise")
        // The sheet's Change, over Up next's.
        onAllNodesWithText("Change").onLast().performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("front squat")
        waitForText("Front squat")
        onAllNodesWithText("Front squat").onLast().performScrollTo().performClick()
        waitForText("Exercise")
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Front squat")
        onNodeWithText("Up next · 2 of 9 sets").assertExists()
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 3 of 9 sets")
        assertEquals(
            listOf("GobletSquat", "FrontSquat"),
            app.storage.sessions.sets.value.map { it.exercise },
        )
        onNodeWithText("Today's plan").performScrollTo().performClick()
        waitForText("Changed")
        onNodeWithText("2 of 3 done").assertExists()
    }
}
