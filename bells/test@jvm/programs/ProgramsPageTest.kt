@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.programs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Programs, from Today: a program's sheet before following it, and the question before switching. */
class ProgramsPageTest {
    /** Following Goblet squat and row with its first day done, so day 2 is next. */
    private fun dayTwoOfGobletSquatRow() = FakeBellsStorage().apply {
        val program = ProgramId.GobletSquatRow.name
        following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        sessions.sessions.value = listOf(
            SessionEntity("d1", 0, 600_000, "Goblet squat and row", 600_000, programId = program),
        )
    }

    @Test
    fun `a program opens its days and is followed from its sheet`() = bellsAppTest { app ->
        onNodeWithText("Follow a program").performClick()
        waitForText("Goblet squat and row")
        onNodeWithText("Goblet squat and row").performScrollTo().performClick()
        waitForText("Follow this program")
        onNodeWithText("Day 1").assertExists()
        // Nothing followed yet, so nothing is replaced and nothing asked.
        onNodeWithText("Replaces", substring = true).assertDoesNotExist()
        onNodeWithText("Follow this program").performScrollTo().performClick()
        waitForText("Day 1 of 8 · week 1")
        assertEquals(ProgramId.GobletSquatRow.name, app.storage.following.row.value?.programId)
    }

    @Test
    fun `switching part way through a program asks first`() = bellsAppTest(dayTwoOfGobletSquatRow()) { app ->
        waitForText("Day 2 of 8 · week 1")
        onNodeWithText("Change program").performScrollTo().performClick()
        waitForText("Easy Strength")
        onNodeWithText("Easy Strength").performScrollTo().performClick()
        waitForText("Replaces Goblet squat and row · day 2 of 8")
        onNodeWithText("Follow this program").performScrollTo().performClick()
        waitForText("Switch to Easy Strength?")
        onNodeWithText("Keep current").performClick()
        onNodeWithText("Switch to Easy Strength?").assertDoesNotExist()
        assertEquals(ProgramId.GobletSquatRow.name, app.storage.following.row.value?.programId)

        onNodeWithText("Easy Strength").performScrollTo().performClick()
        onNodeWithText("Follow this program").performScrollTo().performClick()
        waitForText("Switch")
        onNodeWithText("Switch").performClick()
        waitForText("Day 1 of 40 · week 1")
        assertEquals(ProgramId.EasyStrength.name, app.storage.following.row.value?.programId)
    }

    @Test
    fun `the program followed shows as Following on its sheet`() = bellsAppTest(dayTwoOfGobletSquatRow()) {
        waitForText("Change program")
        onNodeWithText("Change program").performScrollTo().performClick()
        waitForText("Goblet squat and row")
        onNodeWithText("Goblet squat and row").performScrollTo().performClick()
        waitForText("Following")
        onNodeWithText("Follow this program").assertDoesNotExist()
    }
}
