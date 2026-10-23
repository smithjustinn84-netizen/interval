@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.justinsmith.bells.domain.BlockChange
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.runUiTest
import dev.justinsmith.bells.ui.setBellsContent
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertTrue

/** A session on a phone on its side: what it needs next is in view, with nothing to scroll, and turning keeps it. */
class SidewaysTest {
    @Test
    fun `Log set and the summary's Done show without scrolling`() = bellsAppTest(sideways = true) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Finish").assertIsDisplayed()
        onNodeWithText("Log set").assertIsDisplayed().performClick()
        // The sheet's own Log set beside the reps, with the screen's under the sheet.
        waitForText("Reps")
        onAllNodesWithText("Log set").onLast().assertIsDisplayed().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")
        onAllNodesWithText("Done").onLast().assertIsDisplayed()
    }

    @Test
    fun `an EMOM shows its clock and Set done`() = runUiTest(sideways = true) {
        val run = EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, 10, firstSide = Side.Left, startedAtMillis = 0)
        setBellsContent { EmomScreen(run, EmomState(EmomPhase.Work, 3, 10, 45, 0.25f), onFinishSet = {}, onStop = {}) }
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
        setBellsContent {
            val size = if (sideways) DpSize(891.dp, 411.dp) else DpSize(411.dp, 891.dp)
            Box(Modifier.requiredSize(size)) { SummaryScreen(finished, onDone = {}) }
        }
        onNodeWithText("Every day").performScrollTo().performClick()
        onNodeWithText("A new load goes to every block", substring = true).assertExists()
        sideways = true
        onNodeWithText("A new load goes to every block", substring = true).assertExists()
    }
}
