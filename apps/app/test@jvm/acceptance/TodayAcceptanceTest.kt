package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/today.xml`: the app opens on Today, which leads to the plan, starts what is up next, or starts a
 * preset that fits a few minutes.
 */
class TodayAcceptanceTest {
    @Test
    fun `today opens first and leads to the plan while nothing is planned`() = acceptanceScenario {
        seeTodayTab()
        seeNothingPlanned()
        openPlanFromToday()
        goBack()
        seeTodayTab()
    }

    @Test
    fun `a pick looked at before start is planned for later instead`() = acceptanceScenario {
        pickMinutes(5)
        lookAtPick("Tabata")
        planForLaterOnce()
        plannedOnce("Tabata")
        seeTodayTab()
    }

    @Test
    fun `a preset picked by time runs and ends back on today`() = acceptanceScenario {
        pickMinutes(5)
        seePick("Tabata")
        startPick("Tabata")
        seePhase("Get ready")
        skipPhase()
        seePhase("Work")
        seeRound("Round 1 of 8")
        timerHolds(workSeconds = 20, restSeconds = 10, rounds = 8)

        askToEnd()
        seeEndDialogSaying("nothing is saved")
        confirmEnd()
        seeTodayTab()
    }

    @Test
    fun `a flow that fits is offered by time and runs with the timer left alone`() = acceptanceScenario {
        pickMinutes(5)
        seePick("Daily five")
        startPick("Daily five")
        seePhase("Get ready")
        seeMove("Cat-cow", "Move 1 of 5")
        timerIsAsItWas()
    }

    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a session planned for today is up next on today and starts from it`() = acceptanceScenario {
        openPlanFromToday()
        planAnyLiftSessionEvery("Thursday")
        raiseWeeklyGoal()
        seeWeeklyGoal(3)
        goBack()

        seeUpNext("Lift session")
        seeThisWeekOnToday("0 of 3 done")
        startSessionFromToday()
        logASetAndFinish()

        seeTodayTab()
        seeTodayDoneOnTheWeek()
        seeThisWeekOnToday("1 of 3 done")
        seeNothingMoreDue()
    }

    @Test
    fun `the program's next day is up next and says about how long it takes`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            val program = ProgramId.FifteenMinuteKettlebell.name
            following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        },
    ) {
        seeUpNext("15-minute kettlebell plan")
        seeUpNextTakes("about 15 min")
    }

    @Test
    fun `the program's day is offered by time when it fits and starts from its look`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            val program = ProgramId.FifteenMinuteKettlebell.name
            following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        },
    ) {
        // The program's day leads, so the picks are folded under it.
        seePicksFolded(minutes = 10)
        openSomethingElse()
        pickMinutes(10)
        seeNoStrengthPick(minutes = 15)
        pickMinutes(15)
        lookAtStrengthPick(minutes = 15)
        tap("Start day 1")
        seeLiftingPage()
    }

    @Test
    fun `a day with nothing planned is a rest day and tomorrow's stands back`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Friday")
        goBack()

        // The app's clock starts on a Thursday.
        seeCoachSays("rest day", "Nothing is planned today. Rest is part of the week.")
        see("Rest day")
        see("Nothing due until tomorrow")
        see("Up next · Tomorrow")
        see("BJJ class")
        seeNo("Log it")
        seePicksFolded(minutes = 10)
    }

    @Test
    fun `after a very hard session today says so and starts the program's day easier`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            // A session finished an hour before the clock's start, said to have felt very hard.
            val at = -HOUR_MILLIS
            sessions.sets.value += SetEntity(0, "hard", BuiltInExercise.Snatch.key, 24.0, 20, null, at)
            sessions.sessions.value += SessionEntity(
                "hard",
                at - HOUR_MILLIS,
                at,
                "Snatches",
                at,
                effort = PerceivedEffort.VeryHard.level,
            )
            following.row.value = FollowingEntity(programId = ProgramId.BjjKettlebell.name, startedAtEpochMillis = 0)
        },
    ) {
        seeCoachSays("strength day", "The last session felt very hard. Take today a notch easier.")
        see("Easier today")
        tap("Start day 1, easier")
        // The ABC on the minute is thrown: its 15 minutes are 12 already, with no offer left to take.
        see("Start EMOM")
        see(hasText("1 of 12 sets", substring = true))
        sessionIsEased()
    }
}

private const val HOUR_MILLIS = 3_600_000L
