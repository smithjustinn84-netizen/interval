package dev.justinsmith.interval.today

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.ReadinessReason
import dev.justinsmith.lift.fakes.FakeLiftReadiness
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val tabata = tabataPreset.config

class HomeViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val lift = FakeLiftStorage()
    private val bells = lift.repository(now = { clock })
    private val readiness = FakeLiftReadiness()
    private val savedState = SavedStateHandle()

    private fun viewModel() = HomeViewModel(
        repository.history,
        repository.presets,
        repository.flows,
        repository.plans,
        repository.settings,
        bells.sessions,
        readiness,
        FixedTimestampFormatter,
        clock = { clock },
        savedState = savedState,
        computation = dispatcher,
    )

    private val viewModel by lazy { viewModel() }

    @Test
    fun `the first week shows only while its card is on and hiding it is kept`() {
        assertNull(viewModel.state.value.firstWeek)

        repository.settings.updateAppSettings { it.copy(firstWeek = true) }
        assertEquals(0, viewModel.state.value.firstWeek?.done)
        repository.history.recordWorkout(tabata, tabata.totalSeconds)
        assertEquals(1, viewModel.state.value.firstWeek?.done)

        viewModel.hideFirstWeek()
        assertNull(viewModel.state.value.firstWeek)
        assertFalse(repository.settings.appSettings.value.firstWeek)
    }

    @Test
    fun `with nothing planned and no program nothing is up next`() {
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertTrue(state.upNext.isEmpty())
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), state.week.map { it.weekday })
        assertEquals(TODAY, state.week.single { it.today }.epochDay)
    }

    @Test
    fun `a workout planned for today leads up next`() {
        repository.plans.planOnce(hiitPreset.asPlan(), TODAY)
        repository.plans.planOnce(tabataPreset.asPlan(), TODAY + 1)

        val upNext = viewModel.state.value.upNext
        val next = assertIs<UpNextItem.Planned>(upNext.first())
        assertEquals(hiitPreset.name, next.name)
        assertEquals(listOf(HistoryDay.Today, HistoryDay.Tomorrow), upNext.map { it.day })
    }

    @Test
    fun `not today moves the plan leading the day to a day after and Undo brings it back`() {
        repository.plans.planWeekly(hiitPreset.asPlan(), setOf(weekdayOf(TODAY)))
        val planId = assertIs<UpNextItem.Planned>(viewModel.state.value.upNext.first()).planId

        viewModel.openMove(planId)
        val sheet = requireNotNull(viewModel.state.value.moveSheet)
        assertEquals(TODAY + 1, sheet.days.first().epochDay)
        assertEquals(TODAY + 1, sheet.suggested)

        val move = requireNotNull(viewModel.movePlanned(TODAY + 2))
        assertNull(viewModel.state.value.moveSheet)
        val next = assertIs<UpNextItem.Planned>(viewModel.state.value.upNext.first())
        assertEquals(TODAY + 2, next.epochDay)
        assertTrue(viewModel.state.value.upNext.none { it.epochDay == TODAY })

        viewModel.unmovePlanned(move)
        assertEquals(TODAY, viewModel.state.value.upNext.first().epochDay)
    }

    @Test
    fun `a plan done today leaves nothing up next but is still a plan`() {
        assertFalse(viewModel.state.value.hasPlans)
        repository.plans.planOnce(tabataPreset.asPlan(), TODAY)
        assertTrue(viewModel.state.value.hasPlans)

        repository.history.recordWorkout(tabata, tabata.totalSeconds)
        val state = viewModel.state.value
        assertTrue(state.upNext.isEmpty())
        assertTrue(state.hasPlans)
    }

    @Test
    fun `the followed program's next day is up next without a plan`() {
        bells.programs.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))

        val next = assertIs<UpNextItem.Lift>(viewModel.state.value.upNext.single())
        assertEquals(1, next.programDay?.number)
        assertEquals(HistoryDay.Today, next.day)
        assertFalse(next.resume)
        // Three ladders of presses a side and five sets of swings, a minute and a half between them.
        assertEquals(25, next.minutes)
    }

    @Test
    fun `a session in progress is the one to resume`() {
        bells.programs.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))
        viewModel.startLift(null)

        assertNotNull(bells.sessions.active.value)
        val next = assertIs<UpNextItem.Lift>(viewModel.state.value.upNext.single())
        assertTrue(next.resume)
        // Part done, it no longer says how long the whole day takes.
        assertNull(next.minutes)
    }

    @Test
    fun `the week counts both sides against the goal`() {
        repository.settings.updateAppSettings { it.copy(weeklyGoal = 3) }
        repository.history.recordWorkout(tabata, tabata.totalSeconds)
        val finished = TODAY * DAY_MILLIS + DAY_MILLIS / 4
        lift.finished("b1", "Swings and get-ups", finished - 1_570_000, finished, 24.0 to 10)

        val state = viewModel.state.value
        assertEquals(2, state.weekGoal.count)
        assertEquals(1, state.weekGoal.lifts)
        assertEquals(3, state.weekGoal.goal)
        assertEquals(DayState.Done, state.week.single { it.today }.state)
    }

    @Test
    fun `a class logged counts in the week and names its day`() {
        repository.settings.updateAppSettings { it.copy(weeklyGoal = 3) }
        repository.history.recordWorkout(tabata, tabata.totalSeconds)
        repository.history.logSport(Sport.Bjj, TODAY)

        val state = viewModel.state.value
        assertEquals(2, state.weekGoal.count)
        assertEquals(1, state.weekGoal.sports)
        val today = state.week.single { it.today }
        assertEquals(Sport.Bjj, today.sport)
        assertTrue(today.sportLogged)
    }

    @Test
    fun `a class planned is up next to log and its day is no workout to do`() {
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Climbing), TODAY)

        val state = viewModel().state.value
        val next = assertIs<UpNextItem.SportDay>(state.upNext.single { it !is UpNextItem.Flow })
        assertEquals(Sport.Climbing, next.sport)
        val today = state.week.single { it.today }
        assertEquals(DayState.Rest, today.state)
        assertEquals(Sport.Climbing, today.sport)
        assertFalse(today.sportLogged)

        repository.history.logSport(Sport.Climbing, TODAY)
        val logged = viewModel().state.value
        assertTrue(logged.upNext.isEmpty())
        assertTrue(logged.hasPlans)
        assertEquals(DayState.Done, logged.week.single { it.today }.state)
    }

    @Test
    fun `a class logged from today goes on today and comes off by its day`() {
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Bjj), TODAY)

        val day = viewModel.logSport(Sport.Bjj)
        assertEquals(TODAY, day)
        assertEquals(listOf(TODAY to Sport.Bjj), repository.history.sportDays.value.map { it.epochDay to it.sport })

        viewModel.unlogSport(Sport.Bjj, day)
        assertTrue(repository.history.sportDays.value.isEmpty())
    }

    @Test
    fun `on a class day the program's next day stands beside the class until it is logged`() {
        bells.programs.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Bjj), TODAY)

        val agenda = viewModel().state.value.agenda
        assertIs<UpNextItem.Flow>(agenda.lead)
        assertIs<UpNextItem.SportDay>(agenda.also.first())
        assertIs<UpNextItem.Lift>(agenda.also.last())

        repository.history.logSport(Sport.Bjj, TODAY)
        val logged = viewModel().state.value.agenda
        assertIs<UpNextItem.Lift>(logged.lead)
        assertTrue(logged.also.isEmpty())
    }

    @Test
    fun `before a day's sport its flow leads until mobility is done or the sport logged`() {
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Climbing), TODAY)
        repository.settings.updateConfig { it.copy(prepSeconds = 30) }

        val lead = assertIs<UpNextItem.Flow>(viewModel().state.value.agenda.lead)
        assertEquals("Wrists and fingers" to Sport.Climbing, lead.flow.name to lead.sport)
        // After the Timer's own get-ready, half a minute here, not the flow's ten seconds.
        assertEquals(290 + 30, lead.totalSeconds)

        repository.history.recordWorkout(lead.flow.config(prepSeconds = 30), elapsedSeconds = 320)
        val done = viewModel().state.value.agenda
        assertIs<UpNextItem.SportDay>(done.lead)
        assertTrue(done.also.isEmpty())
    }

    @Test
    fun `the day's steps come in order and what is done today is ticked off them`() {
        bells.programs.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Bjj), TODAY)

        val morning = viewModel().state.value.plan
        assertEquals(3, morning.steps.size)
        val flow = assertIs<UpNextItem.Flow>(morning.steps[0])
        assertIs<UpNextItem.SportDay>(morning.steps[1])
        assertTrue(morning.spare(morning.steps[2]))
        assertEquals(DayKind.SportDay(Sport.Bjj), morning.kind)
        assertEquals(CoachNote.ForSport(Sport.Bjj, flowFirst = true), morning.note(taxed = null, goalMet = false))
        assertTrue(morning.done.isEmpty())

        repository.history.recordWorkout(flow.flow.config(prepSeconds = 10), elapsedSeconds = 540)
        val warm = viewModel().state.value.plan
        assertEquals(listOf<DoneStep>(DoneStep.Workout("Before rolling", flow.flow.config(10), 540)), warm.done)
        assertIs<UpNextItem.SportDay>(warm.steps.first())

        // The class logged, the program's day stands back still: the day is done, and it waits for spare time.
        repository.history.logSport(Sport.Bjj, TODAY)
        val evening = viewModel().state.value.plan
        assertTrue(evening.steps.isEmpty())
        assertIs<UpNextItem.Lift>(evening.next)
        assertTrue(evening.waiting)
        assertEquals(DayKind.Done, evening.kind)
        assertEquals(CoachNote.DayDone(goalMet = false), evening.note(taxed = null, goalMet = false))
        assertEquals(DoneStep.SportLogged(Sport.Bjj), evening.done.last())
    }

    @Test
    fun `a day with nothing asked of it and plans to come is a rest day`() {
        repository.plans.planOnce(tabataPreset.asPlan(), TODAY + 1)

        val plan = viewModel.state.value.plan
        assertTrue(plan.resting)
        assertFalse(plan.waiting)
        assertEquals(DayKind.Rest, plan.kind)
        assertEquals(CoachNote.RestDay, plan.note(taxed = null, goalMet = false))
        assertIs<UpNextItem.Planned>(plan.next)
    }

    @Test
    fun `a taxed day is Lift's reading and starting the day easier uses its easier plan`() {
        val press = ProgramKey.BuiltIn(ProgramId.PressLadders)
        bells.programs.follow(press)
        assertNull(viewModel.state.value.taxed)
        assertFalse(viewModel.state.value.easier)

        // An easier plan with no taxed day to offer it on is none.
        readiness.easierAhead.value = true
        assertFalse(viewModel.state.value.easier)
        readiness.taxedBy.value = ReadinessReason.Mat
        val state = viewModel.state.value
        assertEquals(ReadinessReason.Mat, state.taxed)
        assertTrue(state.easier)
        assertEquals(CoachNote.Taxed(ReadinessReason.Mat), state.plan.note(state.taxed, goalMet = false))

        viewModel.startLift(press, easier = true)
        assertNotNull(bells.sessions.active.value)
        assertEquals(1, readiness.used)
    }

    @Test
    fun `a sport on another day suggests nothing today`() {
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Bjj), TODAY + 1)

        assertTrue(viewModel().state.value.upNext.none { it is UpNextItem.Flow })
    }

    @Test
    fun `the presets offered fit the minutes picked and a flow that fits follows them`() {
        assertEquals(DEFAULT_PICK_MINUTES, viewModel.state.value.minutes)
        assertEquals(
            listOf("HIIT 30/30", "Swings on the minute", "Hangboard", "Spine and hamstrings"),
            viewModel.state.value.picks.map { it.preset.name },
        )

        viewModel.setMinutes(5)
        assertEquals(5, viewModel.state.value.minutes)
        assertEquals(
            listOf("Hangboard", "Tabata", "Daily five"),
            viewModel.state.value.picks.map { it.preset.name },
        )

        // Not one of the lengths offered: nothing changes.
        viewModel.setMinutes(7)
        assertEquals(5, viewModel.state.value.minutes)
    }

    @Test
    fun `a length picked changes the picks and keeps the week and up next as they were`() {
        // The screen compares these by instance where the compiler can't tell they are stable (iOS).
        repository.plans.planOnce(PlanWorkout.SportDay(Sport.Climbing), TODAY)
        val before = viewModel.state.value
        assertTrue(before.upNext.any { it is UpNextItem.Flow })

        viewModel.setMinutes(5)

        val after = viewModel.state.value
        assertEquals(5, after.minutes)
        assertSame(before.week, after.week)
        assertSame(before.upNext, after.upNext)
    }

    @Test
    fun `the minutes picked outlive the view model`() {
        viewModel.setMinutes(20)
        assertEquals(20, viewModel().state.value.minutes)
    }

    @Test
    fun `the session's length from set up is the length picked until another is`() {
        repository.settings.updateAppSettings { it.copy(sessionMinutes = 15) }
        assertEquals(15, viewModel.state.value.minutes)

        viewModel.setMinutes(30)
        assertEquals(30, viewModel.state.value.minutes)
        assertEquals(30, viewModel().state.value.minutes)
    }

    @Test
    fun `a length answered in set up while today is open takes the place of one picked by hand`() {
        viewModel.setMinutes(30)

        repository.settings.updateAppSettings { it.copy(sessionMinutes = 20) }

        assertEquals(20, viewModel.state.value.minutes)
        assertEquals(20, viewModel().state.value.minutes)
    }

    @Test
    fun `a pick started is put on the timer`() {
        viewModel.setMinutes(5)
        viewModel.putOnTimer(viewModel.state.value.picks.single { it.preset.name == "Tabata" }.preset)

        val onTimer = repository.settings.config.value
        assertEquals(listOf(20, 10, 8), listOf(onTimer.workSeconds, onTimer.restSeconds, onTimer.rounds))
    }

    @Test
    fun `a planned workout started is put on the timer`() {
        viewModel.putOnTimer(hiitPreset.name, hiitPreset.config)

        val onTimer = repository.settings.config.value
        assertEquals(listOf(30, 30, 10), listOf(onTimer.workSeconds, onTimer.restSeconds, onTimer.rounds))
    }
}
