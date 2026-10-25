package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.estimateCalories

/** Which feedback plays during a workout. */
data class CueSettings(
    val sound: Boolean = true,
    val voice: Boolean = true,
    val halfway: Boolean = true,
    val vibrate: Boolean = true,
)

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/**
 * What the calorie estimates are worked out from. [weightKg] starts at [WeightUnit.Kg]'s default, so the
 * estimates show from the first run; null means the user turned them off.
 */
data class BodySettings(
    val weightKg: Double? = WeightUnit.Kg.default.toDouble(),
    val unit: WeightUnit = WeightUnit.Kg,
    val effort: Effort = Effort.Hard,
) {
    /**
     * The calories of [workSeconds] of [activity] at [effort] and [otherSeconds] recovering, or null
     * without a weight.
     */
    fun calories(workSeconds: Int, otherSeconds: Int, activity: Activity): Int? =
        weightKg?.let { estimateCalories(workSeconds, otherSeconds, it, effort, activity) }

    /** What [config] will burn if it runs as planned, get ready included; null without a weight. */
    fun plannedCalories(config: IntervalConfig): Int? {
        val work = config.rounds * config.workSeconds
        return calories(work, config.totalSeconds - work, config.activity)
    }
}

/** App-wide preferences that are not part of a workout's shape. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val keepScreenOn: Boolean = true,
    val body: BodySettings = BodySettings(),
    /** Workouts a week the user aims for, within [WEEKLY_GOAL_RANGE]; null until they set one. */
    val weeklyGoal: Int? = null,
) {
    companion object {
        val WEEKLY_GOAL_RANGE = 1..14
    }
}
