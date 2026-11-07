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
    fun `Strength sets its groups in two columns`() = runUiTest(Window.Tablet) {
        val program = program(ProgramId.GobletSquatRow)
        val state = TodayUiState(following = program, programDay = program.dayAfter(0))
        setThemedContent(dark = true) { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        // The program followed leads, the programs beside the mat and the wall beside it.
        val following = onNodeWithText("Goblet squat and row").assertIsDisplayed().getUnclippedBoundsInRoot()
        val beside = onNodeWithText("Beside the mat and the wall").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(beside.left >= following.right)
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
