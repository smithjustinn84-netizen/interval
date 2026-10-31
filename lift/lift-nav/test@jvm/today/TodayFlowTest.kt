@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Lift tab over the real ViewModel and storage. The tab over fixed state is lift-today's `TodayScreenTest`. */
class TodayFlowTest {
    @Test
    fun `starting the day over a session with sets asks then takes its place`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
            sessions.sessions.value = listOf(SessionEntity("open", 0, null, "", 0))
            sessions.sets.value = listOf(SetEntity(1, "open", BuiltInExercise.TwoHandSwing.key, 16.0, 10, null, 0))
        },
    ) { app ->
        waitForText("Resume session")
        onNodeWithText("Start day 1").performClick()
        waitForText("Leave the session?")
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        assertEquals(1, app.storage.sessions.sets.value.size)
        onNodeWithText("Start day 1").performClick()
        waitForText("Its sets are not saved: the day starts fresh.")
        onAllNodesWithText("Start day 1").onLast().performClick()
        waitForText("Up next · 1 of 9 sets")
        val open = app.storage.sessions.sessions.value.single { it.finishedAtEpochMillis == null }
        assertEquals(ProgramId.GobletSquatRow.name, open.programId)
        assertTrue(app.storage.sessions.sets.value.isEmpty())
    }

    @Test
    fun `starting the day over a session with no sets drops it without asking`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
            sessions.sessions.value = listOf(SessionEntity("open", 0, null, "", 0))
        },
    ) { app ->
        waitForText("Resume session")
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 9 sets")
        onNodeWithText("Leave the session?").assertDoesNotExist()
        val open = app.storage.sessions.sessions.value.single { it.finishedAtEpochMillis == null }
        assertEquals(ProgramId.GobletSquatRow.name, open.programId)
    }

    @Test
    fun `the goal set on Plan reaches Today`() = liftAppTest(
        intervals = FakeIntervalActivity().apply { weeklyGoal.value = 4 },
    ) {
        waitForText("4 more to hit 4")
    }

    @Test
    fun `a program's bodyweight sets log with no bell and its plank for its seconds`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.BodyweightBasics.name, startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 12 sets")
        onNodeWithText("× 10").assertExists()
        // Three each of push-ups, squats and rows, then the plank.
        for (done in 1..9) {
            onNodeWithText("Log set").performClick()
            waitForText("Up next · ${done + 1} of 12 sets")
        }
        val pushUp = app.storage.sessions.sets.value.first()
        assertEquals(Triple("PushUp", "Bodyweight", 0.0), Triple(pushUp.exercise, pushUp.equipment, pushUp.bellKg))
        onNodeWithText("0:30").assertExists()
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 11 of 12 sets")
        val plank = app.storage.sessions.sets.value.last()
        assertEquals(Triple("Plank", 30, 0), Triple(plank.exercise, plank.seconds, plank.reps))
    }

    @Test
    fun `a program's day opens a session with its plan up next`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 9 sets")
        onNodeWithText("Log set").performClick()
        waitForText("Up next · 2 of 9 sets")
        assertEquals(ProgramId.GobletSquatRow.name, app.storage.sessions.sessions.value.single().programId)
    }

    @Test
    fun `the program's header opens its days done and left`() = liftAppTest(
        FakeLiftStorage().apply {
            val goblets = ProgramId.GobletSquatRow.name
            following.row.value = FollowingEntity(programId = goblets, startedAtEpochMillis = 0)
            sessions.sessions.value = listOf(
                SessionEntity("d1", 0, 600_000, "Goblet squat and row", 600_000, programId = goblets, programDay = 1),
            )
        },
    ) {
        waitForText("Day 2 of 8 · week 1")
        onNodeWithText("Day 2 of 8 · week 1").performClick()
        waitForText("Days left")
        onNodeWithText("Day 1").assertExists()
        onNodeWithText("day 0 · 0 of 9 sets").assertExists()
    }

    @Test
    fun `a program day's page names its program and day`() = liftAppTest(
        FakeLiftStorage().apply {
            val goblets = ProgramId.GobletSquatRow.name
            following.row.value = FollowingEntity(programId = goblets, startedAtEpochMillis = 0)
            sessions.sessions.value = listOf(
                SessionEntity("d2", 0, 600_000, "Goblet squat", 600_000, programId = goblets, programDay = 2),
            )
            sessions.sets.value = listOf(SetEntity(1, "d2", BuiltInExercise.GobletSquat.key, 16.0, 10, null, 0))
        },
    ) {
        waitForText("Last session")
        onNodeWithText("Last session").performClick()
        waitForText("Goblet squat and row · day 2")
    }
}
