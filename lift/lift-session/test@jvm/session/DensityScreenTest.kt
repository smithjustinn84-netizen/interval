@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DensityPhase
import dev.justinsmith.lift.domain.DensityState
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import kotlin.test.Test
import kotlin.test.assertEquals

private val snatches = DensityRun(
    BuiltInExercise.Snatch,
    Load.Bell(16.0),
    reps = 5,
    minutes = 20,
    firstSide = Side.Left,
    startedAtMillis = 0,
    best = 14,
)

/** A density block's page over fixed state. A whole block in a session is lift-nav's `DensityAcceptanceTest`. */
class DensityScreenTest {
    @Test
    fun `the count-in the cap and Done each show and only the cap takes Round done`() = runUiTest {
        var state by mutableStateOf(DensityState(DensityPhase.GetReady, 7, 0, 20, 0.3f))
        var rounds = 0
        setThemedContent(dark = true) { DensityScreen(snatches, state, onFinishRound = { rounds++ }, onStop = {}) }

        onNodeWithText("Get ready").assertExists()
        onNodeWithText("0:07").assertExists()
        onNodeWithText("20 min cap").assertExists()
        onNodeWithText("Best: 14").assertExists()
        onNodeWithText("Round done").assertIsNotEnabled()

        state = DensityState(DensityPhase.Work, 545, 9, 20, 0.55f)
        onNodeWithText("Go").assertExists()
        onNodeWithText("9:05").assertExists()
        onNodeWithText("9 rounds").assertExists()
        onNodeWithText("Round done").assertIsEnabled().performClick()
        assertEquals(1, rounds)

        state = DensityState(DensityPhase.Work, 540, 1, 20, 0.55f)
        onNodeWithText("1 round").assertExists()
        // A one-sided lift's rounds turn sides: the second is on the right.
        onNodeWithText("Snatch · 16 kg × 5 · Right").assertExists()

        state = DensityState(DensityPhase.Done, 0, 15, 20, 1f)
        onNodeWithText("Done").assertExists()
        onNodeWithText("15 rounds").assertExists()
        onNodeWithText("Round done").assertIsNotEnabled()
    }

    @Test
    fun `the first time of a block has no best to show`() = runUiTest {
        val state = DensityState(DensityPhase.Work, 300, 2, 20, 0.7f)
        setThemedContent(dark = true) { DensityScreen(snatches.copy(best = null), state, {}, {}) }
        onNodeWithText("2 rounds").assertExists()
        onNodeWithText("Best", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Stop asks first and keeps the rounds`() = runUiTest {
        var stopped = false
        val state = DensityState(DensityPhase.Work, 300, 4, 20, 0.7f)
        setThemedContent(dark = true) {
            DensityScreen(snatches, state, onFinishRound = {}, onStop = { stopped = true })
        }
        onNodeWithText("Stop").performClick()
        onNodeWithText("Rounds done so far stay in the session.").assertExists()
        onNode(hasText("Stop") and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(true, stopped)
    }
}
