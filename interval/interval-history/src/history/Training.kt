package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.sameAs
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.monthDaysOf
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.timer.weeksMeetingGoal

/** How one planned workout stands on one day. */
enum class PlanState {
    /** A workout of its shape finished that day; for a session on Lift, any session; for a sport, it logged. */
    Done,

    /** Its day has gone by without one. */
    Missed,

    /** Today or later, not done yet. */
    Open,

    /** A sport day gone by unlogged: not missed as far as the app can tell, so it counts for nothing either way. */
    Passed,

    /**
     * Moved to another day by the user: the day sheet's row for one of [TrainingDay.movedAway], the one-off it became
     * on the day it went to. Never among [TrainingDay.plans].
     */
    Moved,
}

/**
 * A planned workout on one day and how it stands: [workoutId] is the interval workout that did it, [liftUid] the
 * Lift session, if any.
 */
data class DayPlan(
    val plan: PlannedWorkout,
    val state: PlanState,
    val workoutId: Long? = null,
    val liftUid: String? = null,
)

/** What a day comes to, for the week strip and the calendar. */
enum class DayState {
    /** At least one workout finished, or a sport logged. */
    Done,

    /** Today or later, with a workout planned and still to do. */
    Planned,

    /** Gone by, with a plan not done and no workout at all. */
    Missed,

    /** Nothing done and no workout planned: a sport day not logged is one, its sport named beside it. */
    Rest,
}

/**
 * One day of training: its plans as they stand and its workouts, oldest first, the interval workouts, then the
 * kettlebell sessions ([liftWorkouts]) and the sport days logged ([sports]). Each counts toward the day and does
 * the plans of its own kind. A sport has no length, so it adds nothing to [seconds].
 */
data class TrainingDay(
    val epochDay: Long,
    val isToday: Boolean,
    val plans: List<DayPlan>,
    val workouts: List<HistoryEntry>,
    val liftWorkouts: List<LiftWorkout> = emptyList(),
    val sports: List<LoggedSport> = emptyList(),
    /**
     * The plans the user moved off the day, each the one-off it became on its new day: kept apart from [plans], so
     * nothing counts, marks or offers them here but the day sheet's line saying where each went.
     */
    val movedAway: List<PlannedWorkout> = emptyList(),
) {
    /** 1 for Monday through 7 for Sunday. */
    val weekday: Int get() = weekdayOf(epochDay)

    /** Every workout that day, of any kind. */
    val count: Int get() = workouts.size + liftWorkouts.size + sports.size

    /** Of [workouts], the mobility: flows, and anything else timed as it. */
    val mobility: Int get() = workouts.count { it.config.activity == Activity.Mobility }

    val seconds: Int get() = workouts.sumOf { it.elapsedSeconds } + liftWorkouts.sumOf { it.elapsedSeconds }

    /** The day's sport, for the label under it: the one logged, else the one planned. */
    val sport: Sport?
        get() = sports.firstOrNull()?.sport
            ?: plans.firstNotNullOfOrNull { (it.plan.workout as? PlanWorkout.SportDay)?.sport }

    val state: DayState
        get() = when {
            count > 0 -> DayState.Done
            plans.any { it.state == PlanState.Missed } -> DayState.Missed
            plans.any { it.state == PlanState.Open && it.plan.workout !is PlanWorkout.SportDay } -> DayState.Planned
            else -> DayState.Rest
        }
}

/** The stretch the History tab's summary covers. */
enum class TrainingRange { Week, Month, AllTime }

/**
 * A range's totals. [plannedDone] of the [plannedDue] plans whose day has come (today's open ones aside, and
 * the sport days that went by unlogged) were done. [firstDay] is the day of the first workout ever, for All time;
 * null without one.
 */
data class RangeStats(
    val count: Int,
    val seconds: Int,
    val calories: Int?,
    val plannedDone: Int,
    val plannedDue: Int,
    val firstDay: Long? = null,
)

/** One week of the weekly bars: its Monday, how many workouts, and whether it is this week. */
data class WeekCount(val weekStart: Long, val count: Int, val current: Boolean)

/**
 * Weeks in a row that met the goal: the run up to now and the longest ever. This week joins the run once
 * it meets the goal and never breaks it while it is still going.
 */
data class Streak(val current: Int, val best: Int)

/** A plan still to do, on [epochDay]. */
data class UpNextPlan(val epochDay: Long, val plan: PlannedWorkout)

/** One workout shape and how often and long it has been done: the most recent run's [config]. */
data class ShapeTally(val config: IntervalConfig, val count: Int, val seconds: Int)

/** One cell of the month calendar: a day, and whether it is in the month shown or pads a week. */
data class MonthCell(val day: TrainingDay, val inMonth: Boolean)

