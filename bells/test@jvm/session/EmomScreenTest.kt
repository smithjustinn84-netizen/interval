@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomCue
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.setBellsContent
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val swings =
    EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, minutes = 10, firstSide = Side.Left, startedAtMillis = 0)

class EmomScreenTest {
    @Test
    fun `each phase shows its word and only work takes Set done`() = runUiTest {
        var state by mutableStateOf(EmomState(EmomPhase.GetReady, 1, 10, 7, 0.3f))
        var setsDone = 0
        setBellsContent { EmomScreen(swings, state, onFinishSet = { setsDone++ }, onStop = {}) }

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
        val presses = swings.copy(exercise = BuiltInExercise.Press, bellKg = 16.0, reps = 5)
        var state by mutableStateOf(EmomState(EmomPhase.Work, 1, 10, 50, 0.1f))
        setBellsContent { EmomScreen(presses, state, onFinishSet = {}, onStop = {}) }
        onNodeWithText("Press · 16 kg × 5 · Left").assertExists()
        state = EmomState(EmomPhase.Work, 2, 10, 50, 0.1f)
        onNodeWithText("Press · 16 kg × 5 · Right").assertExists()
    }

    @Test
    fun `an EMOM counts in, works the minute and rests it out`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNodeWithText("An EMOM").performClick()
        waitForText("Start EMOM")
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Get ready")
        onNodeWithText("Set done").assertIsNotEnabled()

        app.clock.set(10_000)
        waitForText("Work")
        onNodeWithText("Set done").performClick()
        waitForText("Rest")
        assertEquals(1, app.storage.sessions.sets.value.size)

        // Past the last minute Done holds for a beat; then the EMOM ends and the session shows what it logged.
        app.clock.set(10_000 + 10 * 60_000L)
        waitForText("Done")
        onNodeWithText("Set done").assertIsNotEnabled()
        app.clock.set(10_000 + 10 * 60_000L + EmomClock.DONE_HOLD_MILLIS)
        waitForText("Finish")
        onNodeWithText("16 kg × 10").assertExists()
        assertTrue(EmomCue.Start in app.beeper.cues)
    }
}
