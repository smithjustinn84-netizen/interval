@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.mobility

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.timer.Clash
import dev.justinsmith.interval.timer.FlowDraft
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val plank = Move("Plank", 30, "Long line.")
private val bridge = Move("Shoulder bridge", 20, "Hips up.", sided = true, held = false)
private val ready = FlowEditorUiState(FlowDraft("Floor core", FlowUse.After, "", listOf(plank, bridge)), total = "1:45")

class FlowEditorScreenTest {
    @Test
    fun `a new flow asks for its name and moves and saves once it has them`() = runUiTest {
        val calls = mutableListOf<String>()
        setThemedContent {
            FlowEditorScreen(FlowEditorUiState(), FlowEditorActions(onSave = { calls += "save" }))
        }
        onNode(hasText("Build a flow") and isHeading()).assertExists()
        onNodeWithText("No moves yet: add the first.").assertExists()
        onNode(hasText("Save flow") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Delete flow").assertDoesNotExist()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `its moves are listed with their times and the whole flow's`() = runUiTest {
        setThemedContent { FlowEditorScreen(ready, FlowEditorActions()) }
        onNodeWithText("Plank").assertExists()
        onNodeWithText("0:30").assertExists()
        onNodeWithText("0:20 a side").assertExists()
        onNodeWithText("1:45 in all, with the change-overs").performScrollTo()
        onNode(hasText("Save flow") and hasClickAction()).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a flow with another's moves and times says which and can't be saved`() = runUiTest {
        setThemedContent { FlowEditorScreen(ready.copy(clash = Clash("Hips and shoulders")), FlowEditorActions()) }
        onNodeWithText("Same moves and times as Hips and shoulders: change a time to keep both.").performScrollTo()
        onNode(hasText("Save flow") and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a move opens on its sheet where its time steps and it moves down`() = runUiTest {
        val drafts = mutableListOf<FlowDraft>()
        setThemedContent {
            FlowEditorScreen(ready, FlowEditorActions(onChange = { drafts += it(ready.draft) }))
        }
        onNode(hasText("Plank") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("A side at a time").assertExists()
        onNode(hasText("Move up") and hasClickAction()).assertDoesNotExist()
        onNode(hasText("Move down") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf(bridge, plank), drafts.last().moves)
    }

    @Test
    fun `one already saved can be deleted`() = runUiTest(Window.Tablet) {
        var deleted = 0
        setThemedContent { FlowEditorScreen(ready.copy(edits = true), FlowEditorActions(onDelete = { deleted++ })) }
        onNode(hasText("Edit flow") and isHeading()).assertExists()
        onNode(hasText("Delete flow") and hasClickAction()).performScrollTo().performClick()
        assertEquals(1, deleted)
    }
}
