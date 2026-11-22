@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.PhaseTrack
import dev.justinsmith.interval.ui.RepPlayer
import dev.justinsmith.interval.ui.rememberRepPlayer
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoTimelineTest {
    @Test
    fun `holding stops the player where it is sent and toggle resumes`() = runUiTest {
        lateinit var player: RepPlayer
        setThemedContent { player = rememberRepPlayer(KettlebellReps.GobletSquat) }
        player.hold(0.7f)
        assertFalse(player.playing)
        assertEquals(0.7f, player.progress)
        player.hold(1.4f)
        assertEquals(1f, player.progress)
        player.toggle()
        assertTrue(player.playing)
    }

    @Test
    fun `a player that sets up goes back to set-up on replaySetUp`() = runUiTest {
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        lateinit var player: RepPlayer
        setThemedContent { player = rememberRepPlayer(airSquat) }
        player.hold(-3f)
        assertEquals(-1f, player.progress)
        player.hold(0.4f)
        player.replaySetUp()
        assertEquals(-1f, player.progress)
        assertTrue(player.playing)
    }

    @Test
    fun `tapping the phase track holds the rep there`() = runUiTest {
        lateinit var player: RepPlayer
        setThemedContent {
            player = rememberRepPlayer(KettlebellReps.GobletSquat)
            PhaseTrack(
                KettlebellReps.GobletSquat,
                progress = { player.progress },
                onScrub = player::hold,
                label = "Where in the rep",
                modifier = Modifier.width(400.dp),
            )
        }
        onNodeWithContentDescription("Where in the rep").performTouchInput { click(center) }
        assertFalse(player.playing)
        assertTrue(abs(player.progress - 0.5f) < 0.02f, "The tap lands at ${player.progress}")
    }

    @Test
    fun `dragging along the phase track scrubs through the rep`() = runUiTest {
        lateinit var player: RepPlayer
        setThemedContent {
            player = rememberRepPlayer(KettlebellReps.GobletSquat)
            PhaseTrack(
                KettlebellReps.GobletSquat,
                progress = { player.progress },
                onScrub = player::hold,
                label = "Where in the rep",
                modifier = Modifier.width(400.dp),
            )
        }
        onNodeWithContentDescription("Where in the rep").performTouchInput { swipeRight(startX = left + 10f) }
        assertTrue(player.progress > 0.9f, "The drag ends at ${player.progress}")
    }

    @Test
    fun `a screen reader sets where in the rep it is`() = runUiTest {
        lateinit var player: RepPlayer
        setThemedContent {
            player = rememberRepPlayer(KettlebellReps.GobletSquat)
            PhaseTrack(
                KettlebellReps.GobletSquat,
                progress = { player.progress },
                onScrub = player::hold,
                label = "Where in the rep",
                modifier = Modifier.width(400.dp),
            )
        }
        onNodeWithContentDescription("Where in the rep").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.6f)
        }
        assertFalse(player.playing)
        assertEquals(0.6f, player.progress)
    }
}
