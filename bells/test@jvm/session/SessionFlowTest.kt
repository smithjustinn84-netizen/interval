@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.data.BarEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomCue
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A session from Today to the summary, through the whole app over fakes. */
@Suppress("detekt.TooManyFunctions") // One way of logging per test, side by side.
class SessionFlowTest {
    /** On the Log sheet: Change, then narrow the library by [search] or [equipment], and pick [name]. */
    private fun DesktopComposeUiTest.pick(name: String, search: String? = null, equipment: String? = null) {
        waitForText("Change")
        onNodeWithText("Change").performClick()
        waitForText("Search exercises")
        search?.let { onNode(hasSetTextAction()).performTextInput(it) }
        equipment?.let { onNodeWithText(it).performClick() }
        waitForText(name)
        onAllNodesWithText(name).onLast().performScrollTo().performClick()
        waitForText("Change")
    }

    @Test
    fun `a band set logs its band and moves no weight`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Band pull-apart", equipment = "Band")
        // On the medium band to start.
        waitForText("Medium")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("Medium × 10")
        onNodeWithText("0 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple("BandPullApart", "Band", "Medium"), Triple(set.exercise, set.equipment, set.band))
    }

    @Test
    fun `a hold logs how long it lasted on its side and is never an EMOM`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Side plank", search = "side plank")
        waitForText("Held")
        onNodeWithText("0:30").assertExists()
        onNodeWithText("An EMOM").assertDoesNotExist()
        onNodeWithContentDescription("Five seconds more").performScrollTo().performClick()
        waitForText("0:35")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("0:35 · Left")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple(35, 0, "Left"), Triple(set.seconds, set.reps, set.side))
    }

    @Test
    fun `a band set and a hold are records on the summary in their own words`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Band pull-apart", equipment = "Band")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("Medium × 10")
        onNodeWithText("Log set").performClick()
        // The Band chip is still on from the first pick: tapping it again lets the plank through.
        pick("Plank", search = "plank", equipment = "Band")
        waitForText("Held")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("0:30")
        onNodeWithText("Finish").performClick()
        waitForText("New records")
        onNodeWithText("Heaviest Medium × 10").assertExists()
        onNodeWithText("Longest 0:30").assertExists()
    }

    @Test
    fun `a timed plank counts itself down then logs and offers the other side`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Side plank", search = "side plank")
        onNodeWithText("Time it").performScrollTo().performClick()
        waitForText("Get ready")
        onNodeWithText("Side plank · Left").assertExists()
        // Three seconds in, then ten of the thirty held.
        app.clock.set(13_000)
        waitForText("0:20")
        onNodeWithText("Hold").assertExists()
        assertTrue(app.storage.sessions.sets.value.isEmpty())
        app.clock.set(40_000)
        waitForText("Time the other side: Right")
        waitForText("0:30 · Left")
        val first = app.storage.sessions.sets.value.single()
        assertEquals(Triple(30, 0, "Left"), Triple(first.seconds, first.reps, first.side))
        assertEquals(EmomCue.Finished, app.beeper.cues.last())
        assertTrue(EmomCue.Start in app.beeper.cues)
        // The other side for the same length, stopped early: the whole seconds held are logged.
        onNodeWithText("Time the other side: Right").performScrollTo().performClick()
        waitForText("Side plank · Right")
        app.clock.set(40_000 + 3_000 + 12_400)
        waitForText("0:18")
        onNodeWithText("Stop").performScrollTo().performClick()
        waitForText("0:12 · Right")
        val second = app.storage.sessions.sets.value.last()
        assertEquals(12 to "Right", second.seconds to second.side)
        onNodeWithText("Time the other side: Left").assertDoesNotExist()
    }

    @Test
    fun `a barbell set steps the total on the bar and shows the plates a side`() = bellsAppTest(
        FakeBellsStorage().apply { bells.bar.value = BarEntity(kg = 20.0) },
    ) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Power clean", search = "power clean")
        // A lift never done starts on the empty bar.
        waitForText("On the bar")
        onNodeWithText("20 kg bar, empty").assertExists()
        onNodeWithContentDescription("Lighter").assertIsNotEnabled()
        repeat(17) { onNodeWithContentDescription("Heavier").performClick() }
        waitForText("62.5 kg")
        onNodeWithText("20 kg bar · 20 + 1.25 a side").assertExists()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("62.5 kg × 10")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple("BarbellPowerClean", "Barbell", 62.5), Triple(set.exercise, set.equipment, set.bellKg))
    }

    @Test
    fun `weight added to bodyweight counts toward moved`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        pick("Push-up", search = "pushup")
        waitForText("Added weight")
        onNodeWithText("None").assertExists()
        onNodeWithText("8").performScrollTo().performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("+8 kg × 10")
        onNodeWithText("Push-up").assertExists()
        onNodeWithText("80 kg").assertExists()
        assertEquals(8.0, app.storage.sessions.sets.value.single().bellKg)
    }

    @Test
    fun `logging a set adds it to the session`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log your first set: pick the lift, the bell and the reps.")
        onNodeWithText("Log set").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        // The sheet's own Log set, over the screen's.
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Two-hand swing").assertExists()
        onNodeWithText("160 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `a set with two bells has no side and moves twice the weight`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        // A two-hand swing can't be doubled; a clean can, and then has no side.
        onNodeWithText("Two bells").assertDoesNotExist()
        pick("Clean", search = "clean")
        onNodeWithText("Left").assertExists()
        onNodeWithText("Two bells").performScrollTo().performClick()
        onNodeWithText("Left").assertDoesNotExist()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("2 × 16 kg × 10")
        onNodeWithText("Clean · Two bells").assertExists()
        onNodeWithText("320 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertTrue(set.doubled)
        assertEquals(null, set.side)
    }

    @Test
    fun `finishing a session shows its summary then Today`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("Counts toward your week and your goal")
        // The title, and the lift's first records.
        onAllNodesWithText("Two-hand swing").assertCountEquals(2)
        onNodeWithText("New records").assertExists()
        onNodeWithText("1:30").assertExists()
        assertTrue(app.storage.sessions.sessions.value.single().finishedAtEpochMillis != null)
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Last session")
        onNodeWithText("Two-hand swing").assertExists()
    }

    @Test
    fun `a set heavier than any before is tagged a record and announced`() = bellsAppTest(
        storage = FakeBellsStorage().apply {
            sessions.sessions.value = listOf(SessionEntity("old", 0, 60_000, "Two-hand swing", 60_000))
            sessions.sets.value = listOf(SetEntity(1, "old", BuiltInExercise.TwoHandSwing.key, 16.0, 10, null, 30_000))
        },
    ) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        // The same as before: no record.
        onNodeWithText("Record").assertDoesNotExist()
        onNodeWithText("Log set").performClick()
        waitForText("24")
        onNodeWithText("24").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("24 kg × 10")
        onNodeWithText("Record").assertExists()
        onNodeWithText("New record · 24 kg × 10").assertExists()
    }

    @Test
    fun `a lift's first set ever is not a record`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Record").assertDoesNotExist()
    }

    @Test
    fun `the summary says where the week stands against the goal`() = bellsAppTest(
        intervals = FakeIntervalActivity().apply { weeklyGoal.value = 3 },
    ) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Finish").performClick()
        waitForText("1 of 3 this week")
    }

    @Test
    fun `finishing with no sets drops the session`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        onNodeWithText("Finish").performClick()
        waitForText("No sessions yet")
        assertTrue(app.storage.sessions.sessions.value.isEmpty())
    }

    @Test
    fun `a set removed comes back with Undo`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithContentDescription("Remove 16 kg × 10").performClick()
        waitForText("Removed 16 kg × 10")
        assertTrue(app.storage.sessions.sets.value.isEmpty())
        onNodeWithText("Undo").performClick()
        waitForText("160 kg")
        onNodeWithText("Removed 16 kg × 10").assertDoesNotExist()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `back with sets logged asks and leaving keeps them`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        app.back()
        waitForText("Leave the session?")
        onNodeWithText("Stay").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        onNodeWithText("16 kg × 10").assertExists()
        app.back()
        waitForText("Leave")
        onNodeWithText("Leave").performClick()
        waitForText("Resume session")
        assertEquals(1, app.storage.sessions.sets.value.size)
    }

    @Test
    fun `back with nothing logged just leaves`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        it.back()
        waitForText("Resume session")
        onNodeWithText("Leave the session?").assertDoesNotExist()
    }

    @Test
    fun `the sheet's EMOM side starts an EMOM of the lift picked`() = bellsAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onNodeWithText("Minutes").assertDoesNotExist()
        onNodeWithText("An EMOM").performClick()
        // The sheet is a window of its own: wait for it to redraw as an EMOM.
        waitForText("Start EMOM")
        onNodeWithText("Reps each minute").assertExists()
        onNodeWithText("Minutes").performScrollTo().assertExists()
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Set done")
        it.back()
        waitForText("The EMOM stops. Its sets so far are kept: Resume session on Lift picks it up.")
    }

    @Test
    fun `back over the log sheet closes the sheet before asking to leave`() = bellsAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Log set").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        app.back()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Log set").fetchSemanticsNodes().size == 1 }
        onNodeWithText("Leave the session?").assertDoesNotExist()
        app.back()
        waitForText("Leave the session?")
    }
}
