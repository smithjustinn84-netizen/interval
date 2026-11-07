package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.snatchIntervals
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import kotlin.test.assertEquals

// Set up's steps, apart from the driver they extend: it is as large as a class may be.

fun IntervalAcceptanceDriver.seeSetUp() = see("What else do you train?")

/** From Today's card, which offers it while nothing is planned. */
fun IntervalAcceptanceDriver.openSetUpFromToday() {
    tap("Set up your week")
    seeSetUp()
}

/** From Settings' Plan group, where it always is. */
fun IntervalAcceptanceDriver.openSetUpFromSettings() {
    scrollTo("Set up")
    tap(hasText("Set up") and hasClickAction())
    seeSetUp()
}

/** The first answer: a sport by name, "Another sport" or "Just the bell". */
fun IntervalAcceptanceDriver.answerSport(name: String) = tap(hasText(name) and isSelectable())

/** The second: each of [weekdays] ("Monday") tapped, which turns it on or off. */
fun IntervalAcceptanceDriver.trainOn(vararg weekdays: String) = weekdays.forEach {
    tap(hasContentDescription(it) and isToggleable())
}

/** The third: each bell of [kg] tapped, which turns it on or off. */
fun IntervalAcceptanceDriver.ownBells(vararg kg: Int) = kg.forEach {
    tap(hasContentDescription("$it kg") and isToggleable())
}

/** Of the bells owned, each of [kg] tapped as owned two of, which turns it on or off. */
fun IntervalAcceptanceDriver.ownTwoOf(vararg kg: Int) = kg.forEach {
    tap(hasContentDescription("Two of $it kg") and isToggleable())
}

/** The fourth: how long a session is. */
fun IntervalAcceptanceDriver.sessionOf(minutes: Int) = tap(hasText("$minutes min") and isSelectable())

fun IntervalAcceptanceDriver.seeAnswered(sport: String, weekday: String, kg: Int) {
    see(hasText(sport) and isSelected())
    see(hasContentDescription(weekday) and isOn())
    see(hasContentDescription("$kg kg") and isOn())
}

/** A line of "Your week": what it names, and the days it falls on or how it stands. */
fun IntervalAcceptanceDriver.seeYourWeek(name: String, detail: String) = see(
    hasText(name) and hasText(detail, substring = true),
)

fun IntervalAcceptanceDriver.seeGoalToBe(goal: Int) = see("Goal: $goal workouts a week")

/** Build my week, which lands on Today and says so. */
fun IntervalAcceptanceDriver.buildMyWeek() {
    tap("Build my week")
    see("Week built")
    seeTodayTab()
}

/** What building left in both sides' stores. */
fun IntervalAcceptanceDriver.weekHolds(
    sportDays: Int,
    programDays: Int,
    program: ProgramId?,
    bells: List<Double>,
    goal: Int?,
) {
    val lift = app.koin.koin.get<LiftRepository>()
    waitFor("$sportDays sport and $programDays program days planned, following $program") {
        val planned = app.repository.plans.planned.value.map { it.workout }
        planned.count { it is PlanWorkout.SportDay } == sportDays &&
            planned.count { it == PlanWorkout.Lift.ProgramDay } == programDays &&
            lift.programs.followed.value?.program?.key == program?.let { ProgramKey.BuiltIn(it) }
    }
    assertEquals(bells, lift.gear.bells.value)
    assertEquals(goal, app.repository.settings.appSettings.value.weeklyGoal)
}

/** The bells of [kg] kept as owned two of, and the intervals beside the program planned on [intervalDays] days. */
fun IntervalAcceptanceDriver.pairsAndIntervalsHeld(kg: Set<Double>, intervalDays: Int) {
    val lift = app.koin.koin.get<LiftRepository>()
    waitFor("pairs $kg and $intervalDays days of intervals") {
        lift.gear.pairs.value == kg &&
            app.repository.plans.planned.value.count { it.workout == snatchIntervals.asPlan() } == intervalDays
    }
}

/** The session's length is kept, and Today's pick by time stands on it. */
fun IntervalAcceptanceDriver.seeMinutesPicked(minutes: Int) {
    see(hasText("$minutes min") and isSelected())
    assertEquals(minutes, app.repository.settings.appSettings.value.sessionMinutes)
}

/** Whether Set up has had its turn, which keeps it from opening by itself again. */
fun IntervalAcceptanceDriver.setUpOffered(offered: Boolean) = waitFor("Set up offered: $offered") {
    app.repository.settings.appSettings.value.setupOffered == offered
}

fun IntervalAcceptanceDriver.seeNoSetUp() = seeNo("What else do you train?")
