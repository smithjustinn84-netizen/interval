@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.data.CustomExerciseEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises of the user's own: added from the library or a session's search, and changed. */
class ExerciseEditorTest {
    private val plank = CustomExerciseEntity("e1", "Copenhagen plank", "Bodyweight", "Time", true, "Core", "", false, 0)

    @Test
    fun `an exercise made from the library is saved and listed as yours`() = bellsAppTest { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithContentDescription("New exercise").performClick()
        waitForText("Exercise name")
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("Copenhagen plank")
        onNodeWithText("Time").performClick()
        onNodeWithText("One side at a time").performClick()
        onNodeWithText("Core").performScrollTo().performClick()
        onNodeWithText("Save exercise").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Copenhagen plank").assertExists()
        onNodeWithText("Yours").assertExists()
        val row = app.storage.exercises.rows.value.single()
        assertEquals(
            listOf("Copenhagen plank", "Bodyweight", "Time", "true", "Core"),
            listOf(row.name, row.equipment, row.measure, row.perSide.toString(), row.focus),
        )
    }

    @Test
    fun `a search that finds nothing offers to add it by that name`() = bellsAppTest { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("Turkish sit-up")
        waitForText("Add “Turkish sit-up” as an exercise")
        onNodeWithText("Add “Turkish sit-up” as an exercise").performClick()
        waitForText("New exercise")
        onNodeWithText("Save exercise").performScrollTo().performClick()
        // Back on the library, the search kept, it now finds the exercise just added.
        waitForText("1 exercise")
        onNodeWithText("Yours").assertExists()
        assertEquals("Turkish sit-up", app.storage.exercises.rows.value.single().name)
    }

    @Test
    fun `a name another of yours has cannot be saved`() = bellsAppTest(
        storage = FakeBellsStorage().apply { exercises.rows.value = listOf(plank) },
    ) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithContentDescription("New exercise").performClick()
        waitForText("Exercise name")
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("copenhagen PLANK")
        waitForText("One of yours already has this name.")
        onNodeWithText("Save exercise").assertDoesNotExist()
    }

    @Test
    fun `an exercise added from the Log sheet comes back picked`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("Copenhagen plank")
        onNodeWithText("Add “Copenhagen plank” as an exercise").performScrollTo().performClick()
        waitForText("New exercise")
        onNodeWithText("Time").performClick()
        onNodeWithText("One side at a time").performClick()
        onNodeWithText("Save exercise").performScrollTo().performClick()
        // Back on the sheet, the new exercise picked.
        waitForText("Bodyweight · Time · a side · Full body")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("0:30 · Left")
        assertEquals(
            "custom:" + app.storage.exercises.rows.value.single().uid,
            app.storage.sessions.sets.value.single().exercise,
        )
    }

    @Test
    fun `an exercise with sets logged keeps its equipment and takes a new name`() = bellsAppTest(
        storage = FakeBellsStorage().apply {
            exercises.rows.value = listOf(plank)
            sessions.sessions.update { it + SessionEntity("s1", 0, 60_000, "Plank", 60_000) }
            sessions.sets.update { it + SetEntity(1, "s1", "custom:e1", 0.0, 0, "Left", 10, seconds = 40) }
        },
    ) { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onAllNodesWithText("Copenhagen plank").onFirst().performScrollTo().performClick()
        waitForText("Edit exercise")
        onNodeWithText("Sets of it are logged, so its equipment and measure stay as they are.").assertExists()
        onNodeWithText("Kettlebell").assertDoesNotExist()
        val name = onAllNodes(hasSetTextAction()).onFirst()
        name.performTextClearance()
        name.performTextInput("Side plank on a bench")
        onNodeWithText("Save exercise").performScrollTo().performClick()
        waitForText("Search exercises")
        val row = app.storage.exercises.rows.value.single()
        assertEquals(listOf("e1", "Side plank on a bench", "Time"), listOf(row.uid, row.name, row.measure))
    }
}
