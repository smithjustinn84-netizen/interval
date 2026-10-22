@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test

/** The library from Lift: searching, narrowing and clearing, over the whole app on fakes. */
class ExerciseLibraryTest {
    @Test
    fun `the library opens from Lift and finds an exercise however it is typed`() = bellsAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("74 exercises").assertExists()
        onNode(hasSetTextAction()).performTextInput("PUSHUP")
        // The push-up and its two variations, each with its facts.
        waitForText("3 exercises")
        onNodeWithText("Push-up").assertExists()
        onNodeWithText("Pike push-up").assertExists()
        onAllNodesWithText("Bodyweight · Reps · Upper body").assertCountEquals(3)
        onNodeWithText("Clear").performClick()
        waitForText("74 exercises")
    }

    @Test
    fun `filters narrow within a facet by any and across facets by all`() = bellsAppTest {
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
    fun `nothing matching says so with the search`() = bellsAppTest {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("zercher carry")
        waitForText("No exercise matches “zercher carry”")
        onNode(hasSetTextAction()).performTextClearance()
        waitForText("74 exercises")
    }

    @Test
    fun `the filters set on the library narrow the Log sheet's picker too`() = bellsAppTest { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Band").performClick()
        waitForText("15 exercises")
        app.back()
        waitForText("Start session")
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onNodeWithText("Change").performClick()
        waitForText("15 exercises")
    }
}
