package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText
import kotlin.test.Test

/**
 * `journeys/move-plan.xml`: a plan the user can't do on its day goes to another, one day at a time: a missed one
 * from its day in the Log, today's from Today's Not today. The day it left reads as moved, not missed, and the day
 * it went to has it as any plan, with Undo.
 */
class MovePlanAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970: yesterday is Wednesday, epoch day -1.
    @Test
    fun `a workout missed yesterday moves to a day ahead and its day is no longer missed`() = acceptanceScenario {
        plannedOnceOn(-1, "Tabata")
        openTab("Log")
        seeDayOnTheWeek("Wednesday, missed")

        openDayOnTheWeek("Wednesday, missed")
        movePlanTo("Saturday")
        see("Moved Tabata to Saturday")
        planMoved(fromDay = -1, epochDay = 2)
        seeDayOnTheWeek("Wednesday, no workout")
        seeDayOnTheWeek("Saturday, planned")

        openDayOnTheWeek("Wednesday, no workout")
        see(hasText("Moved to Saturday", substring = true))
        dismissSheet()
        openDayOnTheWeek("Saturday, planned")
        see(hasText("Moved from Wednesday", substring = true))
    }

    @Test
    fun `today's workout is put off from Today and Undo puts it back`() = acceptanceScenario {
        openTab("Log")
        openPlanFromEmptyHistory()
        planTimerWorkoutEvery("Thursday")
        goBack()

        openTab("Today")
        putOffTo("Friday")
        planMoved(fromDay = 0, epochDay = 1)
        see("Do it today")
        seeDayOnTheWeek("Friday, planned")

        undoNotice()
        see("Not today")
    }
}
