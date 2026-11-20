@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.MuscleMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuscleBodyMapTest {
    @Test
    fun `all silhouette and muscle paths parse cleanly within the viewBox`() = runUiTest {
        val allPaths = MuscleBodyPaths.frontSilhouette +
            MuscleBodyPaths.backSilhouette +
            MuscleBodyPaths.frontMuscles.values.flatten() +
            MuscleBodyPaths.backMuscles.values.flatten()
        assertTrue(allPaths.isNotEmpty())
        allPaths.forEach { path ->
            val bounds = path.getBounds()
            assertFalse(bounds.isEmpty, "Parsed path produced empty bounds")
            assertTrue(bounds.left >= 0f && bounds.right <= MuscleBodyPaths.VIEW_WIDTH, "Out of X bounds: $bounds")
            assertTrue(bounds.top >= 0f && bounds.bottom <= MuscleBodyPaths.VIEW_HEIGHT, "Out of Y bounds: $bounds")
        }
    }

    @Test
    fun `body map renders with its accessibility description`() = runUiTest {
        setThemedContent { MuscleBodyMap(MuscleMap.of(BuiltInExercise.TwoHandSwing)) }
        onNodeWithContentDescription("Muscles worked: front and back body map").assertExists()
    }

    @Test
    fun `muscles worked card shows primary, assisted, and legend for a built-in exercise`() = runUiTest {
        setThemedContent { MusclesWorkedCard(MuscleMap.of(BuiltInExercise.TwoHandSwing)) }
        onNodeWithText("Glutes · Hamstrings").assertExists()
        onNodeWithText("Also Lower back · Abs · Forearms and grip").assertExists()
        onNodeWithText("Worked most").assertExists()
        onNodeWithText("Assisted").assertExists()
    }

    @Test
    fun `muscles worked card shows assisted muscles and note for a custom exercise`() = runUiTest {
        val custom = CustomExercise("c", "Ab wheel", Equipment.Mat, Measure.Reps, false, Focus.Core)
        setThemedContent { MusclesWorkedCard(MuscleMap.of(custom), note = "From focus: Core") }
        onNodeWithText("Abs · Obliques · Hip flexors").assertExists()
        onNodeWithText("From focus: Core").assertExists()
        onNodeWithText("Assisted").assertExists()
    }
}
