package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.monthDaysOf
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.timer.weeksMeetingGoal

/** How one planned workout stands on one day. */
enum class PlanState {
    /** A workout of its shape finished that day. */
    Done,

    /** Its day has gone by without one. */
    Missed,

    /** Today or later, not done yet. */
    Open,
}

/** A planned workout on one day and how it stands; [workoutId] is the workout that did it, if any. */
data class DayPlan(val plan: PlannedWorkout, val state: PlanState, val workoutId: Long? = null)

/** What a day comes to, for the week strip and the calendar. */
enum class DayState {
    /** At least one workout finished. */
    Done,

    /** Today or later, with a plan still to do. */
    Planned,

    /** Gone by, with a plan not done and no workout at all. */
    Missed,

    /** Nothing done and nothing planned. */
    Rest,
}

/**
 * One day of training: its plans as they stand and its workouts, oldest first, the interval workouts and
 * then the kettlebell sessions ([liftWorkouts]). Both count toward the day; only an interval workout can
 * do a plan.
 */
data class TrainingDay(
    val epochDay: Long,
    val isToday: Boolean,
    val plans: List<DayPlan>,
    val workouts: List<HistoryEntry>,
    val liftWorkouts: List<LiftWorkout> = emptyList(),
) {
    /** 1 for Monday through 7 for Sunday. */
    val weekday: Int get() = weekdayOf(epochDay)

    /** Every workout that day, of either kind. */
    val count: Int get() = workouts.size + liftWorkouts.size

    val seconds: Int get() = workouts.sumOf { it.elapsedSeconds } + liftWorkouts.sumOf { it.elapsedSeconds }

    val state: DayState
        get() = when {
            count > 0 -> DayState.Done
            plans.any { it.state == PlanState.Missed } -> DayState.Missed
            plans.isNotEmpty() -> DayState.Planned
            else -> DayState.Rest
        }
}

/** The stretch the History tab's summary covers. */
enum class TrainingRange { Week, Month, AllTime }

/**
 * A range's totals. [plannedDone] of the [plannedDue] plans whose day has come (today's open ones aside)
 * were done. [firstDay] is the day of the first workout ever, for All time; null without one.
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
 * A plan is done by a workout of the same shape that day. Each workout does at most one plan, the earliest
 * workout going to the earliest plan, and a workout that matches none is logged all the same.
 *
 * [lifts], the kettlebell sessions, count as workouts everywhere (the days, the totals, the weekly
 * counts and the streak) but have no shape, so they do no plan and are not among the shapes done most.
 */
class TrainingLog(
    history: List<HistoryEntry>,
    private val planned: List<PlannedWorkout>,
    private val today: Long,
    lifts: List<LiftWorkout> = emptyList(),
    private val dayOf: (Long) -> Long,
) {
    private val byDay: Map<Long, List<HistoryEntry>> = history
        .sortedWith(compareBy<HistoryEntry> { it.finishedAtEpochMillis }.thenBy { it.id ?: 0 })
        .groupBy { dayOf(it.finishedAtEpochMillis) }

    private val liftsByDay: Map<Long, List<LiftWorkout>> = lifts
        .sortedBy { it.finishedAtEpochMillis }
        .groupBy { dayOf(it.finishedAtEpochMillis) }

    private val madeOn: Map<Long, Long> = planned.associate { it.id to dayOf(it.createdAtEpochMillis) }

    private val firstWorkoutDay: Long? = (byDay.keys + liftsByDay.keys).minOrNull()

    /** The plans that fall on [epochDay], in the order they were planned. */
    fun plansOn(epochDay: Long): List<PlannedWorkout> = planned.filter { plan ->
        plan.fallsOn(epochDay) && (plan.schedule is PlanSchedule.Once || (madeOn[plan.id] ?: epochDay) <= epochDay)
    }

    fun day(epochDay: Long): TrainingDay {
        val workouts = byDay[epochDay].orEmpty()
        return TrainingDay(
            epochDay,
            epochDay == today,
            matchPlans(epochDay, workouts),
            workouts,
            liftsByDay[epochDay].orEmpty(),
        )
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
        val due = dueDays.flatMap { day(it).plans }.filter { it.state != PlanState.Open }
        return RangeStats(
            count = workouts.size + lifts.size,
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
        .groupBy { Triple(it.config.workSeconds, it.config.restSeconds, it.config.rounds) }
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

    private fun countInWeek(weekStart: Long): Int =
        (weekStart until weekStart + DAYS_PER_WEEK).sumOf { (byDay[it]?.size ?: 0) + (liftsByDay[it]?.size ?: 0) }

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

    private fun matchPlans(epochDay: Long, workouts: List<HistoryEntry>): List<DayPlan> {
        val unused = workouts.toMutableList()
        return plansOn(epochDay).map { plan ->
            val match = unused.firstOrNull { it.config.sameShape(plan.config) }
            when {
                match != null -> {
                    unused.remove(match)
                    DayPlan(plan, PlanState.Done, match.id)
                }

                epochDay < today -> DayPlan(plan, PlanState.Missed)

                else -> DayPlan(plan, PlanState.Open)
            }
        }
    }
}
