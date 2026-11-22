@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Celebration
import dev.justinsmith.interval.ui.DemoDisplayMode
import dev.justinsmith.interval.ui.ExerciseDemoCanvas
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RepPlayer
import dev.justinsmith.interval.ui.rememberRepPlayer
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseFigureTest {
    @Test
    fun `a compact session band is described for a screen reader`() = runUiTest {
        setThemedContent {
            ExerciseBand(
                KettlebellReps.Swing,
                contentDescription = "Two-hand swing",
                onClick = {},
                onClickLabel = "How to",
            )
        }
        onNodeWithContentDescription("Two-hand swing").assertIsDisplayed()
    }

    @Test
    fun `the unified canvas draws bells bars benches bands front and above views at any moment`() = runUiTest {
        val reps = listOf(
            KettlebellReps.GetUp,
            ArmorBuildingComplexRep,
            RowDemo,
            HaloDemo,
            KettlebellReps.Snatch,
            FarmersWalkDemo,
            BandChestPressDemo,
            BandClamshellDemo,
            BarbellBackSquatDemo,
            HipEscapeDemo,
        )
        setThemedContent {
            Column {
                reps.forEach { rep ->
                    listOf(DemoDisplayMode.Detail, DemoDisplayMode.Compact, DemoDisplayMode.Thumb).forEach { mode ->
                        ExerciseDemoCanvas(
                            rep,
                            Modifier.size(60.dp),
                            progress = 0.3f,
                            mode = mode,
                        )
                    }
                }
            }
        }
        onRoot().captureToImage()
    }

    @Test
    fun `a compact canvas given a record raises the bell then resumes`() = runUiTest {
        var record by mutableStateOf<Long?>(null)
        setThemedContent {
            ExerciseDemoCanvas(
                KettlebellReps.Swing,
                Modifier.size(200.dp),
                contentDescription = "Two-hand swing",
                mode = DemoDisplayMode.Compact,
                celebrating = record,
            )
        }
        mainClock.autoAdvance = false
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
        record = 1L
        settle()
        onNode(celebrating("Raising the bell")).assertExists()
        mainClock.advanceTimeBy(Celebration.MILLIS.toLong())
        settle()
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
    }

    @Test
    fun `an unweighted standing rep takes a bow and one appearing on a record joins in`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent {
            ExerciseDemoCanvas(
                AirSquatDemo,
                Modifier.size(200.dp),
                contentDescription = "Air squat",
                mode = DemoDisplayMode.Compact,
                celebrating = 3L,
            )
        }
        settle()
        onNode(celebrating("Taking a bow")).assertExists()
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
    }

    @Test
    fun `a rep not on its feet lets a record pass`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent {
            ExerciseDemoCanvas(
                PushUpDemo,
                Modifier.size(200.dp),
                contentDescription = "Push-up",
                mode = DemoDisplayMode.Compact,
                celebrating = 3L,
            )
        }
        settle()
        onNode(celebrating("Taking a bow")).assertDoesNotExist()
    }

    @Test
    fun `a player shown a place glides there smoothly rather than jumping`() = runUiTest {
        lateinit var player: RepPlayer
        setThemedContent {
            player = rememberRepPlayer(KettlebellReps.GobletSquat)
            ExerciseDemoCanvas(
                KettlebellReps.GobletSquat,
                Modifier.size(200.dp),
                progress = player.heldAt,
                position = player.position,
            )
        }
        player.hold(0.2f)
        waitForIdle()
        mainClock.autoAdvance = false
        player.show(0.6f)
        settle()
        mainClock.advanceTimeBy(Motion.CONTENT_MILLIS / 2L)
        assertTrue(player.progress > 0.2f && player.progress < 0.6f, "Partway, it is at ${player.progress}")
        assertFalse(player.playing)
        mainClock.advanceTimeBy(Motion.CONTENT_MILLIS.toLong())
        settle()
        assertEquals(0.6f, player.progress)
    }

    private fun celebrating(words: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, words)

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }
}
