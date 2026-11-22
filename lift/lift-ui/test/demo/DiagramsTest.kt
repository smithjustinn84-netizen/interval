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
    private val drawn = BuiltInExercise.entries.filter { it.diagram != null }

    @Test
    fun `each flagship lift has its diagram`() {
        mapOf(
            BuiltInExercise.TwoHandSwing to KettlebellReps.Swing,
            BuiltInExercise.OneHandSwing to KettlebellReps.Swing,
            BuiltInExercise.Clean to KettlebellReps.Clean,
            BuiltInExercise.Snatch to KettlebellReps.Snatch,
            BuiltInExercise.Press to KettlebellReps.Press,
            BuiltInExercise.GobletSquat to KettlebellReps.GobletSquat,
            BuiltInExercise.GetUp to KettlebellReps.GetUp,
            BuiltInExercise.CleanAndPress to KettlebellReps.CleanAndPress,
        ).forEach { (exercise, rep) -> assertSame(rep, exercise.diagram, exercise.key) }
        assertEquals(8, drawn.size)
    }

    @Test
    fun `a lift with no diagram yet has none and no cues for one`() {
        assertNull(BuiltInExercise.Row.diagram)
        assertTrue(BuiltInExercise.Row.diagramCues.isEmpty())
    }

    @Test
    fun `a lift with a diagram has a figure too for where the page is not what shows it`() {
        drawn.forEach { assertNotNull(it.demo, it.key) }
    }

    @Test
    fun `every part of a lift's rep belongs to one of its three cues and every cue has a part`() {
        drawn.forEach { lift ->
            val cueOf = lift.diagramCues
            val parts = assertNotNull(lift.diagram).phases.map { it.phase }.toSet()
            assertEquals(parts, cueOf.keys, lift.key)
            assertEquals(setOf(0, 1, 2), cueOf.values.toSet(), lift.key)
        }
    }

    @Test
    fun `one of the user's own takes the diagram of the built-in it names but none of its cues`() {
        val own = CustomExercise(
            uid = "own",
            name = "Pause goblet squat",
            equipment = BuiltInExercise.GobletSquat.equipment,
            measure = BuiltInExercise.GobletSquat.measure,
            perSide = false,
            focus = BuiltInExercise.GobletSquat.focus,
            figure = BuiltInExercise.GobletSquat,
        )
        assertSame(KettlebellReps.GobletSquat, own.diagram)
        assertTrue(own.diagramCues.isEmpty())
    }

    @Test
    fun `a rep that hinges is drawn squatted with its parts where they were and no other is`() {
        drawn.forEach { lift ->
            val rep = assertNotNull(lift.diagram)
            val wrong = rep.doneWrong(Fault.SquattedHinge)
            val hinges = rep.keyframes.any { it.pose == KettlebellPoses.Hinge }
            assertEquals(hinges, wrong != null, lift.key)
            if (wrong != null) {
                assertEquals(rep.phases, wrong.phases, lift.key)
                assertEquals(rep.seconds, wrong.seconds)
                assertTrue(wrong.keyframes.none { it.pose == KettlebellPoses.Hinge }, lift.key)
                assertTrue(wrong.keyframes.any { it.pose == KettlebellPoses.SquattedHinge }, lift.key)
            }
            listOf(Fault.LateHand, Fault.DeathGrip, Fault.BentWrist).forEach { assertNull(rep.doneWrong(it)) }
        }
    }

    @Test
    fun `every lift whose page lists the squatted hinge can show it`() {
        drawn.filter { Fault.SquattedHinge in it.faults }.forEach { lift ->
            assertNotNull(assertNotNull(lift.diagram).doneWrong(Fault.SquattedHinge), lift.key)
        }
    }
}
