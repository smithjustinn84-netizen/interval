@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BlockChange
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.builtInPrograms
import kotlin.test.Test
import kotlin.test.assertTrue

/** A session's pages on a phone on its side: what they need next is in view, and turning keeps it. */
class SidewaysTest {
    @Test
    fun `an EMOM shows its clock and Set done`() = runUiTest(Window.PhoneSideways) {
        val run = EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, 10, firstSide = Side.Left, startedAtMillis = 0)
        setThemedContent(dark = true) {
            EmomScreen(run, EmomState(EmomPhase.Work, 3, 10, 45, 0.25f), onFinishSet = {}, onStop = {})
        }
        onNodeWithText("0:45").assertIsDisplayed()
        onNodeWithText("Work").assertIsDisplayed()
        onNodeWithText("Two-hand swing · 24 kg × 10").assertIsDisplayed()
        onNodeWithText("Stop").assertIsDisplayed()
        onNodeWithText("Set done").assertIsDisplayed().assertIsEnabled()
        // Beside the ring, not over it as when the readout sat inside a ring too short for it.
        val clock = onNodeWithText("0:45").getUnclippedBoundsInRoot()
        assertTrue(onNodeWithText("Work").getUnclippedBoundsInRoot().left >= clock.right)
    }

    @Test
    fun `the summary keeps how far to keep the changes when the phone turns`() = runUiTest {
        val program = builtInPrograms.first()
        val block = program.days[0][0]
        val finished = Finished(
            LiftSession("s", 0, 60_000, "Day 1", emptyList()),
            emptyList(),
            changes = PlanChanges(program, 1, 0, listOf(BlockChange(0, block, block.copy(rounds = block.rounds + 1)))),
        )
        var sideways by mutableStateOf(false)
        setThemedContent(dark = true) {
            val size = if (sideways) DpSize(891.dp, 411.dp) else DpSize(411.dp, 891.dp)
            Box(Modifier.requiredSize(size)) { SummaryScreen(finished, onDone = {}) }
        }
        onNodeWithText("Every day").performScrollTo().performClick()
        onNodeWithText("A new load goes to every block", substring = true).assertExists()
        sideways = true
        onNodeWithText("A new load goes to every block", substring = true).assertExists()
    }
}
