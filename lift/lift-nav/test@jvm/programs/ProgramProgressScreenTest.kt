@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.programs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.scrollToText
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.domain.programProgress
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.liftAppTest
import dev.justinsmith.lift.ui.setLiftContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Program page over fixed state: the map, the days done, and the end of a program. */
class ProgramProgressScreenTest {
    private val goblets: Program = program(ProgramId.GobletSquatRow)

    /** Day [day] of Goblet squat and row, every set done: 3 goblet squats, then 3 rows a side. */
    private fun dayDone(day: Int, program: ProgramKey = goblets.key): LiftSession {
        val at = day * 1_000L
        val sets = List(3) { SetLog(it + 1L, BuiltInExercise.GobletSquat, 16.0, 10, null, at) } +
            Side.entries.flatMap { side -> List(3) { SetLog(it + 4L, BuiltInExercise.Row, 16.0, 10, side, at) } }
        return LiftSession("s$day", at, at + 1, "Goblet squat and row", sets, program, day = day)
    }

    private fun state(vararg days: Int, followed: Followed = Followed(goblets, 0)): ProgramProgressUiState {
        val sessions = days.map { dayDone(it, followed.program.key) }.sortedByDescending { it.startedAtEpochMillis }
        val progress = programProgress(followed, sessions)
        return ProgramProgressUiState(
            progress,
            progress.done.map {
                DoneRow(
                    it,
                    if (it.session ==
                        null
                    ) {
                        ""
                    } else {
                        "Mon"
                    },
                )
            },
        )
    }

    @Test
    fun `in progress the page maps the days done and next and lists each day done`() = runUiTest {
        setLiftContent { ProgramProgressScreen(state(1, 2), ProgramProgressActions()) }
        onNodeWithText("Day 3 of 8 · week 2").assertIsDisplayed()
        onNodeWithText("2").assertIsDisplayed()
        onNodeWithText("6").assertIsDisplayed()
        onNodeWithContentDescription("Day 1: done").assertIsDisplayed()
        onNodeWithContentDescription("Day 3: next").assertIsDisplayed()
        onNodeWithContentDescription("Day 8: to come").assertIsDisplayed()
        onNodeWithText("Day 2").assertIsDisplayed()
        onAllNodesWithText("Mon · 9 of 9 sets").assertCountEquals(2)
        onNodeWithText("Follow again").assertDoesNotExist()
    }

    @Test
    fun `a done day opens its session and a deleted one reads as not logged`() = runUiTest {
        val opened = mutableListOf<String>()
        setLiftContent {
            ProgramProgressScreen(state(1, 3), ProgramProgressActions(onOpenSession = { opened += it }))
        }
        onNodeWithContentDescription("Day 2: not logged").assertIsDisplayed()
        onNodeWithText("Day 3").performClick()
        assertEquals(listOf("s3"), opened)
    }

    @Test
    fun `a copy's days before show done with nothing to open`() = runUiTest {
        val copy = goblets.copy(key = ProgramKey.Custom("c"), name = "Goblet squat and row (yours)")
        setLiftContent {
            ProgramProgressScreen(state(3, followed = Followed(copy, 0, daysBefore = 2)), ProgramProgressActions())
        }
        onNodeWithText("Days 1 to 2").assertIsDisplayed()
        onNodeWithText("Done before the copy").assertIsDisplayed()
        onNodeWithContentDescription("Day 1: done before").assertIsDisplayed()
        onNodeWithContentDescription("Day 3: done").assertIsDisplayed()
        onNodeWithText("Day 3").assertIsDisplayed()
    }

    @Test
    fun `before the first day the list says so`() = runUiTest {
        setLiftContent { ProgramProgressScreen(state(), ProgramProgressActions()) }
        onNodeWithText("Day 1 of 8 · week 1").assertIsDisplayed()
        onNodeWithText("No days done yet. Start day 1 from Lift.").assertIsDisplayed()
    }

    @Test
    fun `finished the page sums the program and offers Follow again`() = runUiTest {
        val chosen = mutableListOf<String>()
        setLiftContent {
            ProgramProgressScreen(
                state(1, 2, 3, 4, 5, 6, 7, 8),
                ProgramProgressActions(onFollowAgain = { chosen += "again" }, onPickAnother = { chosen += "another" }),
            )
        }
        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithText("8").assertIsDisplayed()
        onNodeWithText("Reps").assertIsDisplayed()
        onNodeWithText("720").assertIsDisplayed()
        onNodeWithText("Moved").assertIsDisplayed()
        scrollToText("Records set")
        scrollToText("Follow again")
        onNodeWithText("Follow again").performClick()
        scrollToText("Pick another program")
        onNodeWithText("Pick another program").performClick()
        assertEquals(listOf("again", "another"), chosen)
    }

    @Test
    fun `on a tablet the days done sit beside the map`() = runUiTest(Window.Tablet) {
        setLiftContent { ProgramProgressScreen(state(1), ProgramProgressActions()) }
        val week = onNodeWithText("Week 1").getBoundsInRoot()
        val day = onNodeWithText("Day 1").getBoundsInRoot()
        assertTrue(day.left >= week.right || day.right <= week.left, "the days done should sit beside the map")
    }

    @Test
    fun `Follow again starts the program at day 1 and keeps the run in History`() = liftAppTest(
        FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = goblets.key.stored, startedAtEpochMillis = 0)
            sessions.sessions.value = (1..8).map { day ->
                val at = day * DAY_MILLIS
                SessionEntity(
                    "d$day",
                    at,
                    at + 1,
                    "Goblet squat and row",
                    at + 1,
                    programId = goblets.key.stored,
                    programDay = day,
                )
            }
        },
    ) { app ->
        app.clock.set(9 * DAY_MILLIS)
        waitForText("Finished")
        onNodeWithText("Finished").performClick()
        waitForText("Days done")
        scrollToText("Follow again")
        onNodeWithText("Follow again").performClick()
        waitForText("Day 1 of 8 · week 1")
        assertEquals(9 * DAY_MILLIS, app.storage.following.row.value?.startedAtEpochMillis)
        assertEquals(8, app.storage.sessions.sessions.value.size)
    }
}

private const val DAY_MILLIS = 86_400_000L
