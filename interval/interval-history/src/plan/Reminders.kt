package dev.justinsmith.interval.plan

import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.platform.PLAN_REMINDER_DAYS
import dev.justinsmith.interval.platform.PlanReminder

/**
 * The reminders the plan calls for, as [log] reads it over [horizonDays] from [today] ([TrainingLog.upNext]):
 * one at [minuteOfDay] on each day with a workout still to do, today's only while [nowMinuteOfDay] has not
 * reached it. A plan done that day is not among the day's, so finishing today's workout takes today's
 * reminder off; one missed is gone with its day.
 */
fun plannedReminders(
    log: TrainingLog,
    minuteOfDay: Int,
    today: Long,
    nowMinuteOfDay: Int,
    horizonDays: Int = PLAN_REMINDER_DAYS,
): List<PlanReminder> = log.upNext(horizonDays)
    .groupBy { it.epochDay }
    .filterKeys { day -> day > today || nowMinuteOfDay < minuteOfDay }
    .map { (day, plans) -> PlanReminder(day, minuteOfDay, plans.map { it.plan }) }
    .sortedBy { it.epochDay }
