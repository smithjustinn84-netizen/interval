@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlsUxTest {
    @Test
    fun `NameField clears its text on tap and clears focus on Done`() = runUiTest {
        var doneCount = 0
        var passOnDone by mutableStateOf(false)
        setThemedContent {
            val state = rememberTextFieldState("Tabata")
            Column {
                NameField(
                    state = state,
                    placeholder = "Workout name",
                    label = "Name",
                    maxLength = 40,
                    onDone = if (passOnDone) ({ doneCount++ }) else null,
                )
            }
        }
        onNodeWithContentDescription("Clear text").assertExists().performClick()
        onNodeWithContentDescription("Clear text").assertDoesNotExist()
        onNodeWithText("Workout name").assertExists()

        onNode(hasSetTextAction()).performTextInput("HIIT")
        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).performImeAction()
        onNode(hasSetTextAction()).assertIsNotFocused()
        assertEquals(0, doneCount)

        passOnDone = true
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, doneCount)
    }

    @Test
    fun `AccentButton disabled exposes disabled button semantics and ignores clicks`() = runUiTest {
        var clicks = 0
        setThemedContent {
            AccentButton("Save", onClick = { clicks++ }, enabled = false)
        }
        onNodeWithText("Save").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `ConfirmDialog destructive renders and runs confirm`() = runUiTest {
        var confirmed = false
        setThemedContent {
            ConfirmDialog(
                title = "Delete workout?",
                text = "This removes it from history.",
                confirm = "Delete",
                dismiss = "Cancel",
                onConfirm = { confirmed = true },
                onDismiss = {},
                destructive = true,
            )
        }
        onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertTrue(confirmed)
    }

    @Test
    fun `SegmentedPicker lays out and selects under RTL without error`() = runUiTest {
        var selected by mutableStateOf("One")
        setThemedContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SegmentedPicker(
                    options = listOf("One", "Two", "Three"),
                    selected = selected,
                    label = { it },
                    onSelect = { selected = it },
                )
            }
        }
        onNodeWithText("One").assertIsSelected()
        onNodeWithText("Three").performClick()
        onNodeWithText("Three").assertIsSelected()
        assertEquals("Three", selected)
    }
}
