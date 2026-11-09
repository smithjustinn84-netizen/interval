@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A free session taking a day of a program: the programs on a sheet, a day picked, and its sets up next. */
class PickDayFlowTest {
    @Test
    fun `a free session takes a day of a program and its sets are up next`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Do a program day").performScrollTo().performClick()
        waitForText("Program days")
        onNodeWithText("Your first 100 swings").performScrollTo().performClick()
        waitForText("All programs")
        onNodeWithText("Day 2").performScrollTo().performClick()
        // Two deadlift sets, then ten minutes of swings on the minute.
        waitForText("Up next · 1 of 12 sets")
        onNodeWithText("Next set").assertDoesNotExist()
        onNodeWithText("Do a program day").assertDoesNotExist()
        val session = app.storage.sessions.sessions.value.single()
        assertEquals(ProgramId.FirstHundredSwings.name to 2, session.programId to session.programDay)
    }

    @Test
    fun `the program followed comes first with its next day marked`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value =
                FollowingEntity(programId = ProgramId.FirstHundredSwings.name, startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Free session").performScrollTo().performClick()
        waitForText("Next set")
        onNodeWithText("Do a program day").performScrollTo().performClick()
        waitForText("Program days")
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Your first 100 swings").performClick()
        waitForText("Next")
        onNodeWithText("Day 1").performScrollTo().performClick()
        waitForText("Up next · 1 of 12 sets")
        val session = app.storage.sessions.sessions.value.single()
        assertEquals(ProgramId.FirstHundredSwings.name to 1, session.programId to session.programDay)
    }
}
