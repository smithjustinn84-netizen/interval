package dev.justinsmith.interval.plan

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.weekdayOf

/** A workout the add sheet offers: a preset, or whatever is on the Timer. */
data class PlanChoice(val preset: Preset, val onTimer: Boolean = false)

/** A day the add sheet offers for a one-off. */
data class DayChoice(val epochDay: Long, val weekday: Int, val dayOfMonth: Int)

/**
 * What can be planned: the Timer's workout first, under its preset's name or its shape's, then every
 * preset.
 */
fun planChoices(presets: List<Preset>, config: IntervalConfig): List<PlanChoice> {
    val onTimer = presets.matching(config)?.let { Preset(it.name, config, it.id) }
        ?: Preset(config.defaultPresetName(), config)
    return listOf(PlanChoice(onTimer, onTimer = true)) + presets.map { PlanChoice(it) }
}

/** The days a one-off can go on: [first] (today, as a rule) and the [count] − 1 after it. */
fun dayChoices(first: Long, count: Int = ONE_OFF_DAYS): List<DayChoice> =
    (first until first + count).map { DayChoice(it, weekdayOf(it), dateOfEpochDay(it).day) }

/** Two weeks of days to pick a one-off from. */
const val ONE_OFF_DAYS = 14

/** The weekly goal a step up from none starts at. */
const val DEFAULT_WEEKLY_GOAL = 3

/**
 * [goal] raised or lowered by one, within [AppSettings.WEEKLY_GOAL_RANGE]: up from none starts at
 * [DEFAULT_WEEKLY_GOAL], down from the lowest turns it off.
 */
fun steppedGoal(goal: Int?, direction: Int): Int? {
    val range = AppSettings.WEEKLY_GOAL_RANGE
    return when {
        goal == null -> if (direction > 0) DEFAULT_WEEKLY_GOAL else null
        goal + direction < range.first -> null
        else -> (goal + direction).coerceAtMost(range.last)
    }
}
