package dev.justinsmith.lift.domain

/** A finished session that had sets of one exercise: when it started, and just those [sets]. */
data class PastSession(val uid: String, val startedAtEpochMillis: Long, val sets: List<SetLog>)

/**
 * The user's history with one exercise: how many finished sessions had it ([timesDone]), when it was [lastDone],
 * the [records] its sets set (one bell and two apart), and the [recent] sessions with it, newest first.
 */
data class ExerciseHistory(
    val timesDone: Int = 0,
    val lastDoneAtEpochMillis: Long? = null,
    val records: List<PersonalRecord> = emptyList(),
    val recent: List<PastSession> = emptyList(),
)

/**
 * [exercise]'s history over [sessions], finished ones newest first as the repository holds them: the session in
 * progress is not history yet. An exercise is known by its key, so one of the user's renamed since keeps its sets.
 */
fun exerciseHistory(
    exercise: Exercise,
    sessions: List<LiftSession>,
    limit: Int = RECENT_SESSIONS,
): ExerciseHistory {
    val done = sessions.mapNotNull { session ->
        session.sets.filter { it.exercise.key == exercise.key }
            .takeIf { it.isNotEmpty() }
            ?.let { PastSession(session.uid, session.startedAtEpochMillis, it) }
    }
    return ExerciseHistory(
        timesDone = done.size,
        lastDoneAtEpochMillis = done.firstOrNull()?.startedAtEpochMillis,
        // Oldest first, the order records are worked out in.
        records = personalRecords(done.asReversed().flatMap { it.sets }),
        recent = done.take(limit),
    )
}

/** How many of its sessions an exercise's page lists. */
const val RECENT_SESSIONS = 10

/** [count] sets in a row, each the same as [set]: the same load, reps or hold, side and band. */
data class SetRun(val set: SetLog, val count: Int)

/** [sets] in order, each run of the same set in a row folded into one [SetRun]. */
fun collapsedSets(sets: List<SetLog>): List<SetRun> = buildList {
    for (set in sets) {
        val last = lastOrNull()
        if (last != null &&
            last.set.sameAs(set)
        ) {
            set(lastIndex, last.copy(count = last.count + 1))
        } else {
            add(SetRun(set, 1))
        }
    }
}

private fun SetLog.sameAs(other: SetLog) = exercise.key == other.exercise.key && bellKg == other.bellKg &&
    reps == other.reps && side == other.side && double == other.double && equipment == other.equipment &&
    seconds == other.seconds && band == other.band

/** What a trend counts for an exercise: the bell's weight, the reps (bodyweight or band work), or the hold. */
enum class TrendMeasure { Kg, Reps, Seconds }

/** One session's best with an exercise: when the session started, and its [value] in the trend's measure. */
data class TrendPoint(val startedAtEpochMillis: Long, val value: Double)

/** An exercise's best each session over its latest sessions, oldest first, all in one [measure]. */
data class Trend(val measure: TrendMeasure, val points: List<TrendPoint>) {
    val max: Double get() = points.maxOf { it.value }

    val latest: Double get() = points.last().value
}

/**
 * [exercise]'s trend over [recent] (newest first, as [ExerciseHistory.recent] holds them), oldest first: a hold
 * by its longest set, a band or mat exercise, or a bodyweight one with nothing added, by its most reps in a set,
 * anything with a weight by its heaviest set (the bell's weight, one or two). Null until there are two
 * sessions to compare.
 */
fun exerciseTrend(exercise: Exercise, recent: List<PastSession>): Trend? {
    val sessions = recent.asReversed()
    if (sessions.size < MIN_TREND_SESSIONS) return null
    val measure = when {
        exercise.measure == Measure.Time -> TrendMeasure.Seconds
        exercise.equipment == Equipment.Band || exercise.equipment == Equipment.Mat -> TrendMeasure.Reps
        sessions.all { session -> session.sets.all { it.bellKg == 0.0 } } -> TrendMeasure.Reps
        else -> TrendMeasure.Kg
    }
    val points = sessions.map { session ->
        val value = when (measure) {
            TrendMeasure.Seconds -> session.sets.maxOf { it.seconds ?: 0 }.toDouble()
            TrendMeasure.Reps -> session.sets.maxOf { it.reps }.toDouble()
            TrendMeasure.Kg -> session.sets.maxOf { it.bellKg }
        }
        TrendPoint(session.startedAtEpochMillis, value)
    }
    return Trend(measure, points)
}

private const val MIN_TREND_SESSIONS = 2
