package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class DiagramsTest {
    @Test
    fun `each flagship lift is found by its key`() {
        mapOf(
            BuiltInExercise.TwoHandSwing to KettlebellReps.Swing,
            BuiltInExercise.Clean to KettlebellReps.Clean,
            BuiltInExercise.Snatch to KettlebellReps.Snatch,
            BuiltInExercise.Press to KettlebellReps.Press,
            BuiltInExercise.GobletSquat to KettlebellReps.GobletSquat,
            BuiltInExercise.GetUp to KettlebellReps.GetUp,
        ).forEach { (exercise, rep) ->
            assertSame(rep, exercise.diagram, exercise.key)
            assertSame(rep, diagramOf(exercise.key), exercise.key)
        }
    }

    @Test
    fun `a lift with no diagram yet and a key nothing knows have none`() {
        assertNull(BuiltInExercise.Row.diagram)
        assertNull(diagramOf(BuiltInExercise.Row.key))
        assertNull(diagramOf("NotALift"))
        assertNull(diagramOf("custom:gone"))
    }

    @Test
    fun `a lift with a diagram has a figure too so the diagram never stands alone`() {
        BuiltInExercise.entries.filter { it.diagram != null }.forEach { assertNotNull(it.demo, it.key) }
        assertEquals(8, BuiltInExercise.entries.count { it.diagram != null })
    }
}
