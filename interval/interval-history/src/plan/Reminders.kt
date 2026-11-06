package dev.justinsmith.interval.plan

import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.UpNextPlan
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.platform.PLAN_REMINDER_DAYS
import dev.justinsmith.interval.platform.PlanReminder

/**
 * The reminders the plan calls for, as [log] reads it over [horizonDays] from [today] ([TrainingLog.upNext]):
 * one at [minuteOfDay] on each day with a workout still to do, today's only while [nowMinuteOfDay] has not
 * reached it. A plan done that day is not among the day's, so finishing today's workout takes today's
 * reminder off; one missed is gone with its day. A day of a sport is no workout to be reminded of: a class has
 * its own hour, which the app doesn't know.
 */
fun plannedReminders(
    log: TrainingLog,
    minuteOfDay: Int,
    today: Long,
    nowMinuteOfDay: Int,
    horizonDays: Int = PLAN_REMINDER_DAYS,
): List<PlanReminder> = log.workoutsUpNext(horizonDays)
    .groupBy { it.epochDay }
    .filterKeys { day -> day > today || nowMinuteOfDay < minuteOfDay }
    .map { (day, plans) -> PlanReminder(day, minuteOfDay, plans.map { it.plan }) }
    .sortedBy { it.epochDay }

/**
 * The reminder on [epochDay] as [log] reads it now, for one showing: the day's workouts still to do, at
 * [minuteOfDay] whether or not it has passed. Null for a day with none left, or one already gone.
 */
fun reminderOn(log: TrainingLog, minuteOfDay: Int, epochDay: Long): PlanReminder? =
    log.workoutsUpNext(PLAN_REMINDER_DAYS)
        .filter { it.epochDay == epochDay }
        .map { it.plan }
        .takeIf { it.isNotEmpty() }
        ?.let { PlanReminder(epochDay, minuteOfDay, it) }

private fun TrainingLog.workoutsUpNext(days: Int): List<UpNextPlan> =
    upNext(days).filter { it.plan.workout !is PlanWorkout.SportDay }
