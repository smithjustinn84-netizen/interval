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

    @Test
    fun `a change kept on finishing goes in a copy followed from the next day`() = bellsAppTest(followed()) { app ->
        startDay()
        onAllNodesWithText("Change").onFirst().performScrollTo().performClick()
        waitForText("Exercise")
        onNodeWithText("28").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("28 kg × 10")
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 2 of 9 sets")
        onNodeWithText("Finish").performClick()
        waitForText("Keep today's changes?")
        onNodeWithText("3 × 10 → 3 × 10 · 28 kg").assertExists()
        onNodeWithText("Every day").performScrollTo().performClick()
        onNodeWithText("Save as Goblet squat and row (yours)").performScrollTo().performClick()
        waitForText("Saved as Goblet squat and row (yours). You follow it from day 2.")
        val saved = app.storage.programs.rows.value.single()
        assertEquals("Goblet squat and row (yours)", saved.name)
        assertTrue(saved.days.contains("\"kg\":28.0"))
        assertEquals(1, app.storage.following.row.value?.daysBefore)
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Start day 2")
        onNodeWithText("Goblet squat and row (yours)", substring = true).assertExists()
    }

    @Test
    fun `today only keeps the program as it was`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithText("Skip").performScrollTo().performClick()
        waitForText("Up next · 1 of 6 sets")
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 2 of 6 sets")
        onNodeWithText("Finish").performClick()
        waitForText("Done")
        // A skip is for the day it was skipped: nothing to keep.
        onNodeWithText("Keep today's changes?").assertDoesNotExist()
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }

    @Test
    fun `a day done in full offers a step up for next time`() = bellsAppTest(followed()) { app ->
        startDay()
        repeat(9) { n ->
            onNodeWithText("Log it").performScrollTo().performClick()
            if (n < 8) waitForText("Up next · ${n + 2} of 9 sets")
        }
        waitForText("Today's sets are done. Finish when you are.")
        onNodeWithText("Finish").performClick()
        waitForText("Ready for more?")
        // No bells owned: the next standard size up from the 16 kg done.
        onNodeWithText("3 × 10 → 3 × 10 · 20 kg").assertExists()
        onNodeWithText("3 × 10 a side → 3 × 10 a side · 20 kg").assertExists()
        // Rows stay as they are; the goblet squats go up.
        // The card's, under the records.
        onAllNodesWithText("Row").onLast().performScrollTo().performClick()
        onNodeWithText("Save as Goblet squat and row (yours)").performScrollTo().performClick()
        waitForText("Saved as Goblet squat and row (yours). You follow it from day 2.")
        val days = app.storage.programs.rows.value.single().days
        assertTrue(
            days.contains("\"exercise\":\"GobletSquat\",\"kind\":\"Sets\",\"rounds\":3,\"reps\":10,\"kg\":20.0"),
            days,
        )
        assertTrue(days.contains("\"exercise\":\"Row\",\"kind\":\"Sets\",\"rounds\":3,\"reps\":10}"), days)
    }
}
