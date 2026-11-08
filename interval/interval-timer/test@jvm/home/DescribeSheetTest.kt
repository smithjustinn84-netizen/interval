@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Timer's Describe sheet: a workout typed in words, previewed, then put on the Timer or saved as a preset. */
class DescribeSheetTest {
    private val saved = mutableListOf<Preset>()
    private val described = mutableListOf<IntervalConfig>()

    @Composable
    private fun Screen() {
        TimerScreen(
            state = state(),
            onStep = { _, _ -> },
            onSet = { _, _ -> },
            onSavePreset = { saved += it },
            onStart = {},
            onOpenSettings = {},
            onSeeAllPresets = {},
            onSetActivity = {},
            onUseDescription = { described += it },
        )
    }

    @Test
    fun `a workout described in words previews as it is typed and goes onto the Timer`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("Describe a workout").performClick()
        onNodeWithText("Use").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextInput("hello")
        onNodeWithText("Couldn’t spot durations or rounds yet — try “40/20 x8” or “tabata”.").assertExists()
        onNodeWithText("Use").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput("45/15 x12 rower")
        onNodeWithText("Rower").assertExists()
        onNodeWithText("Use").performClick()
        assertEquals(
            listOf(custom.copy(workSeconds = 45, restSeconds = 15, rounds = 12, activity = Activity.Rower)),
            described,
        )
        onNodeWithText("Describe a workout").assertDoesNotExist()
    }

    @Test
    fun `a sequence described in words previews a row a block and goes onto the Timer whole`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("Describe a workout").performClick()
        onNode(hasSetTextAction()).performTextInput("8 x 20/10 then 5 x 1:00/0:30, 1:00 between blocks")
        onNodeWithText("Block 1").assertExists()
        onNodeWithText("20s on · 10s off · ×8").assertExists()
        onNodeWithText("Block 2").assertExists()
        onNodeWithText("1m on · 30s off · ×5").assertExists()
        onNodeWithText("Between blocks").assertExists()
        onNodeWithText("Use").performScrollTo().performClick()
        assertEquals(
            listOf(
                custom.copy(workSeconds = 20, restSeconds = 10, rounds = 8)
                    .withBlocks(
                        listOf(
                            Block(workSeconds = 20, restSeconds = 10, rounds = 8),
                            Block(workSeconds = 60, restSeconds = 30, rounds = 5),
                        ),
                    ).copy(blockRestSeconds = 60),
            ),
            described,
        )
    }

    @Test
    fun `a described workout saves through the preset editor`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("Describe a workout").performClick()
        onNode(hasSetTextAction()).performTextInput("30 on 30 off x5")
        onNodeWithText("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Easy")
        onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf(Preset("Easy", custom.copy(workSeconds = 30, restSeconds = 30, rounds = 5))), saved)
    }
}
