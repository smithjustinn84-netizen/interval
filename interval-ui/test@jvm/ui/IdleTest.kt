@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test

class IdleTest {
    @Test
    fun `the app goes idle without input and wakes at a touch`() = runUiTest {
        setThemedContent {
            WatchForIdle {
                val idle = LocalIdle.current
                Text(if (idle()) "Idle" else "Active", Modifier.fillMaxSize())
            }
        }
        mainClock.autoAdvance = false
        onNodeWithText("Active").assertExists()
        mainClock.advanceTimeBy(Motion.IDLE_MILLIS / 2)
        onRoot().performTouchInput { click() }
        mainClock.advanceTimeBy(Motion.IDLE_MILLIS / 2 + Motion.IDLE_MILLIS / 4)
        onNodeWithText("Active").assertExists()
        mainClock.advanceTimeBy(Motion.IDLE_MILLIS)
        onNodeWithText("Idle").assertExists()
        onRoot().performTouchInput { click() }
        mainClock.advanceTimeByFrame()
        onNodeWithText("Active").assertExists()
    }

    @Test
    fun `nothing is idle where nothing watches`() = runUiTest {
        setThemedContent {
            val idle = LocalIdle.current
            Text(if (idle()) "Idle" else "Active")
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(Motion.IDLE_MILLIS * 2)
        onNodeWithText("Active").assertExists()
    }
}
