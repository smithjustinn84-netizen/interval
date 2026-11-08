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
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseFigureTest {
    @Test
    fun `a moving figure is described for a screen reader`() = runUiTest {
        setThemedContent {
            ExerciseFigure(
                TwoHandSwingDemo,
                rememberDemoPlayer(TwoHandSwingDemo),
                contentDescription = "Two-hand swing",
                modifier = Modifier.size(200.dp),
            )
        }
        onNodeWithContentDescription("Two-hand swing").assertIsDisplayed()
    }

    @Test
    fun `a figure draws one bell or two or leaning on a bench or a band at any moment`() = runUiTest {
        val demos = listOf(
            GetUpDemo,
            ArmorBuildingComplexDemo,
            RowDemo,
            HaloDemo,
            SnatchDemo,
            FarmersWalkDemo,
            BandChestPressDemo,
            BandClamshellDemo,
        )
        setThemedContent {
            Column {
                demos.forEach { demo ->
                    listOf(0f, 0.3f, 0.6f).forEach { at ->
                        ExerciseFigure(demo, { at }, contentDescription = null, modifier = Modifier.size(60.dp))
                    }
                }
            }
        }
        onRoot().captureToImage()
    }

    @Test
    fun `a figure given a record raises the bell with its rep held and then goes on`() = runUiTest {
        var record by mutableStateOf<Long?>(null)
        lateinit var player: DemoPlayer
        setThemedContent {
            player = rememberDemoPlayer(TwoHandSwingDemo)
            ExerciseFigure(TwoHandSwingDemo, player, "Two-hand swing", Modifier.size(200.dp), celebrating = record)
        }
        mainClock.autoAdvance = false
        assertTrue(player.playing)
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
        record = 1L
        settle()
        onNode(celebrating("Raising the bell")).assertExists()
        assertFalse(player.playing)
        mainClock.advanceTimeBy(Celebration.MILLIS.toLong())
        settle()
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
        assertTrue(player.playing)
    }

    @Test
    fun `a figure with nothing in hand takes a bow and one appearing on the record joins in`() = runUiTest {
        // Held before the figure appears: left to run, the clock would play the whole gesture out as it settles.
        mainClock.autoAdvance = false
        setThemedContent {
            ExerciseFigure(
                AirSquatDemo,
                rememberDemoPlayer(AirSquatDemo),
                "Air squat",
                Modifier.size(200.dp),
                celebrating = 3L,
            )
        }
        settle()
        onNode(celebrating("Taking a bow")).assertExists()
        onNode(celebrating("Raising the bell")).assertDoesNotExist()
    }

    @Test
    fun `a figure not on its feet lets a record pass`() = runUiTest {
        lateinit var player: DemoPlayer
        mainClock.autoAdvance = false
        setThemedContent {
            player = rememberDemoPlayer(PushUpDemo)
            ExerciseFigure(PushUpDemo, player, "Push-up", Modifier.size(200.dp), celebrating = 3L)
        }
        settle()
        onNode(celebrating("Taking a bow")).assertDoesNotExist()
        assertTrue(player.playing)
    }

    private fun celebrating(words: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, words)

    /** A few frames, for a change of key to reach the figure's effect and its first frame to draw. */
    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }
}
