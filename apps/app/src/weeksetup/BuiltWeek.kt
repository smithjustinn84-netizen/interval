package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.snatchIntervals
import dev.justinsmith.interval.timer.spreadWeekdays
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramFamily
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.starterProgram

/**
 * Set up's answers: the [sport] trained beside the bell (null for the bell alone) on its [sportDays]
 * (1 for Monday), the [bells] owned in kg and the [pairs] of them owned two of, and how many [minutes] a session is.
 */
data class WeekAnswers(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val bells: Set<Double> = emptySet(),
    val minutes: Int = AppSettings.SESSION_MINUTES.first(),
    val pairs: Set<Double> = emptySet(),
)

/**
 * The week the answers come to: the [sport] on its [sportDays], the strength [program] on its [liftDays], the
 * [snatchIntervals] on its [conditioningDay] beside a grappler's program, and the [goal] they add up to with
 * whatever else is planned every week. [keepsProgram] when the program is the one followed already, which building
 * never starts again. [remove] are the plans building takes off first: the week it made the last time.
 */
data class BuiltWeek(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val program: Program? = null,
    val keepsProgram: Boolean = false,
    val liftDays: Set<Int> = emptySet(),
    val conditioningDay: Int? = null,
    val goal: Int? = null,
    val remove: List<Long> = emptyList(),
)

/** The days a week trained at most, so one is always left to rest. */
private const val MOST_TRAINING_DAYS = DAYS_PER_WEEK - 1

/** The most days of the sport a week beside which the grappler's program gets a third session, of intervals. */
private const val MOST_SPORT_DAYS_FOR_INTERVALS = 2

private val grapplersProgram = ProgramKey.BuiltIn(ProgramId.BjjKettlebell)

/**
 * The week [answers] build, over what is [planned] now. The sport goes on its days. The program is the one
 * [followed], else the one that suits the bells owned and the session's length ([starterProgram], timed at the
 * [restSeconds] the user takes), a grappler's first for BJJ; with no bell and none followed there is none. Its
 * days go on weekdays with nothing planned every week, as many as it asks for while a rest day is left, spread
 * as evenly as they go; with no day free it isn't taken up. The grappler's program ([ProgramId.BjjKettlebell])
 * beside the sport on two days or fewer gets [snatchIntervals] weekly on a third, the free day furthest from its
 * two while a rest day is left; on more days of the mat its two are enough. The weekly days of [was], the sport
 * Set up opened with, and of the sport answered are replaced, and so are the program's, and with the grappler's
 * program followed or taken up the intervals planned weekly; everything else planned stays, and its weekdays are
 * taken.
 */
fun buildWeek(
    answers: WeekAnswers,
    planned: List<PlannedWorkout>,
    followed: Program?,
    restSeconds: Int,
    was: Sport? = null,
): BuiltWeek {
    val sport = answers.sport?.takeIf { answers.sportDays.isNotEmpty() }
    val sportDays = if (sport == null) emptySet() else answers.sportDays
    val replaced = setOfNotNull(answers.sport, was)
    val wanted = followed ?: starter(answers, restSeconds)
    val grappler = wanted?.key == grapplersProgram
    val weekly = planned.filter { it.schedule is PlanSchedule.Weekly }
    val remove = weekly.filter { plan ->
        when (val workout = plan.workout) {
            is PlanWorkout.SportDay -> workout.sport in replaced
            PlanWorkout.Lift.ProgramDay -> true
            is PlanWorkout.Interval -> grappler && workout.config.sameShape(snatchIntervals.config)
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
    val conditioningDay = (free - liftDays).takeIf {
        grappler && liftDays.size == 2 && sportDays.size <= MOST_SPORT_DAYS_FOR_INTERVALS &&
            taken.size + liftDays.size < MOST_TRAINING_DAYS
    }?.furthestFrom(liftDays)
    val total = standing.size + sportDays.size + liftDays.size + listOfNotNull(conditioningDay).size
    return BuiltWeek(
        sport = sport,
        sportDays = sportDays,
        program = program,
        keepsProgram = program != null && followed != null,
        liftDays = liftDays,
        conditioningDay = conditioningDay,
        goal = total.takeIf { it > 0 }?.coerceIn(AppSettings.WEEKLY_GOAL_RANGE),
        remove = remove.map { it.id },
    )
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
    if (answers.bells.isEmpty()) return null
    val prefer = ProgramFamily.Grappling.takeIf { answers.sport == Sport.Bjj }
    return starterProgram(answers.minutes, restSeconds, prefer, pair = answers.pairs.isNotEmpty())
}
