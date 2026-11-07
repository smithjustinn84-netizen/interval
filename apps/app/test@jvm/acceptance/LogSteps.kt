package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import dev.justinsmith.interval.model.PlanWorkout

// Log's week and the look before Start, beside the driver, which is at its size limit.

/** Log's week bar of [kind], as it is read out: "Sport days: 1 of 1". */
fun IntervalAcceptanceDriver.seeKindBar(kind: String, done: Int, of: Int) =
    see(hasContentDescription("$kind: $done of $of"))

/** Log's week lists [name] under what is coming up. */
fun IntervalAcceptanceDriver.seeComingUp(name: String) {
    see("Coming up")
    see(name)
}

/** A row of Coming up, by the day in its column ("Today", "Sat") and its name. */
fun IntervalAcceptanceDriver.seeComingUpOn(day: String, name: String) = see(hasText(day) and hasText(name))

/** Log's "Edit plan", beside Coming up while anything is planned. */
fun IntervalAcceptanceDriver.openPlanFromLog() {
    tap("Edit plan")
    see("Weekly goal")
}

/** Clearing the log is Settings' (Your data), opened from Log's gear and left again once it is done. */
fun IntervalAcceptanceDriver.clearHistory() {
    openSettings()
    scrollTo("Clear the log")
    tap("Clear the log")
    see("Clear the log?")
    tap(hasText("Clear") and hasAnyAncestor(isDialog()))
    // Back is in the title bar, scrolled away with the page's top.
    scrollTo("Cues")
    goBack()
}

/** A pick by time's row, which opens a look at it before Start. */
fun IntervalAcceptanceDriver.lookAtPick(name: String) {
    tap(hasText(name) and hasClickAction())
    see("Plan for later")
}

/** The look's Plan for later, then the add sheet as it opens: once, tomorrow. */
fun IntervalAcceptanceDriver.planForLaterOnce() {
    tap("Plan for later")
    see("Once")
    tap(hasText("Add to plan") and hasClickAction())
}

fun IntervalAcceptanceDriver.plannedOnce(name: String) = waitFor("$name planned") {
    app.repository.plans.planned.value.any { (it.workout as? PlanWorkout.Interval)?.name == name }
}

/** The program's day among the picks by time: its row, the one with the day and its minutes alone. */
private fun strengthRow(minutes: Int) =
    hasText("$minutes min") and hasText("Day 1 of", substring = true) and hasClickAction()

fun IntervalAcceptanceDriver.seeNoStrengthPick(minutes: Int) = seeNo(strengthRow(minutes))

/** The program's day among the picks by time, which opens a look at it. */
fun IntervalAcceptanceDriver.lookAtStrengthPick(minutes: Int) {
    tap(strengthRow(minutes))
    see("Plan for later")
}
