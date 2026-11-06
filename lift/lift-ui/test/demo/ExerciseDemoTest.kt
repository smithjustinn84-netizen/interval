package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExerciseDemoTest {
    private val demos = listOf(TwoHandSwingDemo, GobletSquatDemo)

    @Test
    fun `the swing and the goblet squat are drawn and nothing else yet`() {
        assertSame(TwoHandSwingDemo, BuiltInExercise.TwoHandSwing.demo)
        assertSame(GobletSquatDemo, BuiltInExercise.GobletSquat.demo)
        assertNull(BuiltInExercise.Press.demo)
    }

    @Test
    fun `a rep ends where it starts so it loops without a jump`() {
        demos.forEach { demo -> assertEquals(demo.poseAt(0f), demo.poseAt(1f)) }
    }

    @Test
    fun `a keyframe is met exactly`() {
        demos.forEach { demo ->
            demo.keyframes.forEach { key -> assertEquals(key.pose, demo.poseAt(key.at)) }
        }
    }

    @Test
    fun `the figure moves smoothly through the rep`() {
        demos.forEach { demo ->
            val steps = 400
            (0 until steps).forEach { i ->
                val a = demo.figureAt(i / steps.toFloat()).bell
                val b = demo.figureAt((i + 1) / steps.toFloat()).bell
                assertTrue((a - b).getDistance() < 6f, "The bell jumps at ${i / steps.toFloat()}")
            }
        }
    }

    @Test
    fun `every pose stays in the drawn box with its feet on the floor`() {
        demos.forEach { demo ->
            (0..100).map { demo.figureAt(it / 100f) }.forEach { figure ->
                listOf(figure.head, figure.hand, figure.bell, figure.hip).forEach { joint ->
                    assertTrue(Rig.view.contains(joint), "$joint is outside the view")
                }
                assertTrue(figure.bell.y + Rig.CHEST_BELL <= Rig.FLOOR, "The bell goes through the floor")
                assertEquals(Rig.FLOOR, figure.ankle.y)
            }
        }
    }

    @Test
    fun `the swing floats the bell to chest height and hikes it behind the knees`() {
        val float = TwoHandSwingDemo.figureAt(0.48f)
        val reach = abs(float.bell.y - float.shoulder.y)
        assertTrue(reach < 12f, "The bell floats to ${float.bell}, the shoulder at ${float.shoulder}")
        val hike = TwoHandSwingDemo.figureAt(0f)
        assertTrue(hike.bell.x < hike.knee.x, "The hiked bell is in front of the knee")
    }

    @Test
    fun `the goblet squat sits the hips below the knees and keeps the bell at the chest`() {
        val bottom = GobletSquatDemo.figureAt(0.56f)
        assertTrue(bottom.hip.y > bottom.knee.y, "The hips stop above the knees")
        (0..20).map { GobletSquatDemo.figureAt(it / 20f) }.forEach { figure ->
            assertTrue(figure.bell.y < figure.hip.y, "The bell drops below the hips")
            assertTrue(figure.bell.x > figure.shoulder.x, "The bell sits behind the chest")
        }
    }

    @Test
    fun `the phases cover the rep and name the cues in their order`() {
        assertEquals(listOf(1, 2, 0), listOf(0.1f, 0.5f, 0.9f).map { TwoHandSwingDemo.phaseAt(it).cue })
        assertEquals(listOf(0, 1, 2, 0), listOf(0.02f, 0.4f, 0.8f, 0.99f).map { GobletSquatDemo.phaseAt(it).cue })
        demos.forEach { demo -> assertEquals(demo.phases.last(), demo.phaseAt(1f)) }
    }
}
