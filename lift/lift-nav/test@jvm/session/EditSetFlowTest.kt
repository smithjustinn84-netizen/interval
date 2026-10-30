@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Changing a set already logged, from its row in the session. */
class EditSetFlowTest {
    @Test
    fun `a set logged opens on a tap to change its bell and reps`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onNodeWithText("16 kg × 10").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Change the set")).assertExists()
        // A set logged keeps its exercise, and is never an EMOM.
        onNodeWithText("An EMOM").assertDoesNotExist()
        onNodeWithText("Change").assertDoesNotExist()
        onNodeWithText("24").performScrollTo().performClick()
        onNodeWithContentDescription("More reps").performScrollTo().performClick()
        onNodeWithText("Save set").performScrollTo().performClick()
        waitForText("24 kg × 11")
        onNodeWithText("264 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(24.0 to 11, set.bellKg to set.reps)
        // The sheet for a new set is back as it was: an EMOM, and Change to pick the exercise.
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        onNodeWithText("An EMOM").assertExists()
        // The sheet's Change, and the Next set card's behind it.
        onAllNodesWithText("Change").assertCountEquals(2)
    }
}
