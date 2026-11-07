@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.UI_TEST_TIMEOUT
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lift's timers speak, through the process's speaker, behind the voice switch: what a session hears. */
class SpokenCoachingFlowTest {
    private val swingSetUp = "Set the bell a foot in front, hinge at the hips and hike it high between your legs"
    private val swingFault = "Hips back, shins stay vertical"
    private val timeout = UI_TEST_TIMEOUT.inWholeMilliseconds

    @Test
    fun `an EMOM's count-in says one of the lift's cues`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNodeWithText("An EMOM").performClick()
        waitForText("Start EMOM")
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Get ready")

        app.clock.set(2_000)
        waitUntil(timeoutMillis = timeout) { app.speaker.lines.value.isNotEmpty() }
        assertEquals(listOf(swingSetUp), app.speaker.lines.value)
    }

    @Test
    fun `a rest says a fault of the set just done then the next lift's set-up near its end`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        // The Next set card shows what the rest will say of the swing.
        onNodeWithText("Watch for · $swingFault").assertExists()
        onNodeWithText("Log set").performClick()
        waitForText("Rest")
        waitUntil(timeoutMillis = timeout) { app.speaker.lines.value == listOf(swingFault) }

        app.clock.set((RestClock.DEFAULT_SECONDS - RestClock.SET_UP_LEAD_SECONDS + 1) * 1_000L)
        waitUntil(timeoutMillis = timeout) { app.speaker.lines.value.size == 2 }
        assertEquals(listOf(swingFault, swingSetUp), app.speaker.lines.value)
    }

    @Test
    fun `nothing is said with the voice switched off`() {
        val intervals = FakeIntervalActivity().apply { voice.value = false }
        liftAppTest(intervals = intervals) { app ->
            onNodeWithText("Start session").performClick()
            waitForText("Log set")
            onNodeWithText("Log set").performClick()
            waitForText("Rest")
            app.clock.set(RestClock.DEFAULT_SECONDS * 1_000L)
            waitForText("Log set")
            assertEquals(emptyList(), app.speaker.lines.value)
        }
    }
}
