@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test

/**
 * A session on a phone on its side: what it needs next is in view, with nothing to scroll. The EMOM and the
 * summary on their own are lift-session's `SidewaysTest`.
 */
class SidewaysTest {
    @Test
    fun `Log set and the summary's Done show without scrolling`() = liftAppTest(window = Window.PhoneSideways) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Finish").assertIsDisplayed()
        onNodeWithText("Log set").assertIsDisplayed()
        onNodeWithContentDescription("Add set or EMOM").assertIsDisplayed().performClick()
        // The sheet's own Log set beside the reps, with the screen's under the sheet.
        waitForText("Reps")
        onAllNodesWithText("Log set").onLast().assertIsDisplayed().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")
        onAllNodesWithText("Done").onLast().assertIsDisplayed()
    }
}
