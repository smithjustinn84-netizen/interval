package dev.justinsmith.interval.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The workout under way, as one JSON value under one key: it is written and read whole, and cleared whole.

private val WORKOUT_PROGRESS = stringPreferencesKey("workout_progress")

// Lenient both ways, so a snapshot from another build reads back as far as it can, or as nothing.
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** The saved workout, or null when none is saved or it can't be read: then there is nothing to pick up. */
internal fun Preferences.toWorkoutProgress(): WorkoutProgress? = this[WORKOUT_PROGRESS]?.let { stored ->
    runCatching { json.decodeFromString<StoredProgress>(stored).toDomain() }.getOrNull()
}

internal fun MutablePreferences.write(progress: WorkoutProgress?) {
    if (progress == null) remove(WORKOUT_PROGRESS) else this[WORKOUT_PROGRESS] = json.encodeToString(progress.stored())
}

/** [WorkoutProgress] as stored: primitives only, so the domain types can change shape without a migration. */
@Serializable
private class StoredProgress(
    val id: Long,
    val prepSeconds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val activity: String,
    val phase: String,
    val round: Int,
    val remainingMillis: Long,
    val phaseMillis: Long,
    val halfwayCued: Boolean,
    val running: Boolean,
    val ended: Boolean,
    val activeMillis: Long,
    val workMillis: Long,
    val weekCount: Int? = null,
    val weekGoal: Int? = null,
    val weekStreak: Int? = null,
    val atEpochMillis: Long,
) {
    fun toDomain(): WorkoutProgress? {
        val phase = Phase.entries.firstOrNull { it.name == phase } ?: return null
        val config = IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds, Activity.named(activity))
        return WorkoutProgress(
            id = id,
            state = WorkoutState(config, phase, round, remainingMillis, phaseMillis, halfwayCued),
            running = running,
            ended = ended,
            activeMillis = activeMillis,
            workMillis = workMillis,
            week = weekCount?.let { WeekStanding(it, weekGoal, weekStreak ?: 0) },
            atEpochMillis = atEpochMillis,
        )
    }
}

private fun WorkoutProgress.stored() = StoredProgress(
    id = id,
    prepSeconds = state.config.prepSeconds,
    workSeconds = state.config.workSeconds,
    restSeconds = state.config.restSeconds,
    rounds = state.config.rounds,
    activity = state.config.activity.name,
    phase = state.phase.name,
    round = state.round,
    remainingMillis = state.remainingMillis,
    phaseMillis = state.phaseMillis,
    halfwayCued = state.halfwayCued,
    running = running,
    ended = ended,
    activeMillis = activeMillis,
    workMillis = workMillis,
    weekCount = week?.count,
    weekGoal = week?.goal,
    weekStreak = week?.streak,
    atEpochMillis = atEpochMillis,
)
