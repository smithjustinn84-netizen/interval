@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test

/** The titles a screen reader jumps between: the shared pieces that mark themselves as headings. */
class HeadingsTest {
    @Test
    fun `a page title and a group label are headings`() = runUiTest {
        setThemedContent {
            ScrollPage("Settings") {
                GroupLabel("Cues")
                Text("Beeps at each phase change")
            }
        }
        onNodeWithText("Settings").assert(isHeading())
        onNodeWithText("Cues").assert(isHeading())
        onNodeWithText("Beeps at each phase change").assert(!isHeading())
    }

    @Test
    fun `a dialog's title is a heading`() = runUiTest {
        setThemedContent {
            IntervalDialog("End workout?", onDismiss = {}, actions = {}) { Text("Nothing is saved.") }
        }
        onNodeWithText("End workout?").assert(isHeading())
        onNodeWithText("Nothing is saved.").assert(!isHeading())
    }
}
