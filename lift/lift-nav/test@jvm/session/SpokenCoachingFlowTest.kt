@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.UI_TEST_TIMEOUT
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lift's timers speak, through the process's speaker, behind the voice switch: what a session hears. */
class SpokenCoachingFlowTest {
    private val swingSetUp = "Set the bell a foot in front, hinge at the hips and hike it high between your legs"
    private val swingFault = "Hips back, shins stay vertical"
    private val timeout = UI_TEST_TIMEOUT.inWholeMilliseconds

    /** Swings done at the sheet's 16 kg the day before, so the bell is no new one to groove light first at. */
    private fun swungBefore() = FakeLiftStorage().apply {
        val at = -DAY_MILLIS
        sessions.sets.value += SetEntity(0, "before", BuiltInExercise.TwoHandSwing.key, 16.0, 10, null, at)
        sessions.sessions.value += SessionEntity("before", at - HOUR_MILLIS, at, "Swings", at)
    }

    @Test
    fun `an EMOM's count-in says one of the lift's cues`() = liftAppTest(swungBefore()) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNodeWithText("An EMOM").performClick()
        waitForText("Start EMOM")
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Get ready")

        app.clock.set(app.clock.get() + 2_000)
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

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS
