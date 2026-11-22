package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.KettlebellPoses
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.domain.faults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiagramsTest {
    private val namedPhaseLifts = BuiltInExercise.entries.filter { it.rep?.phases?.isNotEmpty() == true }

    @Test
    fun `each flagship lift uses its canonical KettlebellRep`() {
        mapOf(
            BuiltInExercise.TwoHandSwing to KettlebellReps.Swing,
            BuiltInExercise.OneHandSwing to KettlebellReps.OneHandSwing,
            BuiltInExercise.Clean to KettlebellReps.Clean,
            BuiltInExercise.Snatch to KettlebellReps.Snatch,
            BuiltInExercise.Press to KettlebellReps.Press,
            BuiltInExercise.GobletSquat to KettlebellReps.GobletSquat,
            BuiltInExercise.GetUp to KettlebellReps.GetUp,
            BuiltInExercise.CleanAndPress to KettlebellReps.CleanAndPress,
        ).forEach { (exercise, rep) -> assertSame(rep, exercise.rep, exercise.key) }
        assertEquals(19, namedPhaseLifts.size)
    }

    @Test
    fun `every built-in exercise has a unified rep`() {
        BuiltInExercise.entries.forEach { assertNotNull(it.rep, it.key) }
    }

    @Test
    fun `every named phase of a kettlebell lift belongs to one of its written cues`() {
        namedPhaseLifts.forEach { lift ->
            val cueOf = lift.repCues
            val parts = assertNotNull(lift.rep).phases.map { it.phase }.toSet()
            assertEquals(parts, cueOf.keys, lift.key)
            assertTrue(cueOf.values.all { it in 0..2 }, lift.key)
        }
    }

    @Test
    fun `one of the user's own takes the rep of the built-in it names but none of its cues`() {
        val own = CustomExercise(
            uid = "own",
            name = "Pause goblet squat",
            equipment = BuiltInExercise.GobletSquat.equipment,
            measure = BuiltInExercise.GobletSquat.measure,
            perSide = false,
            focus = BuiltInExercise.GobletSquat.focus,
            figure = BuiltInExercise.GobletSquat,
        )
        assertSame(KettlebellReps.GobletSquat, own.rep)
        assertTrue(own.repCues.isEmpty())
    }

    @Test
    fun `a flagship rep that hinges is drawn squatted with its parts preserved`() {
        namedPhaseLifts.forEach { lift ->
            val rep = assertNotNull(lift.rep)
            val wrong = rep.doneWrong(Fault.SquattedHinge)
            if (Fault.SquattedHinge in lift.faults) {
                assertNotNull(wrong, lift.key)
                assertEquals(rep.phases, wrong.phases, lift.key)
                assertEquals(rep.seconds, wrong.seconds)
                if (rep.keyframes.any {
                        it.pose.torsoAngleDeg == KettlebellPoses.Hinge.torsoAngleDeg &&
                            it.pose.hipAngleDeg == KettlebellPoses.Hinge.hipAngleDeg
                    }
                ) {
                    assertTrue(
                        wrong.keyframes.any { it.pose.torsoAngleDeg == KettlebellPoses.SquattedHinge.torsoAngleDeg },
                        lift.key,
                    )
                }
            }
            listOf(Fault.LateHand, Fault.DeathGrip, Fault.BentWrist).forEach { assertNull(rep.doneWrong(it)) }
        }
    }
}
