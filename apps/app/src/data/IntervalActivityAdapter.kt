package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.domain.IntervalSnatches
import dev.justinsmith.lift.domain.PerceivedEffort
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
    override val workouts: StateFlow<List<IntervalWorkout>> =
        combine(historyStore.history, presetStore.presets, settingsStore.appSettings) { history, presets, app ->
            history.map { it.asIntervalWorkout(presets, app.body) }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            historyStore.history.value.map {
                it.asIntervalWorkout(presetStore.presets.value, settingsStore.appSettings.value.body)
            },
        )

    override val sportDays: StateFlow<List<SportDay>> =
        combine(historyStore.sportDays, historyStore.classes, settingsStore.appSettings) { days, classes, app ->
            days.map { it.asSportDay(classes, app.body) }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            historyStore.sportDays.value.map {
                it.asSportDay(historyStore.classes.value, settingsStore.appSettings.value.body)
            },
        )

    override val weeklyGoal: StateFlow<Int?> = settingsStore.appSettings
        .map { it.weeklyGoal }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.weeklyGoal)

    override val bodyWeightKg: StateFlow<Double?> = settingsStore.appSettings
        .map { it.body.weightKg }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.body.weightKg)

    override val voice: StateFlow<Boolean> = settingsStore.cueSettings
        .map { it.voice }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.cueSettings.value.voice)

    override val maxHeartRate: StateFlow<Int?> = settingsStore.appSettings
        .map { it.body.maxHeartRate }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.body.maxHeartRate)

    override val restingHeartRate: StateFlow<Int?> = settingsStore.appSettings
        .map { it.body.restingHeartRate }
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.appSettings.value.body.restingHeartRate)

    override val heartRateAlerts: StateFlow<Boolean> = settingsStore.cueSettings
        .map { it.heartRateAlerts && it.sound }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            settingsStore.cueSettings.value.let { it.heartRateAlerts && it.sound },
        )

    /** As History counts a session's calories: all of it as kettlebell work, its rests being short. */
    override fun sessionCalories(elapsedSeconds: Int): Int? =
        settingsStore.appSettings.value.body.calories(elapsedSeconds, 0, Activity.Kettlebell)
}

/**
 * The day as Lift reads it, with what a sensor recorded of its classes of the sport: the seconds their rounds came
 * to and what they cost the heart, by the zones [body] sets.
 */
private fun LoggedSport.asSportDay(classes: List<SportClass>, body: BodySettings): SportDay {
    val recorded = classes.filter { it.epochDay == epochDay && it.sport == sport }
    val load = recorded.sumOf { each ->
        heartLoad(each.samples.map { it.bpm }, body.maxHeartRate ?: DEFAULT_MAX_BPM, restingBpm = body.restingHeartRate)
    }
    return SportDay(
        epochDay,
        grappling = sport == Sport.Bjj,
        matSeconds = recorded.takeIf { it.isNotEmpty() }?.sumOf { each -> each.rounds.sumOf { it.seconds } },
        heartLoad = load.takeIf { it > 0 },
    )
}

// Every stored workout has an id; one without is never in the history flow, but it still needs a key.
private fun HistoryEntry.asIntervalWorkout(presets: List<Preset>, body: BodySettings) = IntervalWorkout(
    id?.toString().orEmpty(),
    finishedAtEpochMillis,
    elapsedSeconds,
    snatches?.let { IntervalSnatches(it.perSet, it.bellKg, workEnds(), presets.matching(config)?.name) },
    // The two sides' scales are one, 1 to 5, each kept by its level.
    effort?.let { felt -> PerceivedEffort.entries.first { it.level == felt.level } },
    heartRateSamples
        ?.let { heartLoad(it, body.maxHeartRate ?: DEFAULT_MAX_BPM, restingBpm = body.restingHeartRate) }
        ?.takeIf { it > 0 },
)
