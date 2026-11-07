package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.domain.IntervalSnatches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What Lift reads of the interval side, from its [historyStore] and [settingsStore], kept current on [scope]; a
 * workout's snatches named by the one of [presetStore]'s presets it matches.
 */
class RepositoryIntervalActivity(
    private val historyStore: HistoryStore,
    private val settingsStore: SettingsStore,
    private val presetStore: PresetStore,
    scope: CoroutineScope,
) : IntervalActivity {
    override val workouts: StateFlow<List<IntervalWorkout>> = combine(historyStore.history, presetStore.presets) {
            history,
            presets,
        ->
        history.map { it.asIntervalWorkout(presets) }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        historyStore.history.value.map { it.asIntervalWorkout(presetStore.presets.value) },
    )

    override val sportDays: StateFlow<List<SportDay>> = historyStore.sportDays
        .map { days -> days.map { it.asSportDay() } }
        .stateIn(scope, SharingStarted.Eagerly, historyStore.sportDays.value.map { it.asSportDay() })

    override val weeklyGoal: StateFlow<Int?> = settingsStore.appSettings
        .map { it.weeklyGoal }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.weeklyGoal)

    override val bodyWeightKg: StateFlow<Double?> = settingsStore.appSettings
        .map { it.body.weightKg }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.body.weightKg)

    override val voice: StateFlow<Boolean> = settingsStore.cueSettings
        .map { it.voice }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.cueSettings.value.voice)

    /** As History counts a session's calories: all of it as kettlebell work, its rests being short. */
    override fun sessionCalories(elapsedSeconds: Int): Int? =
        settingsStore.appSettings.value.body.calories(elapsedSeconds, 0, Activity.Kettlebell)
}

private fun LoggedSport.asSportDay() = SportDay(epochDay, grappling = sport == Sport.Bjj)

// Every stored workout has an id; one without is never in the history flow, but it still needs a key.
private fun HistoryEntry.asIntervalWorkout(presets: List<Preset>) = IntervalWorkout(
    id?.toString().orEmpty(),
    finishedAtEpochMillis,
    elapsedSeconds,
    snatches?.let { IntervalSnatches(it.perSet, it.bellKg, workEnds(), presets.matching(config)?.name) },
)
