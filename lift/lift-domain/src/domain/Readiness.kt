package dev.justinsmith.lift.domain

// How ready the lifter is today, from what is logged: the last session, the conditioning, the mat and the grip.

/** How ready for a full day of training: [Taxed] is a day to take easier. */
enum class Readiness { Fresh, Normal, Taxed }

/**
 * Why a day is [Readiness.Taxed]: the last session, an interval workout of the app's ([HardConditioning]), BJJ on the
 * grip, or the grip alone.
 */
enum class ReadinessReason { HardSession, HardConditioning, Mat, Grip }

/** An interval workout as readiness reads it: when it finished, and how hard it was said to feel. */
data class WorkoutEffort(val finishedAtEpochMillis: Long, val effort: PerceivedEffort)

/** How ready, and for a taxed day why. */
data class ReadinessCheck(val readiness: Readiness, val reason: ReadinessReason? = null)

/** A session or a workout felt this hard or more is still in the legs within [HARD_SESSION_HOURS]. */
private val HARD_SESSION = PerceivedEffort.VeryHard

private const val HARD_SESSION_HOURS = 48L
private const val MILLIS_PER_HOUR = 3_600_000L

/** Days off since the last session that leave one fresh. */
private const val FRESH_AFTER_DAYS = 3

/**
 * How ready the lifter is at [nowMillis], from the [sessions] logged, the [grappling] days (an epoch day each,
 * only those logged: a class planned or not yet logged doesn't count), the interval workouts said to have felt as
 * they did ([conditioning]) and the [grip]'s load. Taxed after the last session felt very hard or more within two
 * days; after any of the [conditioning] did, so a later easy one hides none; after BJJ today or yesterday with the
 * grip above the weeks before; or with the grip overreached. Fresh after [FRESH_AFTER_DAYS] days or more with no
 * session, otherwise normal.
 */
fun readiness(
    sessions: List<LiftSession>,
    grappling: List<Long>,
    conditioning: List<WorkoutEffort>,
    grip: GripLoad,
    nowMillis: Long,
    dayOf: (Long) -> Long,
): ReadinessCheck {
    val today = dayOf(nowMillis)
    val (last, lastAt) = sessions.mapNotNull { session -> session.finishedAtEpochMillis?.let { session to it } }
        .maxByOrNull { it.second } ?: (null to null)
    fun recent(at: Long) = nowMillis - at <= HARD_SESSION_HOURS * MILLIS_PER_HOUR
    val hard = last?.effort?.let { it >= HARD_SESSION } == true && lastAt != null && recent(lastAt)
    val hardConditioning = conditioning.any { it.effort >= HARD_SESSION && recent(it.finishedAtEpochMillis) }
    val mat = grappling.any { it == today || it == today - 1 } && grip.aboveUsual
    return when {
        hard -> ReadinessCheck(Readiness.Taxed, ReadinessReason.HardSession)
        hardConditioning -> ReadinessCheck(Readiness.Taxed, ReadinessReason.HardConditioning)
        mat -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Mat)
        grip.overreached -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Grip)
        lastAt != null && today - dayOf(lastAt) >= FRESH_AFTER_DAYS -> ReadinessCheck(Readiness.Fresh)
        else -> ReadinessCheck(Readiness.Normal)
    }
}
