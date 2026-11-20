package dev.justinsmith.lift.ui

import dev.justinsmith.lift.domain.Muscle
import kotlin.test.Test
import kotlin.test.assertTrue

class MuscleBodyPathsTest {
    @Test
    fun `every muscle has at least one region on the front or back body map`() {
        val mapped = MuscleBodyPaths.frontMuscleData.keys + MuscleBodyPaths.backMuscleData.keys
        Muscle.entries.forEach { muscle ->
            assertTrue(muscle in mapped, "$muscle is missing from MuscleBodyPaths")
            val paths = MuscleBodyPaths.frontMuscleData[muscle].orEmpty() +
                MuscleBodyPaths.backMuscleData[muscle].orEmpty()
            assertTrue(paths.isNotEmpty() && paths.all { it.isNotBlank() }, "$muscle has empty path data")
        }
    }
}
