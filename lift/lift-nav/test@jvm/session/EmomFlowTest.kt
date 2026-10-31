@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An EMOM in a session, end to end. The EMOM's page over fixed state is lift-session's `EmomScreenTest`. */
class EmomFlowTest {
    @Test
    fun `an EMOM counts in, works the minute and rests it out`() = liftAppTest { app ->
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
