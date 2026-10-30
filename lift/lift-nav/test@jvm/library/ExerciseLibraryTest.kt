@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.CustomExerciseEntity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test

/** The library from Lift: searching, narrowing and clearing, over the whole app on fakes. */
class ExerciseLibraryTest {
    @Test
    fun `the library opens from Lift and finds an exercise however it is typed`() = liftAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("94 exercises").assertExists()
        onNode(hasSetTextAction()).performTextInput("PUSHUP")
        // The push-up and its two variations, in a card of their own, each with its facts.
        waitForText("3 exercises")
        onNodeWithText("Bodyweight · 3").assertExists()
        onNodeWithText("Push-up").assertExists()
        onNodeWithText("Pike push-up").assertExists()
        onAllNodesWithText("Reps · Upper body").assertCountEquals(3)
        onNodeWithText("Clear").performClick()
        waitForText("94 exercises")
    }

    @Test
    fun `filters narrow within a facet by any and across facets by all`() = liftAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Mat").performClick()
        waitForText("18 exercises")
        onNodeWithText("Filters").performClick()
        waitForText("Measured by")
        onNodeWithText("Time").performClick()
        waitForText("1 exercise")
        onNodeWithText("Swimming").assertExists()
        onNodeWithText("Filters · 1").assertExists()
    }

    @Test
    fun `nothing matching says so with the search`() = liftAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("zercher carry")
        waitForText("No exercise matches “zercher carry”")
        onNode(hasSetTextAction()).performTextClearance()
        waitForText("94 exercises")
    }

    @Test
    fun `the filters set on the library narrow the Log sheet's picker too`() = liftAppTest { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Band").performClick()
        waitForText("15 exercises")
        app.back()
        waitForText("Start session")
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onAllNodesWithText("Change").onLast().performClick()
        waitForText("15 exercises")
    }

    @Test
    fun `show archived on the library leaves the Log sheet without them`() = liftAppTest(
        storage = FakeLiftStorage().apply {
            exercises.rows.value = listOf(
                CustomExerciseEntity("e1", "Copenhagen plank", "Bodyweight", "Time", true, "Core", "", true, 0),
            )
        },
    ) { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Filters").performClick()
        onNodeWithText("Show archived").performScrollTo().performClick()
        waitForText("Filters · 1")
        onNodeWithText("Copenhagen plank").assertExists()
        app.back()
        waitForText("Start session")
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onAllNodesWithText("Change").onLast().performClick()
        waitForText("Search exercises")
        onNodeWithText("Filters").assertExists()
        onNodeWithText("Copenhagen plank").assertDoesNotExist()
        onNodeWithText("Filters").performClick()
        onNodeWithText("Show archived").assertDoesNotExist()
    }
}
