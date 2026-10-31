@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.plan

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Thursday 15 October 2026.

class PlanViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val bells = FakeLiftStorage().repository(now = { clock })
    private val viewModel by lazy {
        PlanViewModel(repository, bells, FixedTimestampFormatter, clock = { clock }, computation = dispatcher)
    }
    private val tabata = tabataPreset
    private val hiit = hiitPreset

    @Test
    fun `the goal starts at three and turns off below one`() {
        assertNull(viewModel.state.value.goal)
        viewModel.stepGoal(-1)
        assertNull(viewModel.state.value.goal)
        viewModel.stepGoal(1)
        assertEquals(3, viewModel.state.value.goal)
        repeat(3) { viewModel.stepGoal(-1) }
        assertNull(repository.appSettings.value.weeklyGoal)
    }

    @Test
    fun `the reminder is off until a time is set and wraps around the clock`() {
        val off = viewModel.state.value.reminder
        assertFalse(off.on)
        assertEquals("7:00", off.text)
        assertEquals(
            listOf("6:00", "7:00", "8:00", "12:00", "17:00", "18:00", "19:00", "20:00"),
            off.picks.map { it.text },
        )

        viewModel.setReminder(450)
        assertEquals(450, repository.appSettings.value.reminderMinuteOfDay)
        assertEquals(
            ReminderUi(on = true, minuteOfDay = 450, text = "7:30", picks = off.picks),
            viewModel.state.value.reminder,
        )

        viewModel.setReminder(-15)
        assertEquals("23:45", viewModel.state.value.reminder.text)
        viewModel.setReminder(24 * 60)
        assertEquals("0:00", viewModel.state.value.reminder.text)

        viewModel.setReminder(null)
        assertEquals(off, viewModel.state.value.reminder)
    }

    @Test
    fun `the goal stops at fourteen`() {
        repeat(20) { viewModel.stepGoal(1) }
        assertEquals(14, viewModel.state.value.goal)
    }

    @Test
    fun `the routine lists each weekday's plans`() {
        viewModel.planWeekly(tabata.asPlan(), setOf(1, 3))
        viewModel.planWeekly(hiit.asPlan(), setOf(3))
        val routine = viewModel.state.value.routine
        assertEquals((1..7).toList(), routine.map { it.weekday })
        assertEquals(listOf("Tabata"), routine[0].plans.map { it.name })
        assertEquals(listOf("Tabata", "HIIT 30/30"), routine[2].plans.map { it.name })
        assertTrue(routine[1].plans.isEmpty())
    }

    @Test
    fun `plans of the program's next day take its days in turn and count against its pace`() {
        bells.follow(ProgramKey.BuiltIn(ProgramId.PressLadders))
        viewModel.planWeekly(PlanWorkout.Lift.ProgramDay, setOf(1, 4, 5))
        viewModel.planWeekly(tabata.asPlan(), setOf(2))
        val state = viewModel.state.value
        assertEquals(1, state.programDay?.number)
        assertEquals(3, state.programWeekdays)
        assertEquals(3, state.routineLifts)
        assertEquals(1, state.routineIntervals)
        // Today, Thursday, is day 1; Friday day 2; next Monday day 3.
        assertEquals(listOf(3), state.routine[0].plans.map { it.programDay })
        assertEquals(listOf(1), state.routine[3].plans.map { it.programDay })
        assertEquals(listOf(2), state.routine[4].plans.map { it.programDay })
        assertEquals(listOf(null), state.routine[1].plans.map { it.programDay })
    }

    @Test
    fun `one-offs still to come are listed by day and can be removed`() {
        viewModel.planOnce(hiit.asPlan(), TODAY + 3)
        viewModel.planOnce(tabata.asPlan(), TODAY)
        viewModel.planOnce(tabata.asPlan(), TODAY - 1)
        val oneOffs = viewModel.state.value.oneOffs
        assertEquals(listOf(TODAY, TODAY + 3), oneOffs.map { it.epochDay })
        assertEquals(listOf(HistoryDay.Today, HistoryDay.On("day ${TODAY + 3}")), oneOffs.map { it.day })

        viewModel.removePlanned(oneOffs.first().entry.planId)
        assertEquals(listOf(TODAY + 3), viewModel.state.value.oneOffs.map { it.epochDay })
    }

    @Test
    fun `the add sheet offers the Timer's workout first and two weeks of days`() {
        repository.updateConfig { IntervalConfig(prepSeconds = 5, workSeconds = 40, restSeconds = 20, rounds = 6) }
        val state = viewModel.state.value
        val first = state.choices.first()
        assertTrue(first.onTimer)
        assertEquals("40/20 ×6", first.preset.name)
        assertEquals(builtInPresets.map { it.name }, state.choices.drop(1).map { it.preset.name })
        assertEquals(14, state.days.size)
        assertEquals(TODAY, state.days.first().epochDay)
        assertEquals(4, state.todayWeekday)
    }

    @Test
    fun `a described routine plans its weekdays and one-offs and sets its goal`() {
        val routine = describeRoutine(
            "tabata mon wed, hiit 30/30 tomorrow, 4 a week",
            IntervalConfig(),
            builtInPresets,
            TODAY,
        )!!
        viewModel.planDescribed(routine)
        val state = viewModel.state.value
        assertEquals(listOf("Tabata"), state.routine[0].plans.map { it.name })
        assertEquals(listOf("Tabata"), state.routine[2].plans.map { it.name })
        assertEquals(listOf(TODAY + 1), state.oneOffs.map { it.epochDay })
        assertEquals("HIIT 30/30", state.oneOffs.single().entry.name)
        assertEquals(4, state.goal)
        assertEquals(TODAY, state.today)
    }

    @Test
    fun `a described workout with no day plans nothing`() {
        viewModel.planDescribed(describeRoutine("tabata", IntervalConfig(), builtInPresets, TODAY)!!)
        assertTrue(viewModel.state.value.routine.all { it.plans.isEmpty() })
        assertTrue(viewModel.state.value.oneOffs.isEmpty())
        assertNull(viewModel.state.value.goal)
    }

    @Test
    fun `a preset on the Timer is offered under its own name`() {
        repository.applyPreset(tabata)
        assertEquals("Tabata", viewModel.state.value.choices.first().preset.name)
    }

    @Test
    fun `a one-off's heading moves on at midnight with the screen open`() = runTest {
        val main = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        var now = noonOf(TODAY)
        repository.planOnce(hiit.asPlan(), TODAY + 1)
        val plan = PlanViewModel(repository, bells, FixedTimestampFormatter, clock = { now }, computation = main)
        assertEquals(listOf(HistoryDay.Tomorrow), plan.state.value.oneOffs.map { it.day })

        now = noonOf(TODAY + 1)
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals(listOf(HistoryDay.Today), plan.state.value.oneOffs.map { it.day })
        plan.viewModelScope.cancel()
    }
}

/** An interval plan's name, as the routine lists it. */
private val PlanEntry.name: String get() = (workout as PlanWorkout.Interval).name
