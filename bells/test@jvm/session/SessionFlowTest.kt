@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Log a set")).assertExists()
        // The sheet's own Log set, over the screen's.
        onAllNodesWithText("Log set").onLast().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Two-hand swing").assertExists()
        onNodeWithText("160 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `finishing a session shows its summary then Today`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week in Bells and Interval")
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
}
