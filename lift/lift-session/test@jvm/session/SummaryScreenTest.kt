@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BlockChange
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.history.LiftDetailScreen
import dev.justinsmith.lift.history.LiftDetailUiState
import kotlin.test.Test
import kotlin.test.assertEquals

private val session = LiftSession(
    "s",
    0,
    600_000,
    "Swings",
    listOf(SetLog(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)),
)

/**
 * "How was it?" on a finished session's summary, what it does to the day's steps up, and what it said on the
 * session's page.
 */
class SummaryScreenTest {
    private val calls = mutableListOf<String>()
    private val healthNotes = mutableListOf<String?>()

    private val reflection = SummaryReflection(
        onEffortChange = { calls += "effort $it" },
        onNoteChange = { calls += "note $it" },
        onDescribeForHealth = { healthNotes += it },
    )

    @Test
    fun `the summary asks how it was and hands on the effort and the note`() = runUiTest {
        var finished by mutableStateOf(Finished(session, emptyList()))
        setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}, reflection = reflection) }
        onNodeWithText("How was it?").performScrollTo()
        onNodeWithText("Hard").performClick()
        onNode(hasSetTextAction()).performTextInput("Felt strong")
        waitForIdle()
        assertEquals(listOf("effort Hard", "note Felt strong"), calls)

        assertEquals(null, healthNotes.last())
        finished = Finished(session.copy(effort = PerceivedEffort.Hard, note = "Felt strong"), emptyList())
        waitForIdle()
        assertEquals("Hard · Felt strong", healthNotes.last())
    }

    @Test
    fun `a day that felt all out leaves its steps up off until one is turned on`() = runUiTest {
        val program = program(ProgramId.GobletSquatRow)
        val squats = program.days[0][0]
        val rows = program.days[0][1]
        val nudges = listOf(BlockChange(0, squats, squats.copy(reps = 11)), BlockChange(1, rows, rows.copy(reps = 11)))
        var finished by mutableStateOf(
            Finished(session, emptyList(), changes = PlanChanges(program, 1, 0, emptyList(), nudges)),
        )
        val kept = mutableListOf<Set<Int>>()
        setThemedContent(dark = true) {
            SummaryScreen(finished, onDone = {}, onKeep = { _, _, taken -> kept += taken })
        }
        // Said nothing yet: both steps up are on, and Save keeps them.
        onNodeWithText("Save as Goblet squat and row (yours)").assertExists()
        onNodeWithText("It felt very hard", substring = true).assertDoesNotExist()

        finished = finished.copy(session = session.copy(effort = PerceivedEffort.AllOut))
        onNodeWithText("It felt very hard: these wait until you turn them on.").performScrollTo()
        onNodeWithText("Save as Goblet squat and row (yours)").assertDoesNotExist()

        onNodeWithText("Row").performScrollTo().performClick()
        onNodeWithText("It felt very hard", substring = true).assertDoesNotExist()
        onNodeWithText("Save as Goblet squat and row (yours)").performScrollTo().performClick()
        assertEquals(listOf(setOf(1)), kept)
    }

    @Test
    fun `a session's page says how it went once something was said`() = runUiTest {
        var shown by mutableStateOf(LiftDetailUiState(session, "Saturday", "1:21 PM"))
        setThemedContent(dark = true) { LiftDetailScreen(shown, onDelete = {}, onBack = {}) }
        onNodeWithText("How it went").assertDoesNotExist()

        shown = shown.copy(session = session.copy(effort = PerceivedEffort.Steady, note = "Grip went"))
        onNodeWithText("How it went").assertExists()
        onNodeWithText("Steady").assertExists()
        onNodeWithText("Grip went").assertExists()
    }
}
