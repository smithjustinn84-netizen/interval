@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.kettlebellLifts
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.runUiTest
import dev.justinsmith.bells.ui.setBellsContent
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayScreenTest {
    @Test
    fun `without a program Today offers one and a free session`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        var choosing = false
        setBellsContent {
            TodayScreen(TodayUiState(), onStartSession = { started += it }, onChooseProgram = {
                choosing =
                    true
            })
        }
        onNodeWithText("No sessions yet").assertExists()
        onNodeWithText("Free session").assertDoesNotExist()
        onNodeWithText("Follow a program").performClick()
        assertEquals(true, choosing)
        onNodeWithText("Start session").performClick()
        assertEquals(listOf<ProgramKey?>(null), started)
    }

    @Test
    fun `with a program Today lays out its next day`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val state = TodayUiState(
            following = program(ProgramId.GobletSquatRow),
            programDay = program(ProgramId.GobletSquatRow).dayAfter(0),
        )
        setBellsContent { TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {}) }
        onNodeWithText("Goblet squat and row").assertExists()
        onNodeWithText("Day 1 of 8 · week 1").assertExists()
        onNodeWithText("Goblet squat").assertExists()
        onNodeWithText("Start session").assertDoesNotExist()
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Free session").performClick()
        assertEquals(listOf<ProgramKey?>(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), null), started)
    }

    @Test
    fun `with a session open Today offers the day beside Resume and asks only when it has sets`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        var state by mutableStateOf(
            TodayUiState(
                inProgress = true,
                following = program(ProgramId.GobletSquatRow),
                programDay = program(ProgramId.GobletSquatRow).dayAfter(0),
            ),
        )
        setBellsContent { TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {}) }
        onNodeWithText("Resume session").assertExists()
        onNodeWithText("Free session").assertDoesNotExist()
        // Nothing logged: the day starts without a word.
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        assertEquals(listOf<ProgramKey?>(ProgramKey.BuiltIn(ProgramId.GobletSquatRow)), started)
        state = state.copy(openHasSets = true)
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Leave the session?").assertExists()
        onNodeWithText("Its sets are not saved: the day starts fresh.").assertExists()
        onNodeWithText("Cancel").performClick()
        waitUntil { onAllNodesWithText("Leave the session?").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, started.size)
        onNodeWithText("Start day 1").performClick()
        onAllNodesWithText("Start day 1").assertCountEquals(2)
        onAllNodesWithText("Start day 1").onLast().performClick()
        // The dialog's action runs once its exit has played.
        waitUntil { started.size == 2 }
        assertEquals(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), started.last())
        onNodeWithText("Resume session").performClick()
        assertEquals(null, started.last())
    }

    @Test
    fun `starting the day over a session with sets asks then takes its place`() = bellsAppTest(
        FakeBellsStorage().apply {
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
    fun `starting the day over a session with no sets drops it without asking`() = bellsAppTest(
        FakeBellsStorage().apply {
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
    fun `the weekly goal shows what is left of it this week`() = runUiTest {
        val week = (0L until 7L).map {
            WeekDay(it, it.toInt() + 1, bells = 0, interval = 0, today = it == 0L, date = "day $it")
        }
        var state by mutableStateOf(TodayUiState(week = week))
        setBellsContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        onNodeWithText("Goal met this week").assertDoesNotExist()
        state = state.copy(
            week = week.mapIndexed { i, day -> if (i == 0) day.copy(bells = 1, interval = 1) else day },
            weeklyGoal = 3,
        )
        onNodeWithText("1 more to hit 3").assertExists()
        state = state.copy(weeklyGoal = 2)
        onNodeWithText("Goal met this week").assertExists()
    }

    @Test
    fun `an empty week shows its count alone and no legend`() = runUiTest {
        val week = (0L until 7L).map {
            WeekDay(it, it.toInt() + 1, bells = 0, interval = 0, today = it == 0L, date = "day $it")
        }
        var state by mutableStateOf(TodayUiState(week = week))
        setBellsContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        onNodeWithText("0 workouts").assertExists()
        onNodeWithText("Lifts").assertDoesNotExist()
        state = state.copy(week = week.mapIndexed { i, day -> if (i == 0) day.copy(interval = 1) else day })
        onNodeWithText("1 workout · 1 on the Timer").assertExists()
        onNodeWithText("Lifts").assertExists()
    }

    @Test
    fun `the goal set on Plan reaches Today`() = bellsAppTest(
        intervals = FakeIntervalActivity().apply { weeklyGoal.value = 4 },
    ) {
        waitForText("4 more to hit 4")
    }

    @Test
    fun `a program's bodyweight sets log with no bell and its plank for its seconds`() = bellsAppTest(
        FakeBellsStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.BodyweightBasics.name, startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 12 sets")
        onNodeWithText("× 10").assertExists()
        // Three each of push-ups, squats and rows, then the plank.
        for (done in 1..9) {
            onNodeWithText("Log it").performScrollTo().performClick()
            waitForText("Up next · ${done + 1} of 12 sets")
        }
        val pushUp = app.storage.sessions.sets.value.first()
        assertEquals(Triple("PushUp", "Bodyweight", 0.0), Triple(pushUp.exercise, pushUp.equipment, pushUp.bellKg))
        onNodeWithText("0:30").assertExists()
        onNodeWithText("Log it").performScrollTo().performClick()
        waitForText("Up next · 11 of 12 sets")
        val plank = app.storage.sessions.sets.value.last()
        assertEquals(Triple("Plank", 30, 0), Triple(plank.exercise, plank.seconds, plank.reps))
    }

    @Test
    fun `a program's day opens a session with its plan up next`() = bellsAppTest(
        FakeBellsStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.GobletSquatRow.name, startedAtEpochMillis = 0)
        },
    ) { app ->
        onNodeWithText("Start day 1").performClick()
        waitForText("Up next · 1 of 9 sets")
        onNodeWithText("Log it").performClick()
        waitForText("Up next · 2 of 9 sets")
        assertEquals(ProgramId.GobletSquatRow.name, app.storage.sessions.sessions.value.single().programId)
    }

    @Test
    fun `the last session opens on a tap`() = runUiTest {
        val opened = mutableListOf<String>()
        val last =
            LiftSession("s1", 0, 600_000, "Swings", listOf(SetLog(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)))
        setBellsContent {
            TodayScreen(
                TodayUiState(last = last),
                onStartSession = {},
                onChooseProgram = {},
                onOpenSession = { opened += it },
            )
        }
        onNodeWithText("Swings").performClick()
        assertEquals(listOf("s1"), opened)
    }

    @Test
    fun `Start stays on screen under a long program day`() = runUiTest {
        val long = Program(
            ProgramKey.Custom("long"),
            days = listOf(
                List(12) {
                    Block(kettlebellLifts[it % kettlebellLifts.size], rounds = 5, reps = 5)
                },
            ),
            daysPerWeek = 3,
            weeks = 4,
            name = "Long day",
        )
        setBellsContent {
            TodayScreen(
                TodayUiState(following = long, programDay = long.dayAfter(0)),
                onStartSession = {},
                onChooseProgram = {},
            )
        }
        onNodeWithText("Start day 1").assertIsDisplayed()
        onNodeWithText("Change program").assertIsNotDisplayed()
    }

    @Test
    fun `a finished program's card says so and opens the program`() = runUiTest {
        var opened = 0
        val state = TodayUiState(following = program(ProgramId.GobletSquatRow), programDay = null)
        setBellsContent {
            TodayScreen(state, onStartSession = {}, onChooseProgram = {}, onOpenProgram = { opened++ })
        }
        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithText("Finished · pick another program").assertDoesNotExist()
        onNodeWithText("Finished").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the program's header opens its days done and left`() = bellsAppTest(
        FakeBellsStorage().apply {
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
    fun `a program day's page names its program and day`() = bellsAppTest(
        FakeBellsStorage().apply {
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
