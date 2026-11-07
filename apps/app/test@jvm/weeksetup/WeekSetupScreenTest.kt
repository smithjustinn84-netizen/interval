@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.weeksetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.interval.ui.weekdayMask
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

private val bjj = WeekSetupUiState(
    sport = 0,
    weekdays = weekdayMask(setOf(1, 3, 5)),
    bells = setOf(16.0),
    minutes = 15,
    week = BuiltWeek(
        sport = Sport.Bjj,
        sportDays = setOf(1, 3, 5),
        program = program(ProgramId.FirstHundredSwings),
        liftDays = setOf(2, 4, 6),
        goal = 6,
    ),
    programMinutes = 15..15,
)

class WeekSetupScreenTest {
    @Test
    fun `unanswered it asks three things and has nothing to build`() = runUiTest {
        setThemedContent { WeekSetupScreen(WeekSetupUiState(), WeekSetupActions()) }

        onNode(hasText("Set up") and isHeading()).assertExists()
        onNodeWithText("What else do you train?").assertExists()
        onNodeWithText("On which days?").assertDoesNotExist()
        onNodeWithText("Which bells do you own, in kg?").assertExists()
        onNodeWithText("How long is a session?").assertExists()
        onNode(hasText("15 min") and isSelectable()).assertIsSelected()
        onNodeWithText("Answer above and your week shows here.").assertExists()
        onNodeWithText("Tick a bell for strength days").assertDoesNotExist()
        onNodeWithText("Pick another program").assertDoesNotExist()
        onNode(hasText("Build my week") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `the answers are handed on as they are given`() = runUiTest {
        val calls = mutableListOf<String>()
        setThemedContent {
            WeekSetupScreen(
                bjj,
                WeekSetupActions(
                    onBack = { calls += "back" },
                    onSport = { calls += "sport $it" },
                    onWeekdays = { calls += "days $it" },
                    onBell = { calls += "bell $it" },
                    onMinutes = { calls += "minutes $it" },
                    onPrograms = { calls += "programs" },
                    onBuild = { calls += "build" },
                ),
            )
        }

        onNode(hasText("BJJ") and isSelectable()).assertIsSelected()
        onNode(hasText("Climbing") and isSelectable()).performClick()
        onNodeWithContentDescription("Monday").assertIsOn()
        onNodeWithContentDescription("Tuesday").assertIsOff().performClick()
        onNodeWithContentDescription("16 kg").assertIsOn()
        onNodeWithContentDescription("24 kg").assertIsOff().performClick()
        onNode(hasText("30 min") and isSelectable()).performScrollTo().performClick()
        onNodeWithText("Pick another program").performScrollTo().performClick()
        onNode(hasText("Build my week") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        onNodeWithText("Skip for now").performScrollTo().performClick()

        // Tuesday joins Monday, Wednesday and Friday: bits 0, 1, 2 and 4.
        assertEquals(
            listOf("sport 1", "days 23", "bell 24.0", "minutes 30", "programs", "build", "back"),
            calls,
        )
    }

    @Test
    fun `the week names the sport's days the program and the goal`() = runUiTest {
        setThemedContent { WeekSetupScreen(bjj, WeekSetupActions()) }

        onNode(hasText("BJJ") and hasText("Mon, Wed, Fri")).assertExists()
        onNode(hasText("Your first 100 swings") and hasText("about 15 min · Tue, Thu, Sat")).assertExists()
        onNodeWithText("Goal: 6 workouts a week").assertExists()
    }

    @Test
    fun `a program followed already says so in place of its length`() = runUiTest {
        val kept = bjj.copy(week = bjj.week.copy(keepsProgram = true, liftDays = emptySet(), goal = 1))
        setThemedContent { WeekSetupScreen(kept, WeekSetupActions()) }

        onNode(
            hasText("Your first 100 swings") and hasText("You follow it already · No day free for it"),
        ).assertExists()
        onNodeWithText("Goal: 1 workout a week").assertExists()
    }

    @Test
    fun `what is missing of the sport is said where its days would be`() = runUiTest {
        var state by mutableStateOf(bjj.copy(weekdays = 0, week = BuiltWeek()))
        setThemedContent { WeekSetupScreen(state, WeekSetupActions()) }
        onNode(hasText("BJJ") and hasText("Pick its days")).assertExists()

        state = state.copy(sport = Sport.OTHER)
        onNode(hasText("Another sport") and hasText("Name it above")).assertExists()

        state = state.copy(sport = SPORT_NONE, bells = setOf(16.0))
        onNodeWithText("On which days?").assertDoesNotExist()
        onNodeWithText("No day left for strength").assertExists()
    }

    @Test
    fun `a sport of the user's own is typed under the picks`() = runUiTest {
        val names = mutableListOf<String>()
        setThemedContent {
            WeekSetupScreen(
                WeekSetupUiState(sport = Sport.OTHER),
                WeekSetupActions(onSportName = { names += it }),
            )
        }

        onNode(hasSetTextAction()).performTextInput("Judo")
        waitForIdle()
        assertEquals("Judo", names.last())
    }

    @Test
    fun `a tablet shows the page in one centred column with everything in view`() = runUiTest(Window.Tablet) {
        setThemedContent { WeekSetupScreen(bjj, WeekSetupActions()) }

        onNodeWithText("What else do you train?").assertExists()
        onNodeWithText("Goal: 6 workouts a week").assertExists()
        onNode(hasText("Build my week") and hasClickAction()).assertIsEnabled()
    }
}
