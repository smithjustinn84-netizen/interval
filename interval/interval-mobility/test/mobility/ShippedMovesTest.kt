package dev.justinsmith.interval.mobility

import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.ui.demo.demo
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The exercises the shipped flows and the library name are Lift's built-ins, each with a figure to show. */
class ShippedMovesTest {
    @Test
    fun `every shipped move with an exercise is a built-in with a figure`() {
        val keyed = builtInFlows.flatMap { it.moves }.mapNotNull { it.exercise }
        assertTrue(keyed.isNotEmpty())
        keyed.forEach { key ->
            val exercise = assertNotNull(Exercise.parse(key) as? BuiltInExercise, key)
            assertNotNull(exercise.demo, key)
        }
    }

    @Test
    fun `every move the library offers has a figure`() {
        assertTrue(libraryMoves.isNotEmpty())
        libraryMoves.forEach { assertNotNull(it.demo, it.key) }
    }
}
