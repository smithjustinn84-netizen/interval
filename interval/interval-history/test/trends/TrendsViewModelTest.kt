package dev.justinsmith.interval.trends

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.lift.domain.bellWeight
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TrendsViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val liftStorage = FakeLiftStorage().apply {
        finished("s", "Swings", clock - 1_800_000, clock - 60_000, 16.0 to 10)
    }
    private val lifts = liftStorage.repository(now = { clock })

    private fun viewModel() = TrendsViewModel(
        repository.history,
        repository.settings,
        lifts.sessions,
        FixedTimestampFormatter,
        { clock },
        computation = Dispatchers.Unconfined,
    )

    @Test
    fun `the weight lifted is in the unit settings weigh in`() {
        repository.settings.updateAppSettings { it.copy(body = it.body.copy(unit = WeightUnit.Lb)) }
        val state = viewModel().state.value
        assertFalse(state.loading)
        assertEquals(WeightUnit.Lb, state.unit)
        assertEquals(bellWeight(16.0, WeightUnit.Lb) * 10, state.trends.weeks.last().lifted)
    }

    @Test
    fun `the heart load goes by the max heart rate set`() {
        val samples = List(120) { 160 }
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 600, heartRateSamples = samples)
        repository.settings.updateAppSettings { it.copy(body = it.body.copy(maxHeartRate = 170)) }
        assertEquals(heartLoad(samples, 170), viewModel().state.value.trends.weeks.last().heartLoad)
    }

    @Test
    fun `records are dated as the Log dates a day`() {
        val line = viewModel().state.value.records.single()
        assertEquals(FixedTimestampFormatter.dateOfDay(TODAY), line.date)
        assertEquals("s", line.moment.sessionUid)
    }
}
