package dev.justinsmith.interval.data

import dev.justinsmith.bells.data.IntervalActivity
import dev.justinsmith.bells.data.IntervalWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What Lift reads of the interval side, from [repository], kept current on [scope]. */
class RepositoryIntervalActivity(repository: IntervalRepository, scope: CoroutineScope) : IntervalActivity {
    override val workouts: StateFlow<List<IntervalWorkout>> = repository.history
        .map { history -> history.map { it.asIntervalWorkout() } }
        .stateIn(scope, SharingStarted.Eagerly, repository.history.value.map { it.asIntervalWorkout() })

    override val weeklyGoal: StateFlow<Int?> = repository.appSettings
        .map { it.weeklyGoal }
        .stateIn(scope, SharingStarted.Eagerly, repository.appSettings.value.weeklyGoal)
}

// Every stored workout has an id; one without is never in the history flow, but it still needs a key.
private fun HistoryEntry.asIntervalWorkout() =
    IntervalWorkout(id?.toString().orEmpty(), finishedAtEpochMillis, elapsedSeconds)
