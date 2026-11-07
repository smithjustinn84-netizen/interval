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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `the countdown pulse rises with each last second and falls back`() = runUiTest {
        mainClock.autoAdvance = false
        var text by mutableStateOf("0:05")
        var pulse = -1f
        setThemedContent { pulse = rememberCountdownPulse(text, countingDown = text <= "0:03") }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(0f, pulse)
        text = "0:03"
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(pulse > 0.5f, "pulse $pulse")
        mainClock.advanceTimeBy(Motion.CLOCK_POP_MILLIS.toLong() + SETTLE_MILLIS)
        assertEquals(0f, pulse)
    }

    @Test
    fun `a count-up starts from nothing and lands on its value unless disabled`() = runUiTest {
        mainClock.autoAdvance = false
        var counted = -1
        var plain = -1
        setThemedContent {
            counted = countUp(40)
            plain = countUp(40, enabled = false)
        }
        mainClock.advanceTimeByFrame()
        assertEquals(40, plain)
        assertTrue(counted < 40, "counted $counted")
        mainClock.advanceTimeBy(Motion.SUMMARY_RING_MILLIS.toLong() + SETTLE_MILLIS)
        assertEquals(40, counted)
    }

    @Test
    fun `a streak that grew rolls up from the week before`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent { StreakBadge(3, label = { "$it-week streak" }, grew = true) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("2-week streak").assertExists()
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS.toLong() + Motion.CONTENT_MILLIS + SETTLE_MILLIS)
        onNodeWithText("3-week streak").assertExists()
        onNodeWithText("2-week streak").assertDoesNotExist()
    }
}

/** A little past an animation's end, so its last frame has drawn. */
private const val SETTLE_MILLIS = 100L
