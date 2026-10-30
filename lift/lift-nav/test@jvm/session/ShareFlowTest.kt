@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A session's words, as the summary and its page in History offer them to share. */
class ShareFlowTest {
    private val figures = "1:30 · 160 kg moved · 10 reps\nTwo-hand swing: Heaviest 16 kg × 10"

    @Test
    fun `the summary shares the session in words`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")

        onNodeWithText("Share").performClick()

        assertEquals(listOf("Two-hand swing\n$figures"), app.sharer.texts)
    }

    @Test
    fun `a past session shares from its page with when it was`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Last session")
        onNodeWithText("Last session").performClick()
        waitForText("Moved")

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Share session").performClick()

        val text = app.sharer.texts.single()
        assertTrue(text.startsWith("Two-hand swing · day 0 · "), text)
        assertTrue(text.endsWith("\n$figures"), text)
    }
}
