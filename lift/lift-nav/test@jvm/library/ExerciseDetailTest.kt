@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.CustomExerciseEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertTrue

/** An exercise's page, opened from the library: what it is, and the user's history with it. */
private const val DAY = 24L * 60 * 60 * 1000

class ExerciseDetailTest {
    private fun pressed() = FakeLiftStorage().apply {
        sessions.sessions.update { it + SessionEntity("s1", 0, 60_000, "Press day", 60_000) }
        sessions.sets.update {
            it + SetEntity(1, "s1", "Press", 16.0, 5, "Left", 10) + SetEntity(2, "s1", "Press", 16.0, 5, "Right", 20)
        }
    }

    @Test
    fun `a built-in never done shows how to do it and what it works`() = liftAppTest {
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
    fun `done more than once its best each session is charted`() = liftAppTest(
        storage = FakeLiftStorage().apply {
            sessions.sessions.update {
                // A day apart, as the fake formatter counts days from the epoch.
                it + SessionEntity("s1", 0, 60_000, "Day 0", 60_000) +
                    SessionEntity("s2", DAY, DAY + 60_000, "Day 1", DAY + 60_000) +
                    SessionEntity("s3", 2 * DAY, 2 * DAY + 60_000, "Day 2", 2 * DAY + 60_000)
            }
            sessions.sets.update {
                it + SetEntity(1, "s1", "Press", 12.0, 8, null, 10) +
                    SetEntity(2, "s2", "Press", 16.0, 5, null, DAY + 10) +
                    SetEntity(3, "s3", "Press", 20.0, 5, null, 2 * DAY + 10) +
                    SetEntity(4, "s3", "Press", 24.0, 3, null, 2 * DAY + 20)
            }
        },
    ) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onAllNodesWithText("Press").onFirst().performClick()
        waitForText("Done 3 times · last day 2")
        onNodeWithText("Best each session").performScrollTo().assertExists()
        onNodeWithText("Last 3 sessions").assertExists()
        onNodeWithText("Latest 24 kg").assertExists()
        onNodeWithContentDescription("Best each session, oldest first: 12 kg, 16 kg, 24 kg").assertExists()
    }

    @Test
    fun `a built-in done shows its sessions and opens one`() = liftAppTest(storage = pressed()) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        // In the recent card, as well as the kettlebell one.
        onAllNodesWithText("Press").onFirst().performClick()
        waitForText("Done 1 time · last day 0")
        // One record, told without the name the page already has.
        onNodeWithText("Your best").assertExists()
        onNodeWithText("Heaviest 16 kg × 5").assertExists()
        onNodeWithText("Best each session").assertDoesNotExist()
        onNodeWithText("16 kg × 5 · Left").performScrollTo().assertExists()
        onNodeWithText("16 kg × 5 · Right").performClick()
        // The sheet gone, the session's own page.
        waitForText("Press day")
        onNodeWithText("Press day").assertIsDisplayed()
        onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun `one of yours shows its cues and opens its form with Edit`() = liftAppTest(
        storage = FakeLiftStorage().apply {
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
    fun `on a tablet it opens beside the library`() = liftAppTest(window = Window.Tablet) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Goblet squat").performClick()
        waitForText("Your history")
        val library = onNodeWithText("Search exercises").assertIsDisplayed().getUnclippedBoundsInRoot()
        val page = onNodeWithText("Your history").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(page.left > library.left)
    }

    @Test
    fun `one of yours with no cues says how to add some`() = liftAppTest(
        storage = FakeLiftStorage().apply {
            exercises.rows.value = listOf(
                CustomExerciseEntity("e1", "Turkish sit-up", "Kettlebell", "Reps", false, "Core", "", false, 0),
            )
        },
    ) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Turkish sit-up").performClick()
        waitForText("No cues yet. Add some with Edit.")
        onNodeWithText("Cues").assertDoesNotExist()
    }
}
