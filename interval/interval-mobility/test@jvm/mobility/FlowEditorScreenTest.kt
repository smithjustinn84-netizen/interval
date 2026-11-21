@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.mobility

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
    fun `a move opens on its sheet where its time steps`() = runUiTest {
        setThemedContent { FlowEditorScreen(ready, FlowEditorActions()) }
        onNode(hasText("Plank") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("A side at a time").assertExists()
    }

    @Test
    fun `a move dragged by its handle past the next takes its place`() = runUiTest {
        var draft by mutableStateOf(ready.draft)
        setThemedContent {
            FlowEditorScreen(ready.copy(draft = draft), FlowEditorActions(onChange = { draft = it(draft) }))
        }
        val next = onNode(hasText("Shoulder bridge") and hasClickAction()).getBoundsInRoot()
        onNodeWithContentDescription("Drag to reorder Plank").performScrollTo().performTouchInput {
            val rowHeight = (next.bottom - next.top).toPx()
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, rowHeight / STEPS)) }
            up()
        }
        waitForIdle()
        assertEquals(listOf(bridge, plank), draft.moves)
        onNodeWithContentDescription("Drag to reorder Plank").performCustomAccessibilityActionWithLabel("Move up")
        waitForIdle()
        assertEquals(listOf(plank, bridge), draft.moves)
    }

    @Test
    fun `one already saved can be deleted`() = runUiTest(Window.Tablet) {
        var deleted = 0
        setThemedContent { FlowEditorScreen(ready.copy(edits = true), FlowEditorActions(onDelete = { deleted++ })) }
        onNode(hasText("Edit flow") and isHeading()).assertExists()
        onNode(hasText("Delete flow") and hasClickAction()).performScrollTo().performClick()
        assertEquals(1, deleted)
    }

    private companion object {
        const val STEPS = 10
    }
}
