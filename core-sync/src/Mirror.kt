package dev.justinsmith.core.sync

/** What to change in a local copy of a sibling's workouts: [upserts] to write, [deletes] (uids) to drop. */
data class MirrorPlan(val upserts: List<SessionRecord>, val deletes: List<String>) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()
}

/**
 * How to bring a local copy, [local] (uid to when each was last changed), in step with [snapshot], the
 * sibling's whole set. The copy mirrors the snapshot: a workout it has live is written when new or changed
 * since, and one it has deleted, or no longer has at all (the sibling was reinstalled), is dropped.
 * Only [expected]'s own workouts are taken, so an app never imports what it published itself.
 */
fun mirror(local: Map<String, Long>, snapshot: FeedSnapshot, expected: AppId): MirrorPlan {
    if (snapshot.source != expected) return MirrorPlan(emptyList(), emptyList())
    val live = snapshot.sessions
        .filter { !it.deleted && it.source == expected }
        .groupBy { it.uid }
        .mapValues { (_, copies) -> copies.maxBy { it.updatedAtEpochMillis } }
    val upserts = live.values.filter { session ->
        val known = local[session.uid]
        known == null || session.updatedAtEpochMillis > known
    }
    val deletes = local.keys.filter { it !in live }
    return MirrorPlan(upserts.sortedBy { it.finishedAtEpochMillis }, deletes.sorted())
}
