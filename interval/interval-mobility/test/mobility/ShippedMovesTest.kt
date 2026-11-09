package dev.justinsmith.interval.mobility

import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.ui.demo.demo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every move of the shipped flows and of the library is one of Lift's built-ins, each with a figure to show. */
class ShippedMovesTest {
    @Test
    fun `every shipped move is a built-in with a figure`() {
        val moves = builtInFlows.flatMap { it.moves }
        assertTrue(moves.isNotEmpty())
        moves.forEach { move ->
            val key = assertNotNull(move.exercise, "${move.name} names no exercise")
            val exercise = assertNotNull(Exercise.parse(key) as? BuiltInExercise, key)
            assertNotNull(exercise.demo, key)
            assertEquals(move.sided, exercise.perSide, "${move.name} is sided as ${exercise.key} is not")
        }
    }

    @Test
    fun `every move the library offers has a figure`() {
        assertTrue(libraryMoves.isNotEmpty())
        libraryMoves.forEach { assertNotNull(it.demo, it.key) }
    }
}
