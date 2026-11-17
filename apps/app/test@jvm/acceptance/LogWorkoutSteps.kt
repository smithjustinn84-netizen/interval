package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.timer.nameOf
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
// Logging a workout after the fact, from a day's sheet in the Log; beside the driver, which is at its size limit.

/** On Plan: a weekday's + and the add sheet as it opens, on the Timer's workout, then Add to plan. */
fun IntervalAcceptanceDriver.planTimerWorkoutEvery(weekday: String) {
    tapDescribed("Add a workout every $weekday")
    tap(hasText("Add to plan") and hasClickAction())
    waitFor("a workout planned") { app.repository.plans.planned.value.any { it.workout is PlanWorkout.Interval } }
}

/** A day of the week strip, by how it is read out: "Wednesday, done". */
fun IntervalAcceptanceDriver.seeDayOnTheWeek(day: String) = see(hasContentDescription(day))

/** The day sheet's Log a workout, then the preset [name] and Log it. */
fun IntervalAcceptanceDriver.logAWorkoutOnItsDay(name: String) {
    tap("Log a workout")
    tap(hasText(name) and isSelectable())
    tap(hasText("Log it") and hasClickAction())
}

/** The day sheet's Log a workout, its Mobility side, then the flow [name] and Log it. */
fun IntervalAcceptanceDriver.logAFlowOnItsDay(name: String) {
    tap("Log a workout")
    tap(hasText("Mobility") and isSelectable())
    tap(hasText(name) and isSelectable())
    tap(hasText("Log it") and hasClickAction())
}

/** The day sheet's Log it, on the workout planned that day. */
fun IntervalAcceptanceDriver.logPlanFromItsDay() = tap("Log it")

/** The plan's row on its day reads done, its shape after: "Done · Every Thursday · 30s on …". */
fun IntervalAcceptanceDriver.seePlanDoneOnItsDay(repeat: String) = see(hasText("Done · $repeat", substring = true))

/** The log lists [name] under [day]. */
fun IntervalAcceptanceDriver.seeLoggedUnder(day: String, name: String) {
    see(day)
    see(hasText(name) and hasClickAction())
}

fun IntervalAcceptanceDriver.workoutsLogged(count: Int) = waitFor("$count workout(s) logged") {
    app.repository.history.history.value.size == count
}

/**
 * A workout run in full is kept on the epoch day [epochDay] (the app's clock starts on day 0, so yesterday is -1),
 * named [name] by its preset or flow when one is given.
 */
fun IntervalAcceptanceDriver.workoutLoggedOn(epochDay: Long, name: String? = null) =
    waitFor("${name ?: "a workout"} logged on day $epochDay") {
        val presets = app.repository.presets.presets.value
        val flows = app.repository.flows.flows.value
        app.repository.history.history.value.any {
            it.finishedAtEpochMillis.floorDiv(DAY_MILLIS) == epochDay &&
                !it.endedEarly &&
                (name == null || presets.nameOf(it.config, flows) == name)
        }
    }

/**
 * Given: Press ladders followed on Lift since [daysAgo] days before the app's clock, then the clock back where it was,
 * so the days since can have the program's day logged on them.
 */
fun IntervalAcceptanceDriver.followingPressLaddersSince(daysAgo: Int) {
    val now = app.clock.get()
    app.clock.set(now - daysAgo * DAY_MILLIS)
    lift.programs.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))
    app.clock.set(now)
    waitFor("Press ladders followed") { lift.programs.programDay.value?.number == 1 }
}

/** The day sheet's Log a workout, which leads with the program's next day on its Strength side, then Log it. */
fun IntervalAcceptanceDriver.logTheProgramDayAsPlanned() {
    tap("Log a workout")
    see(hasText("Strength") and isSelectable())
    see(hasText("Done as planned", substring = true))
    tap(hasText("Log it") and hasClickAction())
}

/** Day [day] of the program is kept as a session finished on the epoch day [epochDay], each of its sets done. */
fun IntervalAcceptanceDriver.programDayLoggedOn(day: Int, epochDay: Long) = waitFor("day $day logged on $epochDay") {
    lift.sessions.sessions.value.any { session ->
        session.day == day &&
            session.finishedAtEpochMillis?.floorDiv(DAY_MILLIS) == epochDay &&
            session.sets.size == lift.programs.following.value?.day(day)?.plannedSets()?.size
    }
}

/** The program followed is at day [day]. */
fun IntervalAcceptanceDriver.programAtDay(day: Int) = waitFor("the program at day $day") {
    lift.programs.programDay.value?.number == day
}

private val IntervalAcceptanceDriver.lift: LiftRepository get() = app.koin.koin.get()
