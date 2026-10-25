@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.data.BellEntity
import dev.justinsmith.bells.data.CustomProgramEntity
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.data.encodeDays
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.RepRange
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.interval.ui.waitForText
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Changing a program day's plan in the session: a block's load, skipping one, and swapping its exercise. */
@Suppress("detekt.TooManyFunctions") // One change, and one way of keeping it, per test, side by side.
class PlanFlowTest {
    /** Goblet squat and row: 3 × 10 goblet squats, then 3 × 10 rows a side, each building to 15. */
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
        onNodeWithText("16 kg").assertExists()
        onAllNodesWithText("Change").onFirst().performScrollTo().performClick()
        waitForText("Exercise")
        onNodeWithText("28").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("28 kg")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        assertEquals(28.0, app.storage.sessions.sets.value.single().bellKg)
        assertTrue(app.storage.sessions.sessions.value.single().plan.orEmpty().contains("\"kg\":28.0"))
    }

    @Test
    fun `the top of a range on the heaviest bell owned says a heavier bell is next`() = bellsAppTest(
        FakeBellsStorage().apply {
            val squats = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 12, kg = 24.0, range = RepRange(10, 12))
            programs.rows.value = listOf(CustomProgramEntity("p1", "Mine", 3, 4, encodeDays(listOf(listOf(squats))), 0))
            following.row.value = FollowingEntity(programId = "custom:p1", startedAtEpochMillis = 0)
            bells.rows.value = listOf(BellEntity(16.0), BellEntity(24.0))
        },
    ) {
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 2 sets")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 2 sets")
        onNodeWithText("Log set").performClick()
        waitForText("Today's sets are done. Finish when you are.")
        onNodeWithText("Finish").performClick()
        waitForText("Top of the range")
        onNodeWithText("2 × 12 (10–12) · 24 kg").performScrollTo().assertExists()
        // Nothing to step up to: no offer for next time.
        onNodeWithText("Ready for more?").assertDoesNotExist()
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
        onNodeWithText("Log set").performClick()
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
        onNodeWithText("Log set").performClick()
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
        waitForText("28 kg")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        onNodeWithText("Finish").performClick()
        waitForText("Keep today's changes?")
        onNodeWithText("3 × 10 (10–15) → 3 × 10 (10–15) · 28 kg").assertExists()
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

    /** A block given the 28 kg bell and logged with it, then Finish: the summary offers to keep the change. */
    private fun DesktopComposeUiTest.finishChanged() {
        startDay()
        onAllNodesWithText("Change").onFirst().performScrollTo().performClick()
        waitForText("Exercise")
        onNodeWithText("28").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("28 kg")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        onNodeWithText("Finish").performClick()
        waitForText("Keep today's changes?")
    }

    @Test
    fun `Done with the changes undecided asks and Drop leaves the program as it was`() = bellsAppTest(
        followed(),
    ) { app ->
        finishChanged()
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Keep saves them as the card has them. Drop leaves the program as it was.")
        onNodeWithText("Drop").performClick()
        waitForText("Start day 2")
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }

    @Test
    fun `Back with the changes undecided asks and Keep saves them as the card stands`() = bellsAppTest(
        followed(),
    ) { app ->
        finishChanged()
        app.back()
        waitForText("Keep saves them as the card has them. Drop leaves the program as it was.")
        onNodeWithText("Keep").performClick()
        waitForText("Start day 2")
        val saved = app.storage.programs.rows.value.single()
        assertEquals("Goblet squat and row (yours)", saved.name)
        assertTrue(saved.days.contains("\"kg\":28.0"))
        assertEquals(1, app.storage.following.row.value?.daysBefore)
    }

    @Test
    fun `Done after Today only asks nothing`() = bellsAppTest(followed()) { app ->
        finishChanged()
        onNodeWithText("Today only").performScrollTo().performClick()
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Start day 2")
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }

    @Test
    fun `today only keeps the program as it was`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithText("Skip").performScrollTo().performClick()
        waitForText("Up next · 1 of 6 sets")
        onNodeWithText("Log set").performClick()
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
            onNodeWithText("Log set").performClick()
            if (n < 8) waitForText("Up next · ${n + 2} of 9 sets")
        }
        waitForText("Today's sets are done. Finish when you are.")
        onNodeWithText("Extra set").assertExists()
        onNodeWithText("Log set").assertDoesNotExist()
        onNodeWithText("Finish").performClick()
        waitForText("Ready for more?")
        // Both build to 15 reps before a heavier bell: a rep more first.
        onNodeWithText("3 × 10 (10–15) → 3 × 11 (10–15)").assertExists()
        onNodeWithText("3 × 10 a side (10–15) → 3 × 11 a side (10–15)").assertExists()
        // Rows stay as they are; the goblet squats go up.
        // The card's, under the records.
        onAllNodesWithText("Row").onLast().performScrollTo().performClick()
        onNodeWithText("Save as Goblet squat and row (yours)").performScrollTo().performClick()
        waitForText("Saved as Goblet squat and row (yours). You follow it from day 2.")
        val days = app.storage.programs.rows.value.single().days
        assertTrue(
            days.contains("\"exercise\":\"GobletSquat\",\"kind\":\"Sets\",\"rounds\":3,\"reps\":11,\"rangeBottom\":10"),
            days,
        )
        assertTrue(
            days.contains("\"exercise\":\"Row\",\"kind\":\"Sets\",\"rounds\":3,\"reps\":10,\"rangeBottom\":10"),
            days,
        )
    }

    @Test
    fun `Heavier on Up next takes the rest of the block up a bell`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithText("16 kg").assertExists()
        onNodeWithContentDescription("Heavier").performScrollTo().performClick()
        waitForText("20 kg")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        // The next set of the block goes on at the new bell.
        onNodeWithText("20 kg").assertExists()
        assertEquals(20.0, app.storage.sessions.sets.value.single().bellKg)
        assertTrue(app.storage.sessions.sessions.value.single().plan.orEmpty().contains("\"kg\":20.0"))
        onNodeWithText("Today's plan").performScrollTo().performClick()
        waitForText("Changed")
    }

    @Test
    fun `the Log sheet starts on the set up next`() = bellsAppTest(followed()) { app ->
        startDay()
        onNodeWithContentDescription("Add set or EMOM").performClick()
        waitForText("Exercise")
        onNodeWithText("Kettlebell · Reps · Lower body").assertExists()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("Up next · 2 of 9 sets")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple("GobletSquat", 16.0, 10), Triple(set.exercise, set.bellKg, set.reps))
    }

    @Test
    fun `an EMOM block up next puts Start EMOM at the foot`() = bellsAppTest(
        FakeBellsStorage().apply {
            val swings = Block(BuiltInExercise.TwoHandSwing, kind = BlockKind.Emom, rounds = 5, reps = 10)
            programs.rows.value =
                listOf(CustomProgramEntity("p1", "Swings", 3, 4, encodeDays(listOf(listOf(swings))), 0))
            following.row.value = FollowingEntity(programId = "custom:p1", startedAtEpochMillis = 0)
        },
    ) {
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 5 sets")
        onNodeWithText("Log set").assertDoesNotExist()
        onNodeWithText("Start EMOM").assertIsDisplayed().performClick()
        waitForText("Get ready")
    }

    @Test
    fun `Extra set after a day is done opens the Log sheet for one more`() = bellsAppTest(
        FakeBellsStorage().apply {
            val squats = Block(BuiltInExercise.GobletSquat, rounds = 1, reps = 10)
            programs.rows.value = listOf(CustomProgramEntity("p1", "One", 3, 4, encodeDays(listOf(listOf(squats))), 0))
            following.row.value = FollowingEntity(programId = "custom:p1", startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 1 sets")
        onNodeWithText("Log set").performClick()
        waitForText("Today's sets are done. Finish when you are.")
        onNodeWithText("Extra set").performClick()
        waitForText("Exercise")
        onNodeWithText("Log set").performScrollTo().performClick()
        assertEquals(2, app.storage.sessions.sets.value.size)
    }

    @Test
    fun `How to on Up next opens the exercise's page with no Edit`() = bellsAppTest(followed()) {
        startDay()
        onNodeWithText("How to").performScrollTo().performClick()
        waitForText("Sit between your heels with the chest up")
        onNodeWithText("Your history").assertExists()
        onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun `How to on the Log sheet shows the page in place and goes back to the set`() = bellsAppTest(followed()) {
        startDay()
        onNodeWithContentDescription("Add set or EMOM").performClick()
        waitForText("Exercise")
        // The sheet's, over Up next's.
        onAllNodesWithText("How to").onLast().performClick()
        waitForText("Sit between your heels with the chest up")
        onNodeWithText("Back to the set").performScrollTo().performClick()
        waitForText("Reps")
        onNodeWithText("Sit between your heels with the chest up").assertDoesNotExist()
    }

    @Test
    fun `Last time is the lift's last set before today and never one just logged`() = bellsAppTest(
        followed().apply {
            sessions.sessions.update { it + SessionEntity("s0", 0, 60_000, "Squats", 60_000) }
            sessions.sets.update { it + SetEntity(1, "s0", "GobletSquat", 16.0, 8, null, 10) }
        },
    ) {
        startDay()
        onNodeWithText("Last time · 16 kg × 8").assertExists()
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        onNodeWithText("Last time · 16 kg × 8").assertExists()
    }

    @Test
    fun `a lift never done has no Last time`() = bellsAppTest(followed()) {
        startDay()
        onNodeWithText("Last time", substring = true).assertDoesNotExist()
    }
}
