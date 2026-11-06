@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.MuscleBalance
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.kettlebellLifts
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Lift tab over fixed state. What it does over the real ViewModel is in lift-nav's `TodayFlowTest`. */
class TodayScreenTest {
    @Test
    fun `without a program Today offers one and a free session`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        var choosing = false
        setThemedContent(dark = true) {
            TodayScreen(TodayUiState(), onStartSession = { started += it }, onChooseProgram = {
                choosing =
                    true
            })
        }
        onNodeWithText("No sessions yet").performScrollTo().assertExists()
        onNodeWithText("Follow a program").performScrollTo().performClick()
        assertEquals(true, choosing)
        onNodeWithText("Start session").performClick()
        assertEquals(listOf<ProgramKey?>(null), started)
    }

    @Test
    fun `the gear opens settings`() = runUiTest {
        var opened = 0
        setThemedContent(dark = true) {
            TodayScreen(TodayUiState(), onStartSession = {}, onChooseProgram = {}, onOpenSettings = { opened++ })
        }
        onNodeWithContentDescription("Settings").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `with a program Today lays out its next day`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val state = TodayUiState(
            following = program(ProgramId.GobletSquatRow),
            programDay = program(ProgramId.GobletSquatRow).dayAfter(0),
        )
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {})
        }
        onNodeWithText("Goblet squat and row").assertExists()
        onNodeWithText("Day 1 of 8 · week 1").assertExists()
        onNodeWithText("about 10 min").assertExists()
        onNodeWithText("Goblet squat").assertExists()
        onNodeWithContentDescription("0 of 8 days done").assertExists()
        onNodeWithText("Start session").assertDoesNotExist()
        onNodeWithText("Start day 1").performClick()
        // A free session is a row of its own, under On your own.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Free session"))
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
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {})
        }
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
        waitUntil(timeoutMillis = DIALOG_TIMEOUT_MILLIS) {
            onAllNodesWithText("Leave the session?").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, started.size)
        onNodeWithText("Start day 1").performClick()
        onAllNodesWithText("Start day 1").assertCountEquals(2)
        onAllNodesWithText("Start day 1").onLast().performClick()
        // The dialog's action runs once its exit has played.
        waitUntil(timeoutMillis = DIALOG_TIMEOUT_MILLIS) { started.size == 2 }
        assertEquals(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), started.last())
        onNodeWithText("Resume session").performClick()
        assertEquals(null, started.last())
    }

    @Test
    fun `the last session opens on a tap`() = runUiTest {
        val opened = mutableListOf<String>()
        val last =
            LiftSession(
                "s1",
                0,
                600_000,
                "Swings",
                listOf(SetLog.of(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)),
            )
        setThemedContent(dark = true) {
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
    fun `the balance shows once there is one and names what went untrained`() = runUiTest {
        fun session(uid: String, vararg sets: SetLog) = LiftSession(uid, 0, 600_000, "Day", sets.toList())
        val swings = session(
            "a",
            SetLog.of(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0),
            SetLog.of(2, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0),
        )
        val presses = session(
            "b",
            SetLog.of(3, BuiltInExercise.Press, 16.0, 5, Side.Left, 0),
            SetLog.of(4, BuiltInExercise.Press, 16.0, 5, Side.Right, 0),
        )
        var state by mutableStateOf(TodayUiState(last = presses))
        setThemedContent(dark = true) { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        onNodeWithText("Balance").assertDoesNotExist()

        state = state.copy(balance = MuscleBalance.of(listOf(presses, swings)))
        onNode(hasScrollAction()).performScrollToNode(hasText("Balance"))
        onNodeWithText("Sets, last 4 weeks").assertExists()
        onNodeWithContentDescription("Glutes and hamstrings: 2 sets worked it most, 0 assisted").assertExists()
        // A left and a right make one set.
        onNodeWithContentDescription("Shoulders: 1 set worked it most, 0 assisted").assertExists()
        onNode(hasScrollAction()).performScrollToNode(hasText("Not trained", substring = true))
        onNodeWithText("Not trained in four weeks: Chest, Quads and calves").assertExists()
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
        setThemedContent(dark = true) {
            TodayScreen(
                TodayUiState(following = long, programDay = long.dayAfter(0)),
                onStartSession = {},
                onChooseProgram = {},
            )
        }
        onNodeWithText("Start day 1").assertIsDisplayed()
        onNodeWithText("All programs").assertDoesNotExist()
    }

    @Test
    fun `a finished program's card says so and opens the program`() = runUiTest {
        var opened = 0
        val state = TodayUiState(following = program(ProgramId.GobletSquatRow), programDay = null)
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = {}, onChooseProgram = {}, onOpenProgram = { opened++ })
        }
        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithText("Finished · pick another program").assertDoesNotExist()
        onNodeWithText("Finished").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the pillar groups what it offers and opens each`() = runUiTest {
        val calls = mutableListOf<String>()
        val state = TodayUiState(ownedBells = listOf(16.0, 24.0))
        setThemedContent(dark = true) {
            TodayScreen(
                state,
                onStartSession = { calls += "start $it" },
                onChooseProgram = { calls += "programs" },
                onOpenBells = { calls += "gear" },
                onOpenExercises = { calls += "exercises" },
                onNewProgram = { calls += "new" },
                onOpenProgramSheet = { calls += "sheet ${it.stored}" },
            )
        }
        onNodeWithText("Following").assertExists()
        onNodeWithText("Beside the mat and the wall").assertExists()
        onNodeWithText("Strength for the mat").performClick()
        onNodeWithText("2 days a week · 8 weeks", substring = true).assertExists()
        onNodeWithContentDescription("New program").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("All programs"))
        onNodeWithText("All programs").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Your equipment"))
        onNodeWithText("16 kg · 24 kg · records").assertExists()
        onNodeWithText("Your equipment").performClick()
        onNodeWithText("Exercises").performClick()
        assertEquals(
            listOf(
                "sheet ${ProgramKey.BuiltIn(ProgramId.GrapplerStrength).stored}",
                "new",
                "programs",
                "gear",
                "exercises",
            ),
            calls,
        )
    }
}

/**
 * How long a dialog's exit may take before its action runs. Compose's default second is wall-clock time, too
 * short for a cold JVM on a CI runner busy with every module's tests.
 */
private const val DIALOG_TIMEOUT_MILLIS = 5_000L
