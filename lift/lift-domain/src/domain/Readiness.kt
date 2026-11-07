package dev.justinsmith.lift.domain

// How ready the lifter is today, from what is logged: the last session, the mat and the grip.

/** How ready for a full day of training: [Taxed] is a day to take easier. */
enum class Readiness { Fresh, Normal, Taxed }

/** Why a day is [Readiness.Taxed]: the last session, BJJ on the grip, or the grip alone. */
enum class ReadinessReason { HardSession, Mat, Grip }

/** How ready, and for a taxed day why. */
data class ReadinessCheck(val readiness: Readiness, val reason: ReadinessReason? = null)

/** A session felt this hard or more is still in the legs within [HARD_SESSION_HOURS]. */
private val HARD_SESSION = PerceivedEffort.VeryHard

private const val HARD_SESSION_HOURS = 36L
private const val MILLIS_PER_HOUR = 3_600_000L

/** Days off since the last session that leave one fresh. */
private const val FRESH_AFTER_DAYS = 3

/**
 * How ready the lifter is at [nowMillis], from the [sessions] logged, the [grappling] days (an epoch day each,
 * only those logged: a class planned or not yet logged doesn't count) and the [grip]'s load. Taxed after a
 * session that felt very hard or more within a day and a half; after BJJ today or yesterday with the grip above
 * the weeks before; or with the grip overreached. Fresh after [FRESH_AFTER_DAYS] days or more with no session,
 * otherwise normal.
 */
fun readiness(
    sessions: List<LiftSession>,
    grappling: List<Long>,
    grip: GripLoad,
    nowMillis: Long,
    dayOf: (Long) -> Long,
): ReadinessCheck {
    val today = dayOf(nowMillis)
    val (last, lastAt) = sessions.mapNotNull { session -> session.finishedAtEpochMillis?.let { session to it } }
        .maxByOrNull { it.second } ?: (null to null)
    val hard = last?.effort?.let { it >= HARD_SESSION } == true &&
        lastAt != null && nowMillis - lastAt <= HARD_SESSION_HOURS * MILLIS_PER_HOUR
    val mat = grappling.any { it == today || it == today - 1 } && grip.aboveUsual
    return when {
        hard -> ReadinessCheck(Readiness.Taxed, ReadinessReason.HardSession)
        mat -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Mat)
        grip.overreached -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Grip)
        lastAt != null && today - dayOf(lastAt) >= FRESH_AFTER_DAYS -> ReadinessCheck(Readiness.Fresh)
        else -> ReadinessCheck(Readiness.Normal)
    }
}
