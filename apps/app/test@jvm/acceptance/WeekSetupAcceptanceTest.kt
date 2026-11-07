package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `journeys/setup.xml`: Set up asks what else you train, on which days, which bells you own and how long a
 * session is, and builds the week from the answers: the sport on its days, a program that one bell and the time
 * are enough for on the days between, the bells and a goal. A new install opens on it, once; after that it is
 * offered while Today has nothing planned, and is always in Settings, where it shows what stands and builds again
 * without starting the program over.
 */
class WeekSetupAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `four answers build the week and today leads with it`() = acceptanceScenario {
        seeNothingPlanned()
        openSetUpFromToday()
        answerSport("BJJ")
        trainOn("Monday", "Wednesday", "Friday")
        ownBells(16)
        sessionOf(15)

        seeYourWeek("BJJ", "Mon, Wed, Fri")
        seeYourWeek("Your first 100 swings", "Tue, Thu, Sat")
        seeGoalToBe(6)
        buildMyWeek()

        weekHolds(
            sportDays = 3,
            programDays = 3,
            program = ProgramId.FirstHundredSwings,
            bells = listOf(16.0),
            goal = 6,
        )
        // Thursday is one of the program's days: its first leads, and says about how long it takes.
        seeUpNext("Your first 100 swings")
        seeUpNextTakes("about 15 min")
        seeMinutesPicked(15)
    }

    @Test
    fun `a grappler with a pair gets twenty minutes beside the mat and the intervals between`() = acceptanceScenario {
        openSetUpFromToday()
        answerSport("BJJ")
        trainOn("Tuesday", "Thursday")
        ownBells(24)
        ownTwoOf(24)
        sessionOf(20)

        seeYourWeek("20 minutes beside BJJ", "Mon, Fri")
        seeYourWeek("15:15 MVO2", "Wed")
        seeGoalToBe(5)
        buildMyWeek()

        weekHolds(sportDays = 2, programDays = 2, program = ProgramId.BjjKettlebell, bells = listOf(24.0), goal = 5)
        pairsAndIntervalsHeld(setOf(24.0), intervalDays = 1)
    }

    @Test
    fun `just the bell asks for no days and plans strength alone`() = acceptanceScenario {
        openSetUpFromToday()
        answerSport("BJJ")
        see("On which days?")
        answerSport("Just the bell")
        seeNo("On which days?")
        ownBells(16)
        sessionOf(30)

        seeYourWeek("Full body for beginners", "Mon, Thu")
        seeGoalToBe(2)
        buildMyWeek()

        weekHolds(sportDays = 0, programDays = 2, program = ProgramId.BeginnerFullBody, bells = listOf(16.0), goal = 2)
    }

    @Test
    fun `with no bell the sport's days are planned alone`() = acceptanceScenario {
        openSetUpFromToday()
        answerSport("Climbing")
        trainOn("Tuesday", "Saturday")

        seeYourWeek("Climbing", "Tue, Sat")
        see("Tick a bell for strength days")
        buildMyWeek()

        weekHolds(sportDays = 2, programDays = 0, program = null, bells = emptyList(), goal = 2)
        seeUpNext("Climbing")
    }

    @Test
    fun `skipping leaves everything as it was`() = acceptanceScenario {
        openSetUpFromToday()
        answerSport("BJJ")
        trainOn("Monday")
        tap("Skip for now")

        seeTodayTab()
        seeNothingPlanned()
        weekHolds(sportDays = 0, programDays = 0, program = null, bells = emptyList(), goal = null)
    }

    @Test
    fun `reopened from settings it shows the answers and builds again without starting the program over`() =
        acceptanceScenario {
            // Sunday is taken by a workout of the user's own, which building never touches.
            app.repository.plans.planWeekly(tabataPreset.asPlan(), setOf(7))
            openSettings()
            openSetUpFromSettings()
            answerSport("BJJ")
            trainOn("Monday", "Wednesday")
            ownBells(16)
            sessionOf(15)
            seeYourWeek("Your first 100 swings", "Tue, Thu, Sat")
            buildMyWeek()
            weekHolds(3 - 1, programDays = 3, program = ProgramId.FirstHundredSwings, bells = listOf(16.0), goal = 6)
            val started = app.lift.following.row.value?.startedAtEpochMillis

            app.clock.addAndGet(60_000)
            openSettings()
            openSetUpFromSettings()
            seeAnswered("BJJ", "Monday", kg = 16)
            seeYourWeek("Your first 100 swings", "You follow it already")
            trainOn("Friday")
            ownBells(24)
            seeYourWeek("Your first 100 swings", "Tue, Sat")
            buildMyWeek()

            // A class more leaves room for two of its three days; the program stands where it was.
            weekHolds(3, programDays = 2, program = ProgramId.FirstHundredSwings, bells = listOf(16.0, 24.0), goal = 6)
            assertEquals(started, app.lift.following.row.value?.startedAtEpochMillis)
            assertEquals(1, app.repository.plans.planned.value.count { it.workout == tabataPreset.asPlan() })
        }

    @Test
    fun `a new install opens on set up and skipping leaves today offering it`() {
        val storage = FakeStorage()
        acceptanceScenario(storage = storage, firstRun = true) {
            seeSetUp()
            setUpOffered(true)
            tap("Skip for now")

            seeTodayTab()
            seeNothingPlanned()
            openSetUpFromToday()
            pressSystemBack()
            seeTodayTab()
        }
        // Opened again with nothing in it still, it goes to Today: Set up has had its turn.
        acceptanceScenario(storage = storage, firstRun = true) {
            seeTodayTab()
            seeNoSetUp()
            seeNothingPlanned()
        }
    }

    @Test
    fun `a week built on the first open shows on today`() = acceptanceScenario(firstRun = true) {
        seeSetUp()
        answerSport("Climbing")
        trainOn("Thursday")
        ownBells(12)
        sessionOf(20)
        seeYourWeek("Kettlebell foundations", "Mon, Wed, Fri")
        buildMyWeek()

        weekHolds(1, programDays = 3, program = ProgramId.KettlebellFoundations, bells = listOf(12.0), goal = 4)
        // Thursday is the wall's day: the wrists come first, with the day to log beside them.
        seeFlowLeads("Before climbing", "Wrists and fingers")
        seeAlsoToday("Climbing")
    }

    @Test
    fun `someone following a program opens on today`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            following.row.value = FollowingEntity(programId = ProgramId.ProgramMinimum.name, startedAtEpochMillis = 0)
        },
        firstRun = true,
    ) {
        seeTodayTab()
        seeNoSetUp()
        setUpOffered(true)
    }

    @Test
    fun `a shortcut taken while set up shows goes where it asks`() = acceptanceScenario(firstRun = true) {
        seeSetUp()
        launchFrom(Shortcut.StartWorkout)
        seePhase("Get ready")
        // Set up has had its turn: it is on Today's card and in Settings from here on.
        setUpOffered(true)
    }
}
