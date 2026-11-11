package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.builtInPresets
// Moving a plan to another day, from its day in the Log or from Today; beside the driver, which is at its size limit.

/** Given: [name], a built-in, planned once on the epoch day [epochDay] (the app's clock starts on day 0). */
fun IntervalAcceptanceDriver.plannedOnceOn(epochDay: Long, name: String) {
    app.repository.plans.planOnce(builtInPresets.first { it.name == name }.asPlan(), epochDay)
    waitFor("$name planned on day $epochDay") { app.repository.plans.planned.value.isNotEmpty() }
}

/** The day sheet's Move, on its one plan that can move, then the day [weekday] and Move to. */
fun IntervalAcceptanceDriver.movePlanTo(weekday: String) {
    tap(hasText("Move") and hasClickAction())
    tap(hasText(weekday) and isSelectable())
    tap("Move to $weekday")
}

/** Today's Not today under the step leading the day, then Move to the day it suggests, [weekday]. */
fun IntervalAcceptanceDriver.putOffTo(weekday: String) {
    tap("Not today")
    tap("Move to $weekday")
}

/** The store holds the plan once, on the epoch day [epochDay], moved there from [fromDay]. */
fun IntervalAcceptanceDriver.planMoved(fromDay: Long, epochDay: Long) = waitFor("a plan moved to day $epochDay") {
    app.repository.plans.planned.value.any { it.schedule == PlanSchedule.Once(epochDay, fromDay) }
}
