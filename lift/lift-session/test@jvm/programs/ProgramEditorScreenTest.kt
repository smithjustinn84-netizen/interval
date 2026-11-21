@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.programs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

/** The program editor over a draft it changes: a day's blocks put in another order by their handles. */
class ProgramEditorScreenTest {
    private val start = ProgramDraft.of(program(ProgramId.GobletSquatRow)).copy(name = "Squat and row")

    private fun ProgramDraft.firstDay() = days[0].map { it.exercise }

    @Test
    fun `a block dragged by its handle past the next takes its place`() = runUiTest(Window.Phone) {
        var draft by mutableStateOf(start)
        setThemedContent {
            ProgramEditorScreen(
                ProgramEditorUiState(draft = draft, initialDraft = start),
                ProgramEditorActions(onChange = { draft = it(draft) }),
            )
        }
        assertEquals(listOf(BuiltInExercise.GobletSquat, BuiltInExercise.Row), draft.firstDay())
        val row = onNodeWithText("Row").getBoundsInRoot()
        onNodeWithContentDescription("Drag to reorder Goblet squat").performTouchInput {
            val rowHeight = (row.bottom - row.top).toPx()
            down(center)
            // A step at a time, as a finger moves, to past the middle of the row below.
            repeat(STEPS) { moveBy(Offset(0f, rowHeight * 2 / STEPS)) }
            up()
        }
        waitForIdle()
        assertEquals(listOf(BuiltInExercise.Row, BuiltInExercise.GobletSquat), draft.firstDay())
    }

    @Test
    fun `a screen reader moves a block up from its handle`() = runUiTest(Window.Phone) {
        var draft by mutableStateOf(start)
        setThemedContent {
            ProgramEditorScreen(
                ProgramEditorUiState(draft = draft, initialDraft = start),
                ProgramEditorActions(onChange = { draft = it(draft) }),
            )
        }
        onNodeWithContentDescription("Drag to reorder Row").performCustomAccessibilityActionWithLabel("Move up")
        waitForIdle()
        assertEquals(listOf(BuiltInExercise.Row, BuiltInExercise.GobletSquat), draft.firstDay())
    }

    private companion object {
        const val STEPS = 10
    }
}
