@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.offeredExercises
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The library's rows, on its page and in a sheet's list, are keyed by exercise: a search that narrows the list
 * moves the rows still shown.
 */
class ExerciseRowsKeyedTest {
    @Test
    fun `a row still shown after a search is the same row`() = runUiTest(Window.Tablet) {
        setThemedContent {
            ExerciseLibraryScreen(LibraryList(offeredExercises(emptyList())), onFilters = {}, onBack = {})
        }
        val row = hasText(ROW) and hasClickAction()
        val before = onAllNodes(row).onFirst().fetchSemanticsNode().id

        // Drops the kettlebell lifts above it that aren't presses (the swings, the clean), so it moves to the top of
        // its card. Unkeyed, the row now at its place would be another exercise's, composed again as this one.
        onNode(hasSetTextAction()).performTextReplacement("press")
        waitForIdle()

        assertEquals(before, onAllNodes(row).onFirst().fetchSemanticsNode().id)
    }

    @Test
    fun `a sheet's row still shown after a search is the same row`() = runUiTest(Window.Phone) {
        setThemedContent {
            val sheet = rememberPickerList(LibraryList(offeredExercises(emptyList())), onFilters = {}, onPick = {})
            LazyColumn(Modifier.fillMaxSize()) { sheet.items(this) {} }
        }
        val row = hasText(ROW) and hasClickAction()
        val before = onAllNodes(row).onFirst().fetchSemanticsNode().id

        onNode(hasSetTextAction()).performTextReplacement("press")
        waitForIdle()

        assertEquals(before, onAllNodes(row).onFirst().fetchSemanticsNode().id)
    }

    private companion object {
        const val ROW = "Press"
    }
}
