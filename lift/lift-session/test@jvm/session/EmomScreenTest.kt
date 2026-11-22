@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.Side
import kotlin.test.Test
import kotlin.test.assertEquals

private val swings =
    EmomRun(BuiltInExercise.TwoHandSwing, Load.Bell(24.0), 10, minutes = 10, firstSide = Side.Left, startedAtMillis = 0)

/** The EMOM's page over fixed state. A whole EMOM in a session is lift-nav's `EmomFlowTest`. */
class EmomScreenTest {
    @Test
    fun `each phase shows its word and only work takes Set done`() = runUiTest {
        var state by mutableStateOf(EmomState(EmomPhase.GetReady, 1, 10, 7, 0.3f))
        var setsDone = 0
        setThemedContent(dark = true) { EmomScreen(swings, state, onFinishSet = { setsDone++ }, onStop = {}) }

        onNodeWithText("Get ready").assertExists()
        onNodeWithText("0:07").assertExists()
        onNodeWithText("Set done").assertIsNotEnabled()

        state = EmomState(EmomPhase.Work, 3, 10, 45, 0.25f)
        onNodeWithText("Work").assertExists()
        onNodeWithText("Minute 3 of 10").assertExists()
        onNodeWithText("Two-hand swing · 24 kg × 10").assertExists()
        onNodeWithText("Set done").assertIsEnabled().performClick()
        assertEquals(1, setsDone)

        state = EmomState(EmomPhase.Rest, 3, 10, 20, 0.66f)
        onNodeWithText("Rest").assertExists()
        onNodeWithText("Next: 24 kg × 10").assertExists()
        onNodeWithText("Set done").assertIsNotEnabled()

        state = EmomState(EmomPhase.Done, 10, 10, 0, 1f)
        onNodeWithText("Done").assertExists()
        onNodeWithText("Set done").assertIsNotEnabled()
    }

    @Test
    fun `a one-sided lift alternates sides minute by minute`() = runUiTest {
        val presses = swings.copy(exercise = BuiltInExercise.Press, load = Load.Bell(16.0), reps = 5)
        var state by mutableStateOf(EmomState(EmomPhase.Work, 1, 10, 50, 0.1f))
        setThemedContent(dark = true) { EmomScreen(presses, state, onFinishSet = {}, onStop = {}) }
        onNodeWithText("Press · 16 kg × 5 · Left").assertExists()
        state = EmomState(EmomPhase.Work, 2, 10, 50, 0.1f)
        onNodeWithText("Press · 16 kg × 5 · Right").assertExists()
    }

    @Test
    fun `a ghost does the lift under the ring through work and rest`() = runUiTest {
        var state by mutableStateOf(EmomState(EmomPhase.GetReady, 1, 10, 7, 0.3f))
        setThemedContent(dark = true) { EmomScreen(swings, state, onFinishSet = {}, onStop = {}) }
        onNodeWithContentDescription("A figure doing Two-hand swing").assertIsDisplayed()
        state = EmomState(EmomPhase.Work, 3, 10, 45, 0.25f)
        onNodeWithContentDescription("A figure doing Two-hand swing").assertIsDisplayed()
        state = EmomState(EmomPhase.Rest, 3, 10, 20, 0.66f)
        onNodeWithContentDescription("A figure doing Two-hand swing").assertIsDisplayed()
    }

    @Test
    fun `a lift of the user's own with no figure has no ghost`() = runUiTest {
        val sandbag = CustomExercise("e1", "Sandbag clean", Equipment.Kettlebell, Measure.Reps, false, Focus.FullBody)
        val state = EmomState(EmomPhase.Work, 3, 10, 45, 0.25f)
        setThemedContent(dark = true) {
            EmomScreen(swings.copy(exercise = sandbag), state, onFinishSet = {}, onStop = {})
        }
        onNodeWithText("Sandbag clean · 24 kg × 10").assertExists()
        onNodeWithContentDescription("A figure doing", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Stop asks before ending the EMOM`() = runUiTest {
        var stops = 0
        setThemedContent(dark = true) {
            EmomScreen(
                swings,
                EmomState(EmomPhase.Work, 2, 10, 45, 0.25f),
                onFinishSet = {},
                onStop = { stops++ },
            )
        }
        onNodeWithText("End").performClick()
        onNodeWithText("End EMOM?").assertExists()
        onNodeWithText("Sets completed so far stay in the session.").assertExists()
        assertEquals(0, stops)

        onNodeWithText("Cancel").performClick()
        onNodeWithText("End EMOM?").assertDoesNotExist()
        assertEquals(0, stops)

        onNodeWithText("End").performClick()
        onNode(hasText("End") and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(1, stops)
    }

    @Test
    fun `Space marks the minute's set done while working`() = runUiTest {
        var state by mutableStateOf(EmomState(EmomPhase.GetReady, 1, 10, 7, 0.3f))
        var setsDone = 0
        setThemedContent(dark = true) { EmomScreen(swings, state, onFinishSet = { setsDone++ }, onStop = {}) }

        onNode(isFocused()).performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(0, setsDone)

        state = EmomState(EmomPhase.Work, 1, 10, 50, 0.1f)
        onNode(isFocused()).performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(1, setsDone)
    }
}
