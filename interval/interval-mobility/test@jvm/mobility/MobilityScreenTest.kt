@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.mobility

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

class MobilityScreenTest {
    private val calls = mutableListOf<String>()
    private val page = MobilityPage("Train", onOpenSettings = { calls += "settings" }) { Text("Pillars") }

    @Test
    fun `the flows are listed in two groups under the tab's title and header`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, { calls += "start $it" }) }
        onNodeWithText("Train").assertExists()
        onNodeWithText("Pillars").assertExists()
        onNodeWithText("Before training").assertExists()
        onNodeWithText("After, or on a day off").assertExists()
        onNode(hasText("Hips and shoulders") and hasText("6 moves · no bell") and hasText("8:00")).assertExists()
        onNode(hasText("Core on the mat") and hasText("9 moves · Pilates") and hasText("18:00")).assertExists()
        onNode(hasText("Full reset") and hasText("10 moves") and hasText("25:00")).assertExists()
        onNodeWithContentDescription("Settings").performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun `a flow opens on what is in it and starts from there`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, { calls += "start $it" }) }
        onNode(hasText("Wrists and fingers") and hasClickAction()).performClick()
        waitForIdle()

        onNodeWithText("Mobility · 5 moves · before the wall").assertExists()
        onNodeWithText("after 0:10 to get ready").assertExists()
        onNodeWithText("Wrist circles").assertExists()
        onNodeWithText("Fists loose, slow circles, one way then the other.").assertExists()
        onNodeWithText("Finger pulls").assertExists()
        // The two holds, each 0:45.
        onAllNodesWithText("0:45").assertCountEquals(2)
        onNode(hasText("Start") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf("start wrists-fingers"), calls)
    }

    @Test
    fun `a move with sides says its time a side and no get-ready says none`() = runUiTest(Window.Tablet) {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 0), page, { calls += "start $it" }) }
        onNode(hasText("Hips and shoulders") and hasClickAction()).performClick()
        waitForIdle()

        // On its row behind the sheet, and in the sheet.
        onAllNodesWithText("7:50").assertCountEquals(2)
        onNodeWithText("Hip flexor lunge").assertExists()
        // The lunge, and the world's greatest stretch after it.
        onAllNodesWithText("0:40 a side").assertCountEquals(2)
        onNodeWithText("to get ready", substring = true).assertDoesNotExist()
    }
}
