package dev.justinsmith.interval.platform

import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.common_joined
import dev.justinsmith.interval.platform.resources.config_cool_down
import dev.justinsmith.interval.platform.resources.config_description
import dev.justinsmith.interval.platform.resources.config_warm_up
import dev.justinsmith.interval.platform.resources.duration_short_minutes
import dev.justinsmith.interval.platform.resources.duration_short_seconds
import dev.justinsmith.interval.platform.resources.reminder_body_one
import dev.justinsmith.interval.platform.resources.reminder_lift_any
import dev.justinsmith.interval.platform.resources.reminder_lift_program_day
import dev.justinsmith.interval.platform.resources.reminder_lift_title
import dev.justinsmith.interval.platform.resources.reminder_sport
import dev.justinsmith.interval.platform.resources.reminder_title_many
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.ShortDuration
import dev.justinsmith.interval.timer.shortDuration
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** What a reminder's notification says: its [title] and the [body] under it. */
data class ReminderWords(val title: String, val body: String)

/**
 * The words for [reminder], in the app's language, the same on every platform: one workout is named, its
 * shape under it ("Tabata" / "Planned today · 20s on · 10s off · ×8"), or a session on Lift as such; several are
 * counted, their names under the count. Android words it as it shows, from the plan as it then stands; iOS, whose
 * notifications carry their words, as it is scheduled.
 */
suspend fun reminderWords(reminder: PlanReminder): ReminderWords {
    val plans = reminder.plans
    val single = plans.singleOrNull()
    return if (single != null) {
        val workout = single.workout
        val what = when (workout) {
            is PlanWorkout.Interval -> workout.config.description()
            PlanWorkout.Lift.ProgramDay -> getString(Res.string.reminder_lift_program_day)
            PlanWorkout.Lift.AnySession -> getString(Res.string.reminder_lift_any)
            is PlanWorkout.SportDay -> workout.reminderName()
        }
        ReminderWords(workout.reminderName(), getString(Res.string.reminder_body_one, what))
    } else {
        ReminderWords(
            getPluralString(Res.plurals.reminder_title_many, plans.size, plans.size),
            plans.map { it.workout.reminderName() }.joined(),
        )
    }
}

/**
 * A plan's name in a reminder: an interval workout's own, or a session on Lift. A day of a sport is never
 * reminded of (`plannedReminders` leaves it out); it is worded all the same, so one that got here says something.
 */
private suspend fun PlanWorkout.reminderName(): String = when (this) {
    is PlanWorkout.Interval -> name
    is PlanWorkout.Lift -> getString(Res.string.reminder_lift_title)
    is PlanWorkout.SportDay -> (sport as? Sport.Custom)?.name ?: getString(Res.string.reminder_sport)
}

/** The shape as the Presets page words it: "20s on · 10s off · ×8", with any warm-up and cool-down either side. */
private suspend fun IntervalConfig.description(): String {
    val shape = getString(
        Res.string.config_description,
        shortDuration(workSeconds).text(),
        shortDuration(restSeconds).text(),
        rounds,
    )
    return listOfNotNull(
        warmUpSeconds.takeIf { it > 0 }?.let { getString(Res.string.config_warm_up, shortDuration(it).text()) },
        shape,
        coolDownSeconds.takeIf { it > 0 }?.let { getString(Res.string.config_cool_down, shortDuration(it).text()) },
    ).joined()
}

private suspend fun ShortDuration.text(): String = when (this) {
    is ShortDuration.Seconds -> getString(Res.string.duration_short_seconds, seconds)
    is ShortDuration.Minutes -> getString(Res.string.duration_short_minutes, minutes)
}

/** [this] run together with the app's separator: "Tabata · HIIT 30/30". */
private suspend fun List<String>.joined(): String =
    drop(1).fold(first()) { joined, next -> getString(Res.string.common_joined, joined, next) }
