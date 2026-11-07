package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VariationsTest {
    @Test
    fun `each harder lift is a bell lift of the same pattern done with one bell`() {
        harderLifts.forEach { (from, to) ->
            assertEquals(Equipment.Kettlebell, to.equipment, "$from to $to")
            assertEquals(from.movement.pattern, to.movement.pattern, "$from to $to")
            assertEquals(Measure.Reps, to.measure, "$from to $to")
            assertEquals(Variation(to, double = false), variationOf(from, double = false, 16.0, emptySet()))
        }
    }

    @Test
    fun `the table moves only to harder lifts`() {
        assertEquals(
            mapOf(
                BuiltInExercise.TwoHandSwing to BuiltInExercise.OneHandSwing,
                BuiltInExercise.HighPull to BuiltInExercise.Snatch,
                BuiltInExercise.HalfSnatch to BuiltInExercise.Snatch,
                BuiltInExercise.PushPress to BuiltInExercise.Press,
                BuiltInExercise.GobletSquat to BuiltInExercise.FrontSquat,
            ),
            harderLifts,
        )
        // Never to an easier one, nor to one that needs a lighter bell.
        assertTrue(
            harderLifts.none { (from, to) ->
                from == BuiltInExercise.Press ||
                    to == BuiltInExercise.BottomsUpPress
            },
        )
    }

    @Test
    fun `a lift that can take two bells goes to two only with a pair of the bell`() {
        val doubleRow = Variation(BuiltInExercise.Row, double = true)
        assertEquals(doubleRow, variationOf(BuiltInExercise.Row, double = false, 16.0, setOf(16.0)))
        assertNull(variationOf(BuiltInExercise.Row, double = false, 16.0, emptySet()))
        assertNull(variationOf(BuiltInExercise.Row, double = false, 16.0, setOf(24.0)))
        // Already with two bells, or a lift never done with two, has none.
        assertNull(variationOf(BuiltInExercise.Row, double = true, 16.0, setOf(16.0)))
        assertNull(variationOf(BuiltInExercise.GetUp, double = false, 16.0, setOf(16.0)))
    }

    @Test
    fun `holds and lifts of other equipment and the user's own have none`() {
        assertNull(variationOf(BuiltInExercise.SuitcaseCarry, double = false, 16.0, setOf(16.0)))
        assertNull(variationOf(BuiltInExercise.BarbellPushPress, double = false, 60.0, emptySet()))
        val own = CustomExercise("u", "My swing", Equipment.Kettlebell, Measure.Reps, perSide = false, Focus.FullBody)
        assertNull(variationOf(own, double = false, 16.0, setOf(16.0)))
    }
}
