@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.library

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
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
import dev.justinsmith.bells.ui.Window
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises of the user's own: added from the library or a session's search, and changed. */
class ExerciseEditorTest {
    private fun DesktopComposeUiTest.edit() {
        waitForText("Edit")
        onNodeWithText("Edit").performScrollTo().performClick()
    }

    private val plank = CustomExerciseEntity("e1", "Copenhagen plank", "Bodyweight", "Time", true, "Core", "", false, 0)

    @Test
    fun `an exercise made from the library is saved and listed as yours`() = bellsAppTest { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithContentDescription("New exercise").performClick()
        waitForText("Equipment")
        onNode(hasSetTextAction() and hasContentDescription("Name")).performTextInput("Copenhagen plank")
        onNodeWithText("Time").performClick()
        onNodeWithText("One side at a time").performClick()
        onNodeWithText("Core").performScrollTo().performClick()
        onNodeWithText("Save exercise").performScrollTo().performClick()
        // The sheet gone, the library lists it among the user's own.
        waitForText("Yours · 1")
        onNodeWithText("Copenhagen plank").assertExists()
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
        waitForText("Equipment")
        onNodeWithText("Save exercise").performScrollTo().performClick()
        // Back on the library, the search kept, it now finds the exercise just added.
        waitForText("1 exercise")
        onNodeWithText("Yours · 1").assertExists()
        assertEquals("Turkish sit-up", app.storage.exercises.rows.value.single().name)
    }

    @Test
    fun `a name another of yours has cannot be saved`() = bellsAppTest(
        storage = FakeBellsStorage().apply { exercises.rows.value = listOf(plank) },
    ) {
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithContentDescription("New exercise").performClick()
        waitForText("Equipment")
        onNode(hasSetTextAction() and hasContentDescription("Name")).performTextInput("copenhagen PLANK")
        waitForText("One of yours already has this name.")
        onNodeWithText("Save exercise").assertDoesNotExist()
    }

    @Test
    fun `an exercise added from the Log sheet comes back picked`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onAllNodesWithText("Change").onLast().performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("Copenhagen plank")
        onNodeWithText("Add “Copenhagen plank” as an exercise").performScrollTo().performClick()
        waitForText("Equipment")
        // The sheet's Time, over the session's.
        onAllNodesWithText("Time").onLast().performClick()
        onNodeWithText("One side at a time").performClick()
        onNodeWithText("Save exercise").performScrollTo().performClick()
        // Back on the Log sheet, the new exercise picked.
        waitForText("Bodyweight · Time a side · Full body")
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
        // Its page first, then Edit for the form.
        edit()
        waitForText("Equipment")
        onNodeWithText("Sets of it are logged, so its equipment and measure stay as they are.").assertExists()
        // Only the library's chip, behind the sheet: the sheet offers no equipment to pick.
        onAllNodesWithText("Kettlebell").assertCountEquals(1)
        val name = onNode(hasSetTextAction() and hasContentDescription("Name"))
        name.performTextClearance()
        name.performTextInput("Side plank on a bench")
        onNodeWithText("Save exercise").performScrollTo().performClick()
        waitForText("Side plank on a bench")
        val row = app.storage.exercises.rows.value.single()
        assertEquals(listOf("e1", "Side plank on a bench", "Time"), listOf(row.uid, row.name, row.measure))
    }

    @Test
    fun `an archived exercise leaves the Log sheet and Show archived brings it back to restore`() = bellsAppTest(
        storage = FakeBellsStorage().apply { exercises.rows.value = listOf(plank) },
    ) { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Copenhagen plank").performScrollTo().performClick()
        // Its page first, then Edit for the form.
        edit()
        waitForText("Equipment")
        onNodeWithText("Archive exercise").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Copenhagen plank").assertDoesNotExist()
        assertEquals(true, app.storage.exercises.rows.value.single().archived)
        // The library shows it again behind Filters, tagged, and it can be restored.
        onNodeWithText("Filters").performScrollTo().performClick()
        onNodeWithText("Show archived").performScrollTo().performClick()
        waitForText("Archived")
        onNodeWithText("Copenhagen plank").performScrollTo().performClick()
        // Its page first, then Edit for the form.
        edit()
        waitForText("Archived: the Log sheet doesn’t offer it.")
        onNodeWithText("Restore exercise").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Archived").assertDoesNotExist()
        assertEquals(false, app.storage.exercises.rows.value.single().archived)
    }

    @Test
    fun `on a tablet the editor opens beside the library and Back closes it`() = bellsAppTest(
        storage = FakeBellsStorage().apply { exercises.rows.value = listOf(plank) },
        window = Window.Tablet,
    ) { app ->
        onNodeWithText("Exercises").performScrollTo().performClick()
        waitForText("Search exercises")
        onNodeWithText("Copenhagen plank").performClick()
        // Its page first, then Edit for the form.
        edit()
        waitForText("Equipment")
        // The library stays where it was, under the sheet.
        val library = onNodeWithText("Search exercises").assertIsDisplayed().getUnclippedBoundsInRoot()
        val sheet = onNodeWithText("Archive exercise").performScrollTo().getUnclippedBoundsInRoot()
        assertTrue(sheet.left >= library.left)
        app.back()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Archive exercise").fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Search exercises").assertIsDisplayed()
        assertEquals(false, app.storage.exercises.rows.value.single().archived)
    }
}
