package dev.justinsmith.interval.weeksetup

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.ui.weekdayMask
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MON = 1
private const val TUE = 2
private const val WED = 3
private const val THU = 4
private const val FRI = 5
private const val SAT = 6

class WeekSetupViewModelTest : MainDispatcherTest() {
    private var now = 1_000L
    private val repository = FakeStorage().repository(now = { now })
    private val lift = FakeLiftStorage().repository(now = { now })
    private val savedState = SavedStateHandle()

    private fun viewModel() = WeekSetupViewModel(
        repository.plans,
        repository.settings,
        lift.gear,
        lift.programs,
        lift.sessions,
        savedState,
    )

    private val viewModel by lazy { viewModel() }

    private fun weekly(workout: PlanWorkout): Set<Int> = repository.plans.planned.value
        .filter { it.workout == workout }
        .mapNotNull { (it.schedule as? PlanSchedule.Weekly)?.weekday }
        .toSet()

    private val followed get() = (lift.programs.followed.value?.program?.key as? ProgramKey.BuiltIn)?.id

    private fun answerBjj() {
        viewModel.pickSport(Sport.named.indexOf(Sport.Bjj))
        viewModel.setWeekdays(weekdayMask(setOf(MON, WED, FRI)))
        viewModel.toggleBell(16.0)
        viewModel.setMinutes(15)
    }

    @Test
    fun `it opens with nothing answered and nothing to build`() {
        val state = viewModel.state.value
        assertEquals(SPORT_UNANSWERED, state.sport)
        assertFalse(state.asksDays)
        assertEquals(SETUP_BELLS, state.bellChoices)
        assertTrue(state.bells.isEmpty())
        assertEquals(DEFAULT_SESSION_MINUTES, state.minutes)
        assertFalse(state.canBuild)
        assertFalse(viewModel.build())
        assertTrue(repository.plans.planned.value.isEmpty())
    }

    @Test
    fun `the answers show the week before it is built`() {
        answerBjj()

        val state = viewModel.state.value
        assertTrue(state.asksDays)
        assertEquals(Sport.Bjj, state.week.sport)
        assertEquals(ProgramId.FirstHundredSwings, (state.week.program?.key as ProgramKey.BuiltIn).id)
        assertEquals(15..15, state.programMinutes)
        assertEquals(setOf(TUE, THU, SAT), state.week.liftDays)
        assertEquals(6, state.week.goal)
        // Nothing is written until it is built.
        assertTrue(repository.plans.planned.value.isEmpty())
        assertNull(followed)
    }

    @Test
    fun `building plans the sport and the program and keeps the bells the goal and the length`() {
        answerBjj()

        assertTrue(viewModel.build())

        assertEquals(setOf(MON, WED, FRI), weekly(PlanWorkout.SportDay(Sport.Bjj)))
        assertEquals(setOf(TUE, THU, SAT), weekly(PlanWorkout.Lift.ProgramDay))
        assertEquals(ProgramId.FirstHundredSwings, followed)
        assertEquals(listOf(16.0), lift.gear.bells.value)
        val app = repository.settings.appSettings.value
        assertEquals(6, app.weeklyGoal)
        assertEquals(15, app.sessionMinutes)
        assertTrue(app.setupOffered)
    }

    @Test
    fun `built twice it writes once`() {
        answerBjj()

        assertTrue(viewModel.build())
        assertFalse(viewModel.build())

        assertEquals(6, repository.plans.planned.value.size)
    }

    @Test
    fun `a sport of the user's own is planned once it is named`() {
        viewModel.pickSport(Sport.OTHER)
        viewModel.setWeekdays(weekdayMask(setOf(TUE)))
        assertNull(viewModel.state.value.week.sport)

        viewModel.nameSport("  Judo ")
        assertEquals(Sport.Custom("Judo"), viewModel.state.value.week.sport)
        assertTrue(viewModel.build())
        assertEquals(setOf(TUE), weekly(PlanWorkout.SportDay(Sport.Custom("Judo"))))
    }

