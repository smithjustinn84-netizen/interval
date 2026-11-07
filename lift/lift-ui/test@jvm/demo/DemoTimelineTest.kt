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
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoTimelineTest {
    @Test
    fun `seeking stops the figure where it is sent`() {
        val player = DemoPlayer(progress = 0.2f, playing = true)
        player.seek(0.7f)
        assertFalse(player.playing)
        assertEquals(0.7f, player.progress)
        player.seek(1.4f)
        assertEquals(1f, player.progress)
        player.toggle()
        assertTrue(player.playing)
    }

    @Test
    fun `tapping the timeline stops the figure there`() = runUiTest {
        val player = DemoPlayer(progress = 0f, playing = true)
        setThemedContent { DemoTimeline(GobletSquatDemo, player, "Where in the rep", Modifier.width(400.dp)) }
        onNodeWithContentDescription("Where in the rep").performTouchInput { click(center) }
        assertFalse(player.playing)
        assertTrue(abs(player.progress - 0.5f) < 0.02f, "The tap lands at ${player.progress}")
    }

    @Test
    fun `dragging along the timeline scrubs through the rep`() = runUiTest {
        val player = DemoPlayer(progress = 0f, playing = false)
        setThemedContent { DemoTimeline(GobletSquatDemo, player, "Where in the rep", Modifier.width(400.dp)) }
        onNodeWithContentDescription("Where in the rep").performTouchInput { swipeRight(startX = left + 10f) }
        assertTrue(player.progress > 0.9f, "The drag ends at ${player.progress}")
    }

    @Test
    fun `a screen reader sets where in the rep it is`() = runUiTest {
        val player = DemoPlayer(progress = 0f, playing = true)
        setThemedContent { DemoTimeline(GobletSquatDemo, player, "Where in the rep", Modifier.width(400.dp)) }
        onNodeWithContentDescription("Where in the rep").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.6f)
        }
        assertFalse(player.playing)
        assertEquals(0.6f, player.progress)
    }
}
