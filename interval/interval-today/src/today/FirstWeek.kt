package dev.justinsmith.interval.today

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity

// Today's First week card: the few things that teach the app by being done once, ticked from what the log, the
// plan and Lift already hold. Nothing of it is stored but whether the card is on (`AppSettings.firstWeek`).

/** One thing to do in the first week, and whether it is done. */
data class FirstWeekStep(
    val kind: Kind,
    val done: Boolean,
    /** The sport a [Kind.Sport] step logs a day of. */
    val sport: Sport? = null,
) {
    enum class Kind {
        /** A week planned or a program followed: Set up's work, or the user's own. */
        Build,

        /** A workout or a session finished. */
        Workout,

        /** A mobility flow followed to its end. */
        Flow,

        /** A day of a sport logged; asked only of someone who has one planned or logged. */
        Sport,
    }
}

/** The card's steps in the order it lists them. */
data class FirstWeek(val steps: List<FirstWeekStep>) {
    val done: Int get() = steps.count { it.done }

    /** Every step done: the card says so and waits to be put away. */
    val finished: Boolean get() = done == steps.size
}

/**
 * The first week as it stands, from what is [planned] or [following] a program, the [history] of workouts and
 * flows, whether Lift has a session [lifted], and the [sports] days logged. The sport is the first one planned,
 * else the first logged; with neither, nothing is asked of one.
 */
internal fun firstWeek(
    planned: List<PlannedWorkout>,
    following: Boolean,
    history: List<HistoryEntry>,
    lifted: Boolean,
    sports: List<LoggedSport>,
): FirstWeek {
    val sport = planned.firstNotNullOfOrNull { (it.workout as? PlanWorkout.SportDay)?.sport }
        ?: sports.firstOrNull()?.sport
    val flows = history.count { it.config.activity == Activity.Mobility }
    return FirstWeek(
        listOfNotNull(
            FirstWeekStep(FirstWeekStep.Kind.Build, done = planned.isNotEmpty() || following),
            FirstWeekStep(FirstWeekStep.Kind.Workout, done = lifted || history.size > flows),
            FirstWeekStep(FirstWeekStep.Kind.Flow, done = flows > 0),
            sport?.let { FirstWeekStep(FirstWeekStep.Kind.Sport, done = sports.isNotEmpty(), sport = it) },
        ),
    )
}
