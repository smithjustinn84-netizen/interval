@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the Timer keeps off its three rows: the ladder, on the work's sheet and said on its row; the rest between
 * sets, on the sets' sheet and said on their chip; and the room that leaves each row's value.
 */
class TimerRowsTest {
    private val steps = mutableListOf<Pair<ConfigField, Int>>()

    @Composable
    private fun Screen(state: TimerUiState = state()) {
        TimerScreen(
            state = state,
            onStep = { field, direction -> steps += field to direction },
            onSet = { _, _ -> },
            onSavePreset = {},
            onStart = {},
            onOpenSettings = {},
            onSeeAllPresets = {},
        )
    }

    @Test
    fun `a ladder reads on the Work row and is set in its sheet`() = runUiTest {
        setThemedContent { Screen(state(custom.copy(rounds = 3, workStepSeconds = 10))) }
        onNodeWithText("Ladder").assertDoesNotExist()
        onNodeWithText("Work · +10s a round").performClick()
        onNodeWithText("Ladder").assertExists()
        onNodeWithText("Rounds work 0:40 · 0:50 · 1:00").assertExists()
        onNodeWithContentDescription("More work each round").performScrollTo().performClick()
        onNodeWithContentDescription("Less work each round").performScrollTo().performClick()
        assertEquals(listOf(ConfigField.WorkStep to 1, ConfigField.WorkStep to -1), steps)
    }

    @Test
    fun `with no ladder the Work row says its name alone and its sheet offers one`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Work").performClick()
        onNode(hasText("Ladder")).assertExists()
        onNodeWithText("Work added each round, or taken off").assertExists()
        onNodeWithText("Rounds work", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a long ladder spells out its first rounds and its last`() = runUiTest {
        setThemedContent { Screen(state(custom.copy(workStepSeconds = -5))) }
        onNodeWithText("Work · −5s a round").performClick()
        onNodeWithText("Rounds work 0:40 · 0:35 · … · 0:15").assertExists()
    }

    @Test
    fun `sets say the rest between them on their chip and take it in their sheet`() = runUiTest {
        setThemedContent { Screen(state(custom.copy(sets = 2, setRestSeconds = 60))) }
        onNodeWithText("Between sets").assertDoesNotExist()
        onNode(hasText("Sets") and hasText("2 · 1m") and hasClickAction()).performClick()
        onNodeWithText("Between sets").assertExists()
        onNodeWithContentDescription("More rest between sets").performScrollTo().performClick()
        assertEquals(listOf(ConfigField.SetRest to 1), steps)
    }

    @Test
    fun `one set offers no rest between sets`() = runUiTest {
        setThemedContent { Screen() }
        onNode(hasText("Sets") and hasText("1") and hasClickAction()).performClick()
        onNodeWithText("Times the rounds run").assertExists()
        onNodeWithText("Between sets").assertDoesNotExist()
    }

    @Test
    fun `a tab as short as a phone's under its bars keeps each value whole`() = runUiTest {
        // An 844 dp phone leaves the tab about this much between the status bar and the navigation bar.
        setThemedContent { Box(Modifier.requiredHeight(740.dp)) { Screen() } }
        listOf("0:40", "0:20", "×6").forEach { value ->
            // The text itself: its card, which the merged tree gives, is taller than the room it leaves the value.
            val height = onNodeWithText(value, useUnmergedTree = true).getUnclippedBoundsInRoot().height
            assertTrue(height >= 40.dp, "$value is ${height.value} dp tall, less than its smallest size")
        }
    }
}
