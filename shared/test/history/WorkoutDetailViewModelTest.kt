package dev.justinsmith.interval.history

import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.timer.IntervalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutDetailViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val hiit = hiitPreset

    private fun viewModel(id: Long = 1) = WorkoutDetailViewModel(id, repository, FixedTimestampFormatter, clock = {
        clock
    })

    @Test
    fun `a workout shows its figures and whether it did a plan`() {
        repository.planOnce(hiit, TODAY)
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
        viewModel.planOnce(viewModel.state.value.choices.single().preset, TODAY + 2)
        assertEquals("HIIT 30/30", repository.planned.value.single().name)
    }

    @Test
    fun `an id with no workout shows none`() {
        assertNull(viewModel(id = 42).state.value.workout)
    }
}
