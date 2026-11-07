package dev.justinsmith.lift.domain

// How hard the hands have worked: what wears the forearms and tears calluses, so the coaching can spare them.

/** A ballistic set is this much harder on the grip than a grind of the same work: the catch, and the float. */
private const val BALLISTIC_GRIP = 1.5

/** Two bells, two hands at work. */
private const val DOUBLE_GRIP = 2.0

/** A hold counts its seconds as this many to a rep. */
private const val HOLD_SECONDS_PER_REP = 10.0

/**
 * A day of BJJ on the grip: gripping the gi through a class, weighed as about a hundred two-hand swings
 * (100 × grip 2 × [BALLISTIC_GRIP]).
 */
const val GRAPPLING_GRIP_LOAD = 300.0

/** The days in the week of load weighed against the weeks before it. */
const val GRIP_ACUTE_DAYS = 7

/** The days of load the week is weighed against: four weeks. */
const val GRIP_CHRONIC_DAYS = 28

/** This week's load past this many times the weeks before's average is too much, too soon. */
const val GRIP_OVERREACHED_RATIO = 1.3

/** A week under this much load is too light to call overreached, however it compares: two days of swings. */
const val GRIP_LOAD_FLOOR = 600.0

/** A session this many times a usual day's load is a heavy grip day. */
private const val HEAVY_DAY_RATIO = 1.5

/** The training days a month needs before a usual day is known. */
private const val USUAL_DAY_MIN_DAYS = 3

/** Snatches in a session past which the hands want looking after. */
const val HAND_CARE_SNATCHES = 100

/** One-hand swings in a session past which the hands want looking after. */
const val HAND_CARE_SWINGS = 300

/**
 * What this set asked of the grip: its reps, each the lifts of a complex ([Movement.perRep]), or a hold's seconds
 * by ten, times the lift's [Movement.grip]; half again for a ballistic lift, double with two bells.
 */
val SetLog.gripLoad: Double
    get() {
        val movement = exercise.movement
        val work = if (exercise.measure == Measure.Time) {
            (seconds ?: reps) / HOLD_SECONDS_PER_REP
        } else {
            reps.toDouble() * movement.perRep
        }
        val tempo = if (movement.tempo == Tempo.Ballistic) BALLISTIC_GRIP else 1.0
        val hands = if (double) DOUBLE_GRIP else 1.0
        return work * movement.grip * tempo * hands
    }

/**
 * The grip's load lately: [acute] over the last [GRIP_ACUTE_DAYS] (of it, [ballistic] thrown and [grind] slow or
 * held, BJJ's in neither), [chronic] over the last [GRIP_CHRONIC_DAYS], and the [trainingDays] in those that
 * carried any.
 */
data class GripLoad(
    val acute: Double = 0.0,
    val chronic: Double = 0.0,
    val ballistic: Double = 0.0,
    val grind: Double = 0.0,
    val trainingDays: Int = 0,
) {
    /** A week's load in the three weeks before this one, on average; null with none, as in a first week. */
    val weeksBefore: Double?
        get() = ((chronic - acute) / (GRIP_CHRONIC_DAYS / GRIP_ACUTE_DAYS - 1)).takeIf { it > 0 }

    /** This week against the weeks before; null without them to go by. */
    val ratio: Double? get() = weeksBefore?.let { acute / it }

    /** Too much, too soon: this week well past the weeks before, and heavy enough to matter. */
    val overreached: Boolean get() = acute >= GRIP_LOAD_FLOOR && (ratio ?: 0.0) > GRIP_OVERREACHED_RATIO

    /** Above what the weeks before carried, on average. */
    val aboveUsual: Boolean get() = (ratio ?: 0.0) > 1.0

    /** Of this week's lifting, how much was ballistic, 0 to 1; null with none. */
    val ballisticShare: Double? get() = (ballistic + grind).takeIf { it > 0 }?.let { ballistic / it }

    /** A training day's load, on average over the month; null until [USUAL_DAY_MIN_DAYS] days are known. */
    val usualDay: Double? get() = trainingDays.takeIf { it >= USUAL_DAY_MIN_DAYS }?.let { chronic / it }

    /** Whether [load], a session's, was a heavy day on the grip: half again a usual day's. */
    fun heavyDay(load: Double): Boolean = usualDay?.let { load > it * HEAVY_DAY_RATIO } == true
}

/**
 * The grip's load up to [today] from [sets] (each on the day [dayOf] its time falls on) and the [grappling] days, an
 * epoch day each, every one [GRAPPLING_GRIP_LOAD].
 */
fun gripLoad(sets: List<SetLog>, grappling: List<Long>, today: Long, dayOf: (Long) -> Long): GripLoad {
    val week = (today - GRIP_ACUTE_DAYS + 1)..today
    val month = (today - GRIP_CHRONIC_DAYS + 1)..today
    val lifted = sets.map { dayOf(it.loggedAtEpochMillis) to it }.filter { (day, _) -> day in month }
    val mat = grappling.filter { it in month }.distinct()
    val thisWeek = lifted.filter { (day, _) -> day in week }.map { it.second }
    val ballistic = thisWeek.filter { it.exercise.movement.tempo == Tempo.Ballistic }.sumOf { it.gripLoad }
    val grind = thisWeek.sumOf { it.gripLoad } - ballistic
    return GripLoad(
        acute = ballistic + grind + mat.count { it in week } * GRAPPLING_GRIP_LOAD,
        chronic = lifted.sumOf { it.second.gripLoad } + mat.size * GRAPPLING_GRIP_LOAD,
        ballistic = ballistic,
        grind = grind,
        trainingDays = (lifted.map { it.first } + mat).distinct().size,
    )
}

/** The grip's load of a session: every set's. */
val LiftSession.gripLoad: Double get() = sets.sumOf { it.gripLoad }

/** Whether a session asked enough of the hands to say how to look after them: many snatches, or one-hand swings. */
val LiftSession.wantsHandCare: Boolean
    get() = sets.filter { it.exercise == BuiltInExercise.Snatch }.sumOf { it.reps } >= HAND_CARE_SNATCHES ||
        sets.filter { it.exercise == BuiltInExercise.OneHandSwing }.sumOf { it.reps } >= HAND_CARE_SWINGS
