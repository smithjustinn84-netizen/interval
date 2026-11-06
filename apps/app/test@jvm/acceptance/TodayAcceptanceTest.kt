package dev.justinsmith.interval.acceptance

import dev.justinsmith.lift.data.FollowingEntity
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
        seePick("Wrists and fingers")
        startPick("Wrists and fingers")
        seePhase("Get ready")
        seeMove("Wrist circles", "Move 1 of 5")
        timerIsAsItWas()
    }

    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a session planned for today is up next on today and starts from it`() = acceptanceScenario {
        openPlanFromToday()
        planAnyLiftSessionEvery("Thursday")
        goBack()

        seeUpNext("Lift session")
        startSessionFromToday()
        logASetAndFinish()

        seeTodayTab()
        seeTodayDoneOnTheWeek()
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
        pickMinutes(10)
        seeNoStrengthPick(minutes = 15)
        pickMinutes(15)
        lookAtStrengthPick(minutes = 15)
        tap("Start day 1")
        seeLiftingPage()
    }
}
