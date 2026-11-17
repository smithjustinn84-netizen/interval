package dev.justinsmith.lift.domain

/** A finished session that had sets of one exercise: when it started, and just those [sets]. */
data class PastSession(val uid: String, val startedAtEpochMillis: Long, val sets: List<SetLog>)

/**
 * The user's history with one exercise: how many finished sessions had it ([timesDone]), when it was [lastDone],
 * the [records] its sets set (one bell and two apart), the [recent] sessions with it, newest first, and the
 * [trendSessions] its trend is drawn over, the latest [TREND_SESSIONS] of them, newest first too.
 */
data class ExerciseHistory(
    val timesDone: Int = 0,
    val lastDoneAtEpochMillis: Long? = null,
    val records: List<PersonalRecord> = emptyList(),
    val recent: List<PastSession> = emptyList(),
    val trendSessions: List<PastSession> = emptyList(),
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
        trendSessions = done.take(TREND_SESSIONS),
    )
}

/** How many of its sessions an exercise's page lists. */
const val RECENT_SESSIONS = 10

/** How many of its sessions an exercise's trend is drawn over: months of training, a session or two a week. */
const val TREND_SESSIONS = 24

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

private fun SetLog.sameAs(other: SetLog) = exercise.key == other.exercise.key && load == other.load &&
    reps == other.reps && side == other.side && seconds == other.seconds

/**
 * What a trend counts for an exercise: the work of its best set, the bell's weight times the reps (so more reps at
 * the same bell climb), the reps alone (bodyweight or band work), or the hold.
 */
enum class TrendMeasure { Work, Reps, Seconds }

/**
 * One session's best with an exercise: when the session started, and its [value] in the trend's measure. A
 * [TrendMeasure.Work] point carries its set's [kg] (the bell's, one of two) and [reps], whose product is the value;
 * the others leave them 0.
 */
data class TrendPoint(val startedAtEpochMillis: Long, val value: Double, val kg: Double = 0.0, val reps: Int = 0)

/** An exercise's best each session over its latest sessions, oldest first, all in one [measure]. */
data class Trend(val measure: TrendMeasure, val points: List<TrendPoint>) {
    val max: Double get() = points.maxOf { it.value }

    val min: Double get() = points.minOf { it.value }

    val first: TrendPoint get() = points.first()

    /**
     * The points, by their place, where a [TrendMeasure.Work] trend went to another bell than the session before's:
     * the work falls there when the bell goes up (16 kg × 12 to 20 kg × 5), which is progress, not a dip. Empty for
     * the other measures.
     */
    val bellChanges: List<Int>
        get() = if (measure != TrendMeasure.Work) {
            emptyList()
        } else {
            points.indices.drop(1).filter { points[it].kg != points[it - 1].kg }
        }

    /**
     * The bell of each stretch of a [TrendMeasure.Work] trend, in kg, oldest first: the first point's, then each of
     * [bellChanges]'. Empty for the other measures.
     */
    val bells: List<Double>
        get() = if (measure != TrendMeasure.Work) emptyList() else (listOf(0) + bellChanges).map { points[it].kg }

    /**
     * Each point's height on the line, from 0 at the bottom to 1 at the top, scaled within its own bell: the points
     * between one [bellChanges] and the next run from their lowest to their highest, so the reps climbing at each
     * bell show, not one bell's work against another's. A stretch all of one value, or of one point, sits at half.
     * The other measures are one stretch.
     */
    val heights: List<Double>
        get() {
            val starts = listOf(0) + bellChanges + points.size
            return starts.zipWithNext().flatMap { (from, until) ->
                val stretch = points.subList(from, until).map { it.value }
                val low = stretch.min()
                val span = stretch.max() - low
                stretch.map { if (span > 0.0) (it - low) / span else HALF }
            }
        }

    val latest: Double get() = points.last().value
}

/**
 * [exercise]'s trend over [sessions] (newest first, as [ExerciseHistory.trendSessions] holds them), the latest
 * [TREND_SESSIONS] oldest first: a hold by its longest set, a band or mat exercise, or a bodyweight one with nothing
 * added, by its most reps in a set, anything with a weight by its best set's work, the bell's weight (one of two)
 * times the reps, a tie going to the heavier bell. Null until there are two sessions to compare.
 */
fun exerciseTrend(exercise: Exercise, sessions: List<PastSession>): Trend? {
    val oldestFirst = sessions.take(TREND_SESSIONS).asReversed()
    if (oldestFirst.size < MIN_TREND_SESSIONS) return null
    val measure = when {
        exercise.measure == Measure.Time -> TrendMeasure.Seconds
        exercise.equipment == Equipment.Band || exercise.equipment == Equipment.Mat -> TrendMeasure.Reps
        oldestFirst.all { session -> session.sets.all { it.load.kg == 0.0 } } -> TrendMeasure.Reps
        else -> TrendMeasure.Work
    }
    val points = oldestFirst.map { session ->
        val at = session.startedAtEpochMillis
        when (measure) {
            TrendMeasure.Seconds -> TrendPoint(at, session.sets.maxOf { it.seconds ?: 0 }.toDouble())

            TrendMeasure.Reps -> TrendPoint(at, session.sets.maxOf { it.reps }.toDouble())

            TrendMeasure.Work -> {
                val best = session.sets.maxWith(compareBy<SetLog> { it.load.kg * it.reps }.thenBy { it.load.kg })
                TrendPoint(at, best.load.kg * best.reps, best.load.kg, best.reps)
            }
        }
    }
    return Trend(measure, points)
}

private const val MIN_TREND_SESSIONS = 2

private const val HALF = 0.5
