package dev.justinsmith.bells.domain

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
