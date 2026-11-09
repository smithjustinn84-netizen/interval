@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.ui.demo.GobletSquatDemo
import kotlin.test.Test

class DemonstrationTest {
    @Test
    fun `the card shows the figure over its timeline and cues and stops on Pause`() = runUiTest {
        setThemedContent { Demonstration(GobletSquatDemo, "Goblet squat", listOf("Set it", "Sit", "Stand")) }
        onNodeWithContentDescription("Goblet squat, shown by a figure").assertExists()
        onNodeWithContentDescription("Where in the rep").assertExists()
        onNodeWithText("Sit").assertExists()
        onNodeWithText("Pause").performClick()
        onNodeWithText("Play").assertExists()
    }

    @Test
    fun `a cue tapped is the one the figure shows`() = runUiTest {
        setThemedContent { Demonstration(GobletSquatDemo, "Goblet squat", listOf("Set it", "Sit", "Stand")) }
        onNodeWithText("Stand").performClick()
        onNodeWithText("Stand").assertIsSelected()
    }
}
