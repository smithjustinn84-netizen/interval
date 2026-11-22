@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.trends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.TestId
import kotlin.test.Test
import kotlin.test.assertEquals

private val weeks = (0 until TREND_WEEKS).map { i ->
    val current = i == TREND_WEEKS - 1
    TrendWeek(weekStart = i * 7L, minutes = if (current) 30 else 60, lifted = 0.0, heartLoad = 0, current = current)
}

private val record = PersonalRecord.Weighted(
    BuiltInExercise.Snatch,
    heaviestKg = 24.0,
    repsAtHeaviest = 12,
    mostReps = 12,
    mostRepsKg = 24.0,
)

private val full = TrendsUiState(
    trends = Trends(
        weeks = weeks,
        recovery = listOf(RecoveryPoint(1, 22), RecoveryPoint(2, 27)),
        records = listOf(RecordMoment(3, "s", record)),
        recordsInWeeks = 1,
        tests = listOf(TestTrend(TestId.SnatchTest, Load.Bell(24.0), listOf(80, 100))),
    ),
    unit = WeightUnit.Kg,
    since = "Aug 31",
    records = listOf(RecordLine("Nov 19", RecordMoment(3, "s", record))),
    loading = false,
)

class TrendsScreenTest {
    @Test
    fun `nothing logged says how the page fills in`() = runUiTest {
        setThemedContent {
            TrendsScreen(TrendsUiState(Trends(weeks = weeks.map { it.copy(minutes = 0) }), loading = false), {})
        }
        onNodeWithText("Trends are drawn from what you log. Train for a week or two and they fill in.").assertExists()
        onNodeWithText("Time a week").assertDoesNotExist()
    }

    @Test
    fun `each card with something to draw is shown and the rest left out`() = runUiTest {
        setThemedContent { TrendsScreen(full, {}) }
        onNodeWithText("Since Aug 31").assertExists()
        onNodeWithText("Time a week").assertExists()
        onNodeWithText("This week 30 min · usual 1h 0m").assertExists()
        onNodeWithText("Lifted a week").assertDoesNotExist()
        onNodeWithText("Heart load a week").assertDoesNotExist()
        onNodeWithText("22 → 27 bpm").assertExists()
        onNodeWithText("1 record in 12 weeks").assertExists()
        onNodeWithText("Heaviest 24 kg × 12").assertExists()
        onNodeWithText("Snatch test").assertExists()
        onNodeWithText("80 reps → 100 reps").assertExists()
    }

    @Test
    fun `beside the log it closes rather than going back`() = runUiTest {
        val events = mutableListOf<String>()
        setThemedContent { TrendsScreen(full, { events += "back" }, inPane = true) }
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithContentDescription("Close trends").performClick()
        assertEquals(listOf("back"), events)
    }
}
