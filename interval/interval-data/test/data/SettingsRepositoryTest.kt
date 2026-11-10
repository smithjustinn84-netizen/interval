@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.stepped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The settings DataStore through [SettingsRepository]: the Timer's setup, cues, app settings, progress. */
class SettingsRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `settings survive a restart`() {
        val config = IntervalConfig(prepSeconds = 5, workSeconds = 45, restSeconds = 15, rounds = 12)
        repository().apply {
            settings.updateConfig { config }
            settings.updateCueSettings { it.copy(voice = false) }
            saveCurrentAsPreset("Long")
        }
        val reloaded = repository()
        assertEquals(config, reloaded.settings.config.value)
        assertEquals(CueSettings(voice = false), reloaded.settings.cueSettings.value)
        assertEquals("Long", reloaded.presets.customPresets.value.single().name)
    }

    @Test
    fun `the workout under way survives a restart and clears`() {
        val config = IntervalConfig(
            workSeconds = 40,
            warmUpSeconds = 60,
            coolDownSeconds = 30,
            sets = 2,
            setRestSeconds = 30,
            workStepSeconds = -5,
            then = listOf(Block(workSeconds = 20, restSeconds = 10, rounds = 3)),
            blockRestSeconds = 45,
        )
        val state = WorkoutEngine.tick(WorkoutEngine.start(config).state, 12_500).state
        val progress = WorkoutProgress(
            id = 42,
            state = state,
            running = true,
            ended = false,
            activeMillis = 12_500,
            workMillis = 2_500,
            week = WeekStanding(count = 3, goal = 4, streak = 2),
            atEpochMillis = 1_700_000_000_000,
            startedAtEpochMillis = 1_699_999_980_000,
            spans = listOf(
                CourseSpan(Phase.Prep, 1_699_999_980_000, 1_699_999_990_000),
                CourseSpan(null, 1_699_999_990_000, 1_699_999_995_000),
                CourseSpan(Phase.WarmUp, 1_699_999_995_000, 1_700_000_000_000),
            ),
        )
        repository().settings.saveWorkoutProgress(progress)
        assertEquals(progress, repository().settings.workoutProgress.value)
        val secondSet = progress.copy(state = state.copy(phase = Phase.Work, set = 2))
        repository().settings.saveWorkoutProgress(secondSet)
        assertEquals(secondSet, repository().settings.workoutProgress.value)

        repository().settings.saveWorkoutProgress(null)
        assertNull(repository().settings.workoutProgress.value)
    }

    @Test
    fun `a workout saved before blocks restores in its one block`() {
        val saved = """
            {"id":7,"prepSeconds":10,"workSeconds":20,"restSeconds":10,"rounds":8,"activity":"General",
            "phase":"Rest","round":3,"remainingMillis":4000,"phaseMillis":10000,"halfwayCued":false,
            "running":true,"ended":false,"activeMillis":70000,"workMillis":60000,"atEpochMillis":1700000000000,
            "set":2,"sets":2,"setRestSeconds":60}
        """.trimIndent()
        val progress = preferencesOf(stringPreferencesKey("workout_progress") to saved).toWorkoutProgress()
        val state = checkNotNull(progress).state
        assertEquals(0, state.block)
        assertEquals(2, state.set)
        assertEquals(3, state.round)
    }

    @Test
    fun `a saved block the workout has not is read as its last`() {
        val state = WorkoutEngine.start(IntervalConfig()).state.copy(block = 3)
        val progress = WorkoutProgress(
            id = 1,
            state = state,
            running = false,
            ended = false,
            activeMillis = 0,
            workMillis = 0,
            week = null,
            atEpochMillis = 1_700_000_000_000,
        )
        repository().settings.saveWorkoutProgress(progress)
        assertEquals(0, repository().settings.workoutProgress.value?.state?.block)
    }

    @Test
    fun `app settings survive a restart`() {
        repository().settings.updateAppSettings { AppSettings(theme = ThemeMode.Light, keepScreenOn = false) }
        assertEquals(
            AppSettings(theme = ThemeMode.Light, keepScreenOn = false),
            repository().settings.appSettings.value,
        )
    }

    @Test
    fun `updates apply to the current value so quick steps all land`() {
        val repository = repository()
        repeat(3) { repository.settings.updateConfig { it.stepped(ConfigField.Rounds, +1) } }
        assertEquals(IntervalConfig().rounds + 3, repository.settings.config.value.rounds)
        assertEquals(IntervalConfig().rounds + 3, repository().settings.config.value.rounds)
    }

    @Test
    fun `body settings survive a restart`() {
        val body = BodySettings(weightKg = 72.5, unit = WeightUnit.Lb, effort = Effort.Max)
        repository().settings.updateAppSettings { it.copy(body = body) }
        assertEquals(body, repository().settings.appSettings.value.body)
    }

    @Test
    fun `a weight never set reads as the default`() {
        assertEquals(WeightUnit.Kg.default.toDouble(), repository().settings.appSettings.value.body.weightKg)
    }

    @Test
    fun `calories turned off stay off after a restart`() {
        repository().settings.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        assertEquals(null, repository().settings.appSettings.value.body.weightKg)
        repository().settings.updateAppSettings { it.copy(body = it.body.copy(weightKg = 80.0)) }
        assertEquals(80.0, repository().settings.appSettings.value.body.weightKg)
    }

    @Test
    fun `the weekly goal survives a restart and clears`() {
        repository().settings.updateAppSettings { it.copy(weeklyGoal = 4) }
        assertEquals(4, repository().settings.appSettings.value.weeklyGoal)
        repository().settings.updateAppSettings { it.copy(weeklyGoal = null) }
        assertEquals(null, repository().settings.appSettings.value.weeklyGoal)
    }

    @Test
    fun `the reminder time survives a restart and clears`() {
        repository().settings.updateAppSettings { it.copy(reminderMinuteOfDay = 450) }
        assertEquals(450, repository().settings.appSettings.value.reminderMinuteOfDay)
        repository().settings.updateAppSettings { it.copy(reminderMinuteOfDay = null) }
        assertEquals(null, repository().settings.appSettings.value.reminderMinuteOfDay)
    }

    @Test
    fun `the session's length survives a restart and one never offered reads as none`() {
        repository().settings.updateAppSettings { it.copy(sessionMinutes = 15) }
        assertEquals(15, repository().settings.appSettings.value.sessionMinutes)
        repository().settings.updateAppSettings { it.copy(sessionMinutes = 45) }
        assertEquals(null, repository().settings.appSettings.value.sessionMinutes)
    }

    @Test
    fun `set up offered once stays offered after a restart`() {
        assertEquals(false, repository().settings.appSettings.value.setupOffered)
        repository().settings.updateAppSettings { it.copy(setupOffered = true) }
        assertEquals(true, repository().settings.appSettings.value.setupOffered)
    }

    @Test
    fun `the first week card stays on after a restart until it is hidden`() {
        assertEquals(false, repository().settings.appSettings.value.firstWeek)
        repository().settings.updateAppSettings { it.copy(firstWeek = true) }
        assertEquals(true, repository().settings.appSettings.value.firstWeek)
        repository().settings.updateAppSettings { it.copy(firstWeek = false) }
        assertEquals(false, repository().settings.appSettings.value.firstWeek)
    }
}
