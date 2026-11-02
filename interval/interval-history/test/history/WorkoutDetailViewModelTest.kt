@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.history

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.IntervalConfig
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

class WorkoutDetailViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val hiit = hiitPreset
    private val health = RecordingHealthStore(HealthStatus.Connected)

    private fun viewModel(id: Long = 1) =
        WorkoutDetailViewModel(id, repository, repository, repository, repository, FixedTimestampFormatter, {
            clock
        }, health)

    @Test
    fun `a workout shows its figures and whether it did a plan`() {
        repository.planOnce(hiit.asPlan(), TODAY)
        repository.recordWorkout(hiit.config, elapsedSeconds = 580, workSeconds = 300)
        val workout = requireNotNull(viewModel().state.value.workout)
        assertEquals("HIIT 30/30", workout.presetName)
        assertEquals(HistoryDay.Today, workout.day)
        assertEquals("9:40", workout.total)
        assertEquals("5:00", workout.work)
        assertTrue(workout.planned)
        assertEquals(repository.history.value.single().calories(BodySettings()), workout.calories)
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        assertNull(viewModel().state.value.workout?.calories)
    }

    @Test
    fun `a shape no preset has is named by its shape`() {
        repository.recordWorkout(IntervalConfig(workSeconds = 25, restSeconds = 5, rounds = 4), elapsedSeconds = 100)
        val workout = requireNotNull(viewModel().state.value.workout)
        assertNull(workout.presetName)
        assertEquals("25/5 ×4", workout.name)
        assertEquals(listOf("25/5 ×4"), viewModel().state.value.choices.map { it.preset.name })
    }

    @Test
    fun `deleting it leaves nothing to show`() {
        repository.recordWorkout(hiit.config, elapsedSeconds = 580)
        val viewModel = viewModel()
        viewModel.delete()
        assertNull(viewModel.state.value.workout)
        assertTrue(repository.history.value.isEmpty())
    }

    @Test
    fun `repeating puts it on the Timer and planning it again adds a plan`() {
        repository.recordWorkout(hiit.config, elapsedSeconds = 580)
        val viewModel = viewModel()
        viewModel.putOnTimer()
        assertEquals(hiit.config.workSeconds, repository.config.value.workSeconds)
        viewModel.planOnce(viewModel.state.value.choices.single().preset.asPlan(), TODAY + 2)
        assertEquals("HIIT 30/30", (repository.planned.value.single().workout as PlanWorkout.Interval).name)
    }

    @Test
    fun `an id with no workout shows none`() {
        assertNull(viewModel(id = 42).state.value.workout)
    }

    @Test
    fun `a workout of today is of yesterday once midnight passes with the page open`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var now = noonOf(TODAY)
        val shared = FakeStorage().repository(now = { now })
        shared.recordWorkout(hiit.config, elapsedSeconds = 580)
        val page = WorkoutDetailViewModel(1, shared, shared, shared, shared, FixedTimestampFormatter, { now }, health)
        assertEquals(HistoryDay.Today, page.state.value.workout?.day)

        now = noonOf(TODAY + 1)
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals(HistoryDay.Yesterday, page.state.value.workout?.day)
        page.viewModelScope.cancel()
    }

    @Test
    fun `the page reads a watch's heart rate over the workout and keeps it`() {
        val started = clock - 600_000
        repository.recordWorkout(hiit.config, elapsedSeconds = 580, startedAtEpochMillis = started)
        health.heartRate = HeartRate(averageBpm = 142, peakBpm = 171)
        val viewModel = viewModel()
        assertNull(viewModel.state.value.workout?.heartRate)

        viewModel.readHeartRate()

        assertEquals(listOf(started..clock), health.heartRateReads)
        assertEquals(HeartRate(142, 171), viewModel.state.value.workout?.heartRate)
        assertEquals(142, repository.history.value.single().averageBpm)
        // Kept: the next time the page shows, the store isn't asked again.
        viewModel.readHeartRate()
        assertEquals(1, health.heartRateReads.size)
    }

    @Test
    fun `a workout recorded before its start was kept is read from its finish back`() {
        repository.recordWorkout(hiit.config, elapsedSeconds = 580)
        viewModel().readHeartRate()
        assertEquals(listOf(clock - 580_000..clock), health.heartRateReads)
    }

    @Test
    fun `with none in the store the page shows none and asks again next time`() {
        repository.recordWorkout(hiit.config, elapsedSeconds = 580)
        health.status.value = HealthStatus.Disconnected
        health.heartRate = HeartRate(142, 171)
        val viewModel = viewModel()
        viewModel.readHeartRate()
        assertNull(viewModel.state.value.workout?.heartRate)
        assertNull(repository.history.value.single().averageBpm)

        health.status.value = HealthStatus.Connected
        viewModel.readHeartRate()
        assertEquals(HeartRate(142, 171), viewModel.state.value.workout?.heartRate)
    }
}
