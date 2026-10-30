@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.BarEntity
import dev.justinsmith.lift.data.CustomProgramEntity
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.data.encodeDays
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.HoldClock
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A session from Today to the summary, through the whole app over fakes. */
@Suppress("detekt.TooManyFunctions") // One way of logging per test, side by side.
class SessionFlowTest {
    /**
     * On the Log sheet: Change (the sheet's, over the Next set card's), then narrow the library by [search] or
     * [equipment], and pick [name].
     */
    private fun DesktopComposeUiTest.pick(name: String, search: String? = null, equipment: String? = null) {
        waitForText("Change")
        onAllNodesWithText("Change").onLast().performClick()
        waitForText("Search exercises")
        search?.let { onNode(hasSetTextAction()).performTextInput(it) }
        equipment?.let { onNodeWithText(it).performClick() }
        waitForText(name)
        onAllNodesWithText(name).onLast().performScrollTo().performClick()
        waitForText("Change")
    }

    @Test
    fun `a band set logs its band and moves no weight`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
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
    fun `a hold logs how long it lasted on its side and is never an EMOM`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        pick("Side plank", search = "side plank")
        waitForText("Held")
        // The card's length and the sheet's: one draft.
        onAllNodesWithText("0:30").assertCountEquals(2)
        onNodeWithText("An EMOM").assertDoesNotExist()
        onAllNodesWithContentDescription("Five seconds more").onLast().performScrollTo().performClick()
        waitForText("0:35")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("0:35 · Left")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple(35, 0, "Left"), Triple(set.seconds, set.reps, set.side))
    }

    @Test
    fun `a band set and a hold are records on the summary in their own words`() = liftAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        pick("Band pull-apart", equipment = "Band")
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("Medium × 10")
        onNodeWithContentDescription("Add set or EMOM").performClick()
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
    fun `a timed plank counts itself down then logs and offers the other side`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        pick("Side plank", search = "side plank")
        onAllNodesWithText("Time it").onLast().performScrollTo().performClick()
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
    fun `a barbell set steps the total on the bar and shows the plates a side`() = liftAppTest(
        FakeLiftStorage().apply { bells.bar.value = BarEntity(kg = 20.0) },
    ) { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        pick("Power clean", search = "power clean")
        // A lift never done starts on the empty bar.
        waitForText("On the bar")
        // On the Next set card and on the sheet, which edit one draft.
        onAllNodesWithText("20 kg bar, empty").assertCountEquals(2)
        onAllNodesWithContentDescription("Lighter").onLast().assertIsNotEnabled()
        repeat(17) { onAllNodesWithContentDescription("Heavier").onLast().performClick() }
        waitForText("62.5 kg")
        onAllNodesWithText("20 kg bar · 20 + 1.25 a side").assertCountEquals(2)
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("62.5 kg × 10")
        val set = app.storage.sessions.sets.value.single()
        assertEquals(Triple("BarbellPowerClean", "Barbell", 62.5), Triple(set.exercise, set.equipment, set.bellKg))
    }

    @Test
    fun `weight added to bodyweight counts toward moved`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        pick("Push-up", search = "pushup")
        waitForText("Added weight")
        // On the card's stepper, the sheet's, and its chip.
        onAllNodesWithText("None").assertCountEquals(3)
        onNodeWithText("8").performScrollTo().performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("+8 kg × 10")
        // The card, ready for the next, and the set's group.
        onAllNodesWithText("Push-up").assertCountEquals(2)
        onNodeWithText("80 kg").assertExists()
        assertEquals(8.0, app.storage.sessions.sets.value.single().bellKg)
    }

    @Test
    fun `logging a set adds it to the session`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onAllNodesWithText("Two-hand swing").assertCountEquals(2)
        onNodeWithText("160 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertEquals(16.0 to 10, set.bellKg to set.reps)
    }

    @Test
    fun `a set logged starts a rest that steps longer and skips back to since last set`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Next set")
        onNodeWithText("Log set").performClick()
        waitForText("Rest")
        onNodeWithText("1:30").assertExists()
        onNodeWithText("Since last set").assertDoesNotExist()
        onNodeWithContentDescription("Rest 15 seconds more").performClick()
        waitForText("1:45")
        assertEquals(105, app.storage.bells.rest.value?.seconds)
        onNodeWithText("Skip").performClick()
        waitForText("Since last set")
        onNodeWithText("Rest").assertDoesNotExist()
    }

    @Test
    fun `a set with two bells has no side and moves twice the weight`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        // A two-hand swing can't be doubled; a clean can, and then has no side.
        onNodeWithText("Two bells").assertDoesNotExist()
        pick("Clean", search = "clean")
        onAllNodesWithText("Left").assertCountEquals(2)
        onAllNodesWithText("Two bells").onLast().performScrollTo().performClick()
        onNodeWithText("Left").assertDoesNotExist()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("2 × 16 kg × 10")
        onAllNodesWithText("Clean · Two bells").assertCountEquals(2)
        onNodeWithText("320 kg").assertExists()
        val set = app.storage.sessions.sets.value.single()
        assertTrue(set.doubled)
        assertEquals(null, set.side)
    }

    @Test
    fun `finishing a session shows its summary then Today`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
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
    fun `a set heavier than any before is tagged a record and announced`() = liftAppTest(
        storage = FakeLiftStorage().apply {
            sessions.sessions.value = listOf(SessionEntity("old", 0, 60_000, "Two-hand swing", 60_000))
            sessions.sets.value = listOf(SetEntity(1, "old", BuiltInExercise.TwoHandSwing.key, 16.0, 10, null, 30_000))
        },
    ) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        // The same as before: no record.
        onNodeWithText("Record").assertDoesNotExist()
        onNodeWithContentDescription("Add set or EMOM").performClick()
        waitForText("24")
        onNodeWithText("24").performClick()
        onAllNodesWithText("Log set").onLast().performScrollTo().performClick()
        waitForText("24 kg × 10")
        onNodeWithText("Record").assertExists()
        onNodeWithText("New record · 24 kg × 10").assertExists()
    }

    @Test
    fun `a lift's first set ever is not a record`() = liftAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Record").assertDoesNotExist()
    }

    @Test
    fun `the summary says where the week stands against the goal`() = liftAppTest(
        intervals = FakeIntervalActivity().apply { weeklyGoal.value = 3 },
    ) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onNodeWithText("Finish").performClick()
        waitForText("1 of 3 this week")
    }

    @Test
    fun `finishing with no sets asks as Back does and Leave drops the session`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        onNodeWithText("Finish").performClick()
        waitForText("Leave the session?")
        onNodeWithText("Nothing is logged yet, so the session isn't kept.").assertExists()
        onNodeWithText("Stay").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        onNodeWithText("Finish").assertExists()
        onNodeWithText("Finish").performClick()
        waitForText("Leave")
        onNodeWithText("Leave").performClick()
        waitForText("No sessions yet")
        onNodeWithText("Start session").assertExists()
        assertTrue(app.storage.sessions.sessions.value.isEmpty())
    }

    @Test
    fun `a set removed comes back with Undo`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
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
    fun `back with sets logged asks and leaving keeps them`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
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
    fun `back with nothing logged asks and Leave drops the session`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Finish")
        app.back()
        waitForText("Leave the session?")
        onNodeWithText("Nothing is logged yet, so the session isn't kept.").assertExists()
        onNodeWithText("Stay").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        onNodeWithText("Finish").assertExists()
        app.back()
        waitForText("Leave")
        onNodeWithText("Leave").performClick()
        // Nothing to resume: the session is gone, not waiting.
        waitForText("Start session")
        onNodeWithText("Resume session").assertDoesNotExist()
        assertTrue(app.storage.sessions.sessions.value.isEmpty())
    }

    @Test
    fun `the sheet's EMOM side starts an EMOM of the lift picked`() = liftAppTest {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNodeWithText("Minutes").assertDoesNotExist()
        onNodeWithText("An EMOM").performClick()
        // The sheet is a window of its own: wait for it to redraw as an EMOM.
        waitForText("Start EMOM")
        onNodeWithText("Reps each minute").assertExists()
        onNodeWithText("Minutes").performScrollTo().assertExists()
        onNodeWithText("Start EMOM").performScrollTo().performClick()
        waitForText("Set done")
        // Before its first set there is nothing to keep; once one is done, the EMOM's own words.
        it.back()
        waitForText("Nothing is logged yet, so the session isn't kept.")
        onNodeWithText("Stay").performClick()
        it.clock.set(10_000)
        waitForText("Work")
        onNodeWithText("Set done").performClick()
        waitForText("Rest")
        it.back()
        waitForText("The EMOM stops. Its sets so far are kept: Resume session on Lift picks it up.")
    }

    @Test
    fun `back over the log sheet closes the sheet before asking to leave`() = liftAppTest { app ->
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        onNodeWithContentDescription("Add set or EMOM").performClick()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add to the session")).assertExists()
        app.back()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Log set").fetchSemanticsNodes().size == 1 }
        onNodeWithText("Leave the session?").assertDoesNotExist()
        app.back()
        waitForText("Leave the session?")
    }

    @Test
    fun `the summary of a program's last day says it finished`() = liftAppTest(
        FakeLiftStorage().apply {
            val goblets = ProgramId.GobletSquatRow.name
            following.row.value = FollowingEntity(programId = goblets, startedAtEpochMillis = 0)
            sessions.sessions.value = (1..7).map { day ->
                val at = day * 1_000L
                SessionEntity("d$day", at, at + 1, "Day", at + 1, programId = goblets, programDay = day)
            } + SessionEntity("open", 10_000, null, "", 10_000, programId = goblets, programDay = 8)
            sessions.sets.value = listOf(SetEntity(1, "open", BuiltInExercise.GobletSquat.key, 16.0, 10, null, 10_000))
        },
    ) {
        waitForText("Resume session")
        onNodeWithText("Resume session").performClick()
        waitForText("Finish")
        onNodeWithText("Finish").performClick()
        waitForText("Goblet squat and row finished · every day done")
    }

    @Test
    fun `a planned hold offers Time it on Up next and skipping a block offers Undo`() = liftAppTest(
        FakeLiftStorage().apply {
            val days = encodeDays(
                listOf(
                    listOf(
                        Block(BuiltInExercise.Plank, rounds = 2, reps = 15),
                        Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 10),
                    ),
                ),
            )
            programs.rows.value = listOf(CustomProgramEntity("p1", "Core and swings", 3, 4, days, 0))
            following.row.value = FollowingEntity(programId = "custom:p1", startedAtEpochMillis = 0)
        },
    ) { app ->
        waitForText("Start day 1")
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 5 sets")
        onNodeWithText("Time it").assertExists()

        // Skipping a block shows an Undo notice that brings it straight back.
        onNodeWithText("Skip").performClick()
        waitForText("Skipped Plank")
        onNodeWithText("Two-hand swing").assertExists()
        onNodeWithText("Undo").performClick()
        waitForText("Plank")

        // Tapping Time it on Up next opens the hold countdown and logs the planned duration.
        onNodeWithText("Time it").performClick()
        waitForText("Get ready")
        app.clock.set(HoldClock.COUNT_IN_MILLIS)
        waitForText("Hold")
        app.clock.set(HoldClock.COUNT_IN_MILLIS + 15_000L)
        waitForText("Up next · 2 of 5 sets")
        assertEquals(15, app.storage.sessions.sets.value.single().seconds)
    }
}
