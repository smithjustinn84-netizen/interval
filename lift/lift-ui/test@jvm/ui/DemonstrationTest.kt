@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.ui.demo.GobletSquatDemo
import dev.justinsmith.lift.ui.demo.TwoHandSwingDemo
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
    fun `Slow halves the speed and Step moves on to the next cue`() = runUiTest {
        setThemedContent { Demonstration(GobletSquatDemo, "Goblet squat", listOf("Set it", "Sit", "Stand")) }
        onNodeWithText("Slow").performClick()
        onNodeWithText("Full speed").assertExists()
        onNodeWithText("Step").performClick()
        onNodeWithText("Play").assertExists()
        onNodeWithText("Sit").assertIsSelected()
        onNodeWithText("Step").performClick()
        onNodeWithText("Stand").assertIsSelected()
    }

    @Test
    fun `a fault the figure can show is done wrong when tapped and right when tapped again`() = runUiTest {
        setThemedContent {
            Demonstration(
                TwoHandSwingDemo,
                "Two-hand swing",
                listOf("Hike", "Snap", "Float"),
                faults = listOf(Fault.SquattedHinge, Fault.DeathGrip),
            )
        }
        onNodeWithText("Watch for").assertExists()
        onNodeWithText("Loosen at the top, grip on the way down").assertHasNoClickAction()
        onNodeWithText("Hips back, shins stay vertical").performClick()
        onNodeWithContentDescription("Two-hand swing, shown by a figure. Done wrong").assertExists()
        onNodeWithText("Done wrong").assertDoesNotExist()
        onNodeWithText("Hips back, shins stay vertical").assertIsSelected().performClick()
        onNodeWithContentDescription("Two-hand swing, shown by a figure").assertExists()
    }

    @Test
    fun `a lift with no faults has nothing to watch for`() = runUiTest {
        setThemedContent { Demonstration(GobletSquatDemo, "Goblet squat", listOf("Set it", "Sit", "Stand")) }
        onNodeWithText("Watch for").assertDoesNotExist()
    }

    @Test
    fun `a cue tapped is the one the figure shows`() = runUiTest {
        setThemedContent { Demonstration(GobletSquatDemo, "Goblet squat", listOf("Set it", "Sit", "Stand")) }
        onNodeWithText("Stand").performClick()
        onNodeWithText("Stand").assertIsSelected()
    }
}
