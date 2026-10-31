@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.offeredExercises
import kotlin.test.Test
import kotlin.test.assertTrue

/** The exercise library on a tablet. */
class TabletTest {
    @Test
    fun `the exercise library lays its cards side by side`() = runUiTest(Window.Tablet) {
        setThemedContent(dark = true) {
            ExerciseLibraryScreen(LibraryList(offeredExercises(emptyList())), onFilters = {}, onBack = {})
        }
        val bells = onNodeWithText("Kettlebell · 19").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bars = onNodeWithText("Barbell · 20").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bars.left >= bells.right)
        assertTrue(bars.top < bells.bottom)
    }
}
