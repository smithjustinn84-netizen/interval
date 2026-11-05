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
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val tabata = tabataPreset.config

class HomeViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val lift = FakeLiftStorage()
    private val bells = lift.repository(now = { clock })
    private val savedState = SavedStateHandle()

    private fun viewModel() = HomeViewModel(
        repository.history,
        repository.presets,
        repository.plans,
        repository.settings,
        bells.sessions,
        FixedTimestampFormatter,
        clock = { clock },
        savedState = savedState,
        computation = dispatcher,
    )

    private val viewModel by lazy { viewModel() }

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
    fun `the presets offered fit the minutes picked`() {
        assertEquals(DEFAULT_PICK_MINUTES, viewModel.state.value.minutes)
        assertEquals(
            listOf("HIIT 30/30", "Hangboard", "Tabata"),
            viewModel.state.value.picks.map { it.preset.name },
        )

        viewModel.setMinutes(5)
        assertEquals(5, viewModel.state.value.minutes)
        assertEquals(listOf("Hangboard", "Tabata"), viewModel.state.value.picks.map { it.preset.name })

        // Not one of the lengths offered: nothing changes.
        viewModel.setMinutes(7)
        assertEquals(5, viewModel.state.value.minutes)
    }

    @Test
    fun `the minutes picked outlive the view model`() {
        viewModel.setMinutes(20)
        assertEquals(20, viewModel().state.value.minutes)
    }

    @Test
    fun `a pick started is put on the timer`() {
        viewModel.setMinutes(5)
        viewModel.putOnTimer(viewModel.state.value.picks.single { it.preset.name == "Tabata" })

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
