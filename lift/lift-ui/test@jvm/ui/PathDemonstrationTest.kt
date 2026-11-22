@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.ui.demo.diagramCues
import kotlin.test.Test

class PathDemonstrationTest {
    private val cues = listOf("Hike it back", "Snap the hips up", "Let it float there")
    private val path = "Two-hand swing from the side, with the path its bell takes"

    private fun swing(faults: List<Fault> = emptyList(), check: ComposeUiTest.() -> Unit) =
        runUiTest {
            setThemedContent {
                PathDemonstration(
                    KettlebellReps.Swing,
                    "Two-hand swing",
                    cues,
                    cueOf = BuiltInExercise.TwoHandSwing.diagramCues,
                    faults = faults,
                )
            }
            check()
        }

    @Test
    fun `the card shows the path over the lift's cues with the one its part belongs to lit`() = swing {
        onNodeWithContentDescription(path).assertExists()
        onNodeWithContentDescription("Where in the bell's path").assertExists()
        onNodeWithText("Float").assertExists()
        onNodeWithText("Let it float there").assertIsSelected()
        onNodeWithText("Snap the hips up").assertIsNotSelected()
    }

    @Test
    fun `a cue tapped holds the rep where its part starts`() = swing {
        onNodeWithText("Snap the hips up").performClick()
        onNodeWithText("Hip snap").assertExists()
        onNodeWithText("Snap the hips up").assertIsSelected()
        onNodeWithContentDescription("Play the bell path").assertExists()
        onNodeWithText("Hike it back").performClick()
        onNodeWithText("Hike pass").assertExists()
        onNodeWithText("Hike it back").assertIsSelected()
    }

    @Test
    fun `Step holds the rep at each part in turn and its cue follows`() = swing {
        onNodeWithText("Step").performClick()
        onNodeWithText("Backswing").assertExists()
        onNodeWithText("Let it float there").assertIsSelected()
        onNodeWithContentDescription("Play the bell path").assertExists()
        onNodeWithText("Step").performClick()
        onNodeWithText("Hike pass").assertExists()
        onNodeWithText("Hike it back").assertIsSelected()
        onNodeWithText("Step").performClick()
        onNodeWithText("Hip snap").assertExists()
        onNodeWithText("Snap the hips up").assertIsSelected()
    }

    @Test
    fun `Pause holds the rep and the speed halves`() = swing {
        onNodeWithContentDescription("Pause the bell path").performClick()
        onNodeWithContentDescription("Play the bell path").assertExists()
        onNodeWithText("1×").performClick()
        onNodeWithText("0.5×").assertExists()
    }

    @Test
    fun `a fault the path can show is done wrong when tapped and right when tapped again`() =
        swing(faults = listOf(Fault.SquattedHinge, Fault.DeathGrip)) {
            onNodeWithText("Watch for").assertExists()
            onNodeWithText("Loosen at the top, grip on the way down").assertHasNoClickAction()
            onNodeWithText("Hips back, shins stay vertical").performClick()
            onNodeWithContentDescription("$path. Done wrong").assertExists()
            onNodeWithText("Done wrong").assertDoesNotExist()
            onNodeWithText("Hips back, shins stay vertical").assertIsSelected().performClick()
            onNodeWithContentDescription(path).assertExists()
        }

    @Test
    fun `a lift with no faults has nothing to watch for`() = swing {
        onNodeWithText("Watch for").assertDoesNotExist()
    }

    @Test
    fun `a lift with no cues of its own is its path alone`() = runUiTest {
        setThemedContent { PathDemonstration(KettlebellReps.GobletSquat, "Pause goblet squat", cues = emptyList()) }
        onNodeWithContentDescription("Pause goblet squat from the side, with the path its bell takes").assertExists()
        onNodeWithText("Bottom").assertExists()
        onNodeWithText("Elbows inside the knees, spine long").assertExists()
    }
}
