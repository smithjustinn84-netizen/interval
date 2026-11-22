package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.ui.BodyJoints
import dev.justinsmith.interval.ui.ExerciseDemoColors
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.RepBeat
import dev.justinsmith.interval.ui.RepFacing
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepRoot
import dev.justinsmith.interval.ui.breathAt
import dev.justinsmith.interval.ui.placeBetween
import dev.justinsmith.interval.ui.pulseAt
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Measure
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExerciseDemoTest {
    private val reps = BuiltInExercise.entries.mapNotNull { it.rep }

    @Test
    fun `every built-in exercise has a unified rep`() {
        assertSame(KettlebellReps.Swing, BuiltInExercise.TwoHandSwing.rep)
        BuiltInExercise.entries.forEach { exercise -> assertNotNull(exercise.rep, "$exercise has no rep") }
    }

    @Test
    fun `an exercise of the user's own inherits the rep of the built-in it names`() {
        val squat =
            CustomExercise("a1", "Pause goblet squat", Equipment.Kettlebell, Measure.Reps, false, Focus.LowerBody)
        assertNull(squat.rep)
        assertSame(BuiltInExercise.GobletSquat.rep, squat.copy(figure = BuiltInExercise.GobletSquat).rep)
    }

    @Test
    fun `the breath loops with the rep and runs on from the set-up`() {
        reps.forEach { rep ->
            assertEquals(rep.breathAt(0f), rep.breathAt(1f), 0.001f)
            assertEquals(0f, rep.breathAt(0f), 0.001f)
            (0..40).forEach { step -> assertTrue(rep.breathAt(rep.earliest + step / 20f) in -1f..1f) }
        }
        val swing = (1..99).map { KettlebellReps.Swing.breathAt(it / 100f) }
        assertEquals(1, swing.zipWithNext().count { (a, b) -> a > 0f && b <= 0f })
        val plank = (1..99).map { PlankDemo.breathAt(it / 100f) }
        assertTrue(plank.zipWithNext().count { (a, b) -> a > 0f && b <= 0f } >= 1)
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        assertTrue(abs(airSquat.breathAt(-0.01f)) < 0.05f, "The set-up's breath jumps into the rep's")
    }

    @Test
    fun `the drive pulses as it starts and is spent before the rep is`() {
        assertEquals(1f, KettlebellReps.Swing.pulseAt(0f))
        assertTrue(KettlebellReps.Swing.pulseAt(0.05f) in 0.2f..0.95f)
        assertEquals(0f, KettlebellReps.Swing.pulseAt(0.5f), "The float pulses")
        assertEquals(0f, KettlebellReps.Swing.pulseAt(0.9f), "The hike back pulses")
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        assertEquals(0f, airSquat.pulseAt(-0.5f), "The set-up pulses")
        reps.forEach { rep ->
            (0..100).forEach { step ->
                val at = step / 100f
                val pulse = rep.pulseAt(at)
                assertTrue(pulse in 0f..1f)
                if (rep.beatAt(at) != RepBeat.Drive) assertEquals(0f, pulse, "A pulse off the drive at $at")
            }
        }
    }

    @Test
    fun `the short way between two places goes round the rep's end and never through the set-up`() {
        assertEquals(0.3f, placeBetween(0.2f, 0.4f, 0.5f), 0.001f)
        assertEquals(0.3f, placeBetween(0.4f, 0.2f, 0.5f), 0.001f)
        assertEquals(0f, placeBetween(0.9f, 0.1f, 0.5f), 0.001f)
        assertEquals(0.95f, placeBetween(0.9f, 0.1f, 0.25f), 0.001f)
        assertEquals(0.95f, placeBetween(0.1f, 0.9f, 0.75f), 0.001f)
        assertEquals(0.15f, placeBetween(-0.5f, 0.8f, 0.5f), 0.001f)
    }

    @Test
    fun `a rep ends where it starts so it loops without a jump`() {
        BuiltInExercise.entries.forEach { exercise ->
            val rep = exercise.rep ?: return@forEach
            val start = rep.jointsAt(0f)
            val end = rep.jointsAt(1f)
            allJoints(start).zip(allJoints(end)).forEach { (a, b) ->
                assertTrue((a - b).getDistance() < 0.002f, "$exercise ends at $b, not $a")
            }
        }
    }

    @Test
    fun `every rep moves smoothly without sudden jumps`() {
        BuiltInExercise.entries.forEach { exercise ->
            val rep = exercise.rep ?: return@forEach
            val steps = (rep.seconds * FRAMES_PER_SECOND).toInt().coerceAtLeast(100)
            (0 until steps).forEach { i ->
                val a = rep.jointsAt(i / steps.toFloat()).kettlebellCenter
                val b = rep.jointsAt((i + 1) / steps.toFloat()).kettlebellCenter
                assertTrue((a - b).getDistance() < 0.08f, "$exercise's bell jumps at ${i / steps.toFloat()}")
            }
        }
    }

    @Test
    fun `every side and front pose keeps joints above the floor`() {
        BuiltInExercise.entries.forEach { exercise ->
            val rep = exercise.rep ?: return@forEach
            if (rep.facing == RepFacing.Above) return@forEach
            (0..100).forEach { step ->
                val at = "$exercise at ${step / 100f}"
                val j = rep.jointsAt(step / 100f)
                val drawn = if (rep.hold == RepHold.Pair) allJoints(j) else allJoints(j) - j.farBellCenter
                drawn.forEach { joint ->
                    assertTrue(joint.y >= -0.06f, "$at: $joint dips below the floor")
                }
            }
        }
    }

    @Test
    fun `a figure seen from the front lies down when turned and foreshortens legs and trunk`() {
        val pose = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)
        val lying = pose.copy(turn = -90f).toBodyPose().joints(root = RepRoot.Hips, facing = RepFacing.Front)
        assertTrue(lying.headCenter.x < lying.hips.x - 0.3f && lying.ankle.x > lying.hips.x + 0.3f, "Not lying")
        assertEquals(lying.hip.x, lying.farHip.x, 0.002f)
        assertTrue(lying.hip.y > lying.farHip.y, "Near side is not uppermost in Y-up")
        val standing = pose.toBodyPose().joints(facing = RepFacing.Front)
        val short = pose.copy(legs = 0.5f, farLegs = 1f, trunk = 0.5f).toBodyPose().joints(facing = RepFacing.Front)
        assertEquals(
            (standing.hip - standing.ankle).getDistance() / 2f,
            (short.hip - short.ankle).getDistance(),
            0.002f,
        )
        assertEquals(
            (standing.neck - standing.hips).getDistance() / 2f,
            (short.neck - short.hips).getDistance(),
            0.002f,
        )
    }

    @Test
    fun `hands round behind a figure seen from the front are marked behind`() {
        val pose = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)
        assertFalse(pose.toBodyPose().joints(facing = RepFacing.Front).armsBehind)
        assertTrue(pose.copy(behind = 0.8f).toBodyPose().joints(facing = RepFacing.Front).armsBehind)
        assertFalse(pose.copy(behind = 0.8f).toBodyPose().joints().front)
    }

    @Test
    fun `setting up holds the rep's first pose with only the set-up lit`() {
        reps.filter { it.setUp != null }.forEach { rep ->
            val setUp = assertNotNull(rep.setUp)
            listOf(-1f, -0.5f, -0.01f).forEach { at ->
                assertEquals(rep.jointsAt(0f).hip, rep.jointsAt(at).hip)
                assertEquals(setUp.cue, rep.cueAt(at))
                assertEquals(RepBeat.Set, rep.beatAt(at))
            }
            assertTrue(rep.spans.none { it.cueIndex == setUp.cue }, "The rep lights the set-up's cue")
            assertEquals(ExerciseRep.SETTING_UP, rep.startOf(setUp.cue))
        }
    }

    @Test
    fun `played on a rep sets up once then loops its reps`() {
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        val setUp = assertNotNull(airSquat.setUp)
        assertEquals(-0.5f, airSquat.after(-1f, setUp.seconds / 2), 0.001f)
        assertEquals(0.25f, airSquat.after(-1f, setUp.seconds + airSquat.seconds / 4), 0.001f)
        assertEquals(0.25f, airSquat.after(0.75f, airSquat.seconds / 2), 0.001f)
        assertEquals(0.1f, KettlebellReps.Swing.after(0f, KettlebellReps.Swing.seconds * 0.1f), 0.001f)
    }

    @Test
    fun `the spans run end to end over the whole rep`() {
        reps.forEach { rep ->
            val spans = rep.spans
            assertTrue(spans.isNotEmpty())
            assertEquals(0f, spans.first().from, 0.001f)
            spans.zipWithNext().forEach { (a, b) -> assertEquals(a.until, b.from, 0.001f) }
            assertEquals(1f, spans.last().until, 0.001f)
        }
    }

    @Test
    fun `the beat colour glides across a phase change and settles on the new beat`() {
        val colors = ExerciseDemoColors(
            body = Color.White,
            far = Color.Gray,
            joint = Color.Black,
            reference = Color.DarkGray,
            bell = Color.Red,
            set = Color.Yellow,
            drive = Color.Red,
            ease = Color.Green,
        )
        val boundary = KettlebellReps.Swing.spans.first().until
        val blended = colors.at(KettlebellReps.Swing, boundary + 0.02f)
        assertTrue(blended != Color.Red && blended != Color.Green, "Expected blend, got $blended")
        assertEquals(Color.Green, colors.at(KettlebellReps.Swing, boundary + 0.1f))
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        assertEquals(Color.Yellow, colors.at(airSquat, -0.5f))
    }

    private fun allJoints(j: BodyJoints) = listOf(
        j.toe, j.heel, j.ankle, j.knee, j.hip, j.shoulder, j.headCenter, j.elbow, j.wrist,
        j.kettlebellAnchor, j.kettlebellCenter, j.farToe, j.farHeel, j.farAnkle, j.farKnee,
        j.farElbow, j.farWrist, j.farBellAnchor, j.farBellCenter, j.farHip, j.farShoulder,
        j.fingers, j.farFingers,
    )
}

private const val FRAMES_PER_SECOND = 120
