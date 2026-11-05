@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.gear.GearScreen
import dev.justinsmith.lift.gear.GearUiState
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Lift tab and the gear on a tablet: their cards side by side. */
class TabletTest {
    @Test
    fun `Today puts the last session beside the program`() = runUiTest(Window.Tablet) {
        val state = TodayUiState(following = program(ProgramId.GobletSquatRow))
        setThemedContent(dark = true) { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        // What's next leads, the last session beside it.
        val last = onNodeWithText("No sessions yet").assertIsDisplayed().getUnclippedBoundsInRoot()
        val following = onNodeWithText("Goblet squat and row").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(last.left >= following.right)
    }

    @Test
    fun `Your equipment puts the bar beside the bells`() = runUiTest(Window.Tablet) {
        setThemedContent(dark = true) { GearScreen(GearUiState(), onToggle = {}, onBack = {}) }
        val bells = onNodeWithText("Your bells").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bar = onNodeWithText("Your bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bar.left >= bells.right)
        assertTrue(bar.top < bells.bottom)
    }
}
