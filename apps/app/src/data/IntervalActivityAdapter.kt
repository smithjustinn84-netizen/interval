package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What Lift reads of the interval side, from [repository], kept current on [scope]. */
class RepositoryIntervalActivity(
    private val repository: IntervalRepository,
    scope: CoroutineScope,
) : IntervalActivity {
    override val workouts: StateFlow<List<IntervalWorkout>> = repository.history
        .map { history -> history.map { it.asIntervalWorkout() } }
        .stateIn(scope, SharingStarted.Eagerly, repository.history.value.map { it.asIntervalWorkout() })

    override val weeklyGoal: StateFlow<Int?> = repository.appSettings
        .map { it.weeklyGoal }
        .stateIn(scope, SharingStarted.Eagerly, repository.appSettings.value.weeklyGoal)

    /** As History counts a session's calories: all of it as kettlebell work, its rests being short. */
    override fun sessionCalories(elapsedSeconds: Int): Int? =
        repository.appSettings.value.body.calories(elapsedSeconds, 0, Activity.Kettlebell)
}

// Every stored workout has an id; one without is never in the history flow, but it still needs a key.
private fun HistoryEntry.asIntervalWorkout() =
    IntervalWorkout(id?.toString().orEmpty(), finishedAtEpochMillis, elapsedSeconds)
