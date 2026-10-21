@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A session from Today to the summary, through the whole app over fakes. */
class SessionFlowTest {
    @Test
    fun `logging a set adds it to the session`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log your first set: pick the lift, the bell and the reps.")
        onNodeWithText("Log set").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        // The sheet's own Log set, over the screen's.
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Two-hand swing").assertExists()
        onNodeWithText("160 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `a set with two bells has no side and moves twice the weight`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        // A two-hand swing can't be doubled; a clean can, and then has no side.
        onNodeWithText("Two bells").assertDoesNotExist()
        onNodeWithText("Clean").performClick()
        onNodeWithText("Left").assertExists()
        onNodeWithText("Two bells").performScrollTo().performClick()
        onNodeWithText("Left").assertDoesNotExist()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("2 × 16 kg × 10")
        onNodeWithText("Clean · Two bells").assertExists()
        onNodeWithText("320 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertTrue(set.doubled)
        assertEquals(null, set.side)
    }

    @Test
    fun `finishing a session shows its summary then Today`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")
        // The title, and the lift's first records.
        onAllNodesWithText("Two-hand swing").assertCountEquals(2)
        onNodeWithText("New records").assertExists()
        onNodeWithText("1:30").assertExists()
        assertTrue(app.storage.sessions.sessions.value.single().finishedAtEpochMillis != null)
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Last session")
        onNodeWithText("Two-hand swing").assertExists()
    }

    @Test
    fun `finishing with no sets drops the session`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        onNodeWithText("Finish").performClick()
        waitForText("No sessions yet")
        assertTrue(app.storage.sessions.sessions.value.isEmpty())
    }

    @Test
    fun `a set removed comes back with Undo`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithContentDescription("Remove 16 kg × 10").performClick()
        waitForText("Removed 16 kg × 10")
        assertTrue(app.storage.sessions.sets.value.isEmpty())
        onNodeWithText("Undo").performClick()
        waitForText("160 kg")
        onNodeWithText("Removed 16 kg × 10").assertDoesNotExist()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `back with sets logged asks and leaving keeps them`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        app.back()
        waitForText("Leave the session?")
        onNodeWithText("Stay").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        onNodeWithText("16 kg × 10").assertExists()
        app.back()
        waitForText("Leave")
        onNodeWithText("Leave").performClick()
        waitForText("Resume session")
        assertEquals(1, app.storage.sessions.sets.value.size)
    }

    @Test
    fun `back with nothing logged just leaves`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        it.back()
        waitForText("Resume session")
        onNodeWithText("Leave the session?").assertDoesNotExist()
    }

    @Test
    fun `the sheet's EMOM side starts an EMOM of the lift picked`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onNodeWithText("Minutes").assertDoesNotExist()
        onNodeWithText("An EMOM").performClick()
        // The sheet is a window of its own: wait for it to redraw as an EMOM.
        waitForText("Start EMOM")
        onNodeWithText("Reps each minute").assertExists()
        onNodeWithText("Minutes").performScrollTo().assertExists()
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Set done")
        it.back()
        waitForText("The EMOM stops. Its sets so far are kept: Resume session on Lift picks it up.")
    }

    @Test
    fun `back over the log sheet closes the sheet before asking to leave`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Log set").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        app.back()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Log set").fetchSemanticsNodes().size == 1 }
        onNodeWithText("Leave the session?").assertDoesNotExist()
        app.back()
        waitForText("Leave the session?")
    }
}
