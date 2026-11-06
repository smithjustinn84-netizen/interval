package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExerciseDemoTest {
    private val demos = BuiltInExercise.entries.mapNotNull { it.demo }

    @Test
    fun `the swing and the goblet squat are drawn and nothing else yet`() {
        assertSame(TwoHandSwingDemo, BuiltInExercise.TwoHandSwing.demo)
        assertSame(GobletSquatDemo, BuiltInExercise.GobletSquat.demo)
        assertNull(BuiltInExercise.Press.demo)
    }

    @Test
    fun `a rep ends where it starts so it loops without a jump`() {
        demos.forEach { demo ->
            demo.figureAt(0f).joints.zip(demo.figureAt(1f).joints).forEach { (start, end) ->
                assertTrue((start - end).getDistance() < 0.01f, "The rep ends at $end, not $start")
            }
        }
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
    fun `the bell keeps its speed through a keyframe it only passes`() {
        // The snap is a keyframe on the way up, not a stop: the bell is as quick just after it as just before.
        val snap = TwoHandSwingDemo.keyframes[2].at
        val before = (TwoHandSwingDemo.figureAt(snap).bell - TwoHandSwingDemo.figureAt(snap - 0.01f).bell).getDistance()
        val after = (TwoHandSwingDemo.figureAt(snap + 0.01f).bell - TwoHandSwingDemo.figureAt(snap).bell).getDistance()
        assertTrue(before > 3f && after > 3f, "The bell stalls at the snap: $before then $after")
        assertTrue(abs(before - after) < before / 2, "The bell lurches at the snap: $before then $after")
    }

    @Test
    fun `no joint swings past the poses it moves between`() {
        demos.forEach { demo ->
            val keyed = demo.keyframes.map { it.pose.angles().toList() }
            (0..400).map { demo.poseAt(it / 400f).angles().toList() }.forEach { angles ->
                angles.forEachIndexed { joint, angle ->
                    val low = keyed.minOf { it[joint] }
                    val high = keyed.maxOf { it[joint] }
                    assertTrue(angle in low - 0.01f..high + 0.01f, "Joint $joint reaches $angle, past $low..$high")
                }
            }
        }
    }

    @Test
    fun `a pose held between two keyframes stays put`() {
        val bottom = GobletSquatDemo.figureAt(0.52f)
        val held = GobletSquatDemo.figureAt(0.56f)
        assertTrue((bottom.hip - held.hip).getDistance() < 0.01f, "The squat's bottom drifts")
    }

    @Test
    fun `every pose stays in its view with a foot on the floor and nothing through it`() {
        demos.forEach { demo ->
            (0..100).map { demo.figureAt(it / 100f) }.forEach { figure ->
                figure.joints.forEach { joint -> assertTrue(demo.view.contains(joint), "$joint is outside the view") }
                assertTrue(figure.joints.all { it.y <= Rig.FLOOR + 0.5f }, "A joint goes through the floor")
                assertTrue(figure.bell.y + Rig.BELL <= Rig.FLOOR + 0.5f, "The bell goes through the floor")
                val feet = listOf(figure.toe, figure.heel, figure.farToe, figure.farHeel)
                assertTrue(feet.any { abs(it.y - Rig.FLOOR) < 0.5f }, "No foot is on the floor")
                if (demo.root == Root.Ankle) assertEquals(Rig.FLOOR, figure.ankle.y)
            }
        }
    }

    @Test
    fun `the swing floats the bell to chest height and hikes it behind the knees`() {
        val float = TwoHandSwingDemo.figureAt(TwoHandSwingDemo.still)
        val reach = abs(float.bell.y - float.shoulder.y)
        assertTrue(reach < 12f, "The bell floats to ${float.bell}, the shoulder at ${float.shoulder}")
        val hike = TwoHandSwingDemo.figureAt(0f)
        assertTrue(hike.bell.x < hike.knee.x, "The hiked bell is in front of the knee")
    }

    @Test
    fun `a swung bell lags the hands and a held one does not`() {
        val rising = 0.2f
        val rigid = TwoHandSwingDemo.poseAt(rising).figure()
        assertTrue(
            TwoHandSwingDemo.figureAt(rising).bell.y > rigid.bell.y,
            "The bell is level with the arms on the way up",
        )
        val held = GobletSquatDemo.poseAt(rising).figure()
        assertEquals(held.bell, GobletSquatDemo.figureAt(rising).bell)
    }

    @Test
    fun `the swing's bell leaves a trail that ends just behind it`() {
        val trail = TwoHandSwingDemo.trailAt(0.3f)
        assertEquals(ExerciseDemo.TRAIL_STEPS, trail.size)
        val bell = TwoHandSwingDemo.figureAt(0.3f).bell
        val gaps = (trail + bell).zipWithNext { a, b -> (a - b).getDistance() }
        assertTrue(gaps.all { it > 0f && it < 30f }, "The trail's steps are $gaps")
        assertTrue(GobletSquatDemo.trailAt(0.3f).isEmpty(), "A held bell leaves a trail")
    }

    @Test
    fun `a trail at the start of a rep reaches back into the last one`() {
        val trail = TwoHandSwingDemo.trailAt(0.02f)
        val lastRep = TwoHandSwingDemo.figureAt(1f - TwoHandSwingDemo.trail + 0.02f).bell
        assertTrue((lastRep - trail.first()).getDistance() < 0.5f, "The trail starts at ${trail.first()}, not $lastRep")
    }

    @Test
    fun `the goblet squat sits the hips below the knees and keeps the bell at the chest`() {
        val bottom = GobletSquatDemo.figureAt(GobletSquatDemo.still)
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
