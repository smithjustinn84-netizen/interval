@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.plan

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FakeLiftStorage
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Thursday 15 October 2026.

class PlanViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val bells = FakeLiftStorage().repository(now = { clock })
    private val viewModel by lazy { PlanViewModel(repository, bells, FixedTimestampFormatter, clock = { clock }) }
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
    fun `the goal stops at fourteen`() {
        repeat(20) { viewModel.stepGoal(1) }
        assertEquals(14, viewModel.state.value.goal)
    }

    @Test
    fun `the routine lists each weekday's plans`() {
        viewModel.planWeekly(tabata, setOf(1, 3))
        viewModel.planWeekly(hiit, setOf(3))
        val routine = viewModel.state.value.routine
        assertEquals((1..7).toList(), routine.map { it.weekday })
        assertEquals(listOf("Tabata"), routine[0].plans.map { it.name })
        assertEquals(listOf("Tabata", "HIIT 30/30"), routine[2].plans.map { it.name })
        assertTrue(routine[1].plans.isEmpty())
    }

    @Test
    fun `one-offs still to come are listed by day and can be removed`() {
        viewModel.planOnce(hiit, TODAY + 3)
        viewModel.planOnce(tabata, TODAY)
        viewModel.planOnce(tabata, TODAY - 1)
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
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var now = noonOf(TODAY)
        repository.planOnce(hiit, TODAY + 1)
        val plan = PlanViewModel(repository, bells, FixedTimestampFormatter, clock = { now })
        assertEquals(listOf(HistoryDay.Tomorrow), plan.state.value.oneOffs.map { it.day })

        now = noonOf(TODAY + 1)
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals(listOf(HistoryDay.Today), plan.state.value.oneOffs.map { it.day })
        plan.viewModelScope.cancel()
    }
}
