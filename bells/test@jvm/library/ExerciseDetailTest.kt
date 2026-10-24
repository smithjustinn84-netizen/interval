@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.bells.data.CustomExerciseEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.Window
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertTrue

/** An exercise's page, opened from the library: what it is, and the user's history with it. */
class ExerciseDetailTest {
    private fun pressed() = FakeBellsStorage().apply {
        sessions.sessions.update { it + SessionEntity("s1", 0, 60_000, "Press day", 60_000) }
        sessions.sets.update {
            it + SetEntity(1, "s1", "Press", 16.0, 5, "Left", 10) + SetEntity(2, "s1", "Press", 16.0, 5, "Right", 20)
        }
    }

    @Test
    fun `a built-in never done shows how to do it and what it works`() = bellsAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Goblet squat").performScrollTo().performClick()
        waitForText("Your history")
        // How to do it, and what it works.
        onNodeWithText("Sit between your heels with the chest up").assertExists()
        onNodeWithText("Quads · Glutes").assertExists()
        onNodeWithText("Also Adductors · Abs · Upper back").assertExists()
        onNodeWithText("Not logged yet. Its sets will show here.").performScrollTo().assertExists()
        onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun `a built-in done shows its sessions and opens one`() = bellsAppTest(storage = pressed()) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        // In the recent card, as well as the kettlebell one.
        onAllNodesWithText("Press").onFirst().performClick()
        waitForText("Done 1 time · last day 0")
        onNodeWithText("Your best").assertExists()
        onNodeWithText("16 kg × 5 · Left").performScrollTo().assertExists()
        onNodeWithText("16 kg × 5 · Right").performClick()
        // The sheet gone, the session's own page.
        waitForText("Delete")
        onNodeWithText("Press day").assertIsDisplayed()
    }

    @Test
    fun `one of yours shows its cues and opens its form with Edit`() = bellsAppTest(
        storage = FakeBellsStorage().apply {
            exercises.rows.value = listOf(
                CustomExerciseEntity(
                    "e1",
                    "Copenhagen plank",
                    "Bodyweight",
                    "Time",
                    true,
                    "Core",
                    "Top knee on the bench",
                    false,
                    0,
                ),
            )
        },
    ) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Copenhagen plank").performClick()
        waitForText("Top knee on the bench")
        onNodeWithText("Edit").performScrollTo().performClick()
        waitForText("Equipment")
        onNodeWithText("Save exercise").performScrollTo().assertExists()
    }

    @Test
    fun `on a tablet it opens beside the library`() = bellsAppTest(window = Window.Tablet) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Goblet squat").performClick()
        waitForText("Your history")
        val library = onNodeWithText("Search exercises").assertIsDisplayed().getUnclippedBoundsInRoot()
        val page = onNodeWithText("Your history").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(page.left > library.left)
    }
}
