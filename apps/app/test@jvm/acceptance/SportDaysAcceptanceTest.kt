package dev.justinsmith.interval.acceptance

import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * `journeys/sport-days.xml`: a class or a day on the wall, trained away from the app, goes on the plan, shows on
 * the week beside the workouts done here, and is logged with one tap, which counts it toward the week. On its day
 * a flow to do before it leads Today, with the class beside it.
 */
class SportDaysAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a class planned for today shows under its day on the week`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        seeSportPlannedOn("BJJ class", weekday = "Thursday")
        goBack()

        seeTodayTab()
        seeSportOnTheWeek("Today, BJJ planned")
        seeAlsoToday("BJJ class")
    }

    @Test
    fun `a class logged from today counts toward the week and can be undone`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()

        seeAlsoToday("BJJ class")
        logClassFromToday()
        seeSportOnTheWeek("Today, done, BJJ logged")
        seeThisWeekOnToday("1 workout")
        seeNothingMoreDue()
        sportDaysLogged(1)

        undoNotice()
        sportDaysLogged(0)
        seeSportOnTheWeek("Today, BJJ planned")
        seeAlsoToday("BJJ class")
    }

    @Test
    fun `on a class day a flow leads and once it is done the class does`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()

        seeFlowLeads("Before class", "Hips and shoulders")
        seeAlsoToday("BJJ class")
        startFlowFromToday()
        seePhase("Get ready")
        seeMove("90/90 hip switch", "Move 1 of 6")
        letTimePass(481.seconds)
        seeFlowSaved("Hips and shoulders", moves = 6)
        timerIsAsItWas()
        closeSummary()

        // The flow done, the class is what is left of the day, and leads it.
        seeTodayTab()
        seeNoFlowSuggested()
        seeUpNext("BJJ class")
        logClassFromToday()
        sportDaysLogged(1)
        seeThisWeekOnToday("1 mobility · 1 sport day")
        seeNothingMoreDue()
    }

    @Test
    fun `before a day on the wall the wrists lead`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("Climbing", weekday = "Thursday")
        goBack()

        seeFlowLeads("Before climbing", "Wrists and fingers")
        seeAlsoToday("Climbing")
    }

    @Test
    fun `on a class day the program steps back and returns once the class is logged`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            val program = ProgramId.FifteenMinuteKettlebell.name
            following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        },
    ) {
        // With no class, the program's next day leads.
        seeUpNext("15-minute kettlebell plan")
        seeProgramDayLeads()
        openTab("History")
        openPlanFromHistory()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        openTab("Today")

        seeFlowLeads("Before class", "Hips and shoulders")
        seeAlsoToday("BJJ class")
        seeAlsoToday("15-minute kettlebell plan")
        seeProgramDayStandsBack()

        logClassFromToday()
        sportDaysLogged(1)
        seeUpNext("15-minute kettlebell plan")
        seeProgramDayLeads()
    }

    @Test
    fun `the program's day starts from beside the class`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            val program = ProgramId.FifteenMinuteKettlebell.name
            following.row.value = FollowingEntity(programId = program, startedAtEpochMillis = 0)
        },
    ) {
        openTab("History")
        openPlanFromHistory()
        planSportEvery("Climbing", weekday = "Thursday")
        goBack()
        openTab("Today")

        seeAlsoToday("15-minute kettlebell plan")
        doProgramDayNow()
        seeLiftingPage()
    }

    @Test
    fun `a workout planned on a class day still leads and the class is logged beside it`() = acceptanceScenario {
        openPlanFromToday()
        planAnyLiftSessionEvery("Thursday")
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()

        seeUpNext("Lift session")
        seeAlsoToday("BJJ class")
        seeNoFlowSuggested()
        logClassFromToday()

        seeSportOnTheWeek("Today, done, BJJ logged")
        sportDaysLogged(1)
        seeUpNext("Lift session")
        seeNo("Also today")
    }

    @Test
    fun `a class logged from its day in history can be removed there`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        openTab("History")

        openDayOnTheWeek("Today, BJJ planned")
        logClassFromItsDay()
        sportDaysLogged(1)
        seeClassLoggedOnItsDay("BJJ class")

        removeLoggedClass("BJJ class")
        sportDaysLogged(0)
        seeSportOnTheWeek("Today, BJJ planned")
    }
}
