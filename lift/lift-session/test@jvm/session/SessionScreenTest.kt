@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RecordingHaptics
import dev.justinsmith.interval.ui.WithHaptics
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.domain.SetLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The session screen over fixed state: the rest's band at the foot, and the next lift's figure on its card. */
class SessionScreenTest {
    private val free = SessionUiState(free = true)

    @Test
    fun `a rest counts down in a band just over Log set while the tiles keep their shape`() = runUiTest {
        val clocks = SessionClocks(elapsed = "1:40", sinceLast = "0:10", rest = RestState(90, 80, 0.11f))
        setThemedContent(dark = true) { SessionScreen(free, { clocks }, SessionActions()) }
        onNodeWithText("Since last set").assertIsDisplayed()
        val rest = onNodeWithText("Rest").assertIsDisplayed().getUnclippedBoundsInRoot()
        onNodeWithText("1:20").assertIsDisplayed()
        onNodeWithText("Skip").assertIsDisplayed()
        val log = onNodeWithText("Log set").assertIsDisplayed().getUnclippedBoundsInRoot()
        val next = onNodeWithText("Next set").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(rest.top > next.bottom && log.top >= rest.bottom, "the band between the card and Log set")
    }

    @Test
    fun `the last seconds of a rest tick under the thumb`() = runUiTest {
        val haptics = RecordingHaptics()
        var clocks by mutableStateOf(
            SessionClocks(elapsed = "1:40", sinceLast = "0:10", rest = RestState(90, 4, 0.95f)),
        )
        setThemedContent(dark = true) { WithHaptics(haptics) { SessionScreen(free, { clocks }, SessionActions()) } }
        onNodeWithText("0:04").assertIsDisplayed()
        assertTrue(haptics.performed.isEmpty())
        for (left in 3 downTo 1) {
            clocks = SessionClocks(elapsed = "1:40", sinceLast = "0:10", rest = RestState(90, left, 1f - left / 90f))
            waitForIdle()
        }
        onNodeWithText("0:01").assertIsDisplayed()
        assertEquals(List(3) { HapticFeedbackType.SegmentTick }, haptics.performed)
    }

    @Test
    fun `a record set logged while the screen is up lands with two beats`() = runUiTest {
        val haptics = RecordingHaptics()
        val open = LiftSession("s", 0, null, "Swings", emptyList())
        var state by mutableStateOf(free.copy(session = open))
        setThemedContent(dark = true) {
            WithHaptics(haptics) { SessionScreen(state, { SessionClocks() }, SessionActions()) }
        }
        onNodeWithText("Record").assertDoesNotExist()
        val set = SetLog.of(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)
        state = state.copy(session = open.copy(sets = listOf(set)), recordSetIds = setOf(1), latestRecord = set)
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS + Motion.RECORD_BEAT_GAP_MILLIS + Motion.CONTENT_MILLIS)
        onNodeWithText("Record").assertExists()
        assertEquals(listOf(HapticFeedbackType.LongPress, HapticFeedbackType.Confirm), haptics.performed)
    }

    @Test
    fun `a record standing as the screen opens is tagged without a beat`() = runUiTest {
        val haptics = RecordingHaptics()
        val set = SetLog.of(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)
        val state = free.copy(
            session = LiftSession("s", 0, null, "Swings", listOf(set)),
            recordSetIds = setOf(1),
            latestRecord = set,
        )
        setThemedContent(dark = true) {
            WithHaptics(haptics) { SessionScreen(state, { SessionClocks() }, SessionActions()) }
        }
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS + Motion.RECORD_BEAT_GAP_MILLIS)
        onNodeWithText("Record").assertExists()
        assertTrue(haptics.performed.isEmpty(), "beat ${haptics.performed}")
    }

    @Test
    fun `with no rest running the band is gone`() = runUiTest {
        val clocks = SessionClocks(elapsed = "1:40", sinceLast = "0:10")
        setThemedContent(dark = true) { SessionScreen(free, { clocks }, SessionActions()) }
        onNodeWithText("Since last set").assertIsDisplayed()
        onNodeWithText("Rest").assertDoesNotExist()
    }

    @Test
    fun `the Next set card shows a figure doing a built-in lift and none for one of the user's own`() = runUiTest {
        setThemedContent(dark = true) { SessionScreen(free, { SessionClocks() }, SessionActions()) }
        onNodeWithContentDescription("A figure doing Two-hand swing").assertIsDisplayed()
    }

    @Test
    fun `an exercise of the user's own has no figure on the card`() = runUiTest {
        val plank = CustomExercise("e1", "Copenhagen plank", Equipment.Bodyweight, Measure.Time, true, Focus.Core)
        val state = free.copy(draft = SetDraft(exercise = plank))
        setThemedContent(dark = true) { SessionScreen(state, { SessionClocks() }, SessionActions()) }
        onNodeWithText("Copenhagen plank").assertIsDisplayed()
        onNodeWithContentDescription("A figure doing", substring = true).assertDoesNotExist()
    }
}
