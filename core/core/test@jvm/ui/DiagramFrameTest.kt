package dev.justinsmith.interval.ui

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A diagram that runs works a stance out every frame, so it must make nothing doing it: what the canvas asks of a
 * rep each frame ([ExerciseRep.anglesAt], [BodyFrame.place], [ExerciseRep.phaseAt]), counted by the JVM's own tally
 * of what the thread has allocated.
 */
class DiagramFrameTest {
    private val memory = ManagementFactory.getThreadMXBean() as ThreadMXBean

    /** [frames] of [rep], one after another round it, as the canvas works them out; what it hands back is no matter. */
    private fun run(rep: ExerciseRep, angles: FloatArray, frame: BodyFrame, frames: Int): Float {
        var reach = 0f
        repeat(frames) { index ->
            val at = index % ROUND / ROUND.toFloat()
            rep.anglesAt(at, angles)
            frame.place(angles)
            if (rep.phaseAt(at) != null && frame.showsFarSide) reach += frame.farToe.x
            reach += frame.kettlebellCenter.x
        }
        return reach
    }

    @Test
    fun `a frame of a running diagram makes nothing`() {
        // A swing, a get-up (its far side out, a hand on the floor) and a knee down, whose foot is laid along it.
        listOf(KettlebellReps.Swing, KettlebellReps.GetUp, KettlebellReps.HalfKneelingPress).forEach { rep ->
            val angles = FloatArray(BodyPose.JOINTS)
            val frame = BodyFrame()
            run(rep, angles, frame, FRAMES)
            val before = memory.currentThreadAllocatedBytes
            run(rep, angles, frame, FRAMES)
            val made = memory.currentThreadAllocatedBytes - before
            // The least a frame could make is an object's header, 16 bytes: under a byte a frame is none.
            assertTrue(made < FRAMES, "$FRAMES frames of a ${rep.seconds} s rep made $made bytes")
        }
    }

    private companion object {
        const val FRAMES = 20_000
        const val ROUND = 997
    }
}
