package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout

// A routine described in words on Plan, beside the driver, which is at its size limit.

/** On Plan: opens Describe a routine and types [words] into it. */
fun IntervalAcceptanceDriver.describeRoutineOnPlan(words: String) {
    tapDescribed("Describe a routine")
    see(hasText("Describe a routine") and hasAnyAncestor(isDialog()))
    typeInto("Routine in words", words)
}

/** The routine's preview names a workout by [name] and reads its days as [days] ("Mon"). */
fun IntervalAcceptanceDriver.seeRoutineWorkout(name: String, days: String) {
    see(hasText(name, substring = true) and hasAnyAncestor(isDialog()))
    see(hasText(days, substring = true) and hasAnyAncestor(isDialog()))
}

/** Add to plan, which plans the routine: one workout of [blocks] blocks, every [weekday] (1 for Monday). */
fun IntervalAcceptanceDriver.planRoutineOfOneSequence(blocks: Int, weekday: Int) {
    tap(hasText("Add to plan") and hasClickAction())
    waitFor("one workout of $blocks blocks planned every weekday $weekday") {
        val plan = app.repository.plans.planned.value.singleOrNull()
        (plan?.workout as? PlanWorkout.Interval)?.config?.blocks?.size == blocks &&
            plan.schedule == PlanSchedule.Weekly(weekday)
    }
}
