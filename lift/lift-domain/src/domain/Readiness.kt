package dev.justinsmith.lift.domain

// How ready the lifter is today, from what is logged: the last session, the conditioning, the mat and the grip.

/** How ready for a full day of training: [Taxed] is a day to take easier. */
enum class Readiness { Fresh, Normal, Taxed }

/**
 * Why a day is [Readiness.Taxed]: the last session, an interval workout of the app's ([HardConditioning]), what a
 * heart-rate sensor read of today or yesterday ([HeartLoad]), BJJ on the grip, or the grip alone.
 */
enum class ReadinessReason { HardSession, HardConditioning, HeartLoad, Mat, Grip }

/**
 * What a day's training cost the heart as a sensor read it, on [epochDay]: the minutes in each zone weighed by
 * the zone, a session's, a workout's or a class's. Several on a day add up.
 */
data class HeartLoad(val epochDay: Long, val load: Int)

/** A day's heart load this many times a usual training day's is still in the body the day after. */
private const val HEAVY_HEART_RATIO = 1.5

/** A day under this much heart load is never a heavy one, however it compares: an hour's easy work. */
private const val HEART_LOAD_FLOOR = 60

/** The days of heart load a heavy day is weighed against, and the training days among them it takes to know. */
private const val HEART_USUAL_DAYS = 28
private const val HEART_USUAL_MIN_DAYS = 3

/**
 * Whether [today] or the day before was a heavy day on the heart by [loads]: half again the usual training
 * day's of the four weeks before those two, and no less than an hour's easy work. Unknown, so not heavy, until
 * a sensor has read [HEART_USUAL_MIN_DAYS] days to go by.
 */
fun heavyHeartDay(loads: List<HeartLoad>, today: Long): Boolean {
    val byDay = loads.groupBy({ it.epochDay }, { it.load }).mapValues { it.value.sum() }
    val recent = maxOf(byDay[today] ?: 0, byDay[today - 1] ?: 0)
    val before = byDay.filterKeys { it in (today - 1 - HEART_USUAL_DAYS)..(today - 2) }.values.filter { it > 0 }
    if (recent < HEART_LOAD_FLOOR || before.size < HEART_USUAL_MIN_DAYS) return false
    return recent > before.average() * HEAVY_HEART_RATIO
}

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
 * days; after any of the [conditioning] did, so a later easy one hides none; after a heavy day on the heart today
 * or yesterday as a sensor read it ([heart], [heavyHeartDay]: what was said of a day comes first, and this reads
 * the days nothing was said of); after BJJ today or yesterday with the grip above the weeks before; or with the
 * grip overreached. Fresh after [FRESH_AFTER_DAYS] days or more with no
 * session, otherwise normal.
 */
fun readiness(
    sessions: List<LiftSession>,
    grappling: List<Long>,
    conditioning: List<WorkoutEffort>,
    grip: GripLoad,
    nowMillis: Long,
    dayOf: (Long) -> Long,
    heart: List<HeartLoad> = emptyList(),
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
        heavyHeartDay(heart, today) -> ReadinessCheck(Readiness.Taxed, ReadinessReason.HeartLoad)
        mat -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Mat)
        grip.overreached -> ReadinessCheck(Readiness.Taxed, ReadinessReason.Grip)
        lastAt != null && today - dayOf(lastAt) >= FRESH_AFTER_DAYS -> ReadinessCheck(Readiness.Fresh)
        else -> ReadinessCheck(Readiness.Normal)
    }
}
