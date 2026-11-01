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
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.SetLog
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

/** "How was it?" on a finished session's summary, and what it said on the session's page. */
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
