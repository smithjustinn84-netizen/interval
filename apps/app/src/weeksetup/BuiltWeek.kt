package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramFamily
import dev.justinsmith.lift.domain.starterProgram

/**
 * Set up's four answers: the [sport] trained beside the bell (null for the bell alone) on its [sportDays]
 * (1 for Monday), the [bells] owned in kg, and how many [minutes] a session is.
 */
data class WeekAnswers(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val bells: Set<Double> = emptySet(),
    val minutes: Int = AppSettings.SESSION_MINUTES.first(),
)

/**
 * The week the answers come to: the [sport] on its [sportDays], the strength [program] on its [liftDays], and
 * the [goal] they add up to with whatever else is planned every week. [keepsProgram] when the program is the one
 * followed already, which building never starts again. [remove] are the plans building takes off first: the
 * week it made the last time.
 */
data class BuiltWeek(
    val sport: Sport? = null,
    val sportDays: Set<Int> = emptySet(),
    val program: Program? = null,
    val keepsProgram: Boolean = false,
    val liftDays: Set<Int> = emptySet(),
    val goal: Int? = null,
    val remove: List<Long> = emptyList(),
)

/** The days a week trained at most, so one is always left to rest. */
private const val MOST_TRAINING_DAYS = DAYS_PER_WEEK - 1

/**
 * The week [answers] build, over what is [planned] now. The sport goes on its days. The program is the one
 * [followed], else the one that suits a single bell and the session's length ([starterProgram], timed at the
 * [restSeconds] the user takes), a grappler's first for BJJ; with no bell and none followed there is none. Its
 * days go on weekdays with nothing planned every week, as many as it asks for while a rest day is left, spread
 * as evenly as they go; with no day free it isn't taken up. The weekly days of [was], the sport Set up opened
 * with, and of the sport answered are replaced, and so are the program's; everything else planned stays, and
 * its weekdays are taken.
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
    val weekly = planned.filter { it.schedule is PlanSchedule.Weekly }
    val remove = weekly.filter { plan ->
        when (val workout = plan.workout) {
            is PlanWorkout.SportDay -> workout.sport in replaced
            PlanWorkout.Lift.ProgramDay -> true
            else -> false
        }
    }
    val standing = weekly - remove.toSet()
    val taken = standing.map { (it.schedule as PlanSchedule.Weekly).weekday }.toSet() + sportDays
    val free = (1..DAYS_PER_WEEK).toSet() - taken
    val wanted = followed ?: starter(answers, restSeconds)
    val liftDays = wanted?.let { evenlySpread(free, minOf(it.daysPerWeek, MOST_TRAINING_DAYS - taken.size)) }.orEmpty()
    val program = wanted?.takeIf { followed != null || liftDays.isNotEmpty() }
    val total = standing.size + sportDays.size + liftDays.size
    return BuiltWeek(
        sport = sport,
        sportDays = sportDays,
        program = program,
        keepsProgram = program != null && followed != null,
        liftDays = liftDays,
        goal = total.takeIf { it > 0 }?.coerceIn(AppSettings.WEEKLY_GOAL_RANGE),
        remove = remove.map { it.id },
    )
}

private fun starter(answers: WeekAnswers, restSeconds: Int): Program? {
    if (answers.bells.isEmpty()) return null
    val prefer = ProgramFamily.Grappling.takeIf { answers.sport == Sport.Bjj }
    return starterProgram(answers.minutes, restSeconds, prefer)
}

/**
 * [count] of the [free] weekdays, as evenly round the week as they go: the days whose gaps, the week taken as a
 * circle, differ least, and of two as even the earlier. Three of a whole week are Monday, Wednesday and Friday.
 */
internal fun evenlySpread(free: Set<Int>, count: Int): Set<Int> {
    if (count <= 0 || free.isEmpty()) return emptySet()
    val days = free.sorted()
    if (count >= days.size) return free
    return combinations(days, count).minByOrNull(::unevenness).orEmpty().toSet()
}

/** The sum of the squared gaps between [days] round the week: least when they are evenly apart. */
private fun unevenness(days: List<Int>): Int = days.indices.sumOf { index ->
    val gap = (days[(index + 1) % days.size] - days[index] + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK + 1
    gap * gap
}

/** Every [count] of [days], each in order and the earliest first. */
private fun combinations(days: List<Int>, count: Int): List<List<Int>> = when {
    count == 0 -> listOf(emptyList())
    days.size < count -> emptyList()
    else -> combinations(days.drop(1), count - 1).map { listOf(days.first()) + it } + combinations(days.drop(1), count)
}
