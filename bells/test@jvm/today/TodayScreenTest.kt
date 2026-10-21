@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.runUiTest
import dev.justinsmith.bells.ui.setBellsContent
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `the goal set on Plan reaches Today`() = bellsAppTest(
        intervals = FakeIntervalActivity().apply { weeklyGoal.value = 4 },
    ) {
        waitForText("4 more to hit 4")
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
        val last = LiftSession("s1", 0, 600_000, "Swings", listOf(SetLog(1, Exercise.TwoHandSwing, 24.0, 10, null, 0)))
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
            days = listOf(List(12) { Block(Exercise.entries[it % Exercise.entries.size], rounds = 5, reps = 5) }),
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
}