/**
 * The history and the plans, read against the calendar as of [today]. Workouts fall on the day [dayOf]
 * gives their finish (the device's local epoch day), as do the days weekly plans were made: a weekly plan
 * covers its weekday from the day it was made on, a one-off its one day.
 *
 * An interval plan is done by a workout of the same shape that day, a plan of a session on Lift by any
 * session that day, a sport day by that sport logged on it. Each workout does at most one plan, the earliest
 * workout going to the earliest plan, and a workout that matches none is logged all the same.
 *
 * [lifts], the kettlebell sessions, and [sports], the sport days logged, count as workouts everywhere (the days,
 * the weekly counts and the streak) but have no shape, so they are not among the shapes done most; a sport day
 * has no length either, so it adds to no time or calories.
 */
class TrainingLog(
    history: List<HistoryEntry>,
    private val planned: List<PlannedWorkout>,
    private val today: Long,
    lifts: List<LiftWorkout> = emptyList(),
    sports: List<LoggedSport> = emptyList(),
    private val dayOf: (Long) -> Long,
) {
    private val byDay: Map<Long, List<HistoryEntry>> = history
        .sortedWith(compareBy<HistoryEntry> { it.finishedAtEpochMillis }.thenBy { it.id ?: 0 })
        .groupBy { dayOf(it.finishedAtEpochMillis) }

    private val liftsByDay: Map<Long, List<LiftWorkout>> = lifts
        .sortedBy { it.finishedAtEpochMillis }
        .groupBy { dayOf(it.finishedAtEpochMillis) }

    private val sportsByDay: Map<Long, List<LoggedSport>> = sports.sortedBy { it.id }.groupBy { it.epochDay }

    private val madeOn: Map<Long, Long> = planned.associate { it.id to dayOf(it.createdAtEpochMillis) }

    private val firstWorkoutDay: Long? = (byDay.keys + liftsByDay.keys + sportsByDay.keys).minOrNull()

    /** The one-offs moved off each day to another, by the day they left. */
    private val movedOff: Map<Long, List<PlannedWorkout>> = planned
        .filter { plan -> plan.movedFromDay.let { it != null && !plan.fallsOn(it) } }
        .groupBy { it.movedFromDay ?: 0 }

    /**
     * The plans that fall on [epochDay], in the order they were planned, but for a weekly plan's day the user moved
     * elsewhere: each plan moved off the day takes the first weekly plan there of its workout. A one-off moved off
     * remembers only the day, so beside a weekly plan of the same workout that day it takes that one too: rare, and
     * the day still reads as moved.
     */
    fun plansOn(epochDay: Long): List<PlannedWorkout> {
        val falling = planned.filter { plan ->
            plan.fallsOn(epochDay) && (plan.schedule is PlanSchedule.Once || (madeOn[plan.id] ?: epochDay) <= epochDay)
        }.toMutableList()
        movedOff[epochDay].orEmpty().forEach { moved ->
            falling.firstOrNull { it.schedule is PlanSchedule.Weekly && it.workout.sameAs(moved.workout) }
                ?.let(falling::remove)
        }
        return falling
    }

    fun day(epochDay: Long): TrainingDay {
        val workouts = byDay[epochDay].orEmpty()
        val lifts = liftsByDay[epochDay].orEmpty()
        val sports = sportsByDay[epochDay].orEmpty()
        val plans = matchPlans(epochDay, workouts, lifts, sports)
        return TrainingDay(epochDay, epochDay == today, plans, workouts, lifts, sports, movedOff[epochDay].orEmpty())
    }

    /** Monday to Sunday of this week. */
    fun week(): List<TrainingDay> = days(weekStartOf(today), DAYS_PER_WEEK)

    /**
     * The calendar for the month [epochDay] is in: whole weeks, Monday first, from the one holding its first
     * day to the one holding its last.
     */
    fun month(epochDay: Long): List<MonthCell> {
        val month = monthDaysOf(epochDay)
        val start = weekStartOf(month.first)
        val end = weekStartOf(month.last) + DAYS_PER_WEEK - 1
        return days(start, (end - start + 1).toInt()).map { MonthCell(it, it.epochDay in month) }
    }

    /** The totals for [range]: this week, the month [monthOf] is in, or everything. */
    fun stats(range: TrainingRange, body: BodySettings, monthOf: Long = today): RangeStats {
        val span = when (range) {
            TrainingRange.Week -> weekStartOf(today).let { it..it + DAYS_PER_WEEK - 1 }
            TrainingRange.Month -> monthDaysOf(monthOf)
            TrainingRange.AllTime -> allTimeStart()..today
        }
        val workouts = byDay.filterKeys { it in span }.values.flatten()
        val lifts = liftsByDay.filterKeys { it in span }.values.flatten()
        val dueDays = span.first..minOf(span.last, today)
        val due = dueDays.flatMap { day(it).plans }
            .filter { it.state == PlanState.Done || it.state == PlanState.Missed }
        return RangeStats(
            count = workouts.size + lifts.size + sportsByDay.filterKeys { it in span }.values.sumOf { it.size },
            seconds = workouts.sumOf { it.elapsedSeconds } + lifts.sumOf { it.elapsedSeconds },
            calories = body.weightKg?.let {
                workouts.sumOf { it.calories(body) ?: 0 } + lifts.sumOf { it.calories(body) ?: 0 }
            },
            plannedDone = due.count { it.state == PlanState.Done },
            plannedDue = due.size,
            firstDay = if (range == TrainingRange.AllTime) firstWorkoutDay else null,
        )
    }

    /** Workouts a week over the last [weeks] weeks, oldest first, this week last. */
    fun weeklyCounts(weeks: Int): List<WeekCount> {
        val current = weekStartOf(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = current - back * DAYS_PER_WEEK
            WeekCount(start, countInWeek(start), current = back == 0)
        }
    }

    /** The streak against a goal of [goal] workouts a week. */
    fun streak(goal: Int): Streak {
        val first = firstWorkoutDay ?: return Streak(0, 0)
        val current = weekStartOf(today)
        val run = weeksMeetingGoal(current, goal, ::countInWeek)
        var best = 0
        var going = 0
        var start = weekStartOf(first)
        while (start <= current) {
            going = if (countInWeek(start) >= goal) going + 1 else 0
            best = maxOf(best, going)
            start += DAYS_PER_WEEK
        }
        return Streak(current = run, best = maxOf(best, run))
    }

    /** The plans still to do over [count] days from today, by day, then as planned. */
    fun upNext(count: Int = DAYS_PER_WEEK): List<UpNextPlan> = days(today, count).flatMap { day ->
        day.plans.filter { it.state == PlanState.Open }.map { UpNextPlan(day.epochDay, it.plan) }
    }

    /** The [limit] shapes done most often, then for longest. */
    fun mostDone(limit: Int): List<ShapeTally> = byDay.values.flatten()
        // The shape alone, as [sameShape] has it: neither the get-ready countdown nor the activity.
        .groupBy { it.config.copy(prepSeconds = 0, activity = Activity.General) }
        .values
        .map { runs ->
            ShapeTally(
                config = runs.maxBy { it.finishedAtEpochMillis }.config,
                count = runs.size,
                seconds = runs.sumOf { it.elapsedSeconds },
            )
        }
        .sortedWith(compareByDescending<ShapeTally> { it.count }.thenByDescending { it.seconds })
        .take(limit)

    private fun days(from: Long, count: Int): List<TrainingDay> = (0 until count).map { day(from + it) }

    private fun countInWeek(weekStart: Long): Int = (weekStart until weekStart + DAYS_PER_WEEK).sumOf {
        (byDay[it]?.size ?: 0) + (liftsByDay[it]?.size ?: 0) + (sportsByDay[it]?.size ?: 0)
    }

    // The first day anything happened: a workout, or a plan made or falling due.
    private fun allTimeStart(): Long {
        val planDays = planned.map { plan ->
            when (val schedule = plan.schedule) {
                is PlanSchedule.Once -> minOf(schedule.epochDay, madeOn[plan.id] ?: schedule.epochDay)
                is PlanSchedule.Weekly -> madeOn[plan.id] ?: today
            }
        }
        return (planDays + listOfNotNull(firstWorkoutDay, today)).min()
    }

    private fun matchPlans(
        epochDay: Long,
        workouts: List<HistoryEntry>,
        lifts: List<LiftWorkout>,
        sports: List<LoggedSport>,
    ): List<DayPlan> {
        val unused = workouts.toMutableList()
        val unusedLifts = lifts.toMutableList()
        val unusedSports = sports.toMutableList()
        return plansOn(epochDay).map { plan ->
            val done = when (val workout = plan.workout) {
                is PlanWorkout.Interval -> unused.firstOrNull { it.config.sameShape(workout.config) }?.let { match ->
                    unused.remove(match)
                    DayPlan(plan, PlanState.Done, workoutId = match.id)
                }

                is PlanWorkout.Lift -> unusedLifts.firstOrNull()?.let { match ->
                    unusedLifts.remove(match)
                    DayPlan(plan, PlanState.Done, liftUid = match.uid)
                }

                is PlanWorkout.SportDay -> unusedSports.firstOrNull { it.sport == workout.sport }?.let { match ->
                    unusedSports.remove(match)
                    DayPlan(plan, PlanState.Done)
                }
            }
            when {
                done != null -> done
                epochDay >= today -> DayPlan(plan, PlanState.Open)
                plan.workout is PlanWorkout.SportDay -> DayPlan(plan, PlanState.Passed)
                else -> DayPlan(plan, PlanState.Missed)
            }
        }
    }
}
