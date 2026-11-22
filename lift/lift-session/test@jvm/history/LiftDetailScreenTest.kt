@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.SetLog
import kotlin.test.Test
import kotlin.test.assertEquals

private val twoSets = listOf(
    SetLog.of(1L, BuiltInExercise.TwoHandSwing, kg = 24.0, reps = 10, side = null, loggedAtEpochMillis = 0L),
    SetLog.of(2L, BuiltInExercise.TwoHandSwing, kg = 16.0, reps = 12, side = null, loggedAtEpochMillis = 60_000L),
)

private val baseSession = LiftSession(
    uid = "s1",
    startedAtEpochMillis = 0L,
    finishedAtEpochMillis = 600_000L,
    title = "Swings",
    sets = twoSets,
)

class LiftDetailScreenTest {
    @Test
    fun `effort and note can be edited inline on the detail page`() = runUiTest {
        val efforts = mutableListOf<PerceivedEffort?>()
        val notes = mutableListOf<String>()
        var state by mutableStateOf(LiftDetailUiState(baseSession, "Saturday, Nov 14", "7:42 AM"))
        setThemedContent {
            LiftDetailScreen(
                state = state,
                onDelete = {},
                onBack = {},
                onEffortChange = {
                    efforts += it
                    state = state.copy(session = state.session?.copy(effort = it))
                },
                onNoteChange = { notes += it },
            )
        }

        onNodeWithText("How it went").assertExists()
        onNodeWithText("Hard").performClick()
        assertEquals(listOf<PerceivedEffort?>(PerceivedEffort.Hard), efforts)

        // Tapping the already-selected effort clears it.
        onNodeWithText("Hard").performClick()
        assertEquals(listOf(PerceivedEffort.Hard, null), efforts)

        onNode(hasSetTextAction()).performTextInput("Strong hips")
        waitForIdle()
        assertEquals("Strong hips", notes.last())
    }

    @Test
    fun `tapping a logged set opens the edit sheet and saves updated reps`() = runUiTest {
        val saved = mutableListOf<SetLog>()
        setThemedContent {
            LiftDetailScreen(
                state = LiftDetailUiState(baseSession, "Saturday, Nov 14", "7:42 AM"),
                onDelete = {},
                onBack = {},
                onUpdateSet = { saved += it },
            )
        }

        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasClickAction() and hasText("24 kg × 10"))
        onNode(hasClickAction() and hasText("24 kg × 10")).performClick()
        waitForIdle()
        onNodeWithText("Save set").assertExists()
        onNodeWithContentDescription("More reps").performClick()
        onNodeWithText("Save set").performClick()
        waitForIdle()

        assertEquals(1, saved.size)
        assertEquals(11, saved.single().reps)
        assertEquals(24.0, saved.single().load.kg)
    }

    @Test
    fun `removing a set is offered when more than one set is logged and hidden for a single set`() = runUiTest {
        val removed = mutableListOf<Long>()
        var state by mutableStateOf(LiftDetailUiState(baseSession, "Saturday, Nov 14", "7:42 AM"))
        setThemedContent {
            LiftDetailScreen(
                state = state,
                onDelete = {},
                onBack = {},
                onRemoveSet = { set, _ -> removed += set.id },
            )
        }

        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasClickAction() and hasText("16 kg × 12"))
        onNode(hasClickAction() and hasText("16 kg × 12")).performClick()
        waitForIdle()
        onNodeWithText("Remove set").performClick()
        waitForIdle()
        assertEquals(listOf(2L), removed)

        // Single-set session hides Remove set.
        state = state.copy(session = baseSession.copy(sets = listOf(twoSets.first())))
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasClickAction() and hasText("24 kg × 10"))
        onNode(hasClickAction() and hasText("24 kg × 10")).performClick()
        waitForIdle()
        onNodeWithText("Save set").assertExists()
        onNodeWithText("Remove set").assertDoesNotExist()
    }
}
