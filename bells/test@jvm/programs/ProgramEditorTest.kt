@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.programs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.scrollToText
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Making a program, from Today through Programs and the editor, and following it. */
class ProgramEditorTest {
    @Test
    fun `a program made on the editor is saved and followed from Today`() = bellsAppTest { app ->
        onNodeWithText("Follow a program").performClick()
        scrollToText("Dan John–inspired")
        scrollToText("New program")
        onNodeWithText("New program").performClick()
        waitForText("Day 1")
        // Nothing to save until it has a name and a block on every day.
        onNodeWithText("Save program").assertDoesNotExist()

        onNodeWithText("Add block").performScrollTo().performClick()
        onNodeWithText("Ladder").performClick()
        onNodeWithText("Add rung").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("5 ladders of 1-2-3-4")

        onNodeWithText("Add day").performScrollTo().performClick()
        waitForText("Day 2")
        onAllNodesWithText("Add block").onLast().performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("5 × 10")

        onNode(hasSetTextAction()).performTextInput("Hill day")
        onNodeWithText("Save program").performScrollTo().performClick()
        waitForText("Hill day")
        onNodeWithText("2 days · 3 a week for 4 weeks").assertExists()
        assertEquals("Hill day", app.storage.programs.rows.value.single().name)

        onNodeWithText("Hill day").performClick()
        onNodeWithText("Follow this program").performScrollTo().performClick()
        waitForText("Day 1 of 12 · week 1")
        onNodeWithText("5 ladders of 1-2-3-4").assertExists()
        assertTrue(app.storage.following.row.value?.programId.orEmpty().startsWith("custom:"))
    }

    @Test
    fun `a block of a lift that can be doubled offers two bells`() = bellsAppTest { app ->
        onNodeWithText("Follow a program").performClick()
        scrollToText("New program")
        onNodeWithText("New program").performClick()
        waitForText("Day 1")
        onNodeWithText("Add block").performScrollTo().performClick()
        onNodeWithText("Two bells").assertDoesNotExist()
        onNodeWithText("Change").performClick()
        onNodeWithText("Press").performScrollTo().performClick()
        onNodeWithText("Two bells").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Press · Two bells")
        onNodeWithText("5 × 10").assertExists()
        onNode(hasSetTextAction()).performTextInput("Doubles")
        onNodeWithText("Save program").performScrollTo().performClick()
        waitForText("Doubles")
        assertTrue(app.storage.programs.rows.value.single().days.contains("\"double\":true"))
    }

    @Test
    fun `a block can be of any exercise with the load it names`() = bellsAppTest { app ->
        onNodeWithText("Follow a program").performClick()
        scrollToText("New program")
        onNodeWithText("New program").performClick()
        waitForText("Day 1")
        onNodeWithText("Add block").performScrollTo().performClick()
        // A kettlebell lift's bell is left to last time until one is picked.
        onNodeWithText("Last time").assertExists()
        onNodeWithText("24").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("5 × 10 · 24 kg")

        onNodeWithText("Two-hand swing").performClick()
        onNodeWithText("Change").performClick()
        onNodeWithText("Band pull-apart").performScrollTo().performClick()
        // A band exercise takes a band, not the bell it had.
        onNodeWithText("Medium").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForText("5 × 10 · Medium")
        onNode(hasSetTextAction()).performTextInput("Pulls")
        onNodeWithText("Save program").performScrollTo().performClick()
        waitForText("Pulls")
        val days = app.storage.programs.rows.value.single().days
        assertTrue(days.contains("\"band\":\"Medium\"") && !days.contains("\"kg\""), days)
    }

    @Test
    fun `a program made can be changed and deleted`() = bellsAppTest { app ->
        onNodeWithText("Follow a program").performClick()
        scrollToText("New program")
        onNodeWithText("New program").performClick()
        waitForText("Day 1")
        onNodeWithText("Add block").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        onNode(hasSetTextAction()).performTextInput("Swings")
        onNodeWithText("Save program").performScrollTo().performClick()
        waitForText("Swings")

        onNodeWithContentDescription("Edit Swings").performScrollTo().performClick()
        waitForText("Edit program")
        onNodeWithText("Delete program").performScrollTo().performClick()
        onNodeWithText("Delete").performClick()
        scrollToText("Dan John–inspired")
        onNodeWithText("Swings").assertDoesNotExist()
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }
}
