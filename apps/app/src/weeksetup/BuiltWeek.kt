package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.hangboardIntervals
import dev.justinsmith.interval.timer.hiitIntervals
import dev.justinsmith.interval.timer.longEffortsIntervals
import dev.justinsmith.interval.timer.matRoundsIntervals
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.snatchIntervals
import dev.justinsmith.interval.timer.spreadWeekdays
import dev.justinsmith.interval.timer.tabataIntervals
import dev.justinsmith.interval.timer.twentyMinuteCapIntervals
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.StarterGear
import dev.justinsmith.lift.domain.StarterSport
import dev.justinsmith.lift.domain.grapplersPrograms
import dev.justinsmith.lift.domain.starterProgram

/** What the user trains with, as chosen on Set up. */
enum class SetupEquipment {
    Kettlebells,
    Barbell,
    Bands,
    PullUpBar,
    Bodyweight,
    ConditioningOnly,
}

/**
 * Set up's answers: the [sport] trained beside strength/conditioning (null for none) on its [sportDays]
 * (1 for Monday), the [equipment] available, the [bells] owned in kg and the [pairs] of them owned two of,
 * and how many [minutes] a session is.
 */
data class WeekAnswers(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val bells: Set<Double> = emptySet(),
    val minutes: Int = AppSettings.SESSION_MINUTES.first(),
    val pairs: Set<Double> = emptySet(),
    val equipment: Set<SetupEquipment> = emptySet(),
) {
    /** Effective equipment: treats non-empty [bells] as [SetupEquipment.Kettlebells] when [equipment] wasn't set. */
    val effectiveEquipment: Set<SetupEquipment>
        get() = if (equipment.isEmpty() && bells.isNotEmpty()) setOf(SetupEquipment.Kettlebells) else equipment
}

/**
 * The week the answers come to: the [sport] on its [sportDays], the strength [program] on its [liftDays], the
 * [conditioning] preset on its [conditioningDays] (either [snatchIntervals] beside a grappler's program or the
 * conditioning-only sessions), and the [goal] they add up to with whatever else is planned every week.
 * [keepsProgram] when the program is the one followed already, which building never starts again. [remove] are
 * the plans building takes off first: the week it made the last time.
 */
data class BuiltWeek(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val program: Program? = null,
    val keepsProgram: Boolean = false,
    val liftDays: Set<Int> = emptySet(),
    val conditioning: Preset? = null,
    val conditioningDays: Set<Int> = emptySet(),
    val goal: Int? = null,
    val remove: List<Long> = emptyList(),
) {
    /** The single conditioning day when exactly one is planned (e.g. beside a grappler's program). */
    val conditioningDay: Int? get() = conditioningDays.singleOrNull()
}

/** The days a week trained at most, so one is always left to rest. */
private const val MOST_TRAINING_DAYS = DAYS_PER_WEEK - 1

/** The most days of the sport a week beside which the grappler's program gets a third session, of intervals. */
private const val MOST_SPORT_DAYS_FOR_INTERVALS = 2

private const val CONDITIONING_ALONE_DAYS = 3
private const val CONDITIONING_BESIDE_SPORT_DAYS = 2
private const val SHORT_SESSION_MINUTES = 10
private const val MEDIUM_SESSION_MINUTES = 15
private const val STANDARD_SESSION_MINUTES = 20
private const val LONG_SESSION_MINUTES = 30

private val setupConditioningPresets: List<Preset> = listOf(
    snatchIntervals,
    hangboardIntervals,
    matRoundsIntervals,
    tabataIntervals,
    hiitIntervals,
    longEffortsIntervals,
    twentyMinuteCapIntervals,
)

/**
 * The conditioning preset Set up schedules for a conditioning-only user, matched to [sport] and [minutes]. Never the
 * hangboard for a climber: fingers new to the wall tear a pulley on it, and Set up can't tell how long they have
 * climbed. It stays on Conditioning for whoever picks it.
 */
fun conditioningPreset(sport: Sport?, minutes: Int): Preset = when {
    sport == Sport.Bjj && minutes >= LONG_SESSION_MINUTES -> matRoundsIntervals
    minutes <= SHORT_SESSION_MINUTES -> tabataIntervals
    minutes <= MEDIUM_SESSION_MINUTES -> hiitIntervals
    minutes <= STANDARD_SESSION_MINUTES -> longEffortsIntervals
    else -> twentyMinuteCapIntervals
}

/**
 * The week [answers] build, over what is [planned] now. The sport goes on its days. The program is the one
 * [followed], else the one that suits the equipment owned and the session's length ([starterProgram], timed at the
 * [restSeconds] the user takes). For conditioning-only users (with no program followed), weekly conditioning
 * intervals go on free days instead. A grappler's 15:15 goes beside the program only for someone who has [snatched]
 * (or has it planned every week already): forty rounds of snatches are no first meeting with the lift. Its days go
 * on weekdays with nothing planned every week, as many as it asks for while a rest day is left, spread as evenly as
 * they go; with no day free it isn't taken up.
 */
