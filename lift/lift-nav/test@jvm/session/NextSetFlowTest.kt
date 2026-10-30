@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A free session's Next set card: the set to log composed on the screen, and logged in one tap. */
class NextSetFlowTest {
    /** Storage with one session of [exercise], [kg] × [reps] on [side], [finished] earlier or still open. */
    private fun storageWith(exercise: Exercise, kg: Double, reps: Int, side: Side?, finished: Boolean) =
        FakeLiftStorage().apply {
            sessions.sessions.update {
                it + SessionEntity("s1", 0, if (finished) 60_000 else null, "Earlier", 60_000)
            }
            sessions.sets.update {
                it + SetEntity(1, "s1", exercise.key, kg, reps, side?.name, 30_000, equipment = exercise.equipment.name)
            }
        }

    @Test
    fun `a free session logs the card's set in one tap and keeps it for the next`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Log set").performClick()
        waitUntil { onAllNodesWithText("16 kg × 10").fetchSemanticsNodes().size == 2 }
        // The card still reads the same set.
        onNodeWithText("16 kg").assertExists()
        onNodeWithText("× 10").assertExists()
        assertEquals(listOf(16.0 to 10, 16.0 to 10), app.storage.sessions.sets.value.map { it.bellKg to it.reps })
    }

    @Test
    fun `Heavier and More reps on the card change the set logged`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithContentDescription("Heavier").performClick()
        waitForText("20 kg")
        onNodeWithContentDescription("More reps").performClick()
        waitForText("× 11")
        onNodeWithText("Log set").performClick()
        waitForText("20 kg × 11")
        assertEquals(20.0 to 11, app.storage.sessions.sets.value.single().let { it.bellKg to it.reps })
    }

    @Test
    fun `Change on the card picks a lift and the card suggests its last bell`() = liftAppTest(
        storageWith(BuiltInExercise.Press, 12.0, 8, Side.Left, finished = true),
    ) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("press")
        waitForText("Press")
        onAllNodesWithText("Press").onLast().performScrollTo().performClick()
        waitForText("12 kg")
        onNode(hasText("Last time · 12 kg × 8", substring = true)).assertExists()
        onNodeWithText("Left").assertExists()
        onNodeWithText("Right").assertExists()
        // Its bell, not its reps: those stay as set.
        onNodeWithText("Log set").performClick()
        waitForText("12 kg × 10 · Left")
        // The other side is up next.
        onNodeWithText("Log set").performClick()
        waitForText("12 kg × 10 · Right")
        val today = app.storage.sessions.sets.value.filter { it.sessionUid != "s1" }
        assertEquals(listOf("Left", "Right"), today.map { it.side })
    }

    @Test
    fun `Two bells on the card doubles the load and drops the side`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Two bells").assertDoesNotExist()
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("clean")
        waitForText("Clean")
        onAllNodesWithText("Clean").onLast().performScrollTo().performClick()
        waitForText("Two bells")
        onNodeWithText("Left").assertExists()
        onNodeWithText("Two bells").performClick()
        onNodeWithText("Left").assertDoesNotExist()
        waitForText("2 × 16 kg")
        onNodeWithText("Clean · Two bells").assertExists()
        onNodeWithText("Log set").performClick()
        waitForText("2 × 16 kg × 10")
        onNodeWithText("320 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertTrue(set.doubled)
        assertEquals(null, set.side)
    }

    @Test
    fun `a hold on the card is timed through the sheet`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("side plank")
        waitForText("Side plank")
        onAllNodesWithText("Side plank").onLast().performScrollTo().performClick()
        waitForText("Time it")
        onNodeWithText("0:30").assertExists()
        onNodeWithText("Time it").performClick()
        waitForText("Get ready")
        app.clock.set(40_000)
        waitForText("Time the other side: Right")
        waitForText("0:30 · Left")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple(30, 0, "Left"), Triple(set.seconds, set.reps, set.side))
    }

    @Test
    fun `the card and the sheet edit one draft`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithContentDescription("Heavier").performClick()
        waitForText("20 kg")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        waitForText("Reps")
        // The sheet's stepper reads what the card set.
        onAllNodesWithText("20 kg").assertCountEquals(2)
        onAllNodesWithContentDescription("More reps").onLast().performScrollTo().performClick()
        app.back()
        waitForText("× 11")
        onAllNodesWithText("Log set").assertCountEquals(1)
    }

    @Test
    fun `an exercise added from the card comes back picked`() = liftAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        onNode(hasSetTextAction()).performTextInput("Copenhagen plank")
        onNodeWithText("Add “Copenhagen plank” as an exercise").performScrollTo().performClick()
        waitForText("Save exercise")
        onNodeWithText("Save exercise").performScrollTo().performClick()
        waitForText("Copenhagen plank")
        onNodeWithText("Log set").assertExists()
        onNodeWithText("Search exercises").assertDoesNotExist()
    }

    @Test
    fun `a resumed session's card starts from its last set on the other side`() = liftAppTest(
        storageWith(BuiltInExercise.Press, 24.0, 5, Side.Left, finished = false),
    ) { app ->
        onNodeWithText("Resume session").performClick()
        waitForText("Next set")
        // The card's heading and the set's group.
        onAllNodesWithText("Press").assertCountEquals(2)
        onNodeWithText("24 kg").assertExists()
        onNodeWithText("× 5").assertExists()
        onNodeWithText("Log set").performClick()
        waitForText("24 kg × 5 · Right")
        assertEquals(listOf("Left", "Right"), app.storage.sessions.sets.value.map { it.side })
    }

    @Test
    fun `on a tablet the next set sits beside the session's numbers`() = liftAppTest(window = Window.Tablet) {
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        val time = onNodeWithText("Time").assertIsDisplayed().getUnclippedBoundsInRoot()
        val next = onNodeWithText("Next set").assertIsDisplayed().getUnclippedBoundsInRoot()
        val log = onNodeWithText("Log set").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(next.left > time.right, "the card beside the numbers: $next vs $time")
        assertTrue(log.left < next.left, "Log set in the left column: $log vs $next")
    }
}