    @Test
    fun `the bell alone drops the days picked for a sport`() {
        viewModel.pickSport(0)
        viewModel.setWeekdays(weekdayMask(setOf(MON)))
        viewModel.pickSport(SPORT_NONE)
        viewModel.toggleBell(16.0)

        val state = viewModel.state.value
        assertFalse(state.asksDays)
        assertNull(state.week.sport)
        assertTrue(viewModel.build())
        assertTrue(repository.plans.planned.value.none { it.workout is PlanWorkout.SportDay })
    }

    @Test
    fun `it reopens on what stands`() {
        repository.plans.planWeekly(PlanWorkout.SportDay(Sport.Climbing), setOf(TUE, SAT))
        lift.gear.setOwned(20.0, owned = true)
        lift.gear.setOwned(28.0, owned = true)
        repository.settings.updateAppSettings { it.copy(sessionMinutes = 30) }

        val state = viewModel.state.value

        assertEquals(Sport.named.indexOf(Sport.Climbing), state.sport)
        assertEquals(weekdayMask(setOf(TUE, SAT)), state.weekdays)
        assertEquals(setOf(20.0, 28.0), state.bells)
        // A size Set up doesn't offer is among the choices, so it isn't read as not owned.
        assertEquals(listOf(8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0), state.bellChoices)
        assertEquals(30, state.minutes)
    }

    @Test
    fun `a sport of the user's own planned before comes back by name`() {
        repository.plans.planWeekly(PlanWorkout.SportDay(Sport.Custom("Judo")), setOf(WED))

        val state = viewModel.state.value

        assertEquals(Sport.OTHER, state.sport)
        assertEquals("Judo", state.sportName)
        assertEquals(Sport.Custom("Judo"), state.week.sport)
    }

    @Test
    fun `building again replaces its week and never starts the program over`() {
        answerBjj()
        viewModel.build()
        repository.plans.planWeekly(tabataPreset.asPlan(), setOf(7))
        val started = lift.programs.followed.value?.startedAtEpochMillis
        now += 60_000

        val again = WeekSetupViewModel(
            repository.plans,
            repository.settings,
            lift.gear,
            lift.programs,
            lift.sessions,
            SavedStateHandle(),
        )
        assertTrue(again.state.value.week.keepsProgram)
        again.pickSport(Sport.named.indexOf(Sport.Climbing))
        again.setWeekdays(weekdayMask(setOf(TUE)))
        again.toggleBell(16.0)
        again.toggleBell(24.0)
        assertTrue(again.build())

        // The classes go with the sport they were for; the user's own workout stays.
        assertTrue(weekly(PlanWorkout.SportDay(Sport.Bjj)).isEmpty())
        assertEquals(setOf(TUE), weekly(PlanWorkout.SportDay(Sport.Climbing)))
        assertEquals(setOf(7), weekly(tabataPreset.asPlan()))
        assertEquals(3, weekly(PlanWorkout.Lift.ProgramDay).size)
        assertEquals(ProgramId.FirstHundredSwings, followed)
        assertEquals(started, lift.programs.followed.value?.startedAtEpochMillis)
        // The bell unticked is no longer owned; the one ticked is.
        assertEquals(listOf(24.0), lift.gear.bells.value)
        assertEquals(5, repository.settings.appSettings.value.weeklyGoal)
    }

    @Test
    fun `the answers outlive the view model`() {
        answerBjj()
        viewModel.pickSport(Sport.OTHER)
        viewModel.nameSport("Judo")

        val state = viewModel().state.value

        assertEquals(Sport.OTHER, state.sport)
        assertEquals("Judo", state.sportName)
        assertEquals(weekdayMask(setOf(MON, WED, FRI)), state.weekdays)
        assertEquals(setOf(16.0), state.bells)
        assertEquals(15, state.minutes)
    }

    @Test
    fun `a length that isn't offered is not taken`() {
        viewModel.setMinutes(25)
        assertEquals(DEFAULT_SESSION_MINUTES, viewModel.state.value.minutes)
    }
}
