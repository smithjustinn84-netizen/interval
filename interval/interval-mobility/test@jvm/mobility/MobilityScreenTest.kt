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
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.ownFlowKey
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

class MobilityScreenTest {
    private val calls = mutableListOf<String>()
    private val page = MobilityPage("Train", onOpenSettings = { calls += "settings" }) { Text("Pillars") }
    private val actions = MobilityActions(
        onStart = { calls += "start $it" },
        onBuild = { calls += "build" },
        onCopy = { calls += "copy $it" },
        onEdit = { calls += "edit $it" },
    )

    @Test
    fun `the flows are listed in two groups under the tab's title and header`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, actions) }
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
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, actions) }
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
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 0), page, actions) }
        onNode(hasText("Hips and shoulders") and hasClickAction()).performClick()
        waitForIdle()

        // On its row behind the sheet, and in the sheet.
        onAllNodesWithText("7:50").assertCountEquals(2)
        onNodeWithText("Hip flexor lunge").assertExists()
        // The lunge, and the world's greatest stretch after it.
        onAllNodesWithText("0:40 a side").assertCountEquals(2)
        onNodeWithText("to get ready", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a move on the mat opens on Lift's figure and comes back to the flow`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, actions) }
        onNode(hasText("Core on the mat") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Tap one to see how it goes", substring = true).assertExists()
        onNode(hasText("The hundred") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Move 1 of 9 · 1:40 · Moved through").assertExists()
        onNodeWithText("Head and shoulders up, legs long. Pump the arms: in for five, out for five.").assertExists()
        onNodeWithContentDescription("The hundred, shown by a figure").assertExists()
        onNodeWithContentDescription("Where in the rep").assertExists()
        onNodeWithText("Start").assertDoesNotExist()
        onNode(hasText("Back to the moves") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Start").assertExists()
        onNodeWithText("Roll-up").assertExists()
    }

    @Test
    fun `a stretch opens on its words and its figure`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, actions) }
        onNode(hasText("Hips and shoulders") and hasClickAction()).performClick()
        waitForIdle()
        onNode(hasText("Deep squat") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Move 3 of 6 · 1:00 · Held").assertExists()
        onNodeWithText("Heels down, chest up. Elbows inside the knees, pressing them out.").assertExists()
        onNodeWithContentDescription("Deep squat, shown by a figure").assertExists()
        onNodeWithText("Back to the moves").assertExists()
    }

    @Test
    fun `on your own leads to a new flow and a shipped one's sheet to a copy of it`() = runUiTest {
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10), page, actions) }
        onNodeWithText("On your own").performScrollTo()
        onNode(hasText("Build a flow") and hasClickAction()).performScrollTo().performClick()
        onNode(hasText("Hips and shoulders") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Edit").assertDoesNotExist()
        onNode(hasText("Make it yours") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf("build", "copy hips-shoulders"), calls)
    }

    @Test
    fun `a flow of the user's is listed in its group and its sheet leads to its editor`() = runUiTest {
        val mine = Flow(ownFlowKey(7), "Floor core", FlowUse.Before, listOf(Move("Plank", 30, "Long line.")), id = 7)
        setThemedContent { MobilityScreen(mobilityState(prepSeconds = 10, builtInFlows + mine), page, actions) }
        onNode(hasText("Floor core") and hasText("1 move") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Make it yours").assertDoesNotExist()
        onNode(hasText("Edit") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf("edit 7"), calls)
    }
}
