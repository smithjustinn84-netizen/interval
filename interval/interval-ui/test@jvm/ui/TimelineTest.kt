@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineTest {
    @Test
    fun `20-round workout keeps individual round peaks on the waveform`() {
        val mvo2 = IntervalConfig(workSeconds = 15, restSeconds = 15, rounds = 20)
        val segments = timelineSegments(mvo2, showPrep = false)
        assertEquals(20, segments.count { it.first == Phase.Work })
        assertEquals(19, segments.count { it.first == Phase.Rest })
    }

    @Test
    fun `very high round counts fold into block shares`() {
        val marathon = IntervalConfig(workSeconds = 15, restSeconds = 15, rounds = 60)
        val segments = timelineSegments(marathon, showPrep = false)
        assertEquals(listOf(Phase.Work to 900, Phase.Rest to 885), segments)
    }

    @Test
    fun `timeline and mini timeline compose for single and multi-block configs`() = runUiTest {
        val sequence = IntervalConfig(
            workSeconds = 30,
            restSeconds = 15,
            rounds = 4,
            then = listOf(Block(workSeconds = 45, restSeconds = 15, rounds = 3)),
            blockRestSeconds = 60,
            warmUpSeconds = 120,
            coolDownSeconds = 120,
        )
        assertTrue(timelineSegments(sequence, showPrep = true).any { it.first == Phase.WarmUp })
        setThemedContent {
            Timeline(sequence)
            MiniTimeline(sequence)
        }
        waitForIdle()
    }
}
