@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertTrue

/** How long Lift's view models, ticking on Swing's thread, may take to show what the test did. */
private const val STATE_TIMEOUT_MILLIS = 5_000L

class LiftFlowTest {
    @Test
    fun `a session logged on Lift lands in History and the week`() = appTest { app ->
        tab("Lift").performClick()
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("New records")
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Last session")

        tab("History").performClick()
        val row = hasText("Two-hand swing") and hasClickAction()
        waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Kettlebell", substring = true) and hasText("at 90000", substring = true)).assertExists()
        // History's week counts it too.
        onNodeWithText("1 workout").assertExists()
    }

    @Test
    fun `the goal set on Plan shows on Lift`() = appTest { app ->
        app.repository.updateAppSettings { it.copy(weeklyGoal = 3) }
        tab("Lift").performClick()
        waitForText("3 more to hit 3")
    }

    @Test
    fun `on a tablet a session opens beside History but on a page of its own from Lift`() = appTest(
        Window.Tablet,
        lift = FakeBellsStorage().apply { finished("b1", "Swings", startedAt = 0, finishedAt = 600_000, 24.0 to 10) },
    ) {
        tab("History").performClick()
        onNode(hasText("Swings") and hasClickAction()).performClick()
        waitForText("Two-hand swing")
        onNodeWithContentDescription("Close session").assertExists()
        onNodeWithText("Week").assertExists()
        tab("History").assertIsSelected()

        tab("Lift").performClick()
        waitForText("Last session")
        onNode(hasText("Swings") and hasClickAction()).performClick()
        waitForText("Two-hand swing")
        onNodeWithContentDescription("Back").assertExists()
        onNodeWithContentDescription("Close session").assertDoesNotExist()
        onNodeWithText("Pick a workout").assertDoesNotExist()
    }

    @Test
    fun `a session opens from History and Delete takes it out with Undo`() = appTest(
        lift = FakeBellsStorage().apply { finished("b1", "Swings", startedAt = 0, finishedAt = 600_000, 24.0 to 10) },
    ) { app ->
        tab("History").performClick()
        onNode(hasText("Swings") and hasClickAction()).performClick()
        waitForText("Two-hand swing")
        onNodeWithText("24 kg × 10").assertExists()
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete session?").assertExists()
        onAllNodesWithText("Delete").onLast().performClick()
        waitForText("Session deleted")
        onNode(hasText("Swings") and hasClickAction()).assertDoesNotExist()
        assertTrue(app.lift.sessions.sessions.value.single().deleted)

        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            onAllNodes(hasText("Swings") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!app.lift.sessions.sessions.value.single().deleted)
    }

    @Test
    fun `Lift opens the bells owned and Back returns`() = appTest {
        tab("Lift").performClick()
        onNodeWithContentDescription("Your equipment and records").performClick()
        onNodeWithText("Your bells").assertExists()
        tab("Lift").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start session").assertExists()
    }

    @Test
    fun `the last session on Lift opens its page`() = appTest(
        lift = FakeBellsStorage().apply { finished("b1", "Swings", startedAt = 0, finishedAt = 600_000, 24.0 to 10) },
    ) {
        tab("Lift").performClick()
        waitForText("Swings")
        onNode(hasText("Swings") and hasClickAction()).performClick()
        waitForText("24 kg × 10")
        onNodeWithContentDescription("More options").assertExists()
        tab("Lift").assertDoesNotExist()
    }

    @Test
    fun `the program followed opens from Lift and a day opens its session`() = appTest(
        lift = FakeBellsStorage().apply {
            val goblets = "GobletSquatRow"
            following.row.value = FollowingEntity(programId = goblets, startedAtEpochMillis = 0)
            sessions.sessions.value = listOf(
                SessionEntity("d1", 0, 600_000, "Goblet squat and row", 600_000, programId = goblets, programDay = 1),
            )
        },
    ) {
        tab("Lift").performClick()
        waitForText("Day 2 of 8 · week 1")
        onNodeWithText("Day 2 of 8 · week 1").performClick()
        waitForText("Days left")
        onNodeWithText("Day 1").performClick()
        waitForText("Goblet squat and row · day 1")
    }
}
