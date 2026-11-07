package dev.justinsmith.lift.library

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.domain.muscles
import dev.justinsmith.lift.ui.cues
import dev.justinsmith.lift.ui.words
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every built-in's page has something to say: how to do it, and what it works. */
class ExerciseContentTest {
    @Test
    fun `every built-in has its cues and its muscles`() = runTest {
        BuiltInExercise.entries.forEach { exercise ->
            val cues = getStringArray(exercise.cues())
            assertTrue(cues.size == 3 && cues.none { it.isBlank() }, "$exercise has not three cues: $cues")
            assertTrue(exercise.muscles.primary.isNotEmpty(), "$exercise works nothing")
            assertTrue(exercise.muscles.primary.intersect(exercise.muscles.also.toSet()).isEmpty(), "$exercise twice")
        }
    }

    @Test
    fun `every fault has its words`() = runTest {
        Fault.entries.forEach { assertTrue(getString(it.words()).isNotBlank(), "$it says nothing") }
    }
}
