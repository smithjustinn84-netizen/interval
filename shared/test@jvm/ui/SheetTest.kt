@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** [IntervalSheet] on each window: a bottom sheet on phones, a side sheet on wider and taller windows. */
class SheetTest {
    private var dismissed = 0

    @Composable
    private fun Sheet() {
        IntervalSheet(title = "Work", onDismiss = { dismissed++ }) { hide ->
            Text("Body")
            AccentButton("Done", onClick = hide)
        }
    }

    private fun DesktopComposeUiTest.pane(): SemanticsNodeInteraction =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Work"))

    @Test
    fun `on a tablet the sheet stands full height at the end edge`() = runUiTest(Window.Tablet) {
        setThemedContent { Sheet() }
        val bounds = pane().getBoundsInRoot()
        assertEquals(Window.Tablet.width.dp, bounds.right)
        assertEquals(Sizes.sideSheet, bounds.right - bounds.left)
        assertEquals(0.dp, bounds.top)
        assertEquals(Window.Tablet.height.dp, bounds.bottom)
        onNodeWithContentDescription("Close").assertExists()
        onNodeWithContentDescription("Drag handle").assertDoesNotExist()
    }

    @Test
    fun `on a phone the sheet rises from the bottom`() = runUiTest(Window.Phone) {
        setThemedContent { Sheet() }
        onNodeWithContentDescription("Drag handle").assertExists()
        onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun `on a phone held sideways the sheet still rises from the bottom`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Sheet() }
        onNodeWithContentDescription("Drag handle").assertExists()
        onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun `on a tablet done closes the sheet and the caller hears once it has slid away`() = runUiTest(Window.Tablet) {
        setThemedContent { Sheet() }
        mainClock.autoAdvance = false
        onNodeWithText("Done").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(0, dismissed)
        mainClock.advanceTimeBy(Motion.EXIT_MILLIS * 2L)
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(1, dismissed)
        onNodeWithText("Body").assertDoesNotExist()
    }

    @Test
    fun `on a tablet the close button closes the sheet`() = runUiTest(Window.Tablet) {
        setThemedContent { Sheet() }
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(1, dismissed)
        onNodeWithText("Body").assertDoesNotExist()
    }

    @Test
    fun `on a tablet escape closes the sheet`() = runUiTest(Window.Tablet) {
        setThemedContent { Sheet() }
        onNodeWithText("Done").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `on a tablet a tap on the scrim closes the sheet`() = runUiTest(Window.Tablet) {
        setThemedContent { Sheet() }
        onNode(isDialog()).performTouchInput { click(Offset(100f, 400f)) }
        waitForIdle()
        assertEquals(1, dismissed)
        onNodeWithText("Body").assertDoesNotExist()
    }
}
