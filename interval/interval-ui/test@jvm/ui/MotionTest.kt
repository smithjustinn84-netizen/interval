@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test

class MotionTest {
    @Test
    fun `rolling text settles on its new value, the old one gone`() = runUiTest {
        var text by mutableStateOf("4:40")
        setThemedContent {
            Column { RollingText(text, rising = true, style = IntervalTheme.type.display, color = Color.Black) }
        }
        onNodeWithText("4:40").assertIsDisplayed()
        text = "5:00"
        onNodeWithText("5:00").assertIsDisplayed()
        onNodeWithText("4:40").assertDoesNotExist()
        text = "4:30"
        onNodeWithText("4:30").assertIsDisplayed()
        onNodeWithText("5:00").assertDoesNotExist()
    }

    @Test
    fun `an empty state shows its title and its text`() = runUiTest {
        setThemedContent {
            Column { EmptyState(IntervalIcons.History, title = "No workouts yet", text = "They show up here.") }
        }
        onNodeWithText("No workouts yet").assertIsDisplayed()
        onNodeWithText("They show up here.").assertIsDisplayed()
    }
}
