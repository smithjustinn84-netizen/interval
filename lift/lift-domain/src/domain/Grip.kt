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

/**
 * The time rolling a class of [GRAPPLING_GRIP_LOAD] is taken to hold: five rounds of five minutes. A class a
 * sensor recorded is weighed by its own rounds against this.
 */
const val USUAL_MAT_SECONDS = 1_500

/** The least and the most of [GRAPPLING_GRIP_LOAD] a recorded class weighs, however little or much was rolled. */
private const val MAT_LOAD_LEAST = 0.5
private const val MAT_LOAD_MOST = 2.0

/**
 * A grappling day's load on the grip: [GRAPPLING_GRIP_LOAD], or for a class a sensor recorded that by the time
 * its rounds came to ([matSeconds]) against [USUAL_MAT_SECONDS], so a night of drilling weighs half a day and a
 * long open mat up to two.
 */
fun grapplingGripLoad(matSeconds: Int?): Double = if (matSeconds == null) {
    GRAPPLING_GRIP_LOAD
} else {
    GRAPPLING_GRIP_LOAD * (matSeconds.toDouble() / USUAL_MAT_SECONDS).coerceIn(MAT_LOAD_LEAST, MAT_LOAD_MOST)
}

/** The days in the week of load weighed against the weeks before it. */
const val GRIP_ACUTE_DAYS = 7

/** The days of load the week is weighed against: four weeks. */
const val GRIP_CHRONIC_DAYS = 28

/** The weeks of [GRIP_CHRONIC_DAYS] that [GripLoad.weeks] holds. */
const val GRIP_WEEKS = GRIP_CHRONIC_DAYS / GRIP_ACUTE_DAYS

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
 * held, BJJ's in neither), [chronic] over the last [GRIP_CHRONIC_DAYS], the [trainingDays] in those that carried
 * any, and the [weeks] of the month, each [GRIP_ACUTE_DAYS] days counted back from today (not calendar weeks),
 * oldest first: so the last is [acute], and together they are [chronic]. Empty with nothing in the month.
 */
data class GripLoad(
    val acute: Double = 0.0,
    val chronic: Double = 0.0,
    val ballistic: Double = 0.0,
    val grind: Double = 0.0,
    val trainingDays: Int = 0,
    val weeks: List<Double> = emptyList(),
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
 * epoch day each, every one [GRAPPLING_GRIP_LOAD] but those in [matSeconds], the days a sensor recorded the class
 * on, each weighed by the seconds its rounds came to ([grapplingGripLoad]).
 */
fun gripLoad(
    sets: List<SetLog>,
    grappling: List<Long>,
    today: Long,
    dayOf: (Long) -> Long,
    matSeconds: Map<Long, Int> = emptyMap(),
): GripLoad {
    val week = (today - GRIP_ACUTE_DAYS + 1)..today
    val month = (today - GRIP_CHRONIC_DAYS + 1)..today
    val lifted = sets.map { dayOf(it.loggedAtEpochMillis) to it }.filter { (day, _) -> day in month }
    val mat = grappling.filter { it in month }.distinct()
    val thisWeek = lifted.filter { (day, _) -> day in week }.map { it.second }
    val ballistic = thisWeek.filter { it.exercise.movement.tempo == Tempo.Ballistic }.sumOf { it.gripLoad }
    val grind = thisWeek.sumOf { it.gripLoad } - ballistic
    // Each week's load, by how many weeks back from today its day falls: 0 this week.
    val byWeek = DoubleArray(GRIP_WEEKS)
    lifted.forEach { (day, set) -> byWeek[((today - day) / GRIP_ACUTE_DAYS).toInt()] += set.gripLoad }
    fun matLoad(day: Long) = grapplingGripLoad(matSeconds[day])
    mat.forEach { day -> byWeek[((today - day) / GRIP_ACUTE_DAYS).toInt()] += matLoad(day) }
    val weeks = byWeek.reversed().takeIf { load -> load.any { it > 0 } }.orEmpty()
    return GripLoad(
        acute = ballistic + grind + mat.filter { it in week }.sumOf(::matLoad),
        chronic = lifted.sumOf { it.second.gripLoad } + mat.sumOf(::matLoad),
        ballistic = ballistic,
        grind = grind,
        trainingDays = (lifted.map { it.first } + mat).distinct().size,
        weeks = weeks,
    )
}

/** The grip's load of a session: every set's. */
val LiftSession.gripLoad: Double get() = sets.sumOf { it.gripLoad }

/** Whether a session asked enough of the hands to say how to look after them: many snatches, or one-hand swings. */
val LiftSession.wantsHandCare: Boolean get() = sets.wantsHandCare

/**
 * Whether these sets, a day's however they were done (a session's, an interval workout's snatches), asked enough of
 * the hands to say how to look after them: [HAND_CARE_SNATCHES] snatches, or [HAND_CARE_SWINGS] one-hand swings.
 */
val List<SetLog>.wantsHandCare: Boolean
    get() = filter { it.exercise == BuiltInExercise.Snatch }.sumOf { it.reps } >= HAND_CARE_SNATCHES ||
        filter { it.exercise == BuiltInExercise.OneHandSwing }.sumOf { it.reps } >= HAND_CARE_SWINGS

/**
 * Whether the hands want looking after once [done], the sets of a workout just finished, are added to [others] (the
 * day's other sets wherever they were logged, each on the day [dayOf] its time falls on): [done] holds snatches or
 * one-hand swings, and with the others on [today] they come to [wantsHandCare]'s mark. So 15:15's snatches and a
 * session's on the same day say it together, and a session of squats after them says nothing of the hands.
 */
fun wantsHandCare(done: List<SetLog>, others: List<SetLog>, today: Long, dayOf: (Long) -> Long): Boolean {
    val handWork = done.any { it.exercise == BuiltInExercise.Snatch || it.exercise == BuiltInExercise.OneHandSwing }
    return handWork && (done + others.filter { dayOf(it.loggedAtEpochMillis) == today }).wantsHandCare
}
