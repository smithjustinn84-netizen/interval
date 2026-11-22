@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BlockChange
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.history.LiftDetailScreen
import dev.justinsmith.lift.history.LiftDetailUiState
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

private val session = LiftSession(
    "s",
    0,
    600_000,
    "Swings",
    listOf(SetLog.of(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)),
)

/**
 * "How was it?" on a finished session's summary, what it does to the day's steps up, and what it said on the
 * session's page.
 */
class SummaryScreenTest {
    private val calls = mutableListOf<String>()
    private val healthNotes = mutableListOf<String?>()

    private val reflection = SummaryReflection(
        onEffortChange = { calls += "effort $it" },
        onNoteChange = { calls += "note $it" },
        onDescribeForHealth = { healthNotes += it },
    )

    @Test
    fun `the summary asks how it was and hands on the effort and the note`() = runUiTest {
        var finished by mutableStateOf(Finished(session, emptyList()))
        setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}, reflection = reflection) }
        onNodeWithText("How was it?").performScrollTo()
        onNodeWithText("Hard").performClick()
        onNode(hasSetTextAction()).performTextInput("Felt strong")
        waitForIdle()
        assertEquals(listOf("effort Hard", "note Felt strong"), calls)

        assertEquals(null, healthNotes.last())
        finished = Finished(session.copy(effort = PerceivedEffort.Hard, note = "Felt strong"), emptyList())
        waitForIdle()
        assertEquals("Hard · Felt strong", healthNotes.last())
    }

    @Test
    fun `the summary puts a session's bests on a card of their own`() = runUiTest {
        val finished = Finished(
            session,
            emptyList(),
            milestones = listOf(BuiltInExercise.TwoHandSwing to 1000),
            snatchTest = 100,
        )
        setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}) }
        onNodeWithText("Bests").performScrollTo()
        onNodeWithText("Snatch test: 100 in 5 minutes, your best").assertExists()
        onNodeWithText("1000 in all: Two-hand swing").assertExists()
    }

    @Test
    fun `the week's line says the session counts and says it once`() = runUiTest {
        var finished by mutableStateOf(
            Finished(session, emptyList(), week = WeekStanding(count = 3, goal = 4, streak = 0)),
        )
        setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}) }
        onNodeWithText("3 of 4 days this week").assertExists()
        onNodeWithText("Counts toward your week and your goal").assertDoesNotExist()

        // With no week to show, the line says it.
        finished = Finished(session, emptyList())
        onNodeWithText("Counts toward your week and your goal").assertExists()
    }

    @Test
    fun `the summary shows the muscles worked in the session`() = runUiTest {
        setThemedContent(dark = true) { SummaryScreen(Finished(session, emptyList()), onDone = {}) }
        onNodeWithText("Muscles worked").performScrollTo().assertExists()
        onNodeWithText("Glutes · Hamstrings").assertExists()
    }

    @Test
    fun `share offers the session as a picture of its card or in words`() = runUiTest {
        val texts = mutableListOf<String>()
        var picture: ByteArray? = null
        setThemedContent(dark = true) {
            SummaryScreen(
                Finished(session, emptyList()),
                onDone = {},
                onShare = { texts += it },
                onSharePicture = { picture = it },
            )
        }
        onNodeWithText("Share").performClick()
        onNodeWithText("Two-hand swing").assertExists()
        onNodeWithText("1 set").assertExists()
        onNodeWithText("Figure").assertExists()
        onNodeWithText("Pose").assertExists()
        onNodeWithText("Pose").performClick()
        onNodeWithText("Share picture").performClick()
        waitUntil { picture != null }
        val image = Image.makeFromEncoded(checkNotNull(picture))
        assertEquals(1080 to 1350, image.width to image.height)

        onNodeWithText("Share").performClick()
        onNodeWithText("Share as text").performClick()
        assertEquals(listOf("Swings\n10:00 · 240 kg moved · 10 reps"), texts)
    }

    @Test
    fun `a session with no bests has no card for them`() = runUiTest {
        setThemedContent(dark = true) { SummaryScreen(Finished(session, emptyList()), onDone = {}) }
        onNodeWithText("How was it?").performScrollTo()
        onNodeWithText("Bests").assertDoesNotExist()
    }

    @Test
    fun `a day that felt all out leaves its steps up off until one is turned on`() = runUiTest {
        val program = program(ProgramId.GobletSquatRow)
        val squats = program.days[0][0]
        val rows = program.days[0][1]
        val nudges = listOf(BlockChange(0, squats, squats.copy(reps = 11)), BlockChange(1, rows, rows.copy(reps = 11)))
        var finished by mutableStateOf(
            Finished(session, emptyList(), changes = PlanChanges(program, 1, 0, emptyList(), nudges)),
        )
        val kept = mutableListOf<Set<Int>>()
        setThemedContent(dark = true) {
            SummaryScreen(finished, onDone = {}, onKeep = { _, _, taken -> kept += taken })
        }
        // Said nothing yet: both steps up are on, and Save keeps them.
        onNodeWithText("Save as Goblet squat and row (yours)").assertExists()
        onNodeWithText("It felt very hard", substring = true).assertDoesNotExist()

        finished = finished.copy(session = session.copy(effort = PerceivedEffort.AllOut))
        onNodeWithText("It felt very hard: these wait until you turn them on.").performScrollTo()
        onNodeWithText("Save as Goblet squat and row (yours)").assertDoesNotExist()

        onNodeWithText("Row").performScrollTo().performClick()
        onNodeWithText("It felt very hard", substring = true).assertDoesNotExist()
        onNodeWithText("Save as Goblet squat and row (yours)").performScrollTo().performClick()
        assertEquals(listOf(setOf(1)), kept)
    }

    @Test
    fun `the summary's numbers count up as it arrives`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent(dark = true) { SummaryScreen(Finished(session, emptyList()), onDone = {}) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("0:00").assertExists()
        onNodeWithText("10:00").assertDoesNotExist()
        mainClock.advanceTimeBy(Motion.SUMMARY_RING_MILLIS + Motion.ENTER_MILLIS.toLong())
        onNodeWithText("10:00").assertExists()
        onNodeWithText("240 kg").assertExists()
    }

    @Test
    fun `a session's page shows its numbers at once`() = runUiTest {
        mainClock.autoAdvance = false
        setThemedContent(dark = true) {
            LiftDetailScreen(LiftDetailUiState(session, "Saturday", "1:21 PM"), onDelete = {}, onBack = {})
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("10:00").assertExists()
    }

    @Test
    fun `a session's page shows how it went for inline editing`() = runUiTest {
        val shown = LiftDetailUiState(
            session.copy(effort = PerceivedEffort.Steady, note = "Grip went"),
            "Saturday",
            "1:21 PM",
        )
        setThemedContent(dark = true) { LiftDetailScreen(shown, onDelete = {}, onBack = {}) }
        onNodeWithText("How it went").assertExists()
        onNodeWithText("Steady").assertExists()
        onNodeWithText("Grip went").assertExists()
    }

    @Test
    fun `a watch's heart rate shows on the summary and the session's page once it is read`() = runUiTest {
        var finished by mutableStateOf(Finished(session, emptyList()))
        setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}) }
        onNodeWithText("Average · ", substring = true).assertDoesNotExist()

        finished = Finished(session.copy(averageBpm = 128, peakBpm = 160), emptyList())
        onNodeWithContentDescription("128 beats per minute", substring = true).assertExists()
        onNodeWithText("Average · 160 peak").assertExists()
    }

    @Test
    fun `a session's page shows a watch's heart rate once it is read`() = runUiTest {
        val read = session.copy(averageBpm = 128, peakBpm = 160)
        setThemedContent(dark = true) {
            LiftDetailScreen(LiftDetailUiState(read, "Saturday", "1:21 PM"), onDelete = {}, onBack = {})
        }
        onNodeWithContentDescription("128 beats per minute", substring = true).assertExists()
        onNodeWithText("Average · 160 peak").assertExists()
    }
}
