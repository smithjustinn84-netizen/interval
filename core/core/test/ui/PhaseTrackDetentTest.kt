package dev.justinsmith.interval.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A drag along a rep's track has a detent at each checkpoint: where it clicks on, and which one it came to. */
class PhaseTrackDetentTest {
    private val checkpoints = listOf(0.25f, 0.5f, 0.8f)

    @Test
    fun `a rep's checkpoints are where its stretches meet and never its ends`() {
        val squat = KettlebellReps.GobletSquat
        assertEquals(squat.spans.dropLast(1).map { it.until }, squat.checkpoints)
        assertEquals(squat.spans.size - 1, squat.checkpoints.size)
        assertTrue(squat.checkpoints.all { it > 0f && it < 1f })
        assertEquals(squat.checkpoints.sorted(), squat.checkpoints)
    }

    @Test
    fun `the stance at a checkpoint is the one the stretch before it is named for`() {
        val squat = KettlebellReps.GobletSquat
        assertEquals(
            listOf(RepPhase.Descend, RepPhase.Bottom, RepPhase.Drive),
            squat.checkpoints.map { squat.phaseAt(it) },
        )
    }

    @Test
    fun `a place within reach of a checkpoint clicks onto it`() {
        assertEquals(0.5f, checkpoints.clickedOnto(0.49f, reach = 0.02f))
        assertEquals(0.5f, checkpoints.clickedOnto(0.52f, reach = 0.02f))
        assertEquals(0.5f, checkpoints.clickedOnto(0.5f, reach = 0.02f))
    }

    @Test
    fun `a place out of reach of every checkpoint stays where it is`() {
        assertEquals(0.4f, checkpoints.clickedOnto(0.4f, reach = 0.02f))
        assertEquals(0.97f, checkpoints.clickedOnto(0.97f, reach = 0.02f))
        assertEquals(0.3f, emptyList<Float>().clickedOnto(0.3f, reach = 0.02f))
    }

    @Test
    fun `between two checkpoints in reach it clicks onto the nearer`() {
        assertEquals(0.5f, listOf(0.48f, 0.5f).clickedOnto(0.495f, reach = 0.05f))
        assertEquals(0.48f, listOf(0.48f, 0.5f).clickedOnto(0.485f, reach = 0.05f))
    }

    @Test
    fun `a drag comes to the checkpoint it reaches or passes going either way`() {
        assertEquals(1, checkpoints.reached(from = 0.4f, to = 0.5f))
        assertEquals(1, checkpoints.reached(from = 0.4f, to = 0.6f))
        assertEquals(1, checkpoints.reached(from = 0.6f, to = 0.5f))
        assertEquals(1, checkpoints.reached(from = 0.6f, to = 0.4f))
    }

    @Test
    fun `a quick drag over two comes to the one nearer where it stops`() {
        assertEquals(2, checkpoints.reached(from = 0.1f, to = 0.9f))
        assertEquals(0, checkpoints.reached(from = 0.9f, to = 0.1f))
    }

    @Test
    fun `leaving a checkpoint or resting on it comes to none`() {
        assertEquals(NO_CHECKPOINT, checkpoints.reached(from = 0.5f, to = 0.6f))
        assertEquals(NO_CHECKPOINT, checkpoints.reached(from = 0.5f, to = 0.4f))
        assertEquals(NO_CHECKPOINT, checkpoints.reached(from = 0.5f, to = 0.5f))
        assertEquals(NO_CHECKPOINT, checkpoints.reached(from = 0.3f, to = 0.4f))
    }

    @Test
    fun `a drag that starts on a checkpoint has come to it`() {
        assertEquals(2, checkpoints.reached(from = Float.NaN, to = 0.8f))
        assertEquals(NO_CHECKPOINT, checkpoints.reached(from = Float.NaN, to = 0.7f))
    }
}