fun buildWeek(
    answers: WeekAnswers,
    planned: List<PlannedWorkout>,
    followed: Program?,
    restSeconds: Int,
    was: Sport? = null,
    snatched: Boolean = false,
): BuiltWeek {
    val sport = answers.sport?.takeIf { answers.sportDays.isNotEmpty() }
    val sportDays = if (sport == null) emptySet() else answers.sportDays
    val replaced = setOfNotNull(answers.sport, was)
    val conditioningOnly = SetupEquipment.ConditioningOnly in answers.effectiveEquipment && followed == null
    val wanted = followed ?: if (conditioningOnly) null else starter(answers, restSeconds)
    val grappler = (wanted?.key as? ProgramKey.BuiltIn)?.id in grapplersPrograms
    val weekly = planned.filter { it.schedule is PlanSchedule.Weekly }
    val snatching = snatched ||
        weekly.any { (it.workout as? PlanWorkout.Interval)?.config?.sameShape(snatchIntervals.config) == true }
    val remove = weekly.filter { plan ->
        when (val workout = plan.workout) {
            is PlanWorkout.SportDay -> workout.sport in replaced

            PlanWorkout.Lift.ProgramDay -> true

            is PlanWorkout.Interval -> (grappler && workout.config.sameShape(snatchIntervals.config)) ||
                (conditioningOnly && setupConditioningPresets.any { workout.config.sameShape(it.config) })

            else -> false
        }
    }
    val standing = weekly - remove.toSet()
    val taken = standing.map { (it.schedule as PlanSchedule.Weekly).weekday }.toSet() + sportDays
    val free = (1..DAYS_PER_WEEK).toSet() - taken
    val liftDays = wanted?.let {
        spreadWeekdays(free, minOf(it.daysPerWeek, MOST_TRAINING_DAYS - taken.size))
    }.orEmpty()
    val program = wanted?.takeIf { followed != null || liftDays.isNotEmpty() }
    val (conditioning, conditioningDays) = planConditioning(
        conditioningOnly = conditioningOnly,
        grappler = grappler && snatching,
        sport = sport,
        sportDays = sportDays,
        minutes = answers.minutes,
        free = free,
        taken = taken,
        liftDays = liftDays,
    )
    // Days trained, as the goal counts them: two plans on one weekday are one day.
    val total = taken.size + liftDays.size + conditioningDays.size
    return BuiltWeek(
        sport = sport,
        sportDays = sportDays,
        program = program,
        keepsProgram = program != null && followed != null,
        liftDays = liftDays,
        conditioning = conditioning,
        conditioningDays = conditioningDays,
        goal = total.takeIf { it > 0 }?.coerceIn(AppSettings.WEEKLY_GOAL_RANGE),
        remove = remove.map { it.id },
    )
}

private fun planConditioning(
    conditioningOnly: Boolean,
    grappler: Boolean,
    sport: Sport?,
    sportDays: Set<Int>,
    minutes: Int,
    free: Set<Int>,
    taken: Set<Int>,
    liftDays: Set<Int>,
): Pair<Preset?, Set<Int>> {
    if (conditioningOnly) {
        val desired = if (sportDays.isEmpty()) CONDITIONING_ALONE_DAYS else CONDITIONING_BESIDE_SPORT_DAYS
        val days = spreadWeekdays(free, minOf(desired, MOST_TRAINING_DAYS - taken.size))
        return if (days.isEmpty()) null to emptySet() else conditioningPreset(sport, minutes) to days
    }
    val day = (free - liftDays).takeIf {
        grappler && liftDays.size == 2 && sportDays.size <= MOST_SPORT_DAYS_FOR_INTERVALS &&
            taken.size + liftDays.size < MOST_TRAINING_DAYS
    }?.furthestFrom(liftDays)
    return if (day == null) null to emptySet() else snatchIntervals to setOf(day)
}

/** Of these weekdays, the one furthest round the week from the nearest of [days]; of two as far, the earlier. */
private fun Set<Int>.furthestFrom(days: Set<Int>): Int? = sorted().maxByOrNull { day ->
    days.minOf { other ->
        minOf(
            (day - other + DAYS_PER_WEEK) % DAYS_PER_WEEK,
            (other - day + DAYS_PER_WEEK) % DAYS_PER_WEEK,
        )
    }
}

private fun starter(answers: WeekAnswers, restSeconds: Int): Program? {
    val gear = answers.starterGear()
    if (!gear.hasAny) return null
    val sport = when (answers.sport) {
        Sport.Bjj -> StarterSport.Grappling
        Sport.Climbing -> StarterSport.Climbing
        Sport.Running -> StarterSport.Running
        else -> null
    }
    return starterProgram(answers.minutes, restSeconds, gear, sport)
}

private fun WeekAnswers.starterGear(): StarterGear {
    val eq = effectiveEquipment
    val hasKettlebells = SetupEquipment.Kettlebells in eq && bells.isNotEmpty()
    val hasPairs = hasKettlebells && pairs.isNotEmpty()
    return StarterGear(
        oneBell = hasKettlebells && !hasPairs,
        pairOfBells = hasPairs,
        barbell = SetupEquipment.Barbell in eq,
        bands = SetupEquipment.Bands in eq,
        pullUpBar = SetupEquipment.PullUpBar in eq,
        bodyweight = SetupEquipment.Bodyweight in eq,
    )
}
