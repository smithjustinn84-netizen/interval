@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.PhaseTrack
import dev.justinsmith.interval.ui.RecordingHaptics
import dev.justinsmith.interval.ui.RepPlayer
import dev.justinsmith.interval.ui.WithHaptics
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

    /** The goblet squat's track, 400dp wide, with the haptics it performs recorded. */
    private fun DesktopComposeUiTest.squatTrack(haptics: RecordingHaptics): () -> RepPlayer {
        lateinit var player: RepPlayer
        setThemedContent {
            player = rememberRepPlayer(KettlebellReps.GobletSquat)
            WithHaptics(haptics) {
                PhaseTrack(
                    KettlebellReps.GobletSquat,
                    progress = { player.progress },
                    onScrub = player::hold,
                    label = "Where in the rep",
                    modifier = Modifier.width(400.dp),
                )
            }
        }
        return { player }
    }

    @Test
    fun `a drag ticks once at each checkpoint it comes to`() = runUiTest {
        val haptics = RecordingHaptics()
        squatTrack(haptics)
        val checkpoints = KettlebellReps.GobletSquat.checkpoints
        // A stop between each checkpoint and the next, so no move passes two.
        val stops = listOf(0.05f, 0.2f) + checkpoints.zipWithNext { a, b -> (a + b) / 2 } + 0.9f
        onNodeWithContentDescription("Where in the rep").performTouchInput {
            down(Offset(width * stops.first(), centerY))
            stops.drop(1).forEach { moveTo(Offset(width * it, centerY)) }
            up()
        }
        assertEquals(List(checkpoints.size) { HapticFeedbackType.TextHandleMove }, haptics.performed)
    }

    @Test
    fun `a drag that stops by a checkpoint clicks onto it and ticks once however it wavers there`() = runUiTest {
        val haptics = RecordingHaptics()
        val player = squatTrack(haptics)
        val bottom = KettlebellReps.GobletSquat.checkpoints.first()
        onNodeWithContentDescription("Where in the rep").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * bottom - 3f, centerY))
            moveTo(Offset(width * bottom + 2f, centerY))
            moveTo(Offset(width * bottom - 1f, centerY))
            up()
        }
        assertEquals(bottom, player().progress)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), haptics.performed)
    }

    @Test
    fun `a tap on the track goes where it lands with no tick`() = runUiTest {
        val haptics = RecordingHaptics()
        val player = squatTrack(haptics)
        val bottom = KettlebellReps.GobletSquat.checkpoints.first()
        onNodeWithContentDescription("Where in the rep").performTouchInput {
            click(Offset(width * bottom - 3f, centerY))
        }
        assertTrue(player().progress < bottom, "The tap lands at ${player().progress}")
        assertTrue(haptics.performed.isEmpty())
    }
}
